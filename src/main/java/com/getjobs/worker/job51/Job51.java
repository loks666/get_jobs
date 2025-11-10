package com.getjobs.worker.job51;

import com.getjobs.worker.utils.JobUtils;
import com.getjobs.worker.utils.PlaywrightUtil;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
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

    @Setter
    private Page page;

    @Setter
    private Job51Config config;

    @Setter
    private ProgressCallback progressCallback;

    @Setter
    private Supplier<Boolean> shouldStopCallback;

    private final List<String> resultList = new ArrayList<>();

    private static final int DEFAULT_MAX_PAGE = 50;
    private static final String BASE_URL = "https://we.51job.com/pc/search?";

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
            log.info("开始投递关键词: {}", keyword);
            sendProgress("正在搜索关键词: " + keyword, null, null);

            // 导航到搜索页面
            page.navigate(searchUrl);
            PlaywrightUtil.sleep(2);

            // 检查是否需要登录
            if (checkNeedLogin()) {
                log.error("需要重新登录，跳过当前关键词");
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
            } catch (Exception e) {
                log.warn("点击排序选项失败: {}", e.getMessage());
            }

            // 遍历页面投递
            for (int pageNum = 1; pageNum <= DEFAULT_MAX_PAGE; pageNum++) {
                if (shouldStop()) {
                    sendProgress("用户取消投递", null, null);
                    return;
                }

                sendProgress(String.format("正在投递第%d页", pageNum), pageNum, DEFAULT_MAX_PAGE);

                // 跳转到指定页码
                if (pageNum > 1 && !jumpToPage(pageNum)) {
                    log.warn("跳转到第{}页失败，停止当前关键词投递", pageNum);
                    break;
                }

                PlaywrightUtil.sleep(2);

                // 检查是否出现访问验证
                if (checkAccessVerification()) {
                    log.error("出现访问验证，停止投递");
                    sendProgress("出现访问验证，停止投递", null, null);
                    return;
                }

                // 投递当前页面的所有职位
                deliverCurrentPage();

                PlaywrightUtil.sleep(3);
            }

            log.info("关键词【{}】投递完成", keyword);
        } catch (Exception e) {
            log.error("投递关键词【{}】时出现异常", keyword, e);
        }
    }

    /**
     * 投递当前页面的所有职位
     */
    private void deliverCurrentPage() {
        try {
            PlaywrightUtil.sleep(1);

            // 查找所有职位的checkbox
            Locator checkboxes = page.locator("div.ick");
            if (checkboxes.count() == 0) {
                log.info("当前页面没有职位可投递");
                return;
            }

            // 查找职位名称和公司名称
            Locator titles = page.locator("[class*='jname text-cut']");
            Locator companies = page.locator("[class*='cname text-cut']");

            int jobCount = checkboxes.count();
            log.info("当前页面有{}个职位", jobCount);

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
                    log.info("选中: {}", jobInfo);
                } catch (Exception e) {
                    log.warn("选中第{}个职位失败: {}", i, e.getMessage());
                }
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
                    log.info("批量投递按钮点击成功");
                } else {
                    log.warn("未找到批量投递按钮");
                    break;
                }
            } catch (Exception e) {
                retryCount++;
                log.error("点击批量投递按钮失败，1秒后重试（第{}次）", retryCount);
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
     * 检查是否出现访问验证
     */
    private boolean checkAccessVerification() {
        try {
            Locator verifyElement = page.locator("//p[@class='waf-nc-title']");
            if (verifyElement.count() > 0) {
                String text = verifyElement.textContent();
                if (text != null && text.contains("验证")) {
                    log.error("出现访问验证，需要手动处理");
                    return true;
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
