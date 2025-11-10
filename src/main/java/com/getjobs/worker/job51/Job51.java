package com.getjobs.worker.job51;

import com.getjobs.application.service.Job51Service;
import com.getjobs.worker.utils.JobUtils;
import com.getjobs.worker.utils.PlaywrightUtil;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitForSelectorState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * @author loks666
 * 项目链接: <a href="https://github.com/loks666/get_jobs">https://github.com/loks666/get_jobs</a>
 * 前程无忧自动投递简历 - Playwright版本
 */
@Slf4j
@Component
@Scope("prototype")
@RequiredArgsConstructor
public class Job51 {

    private Page page;

    private Job51Config config;

    private ProgressCallback progressCallback;

    private Supplier<Boolean> shouldStopCallback;

    private final List<String> resultList = new ArrayList<>();
    private final Job51Service job51Service;
    private boolean networkHooked = false;
    private boolean reachedDailyLimit = false;
    private final java.util.Set<String> processedRequestIds = new java.util.HashSet<>();
    private int currentPageNum = 0;

    private static final int DEFAULT_MAX_PAGE = 50;
    private static final String BASE_URL = "https://we.51job.com/pc/search?";

    // 显式setter，避免对 Lombok 的依赖导致编译问题
    public void setPage(Page page) { this.page = page; }
    public void setConfig(Job51Config config) { this.config = config; }
    public void setProgressCallback(ProgressCallback progressCallback) { this.progressCallback = progressCallback; }
    public void setShouldStopCallback(Supplier<Boolean> shouldStopCallback) { this.shouldStopCallback = shouldStopCallback; }

    /**
     * 进度回调接口
     */
    @FunctionalInterface
    public interface ProgressCallback {
        void accept(String message, Integer current, Integer total);
    }

    /**
     * 准备工作：加载配置、初始化数据
     */
    public void prepare() {
        log.info("51job准备工作开始...");
        resultList.clear();
        log.info("51job准备工作完成");
    }

    /**
     * 执行投递任务
     * @return 投递数量
     */
    public int execute() {
        log.info("51job投递任务开始...");
        long startTime = System.currentTimeMillis();

        try {
            // 遍历所有关键词进行投递
            for (String keyword : config.getKeywords()) {
                if (shouldStop()) {
                    sendProgress("用户取消投递", null, null);
                    break;
                }

                String searchUrl = buildSearchUrl(keyword);
                deliverByKeyword(keyword, searchUrl);
            }

            long duration = System.currentTimeMillis() - startTime;
            String message = String.format("51job投递完成，共投递%d个简历，用时%s",
                resultList.size(), formatDuration(duration));
            log.info(message);
            sendProgress(message, null, null);

        } catch (Exception e) {
            log.error("51job投递过程出现异常", e);
            sendProgress("投递出现异常: " + e.getMessage(), null, null);
        }

        return resultList.size();
    }

    /**
     * 按关键词投递
     */
    private void deliverByKeyword(String keyword, String searchUrl) {
        try {
            // 收敛日志：不输出关键词级日志，仅保留页级摘要

            // 在跳转前监听 51job 搜索接口，抓取 JSON 并保存到数据库 + 打印诊断日志
            if (!networkHooked) {
                try {
                    page.onResponse(r -> {
                        try {
                            String url = r.url();
                            if (url != null && url.contains("/api/job/search-pc") && "GET".equalsIgnoreCase(r.request().method())) {
                                int status = 0;
                                try { status = r.status(); } catch (Throwable ignored) {}
                                String text = null;
                                try { text = r.text(); } catch (Throwable ignored) {}
                                int len = text == null ? 0 : text.length();
                                // 基于 URL 的 requestId 做去重，避免重复解析
                                String requestId = null;
                                try {
                                    java.net.URI u = new java.net.URI(url);
                                    String q = u.getQuery();
                                    if (q != null) {
                                        for (String part : q.split("&")) {
                                            int i = part.indexOf('=');
                                            if (i > 0 && "requestId".equals(part.substring(0, i))) {
                                                requestId = java.net.URLDecoder.decode(part.substring(i + 1), java.nio.charset.StandardCharsets.UTF_8);
                                                break;
                                            }
                                        }
                                    }
                                } catch (Exception ignored) {}
                                if (requestId != null && !requestId.isBlank() && processedRequestIds.contains(requestId)) {
                                    return;
                                }
                                if (text != null) {
                                    // 根据 Content-Type 粗判是否为 JSON
                                    boolean isJson = false;
                                    try {
                                        java.util.Map<String, String> headers = r.headers();
                                        if (headers != null) {
                                            String ct = headers.getOrDefault("content-type", headers.get("Content-Type"));
                                            if (ct != null && ct.toLowerCase().contains("json")) isJson = true;
                                        }
                                    } catch (Throwable ignored) {}
                                    if (isJson) {
                                        job51Service.parseAndPersistJob51SearchJson(text);
                                        if (requestId != null && !requestId.isBlank()) processedRequestIds.add(requestId);
                                    } // 非JSON静默跳过
                                }
                            }
                        } catch (Throwable e) {
                            // 静默错误
                        }
                    });
                    networkHooked = true;
                } catch (Throwable e) {
                    // 静默错误
                }
            }

            // 导航到搜索页面
            try {
                java.util.Map<String, String> headers = new java.util.HashMap<>();
                headers.put("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8");
                headers.put("Accept-Language", "zh-CN,zh;q=0.9");
                headers.put("Sec-Fetch-Site", "same-site");
                headers.put("Sec-Fetch-Mode", "navigate");
                page.setExtraHTTPHeaders(headers);
            } catch (Throwable ignored) {}
            page.navigate(searchUrl);
            PlaywrightUtil.sleep(1);

            // 检查是否需要登录
            if (checkNeedLogin()) {
                sendProgress("需要重新登录，跳过关键词: " + keyword, null, null);
                return;
            }

            // 点击排序选项（选择第一个排序方式）
            try {
                Locator sortOptions = page.locator("div.ss");
                if (sortOptions.count() > 0) {
                    sortOptions.first().click();
                    PlaywrightUtil.sleep(1);
                }
            } catch (Exception e) { /* 静默 */ }

            // 遍历页面投递
            for (int pageNum = 1; pageNum <= DEFAULT_MAX_PAGE; pageNum++) {
                if (shouldStop()) {
                    sendProgress("用户取消投递", null, null);
                    return;
                }

                sendProgress(String.format("正在投递第%d页", pageNum), pageNum, DEFAULT_MAX_PAGE);
                currentPageNum = pageNum;

                // 跳转到指定页码
                if (pageNum > 1 && !jumpToPage(pageNum)) {
                    break;
                }

                PlaywrightUtil.sleep(2);

                // 检查是否出现访问验证
                if (checkAccessVerification()) {
                    sendProgress("出现访问验证，停止投递", null, null);
                    return;
                }

                // 检测“无职位”文案，提前结束当前关键词
                try {
                    if (detectNoJobs51job()) {
                        sendProgress("该关键词暂无职位，提前结束", null, null);
                        break;
                    }
                } catch (Exception ignored) {}

                // 投递当前页面的所有职位
                deliverCurrentPage();
                if (reachedDailyLimit) break;

                PlaywrightUtil.sleep(3);
            }

            // 关键词完成不输出日志
        } catch (Exception e) { /* 静默 */ }
    }

    /**
     * 投递当前页面的所有职位
     */
    private void deliverCurrentPage() {
        try {
            PlaywrightUtil.sleep(1);

            // 查找所有职位的checkbox
            Locator checkboxes = page.locator("div.ick");
            if (checkboxes.count() == 0) { return; }

            // 查找职位名称和公司名称
            Locator titles = page.locator("[class*='jname text-cut']");
            Locator companies = page.locator("[class*='cname text-cut']");

            int jobCount = checkboxes.count();

            // 选中所有职位
            for (int i = 0; i < jobCount; i++) {
                if (shouldStop()) {
                    return;
                }

                try {
                    Locator checkbox = checkboxes.nth(i);
                    // 使用JavaScript点击，避免元素被遮挡
                    checkbox.evaluate("el => el.click()");

                    String title = i < titles.count() ? titles.nth(i).textContent() : "未知职位";
                    String company = i < companies.count() ? companies.nth(i).textContent() : "未知公司";
                    String jobInfo = company + " | " + title;
                    resultList.add(jobInfo);
//                    log.info("选中: {}", jobInfo);
                } catch (Exception e) { /* 静默 */ }
            }

            PlaywrightUtil.sleep(1);

            // 滚动到页面顶部
            page.evaluate("window.scrollTo(0, 0)");
            PlaywrightUtil.sleep(1);

            // 点击批量投递按钮
            clickBatchDeliverButton();

            PlaywrightUtil.sleep(3);

            // 处理投递成功弹窗
            handleDeliverySuccessDialog();

            // 处理单独投递申请弹窗
            handleSeparateDeliveryDialog();

            // 投递状态写回：采集当前页 jobId 并标记 delivered=1
            try {
                List<Long> deliveredIds = collectJobIdsOnPage();
                if (!deliveredIds.isEmpty()) {
                    job51Service.markDeliveredBatch(deliveredIds);
                }
            } catch (Exception e) { /* 静默 */ }

        } catch (Exception e) {
            log.error("投递当前页面失败", e);
        }
    }

    /**
     * 点击批量投递按钮
     */
    private void clickBatchDeliverButton() {
        int retryCount = 0;
        boolean success = false;

        while (!success && retryCount < 5) {
            try {
                if (shouldStop()) {
                    return;
                }

                // 查找批量投递按钮
                Locator parent = page.locator("div.tabs_in");
                Locator buttons = parent.locator("button.p_but");

                if (buttons.count() > 1) {
                    PlaywrightUtil.sleep(1);
                    buttons.nth(1).click();
                    success = true;
                } else {
                    break;
                }
            } catch (Exception e) {
                retryCount++;
                PlaywrightUtil.sleep(1);
            }
        }
    }

    /**
     * 处理投递成功弹窗
     */
    private void handleDeliverySuccessDialog() {
        try {
            PlaywrightUtil.sleep(2);

            Locator successContent = page.locator("//div[@class='successContent']");
            if (successContent.count() > 0) {
                String text = successContent.textContent();
                if (text != null && text.contains("快来扫码下载")) {
                    log.info("检测到下载App弹窗，关闭中...");
                    // 关闭弹窗
                    Locator closeButton = page.locator("[class*='van-icon van-icon-cross van-popup__close-icon van-popup__close-icon--top-right']");
                    if (closeButton.count() > 0) {
                        closeButton.click();
                        log.info("成功关闭下载App弹窗");
                    }
                }
            }

            // 兼容提示弹框：投递成功N个，未投递M个（更稳健选择器）
            Locator elDialogBody = page.locator(".el-dialog__body");
            if (elDialogBody.count() > 0) {
                String dialogText = elDialogBody.first().innerText();
                if (dialogText != null && dialogText.contains("投递成功")) {
                    Integer successNum = null;
                    Integer failNum = null;
                    try {
                        java.util.regex.Matcher m1 = java.util.regex.Pattern.compile("投递成功\\D*(\\d+)").matcher(dialogText);
                        if (m1.find()) successNum = Integer.parseInt(m1.group(1));
                        java.util.regex.Matcher m2 = java.util.regex.Pattern.compile("未投递\\D*(\\d+)").matcher(dialogText);
                        if (m2.find()) failNum = Integer.parseInt(m2.group(1));
                    } catch (Exception ignored) {}
                    log.info("[51job] 投递结果：成功 {} 个，未投递 {} 个", successNum, failNum);
                    sendProgress(String.format("投递结果：成功 %s 个，未投递 %s 个", successNum == null ? "?" : successNum, failNum == null ? "?" : failNum), null, null);

                    // 优先点击“确定/关闭”按钮，其次点右上角关闭，再次退格键
                    try {
                        Locator okBtn = page.locator(".el-dialog__footer button:has-text('确定'), .el-message-box__btns button:has-text('确定')");
                        if (okBtn.count() > 0) {
                            okBtn.first().click();
                        } else {
                            // 1) 点击关闭图标的父按钮
                            Locator iconClose = page.locator("i.el-dialog__close.el-icon.el-icon-close");
                            boolean closed = false;
                            if (iconClose.count() > 0 && iconClose.first().isVisible()) {
                                try {
                                    // 有些站点图标本身不接收点击，点击其父按钮更稳定
                                    iconClose.first().evaluate("el => el.parentElement && el.parentElement.click()");
                                    closed = true;
                                } catch (Exception ignored) {}
                            }
                            // 2) 直接点击 header 关闭按钮（带 aria-label="Close"）
                            if (!closed) {
                                Locator headerBtn = page.locator("button.el-dialog__headerbtn, .el-dialog__header button.el-dialog__headerbtn, button[aria-label='Close']");
                                if (headerBtn.count() > 0 && headerBtn.first().isVisible()) {
                                    try {
                                        headerBtn.first().click(new Locator.ClickOptions().setForce(true).setTimeout(2000));
                                        closed = true;
                                    } catch (Exception ignored) {}
                                }
                            }
                            // 3) JS 兜底点击
                            if (!closed) {
                                try {
                                    page.evaluate("document.querySelector('button.el-dialog__headerbtn')?.click() || document.querySelector('button[aria-label=\\'Close\\']')?.click() ");
                                    closed = true;
                                } catch (Exception ignored) {}
                            }
                            // 4) 最终兜底：按 ESC
                            if (!closed) {
                                page.keyboard().press("Escape");
                            }
                        }
                        PlaywrightUtil.sleep(1);
                    } catch (Exception ignored) {}
                }
            }

            // 统一尝试关闭任何残留的弹框覆盖层
            closeAnyModalOverlays();
            // 弹窗处理后再次检测是否出现“日投递上限”提示
            try {
                if (detectDailyLimitToast51job()) {
                    reachedDailyLimit = true;
                    log.warn("处理成功弹窗后，检测到 51job 日投递上限提示，停止当前页");
                }
            } catch (Exception ignored) {}
        } catch (Exception e) {
            log.debug("未找到投递成功弹窗或处理失败: {}", e.getMessage());
        }
    }

    /**
     * 处理单独投递申请弹窗
     */
    private void handleSeparateDeliveryDialog() {
        try {
            Locator dialogContent = page.locator("//div[@class='el-dialog__body']/span");
            if (dialogContent.count() > 0) {
                String text = dialogContent.textContent();
                if (text != null && text.contains("需要到企业招聘平台单独申请")) {
                    log.info("检测到单独投递申请弹窗，关闭中...");
                    // 关闭弹窗
                    Locator closeButton = page.locator("#app > div > div.post > div > div > div.j_result > div > div:nth-child(2) > div > div:nth-child(2) > div:nth-child(2) > div > div.el-dialog__header > button > i");
                    if (closeButton.count() > 0) {
                        closeButton.click();
                        log.info("成功关闭单独投递申请弹窗");
                    }
                }
            }
        } catch (Exception e) {
            log.debug("未找到单独投递申请弹窗或处理失败: {}", e.getMessage());
        }
    }

    /**
     * 跳转到指定页码
     */
    private boolean jumpToPage(int pageNum) {
        for (int retry = 0; retry < 3; retry++) {
            try {
                if (shouldStop()) {
                    return false;
                }

                // 跳页前先尝试关闭可能遮挡操作的弹框
                closeAnyModalOverlays();

                Locator pageInput = page.locator("#jump_page");
                if (pageInput.count() == 0) {
                    log.warn("未找到页码输入框");
                    return false;
                }

                PlaywrightUtil.sleep(1);
                pageInput.click();
                pageInput.fill("");
                pageInput.fill(String.valueOf(pageNum));

                // 点击跳转按钮
                Locator jumpButton = page.locator("#app > div > div.post > div > div > div.j_result > div > div:nth-child(2) > div > div.bottom-page > div > div > span.jumpPage");
                if (jumpButton.count() > 0) {
                    jumpButton.click();
                }

                // 滚动到页面顶部
                page.evaluate("window.scrollTo(0, 0)");
                PlaywrightUtil.sleep(2);

                log.info("成功跳转到第{}页", pageNum);
                return true;
            } catch (Exception e) {
                log.warn("跳转到第{}页失败，重试第{}次: {}", pageNum, retry + 1, e.getMessage());
                PlaywrightUtil.sleep(1);

                // 检查是否出现异常，如果出现则刷新页面
                if (checkAccessVerification()) {
                    return false;
                }
                page.reload();
                PlaywrightUtil.sleep(2);
            }
        }
        return false;
    }

    /**
     * 统一关闭可能出现的弹框覆盖层（ElementUI/VanPopup 等）。
     */
    private void closeAnyModalOverlays() {
        try {
            boolean closedOnce = false;
            for (int t = 0; t < 3; t++) {
                boolean closedThisRound = false;
                Locator headerClose = page.locator("button.el-dialog__headerbtn, button[aria-label='Close']");
                if (headerClose.count() > 0 && headerClose.first().isVisible()) {
                    try {
                        headerClose.first().click(new Locator.ClickOptions().setForce(true).setTimeout(2000));
                        closedThisRound = true;
                    } catch (Exception ignored) {}
                }
                // 直接点击关闭图标或其父按钮
                Locator iconClose = page.locator("i.el-dialog__close.el-icon.el-icon-close");
                if (iconClose.count() > 0 && iconClose.first().isVisible()) {
                    try {
                        iconClose.first().evaluate("el => el.parentElement && el.parentElement.click()");
                        closedThisRound = true;
                    } catch (Exception ignored) {}
                }
                Locator okBtn = page.locator(".el-dialog__footer button:has-text('确定'), .el-message-box__btns button:has-text('确定')");
                if (okBtn.count() > 0 && okBtn.first().isVisible()) {
                    okBtn.first().click();
                    closedThisRound = true;
                }
                // JS 一次性点击所有可能的关闭按钮，作为强兜底
                if (!closedThisRound) {
                    try {
                        page.evaluate("document.querySelectorAll('button.el-dialog__headerbtn, button[aria-label=\\'Close\\']').forEach(b=>b.click())");
                    } catch (Exception ignored) {}
                }
                Locator popupClose = page.locator(".van-popup__close-icon, .van-icon-cross");
                if (popupClose.count() > 0 && popupClose.first().isVisible()) {
                    popupClose.first().click();
                    closedThisRound = true;
                }
                if (!closedThisRound) break;
                closedOnce = true;
                PlaywrightUtil.sleep(1);
            }
            if (closedOnce) {
                // 等待弹层移除
                try { page.waitForSelector(".el-dialog__wrapper, .van-popup", new Page.WaitForSelectorOptions().setState(WaitForSelectorState.DETACHED).setTimeout(2000)); } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            log.debug("关闭弹框覆盖层失败: {}", e.getMessage());
        }
    }

    /**
     * 检查是否需要登录
     */
    private boolean checkNeedLogin() {
        try {
            Locator loginElement = page.locator("//a[contains(@class, 'uname')]");
            if (loginElement.count() > 0) {
                String text = loginElement.textContent();
                return text != null && text.contains("登录");
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 检测 51job 页面是否出现“日投递上限”提示的浮框（短暂存在，需及时检查）。
     */
    private boolean detectDailyLimitToast51job() {
        try {
            String[] kws = new String[]{
                    "今日投递太多", "您今日投递太多", "休息一下明天再来", "达到上限", "次数过多"
            };
            for (String kw : kws) {
                Locator textToast = page.locator("text=" + kw);
                if (textToast.count() > 0 && textToast.first().isVisible()) {
                    return true;
                }
            }
            Locator msg = page.locator(".el-message, .el-message--info, .toast, .message, div[role='alert'], .el-notification__content");
            if (msg.count() > 0) {
                java.util.List<String> texts = new java.util.ArrayList<>();
                try { texts = msg.allInnerTexts(); } catch (Exception ignored) {}
                for (String t : texts) {
                    if (t == null) continue;
                    String tt = t.replace('\n', ' ').trim();
                    for (String kw : kws) {
                        if (tt.contains(kw)) return true;
                    }
                }
            }
            Object foundObj = page.evaluate("() => { const kws = ['今日投递太多','您今日投递太多','休息一下明天再来','达到上限','次数过多']; const bodyText = document.body ? (document.body.innerText || '') : ''; return kws.some(k=>bodyText.includes(k)); }");
            if (foundObj instanceof Boolean) {
                return (Boolean) foundObj;
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 检查是否出现访问验证
     */
    private boolean checkAccessVerification() {
        try {
            Locator wafTitle = page.locator("//p[@class='waf-nc-title']");
            Locator wafScript = page.locator("script[name^='aliyunwaf_']");
            Locator verifyText = page.locator("text=访问验证, text=请按住滑块");
            if ((wafTitle.count() > 0 && wafTitle.first().isVisible()) || wafScript.count() > 0 || verifyText.count() > 0) {
                log.error("出现访问验证，需要手动处理");
                return true;
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 检测 51job 页面是否显示“无职位/没有符合条件的职位”等提示。
     */
    private boolean detectNoJobs51job() {
        try {
            String[] kws = new String[]{
                    "暂无职位", "没有符合条件的职位", "暂无符合条件职位", "暂无符合职位", "暂无相关职位"
            };
            for (String kw : kws) {
                Locator t = page.locator("text=" + kw);
                if (t.count() > 0 && t.first().isVisible()) {
                    return true;
                }
            }
            // 常见空态容器（若存在则进一步通过文本确认）
            Locator empty = page.locator(".el-empty, .empty, .no-result, .no_res");
            if (empty.count() > 0) {
                java.util.List<String> texts = new java.util.ArrayList<>();
                try { texts = empty.allInnerTexts(); } catch (Exception ignored) {}
                for (String t : texts) {
                    if (t == null) continue;
                    String tt = t.replace('\n', ' ').trim();
                    for (String kw : kws) {
                        if (tt.contains(kw)) return true;
                    }
                }
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 构建搜索URL
     */
    private String buildSearchUrl(String keyword) {
        StringBuilder url = new StringBuilder(BASE_URL);
        url.append(JobUtils.appendListParam("jobArea", config.getJobArea()));
        url.append(JobUtils.appendListParam("salary", config.getSalary()));
        url.append("&keyword=").append(keyword);
        return url.toString();
    }

    /**
     * 采集当前页所有岗位的 jobId（解析 jobdetail 链接/数据属性）
     */
    private List<Long> collectJobIdsOnPage() {
        List<Long> ids = new ArrayList<>();
        try {
            // 1) 解析常见 jobdetail 链接形态
            Locator anchors = page.locator(
                    "a[href*='/pc/jobdetail?jobId='], " +
                    "a[href*='/pc/jobdetail'], " +
                    "a[href*='jobs.51job.com/'], " +
                    "a.jname[href]"
            );
            int count = anchors.count();
            for (int i = 0; i < count; i++) {
                try {
                    String href = anchors.nth(i).getAttribute("href");
                    Long id = parseJobIdFromHref(href);
                    if (id != null) ids.add(id);
                } catch (Exception ignored) {}
            }

            // 2) 解析卡片上的数据属性（部分页面存在）
            try {
                Locator cards = page.locator("[data-jobid], [data-analysis-jobid], [data-job-id]");
                int c = cards.count();
                for (int i = 0; i < c; i++) {
                    try {
                        String v = null;
                        Locator card = cards.nth(i);
                        v = v == null ? card.getAttribute("data-jobid") : v;
                        v = v == null ? card.getAttribute("data-analysis-jobid") : v;
                        v = v == null ? card.getAttribute("data-job-id") : v;
                        if (v != null) {
                            try {
                                Long id = Long.parseLong(v.replaceAll("[^0-9]", ""));
                                if (id != null) ids.add(id);
                            } catch (Exception ignored) {}
                        }
                    } catch (Exception ignored) {}
                }
            } catch (Exception ignored) {}

            // 去重
            java.util.Set<Long> uniq = new java.util.LinkedHashSet<>(ids);
            ids = new java.util.ArrayList<>(uniq);

            // 记录采集到的数量与部分样例，便于诊断
            try {
                if (!ids.isEmpty()) {
                    String sample = ids.stream().limit(5).map(String::valueOf).collect(java.util.stream.Collectors.joining(", "));
                    log.info("[51job] 当前页采集到 {} 个 jobId, 示例: {}", ids.size(), sample);
                } else {
                    // 采集为空：按用户约定视为达到投递上限/页面结构变化，立即通知并停止
                    log.warn("[51job] 当前页未采集到任何 jobId，可能页面结构变化或选择器不匹配");
                    // 向前端推送警告，便于按钮重置
                    sendProgress("[51job] 当前页未采集到任何 jobId，可能页面结构变化或选择器不匹配", null, null);
                    // 设置达上限标记，外层循环将终止
                    reachedDailyLimit = true;
                }
            } catch (Exception ignored) {}
        } catch (Exception e) {
            log.debug("采集当前页 jobId 失败: {}", e.getMessage());
        }
        return ids;
    }

    private Long parseJobIdFromHref(String href) {
        if (href == null || href.isEmpty()) return null;
        try {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("[?&]jobId=(\\d+)").matcher(href);
            if (m.find()) {
                return Long.parseLong(m.group(1));
            }
            java.util.regex.Matcher m2 = java.util.regex.Pattern.compile("/(\\d+)\\.html").matcher(href);
            if (m2.find()) {
                return Long.parseLong(m2.group(1));
            }
            // 兜底：从路径段中找较长数字片段
            java.util.regex.Matcher m3 = java.util.regex.Pattern.compile("(\\d{5,})").matcher(href);
            if (m3.find()) {
                return Long.parseLong(m3.group(1));
            }
        } catch (Exception ignored) {}
        return null;
    }

    /**
     * 格式化时长
     */
    private String formatDuration(long millis) {
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;

        if (hours > 0) {
            return String.format("%d小时%d分钟", hours, minutes % 60);
        } else if (minutes > 0) {
            return String.format("%d分钟%d秒", minutes, seconds % 60);
        } else {
            return String.format("%d秒", seconds);
        }
    }

    /**
     * 发送进度消息
     */
    private void sendProgress(String message, Integer current, Integer total) {
        if (progressCallback != null) {
            progressCallback.accept(message, current, total);
        }
    }

    /**
     * 检查是否应该停止
     */
    private boolean shouldStop() {
        return shouldStopCallback != null && shouldStopCallback.get();
    }
}
