package com.getjobs.worker.boss;

import com.getjobs.application.entity.AiEntity;
import com.getjobs.application.service.AiService;
import com.getjobs.application.service.BossService;
import com.getjobs.worker.utils.Job;
import com.getjobs.worker.utils.JobUtils;
import com.getjobs.worker.utils.DeliveryLimit;
import com.getjobs.worker.utils.PlaywrightUtil;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Response;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

import static com.getjobs.worker.boss.Locators.*;


/**
 * @author loks666
 * 项目链接: <a href=
 * "https://github.com/loks666/get_jobs">https://github.com/loks666/get_jobs</a>
 * Boss直聘自动投递
 */
@Slf4j
@Component
@Scope("prototype")
@RequiredArgsConstructor
public class Boss {

    private static final long CHAT_INPUT_TIMEOUT_MS = 60_000;
    private static final long CHAT_INPUT_POLL_MS = 1_000;

    @Setter
    private Page page;
    @Setter
    private BossConfig config;
    private final BossService bossService;
    private final AiService aiService;
    private Set<String> blackCompanies;
    private Set<String> blackRecruiters;
    private Set<String> blackJobs;
    // 记录 encryptId -> encryptUserId 的映射，用于后续更新投递状态
    private final ConcurrentMap<String, String> encryptIdToUserId = new ConcurrentHashMap<>();
    private final int maxDeliveries = DeliveryLimit.configuredMax();
    private int deliveryAttempts;
    private boolean platformDeliveryLimitReached;
    private volatile boolean authenticationResponseDetected;
    @Setter
    private ProgressCallback progressCallback;
    @Setter
    private Supplier<Boolean> shouldStopCallback;

    private final List<Job> resultList = new ArrayList<>();

    /**
     * 进度回调接口
     */
    @FunctionalInterface
    public interface ProgressCallback {
        void accept(String message, Integer current, Integer total);
    }

    // 通过 Lombok @RequiredArgsConstructor 使用构造器注入 bossService 与 aiService

    public void prepare() {
        authenticationResponseDetected = false;
        if (page != null) {
            page.onResponse(response -> {
                if (isAuthenticationResponse(response)) {
                    authenticationResponseDetected = true;
                }
            });
        }
        // 调整 boss_data 表结构：将 encrypt_id、encrypt_user_id 前置
        try { bossService.ensureBossDataColumnOrder(); } catch (Throwable ignore) {}
        // 从数据库加载黑名单
        this.blackCompanies = bossService.getBlackCompanies();
        this.blackRecruiters = bossService.getBlackRecruiters();
        this.blackJobs = bossService.getBlackJobs();

        log.info("黑名单加载完成: 公司({}) 招聘者({}) 职位({})",
                blackCompanies != null ? blackCompanies.size() : 0,
                blackRecruiters != null ? blackRecruiters.size() : 0,
                blackJobs != null ? blackJobs.size() : 0);
        // 不在页面初始化阶段入库，仅用于后续点击卡片时按需入库
    }

    /**
     * 执行投递
     */
    public int execute() {
        for (String cityCode : config.getCityCode()) {
            ensureBossSession(page);
            if (deliveryLimitReached() || shouldStopCallback != null && Boolean.TRUE.equals(shouldStopCallback.get())) {
                progressCallback.accept("用户取消投递", 0, 0);
                break;
            }
            postJobByCity(cityCode);
            if (shouldStopCallback != null && Boolean.TRUE.equals(shouldStopCallback.get())) {
                progressCallback.accept("用户取消投递", 0, 0);
                break;
            }
        }
        return resultList.size();
    }

    private boolean deliveryLimitReached() {
        return platformDeliveryLimitReached || deliveryAttempts >= maxDeliveries;
    }

    /**
     * 获取结果列表
     */
    public List<Job> getResultList() {
        return new ArrayList<>(resultList);
    }

    /**
     * 更新黑名单（从聊天记录中）
     */
    public Map<String, Set<String>> updateBlacklistFromChats() {
        page.navigate("https://www.zhipin.com/web/geek/chat");
        PlaywrightUtil.sleep(3);

        int newBlacklistCount = 0;
        boolean shouldBreak = false;
        while (!shouldBreak) {
            try {
                Locator bottomLocator = page.locator(FINISHED_TEXT);
                if (bottomLocator.count() > 0 && "没有更多了".equals(bottomLocator.textContent())) {
                    shouldBreak = true;
                }
            } catch (Exception ignore) {
            }

            Locator items = page.locator(CHAT_LIST_ITEM);
            int itemCount = items.count();

            for (int i = 0; i < itemCount; i++) {
                try {
                    Locator companyElements = page.locator(COMPANY_NAME_IN_CHAT);
                    Locator messageElements = page.locator(LAST_MESSAGE);

                    if (i >= companyElements.count() || i >= messageElements.count()) {
                        break;
                    }

                    String companyName = null;
                    String message = null;
                    int retryCount = 0;

                    while (true) {
                        try {
                            companyName = companyElements.nth(i).textContent();
                            message = messageElements.nth(i).textContent();
                            break;
                        } catch (Exception e) {
                            retryCount++;
                            if (retryCount >= 2) {
                                log.info("尝试获取元素文本2次失败，放弃本次获取");
                                break;
                            }
                            log.info("页面元素已变更，正在重试第{}次获取元素文本...", retryCount);
                            PlaywrightUtil.sleep(1);
                        }
                    }

                    if (companyName != null && message != null) {
                        boolean match = message.contains("不") || message.contains("感谢") || message.contains("但")
                                || message.contains("遗憾") || message.contains("需要本") || message.contains("对不");
                        boolean nomatch = message.contains("不是") || message.contains("不生");
                        if (match && !nomatch) {
                            if (blackCompanies.stream().anyMatch(companyName::contains)) {
                                continue;
                            }
                            companyName = companyName.replaceAll("\\.{3}", "");
                            if (companyName.matches(".*(\\p{IsHan}{2,}|[a-zA-Z]{4,}).*")) {
                                blackCompanies.add(companyName);
                                // 保存到数据库
                                bossService.addBlacklist("company", companyName);
                                newBlacklistCount++;
                                log.info("黑名单公司：【{}】，信息：【{}】", companyName, message);
                            }
                        }
                    }
                } catch (Exception e) {
                    log.error("寻找黑名单公司异常...", e);
                }
            }

            try {
                Locator scrollElement = page.locator(SCROLL_LOAD_MORE);
                if (scrollElement.count() > 0) {
                    scrollElement.scrollIntoViewIfNeeded();
                } else {
                    page.evaluate("window.scrollTo(0, document.body.scrollHeight);");
                }
            } catch (Exception e) {
                log.error("滚动元素出错", e);
                break;
            }
        }
        log.info("黑名单公司数量：{}，本次新增：{}", (blackCompanies != null ? blackCompanies.size() : 0), newBlacklistCount);

        Map<String, Set<String>> result = new HashMap<>();
        result.put("blackCompanies", new HashSet<>(blackCompanies != null ? blackCompanies : Collections.emptySet()));
        result.put("blackRecruiters", new HashSet<>(blackRecruiters != null ? blackRecruiters : Collections.emptySet()));
        result.put("blackJobs", new HashSet<>(blackJobs != null ? blackJobs : Collections.emptySet()));
        return result;
    }

    private void postJobByCity(String cityCode) {
        String searchUrl = getSearchUrl(cityCode);
        for (String keyword : config.getKeywords()) {
            // 检查是否需要停止
            if (deliveryLimitReached() || shouldStopCallback.get()) {
                progressCallback.accept("用户取消投递", 0, 0);
                return;
            }

            int postCount = 0;
            // 使用 URLEncoder 对关键词进行编码
            String encodedKeyword = URLEncoder.encode(keyword, StandardCharsets.UTF_8);

            String url = searchUrl + (searchUrl.contains("?") ? "&" : "?") + "query=" + encodedKeyword;
            page.navigate(url, new Page.NavigateOptions()
                    .setWaitUntil(com.microsoft.playwright.options.WaitUntilState.DOMCONTENTLOADED)
                    .setTimeout(15_000));
            ensureBossSession(page);
            waitForSearchResults();

            // 1. 基于 footer 出现滚动到底，确保加载全部岗位
            int lastCount = -1;
            int stableTries = 0;
            for (int i = 0; i < 5000; i++) { // 最多尝试约120次，避免死循环
                // 停止检查：滚动加载过程中也要及时响应
                if (shouldStopCallback != null && Boolean.TRUE.equals(shouldStopCallback.get())) {
                    progressCallback.accept("用户取消投递", 0, 0);
                    return;
                }
                try {
                    Locator footer = page.locator("div#footer, #footer");
                    if (footer.count() > 0 && footer.first().isVisible()) {
                        break; // 到达页面底部
                    }
                    // 按视口高度的90%渐进滚动，触发懒加载
                    page.evaluate("() => window.scrollBy(0, Math.floor(window.innerHeight * 1.5))");

                    // 获取卡片数量变化，判断是否需要强制触底
                    Locator cardsProbe = page.locator(JOB_LIST_SELECTOR);
                    int currentCount = cardsProbe.count();
                    if (currentCount == lastCount) {
                        stableTries++;
                    } else {
                        stableTries = 0;
                    }
                    lastCount = currentCount;

                    if (stableTries >= 3) { // 连续多次无新增，则强制触底一次
                        page.evaluate("() => window.scrollTo(0, document.body.scrollHeight)");
                        // 触底不再等待，继续检测 footer 出现
                    }
                } catch (RuntimeException e) {
                    if (!isNavigationRace(e)) {
                        throw e;
                    }
                    log.info("Boss搜索页发生站内跳转，等待新页面稳定后继续");
                    waitForSearchResults();
                    lastCount = -1;
                    stableTries = 0;
                }
            }
            // 统计最终岗位数量
            Locator cardsFinal = page.locator(JOB_LIST_SELECTOR);
            int loadedCount = cardsFinal.count();
            log.info("【{}】岗位已全部加载，总数:{}", keyword, loadedCount);
            progressCallback.accept("岗位加载完成：" + keyword, 0, loadedCount);

            // 2. 回到页面顶部
            page.evaluate("window.scrollTo(0, 0);");
            PlaywrightUtil.sleep(1);

            // 3. 逐个遍历所有岗位
            Locator cards = page.locator(JOB_LIST_SELECTOR);
            int count = cards.count();
            for (int i = 0; i < count; i++) {
                // 检查是否需要停止
                if (deliveryLimitReached() || shouldStopCallback != null && Boolean.TRUE.equals(shouldStopCallback.get())) {
                    progressCallback.accept("用户取消投递", i, count);
                    return;
                }

                // 重新获取卡片，避免元素过期
                cards = page.locator(JOB_LIST_SELECTOR);
                // 在点击卡片时同步等待岗位详情接口返回，随后解析并入库
                Response detailResp = null;
                try {
                    if (i == 0 && count > 1) {
                        // 第一个卡片默认展开不会触发请求：先切到第二个，再切回第一个，并在返回第一个时监听响应
                        final Locator secondCard = cards.nth(1);
                        secondCard.click();
                        PlaywrightUtil.sleep(1);
                        final Locator firstCard = cards.nth(0);
                        detailResp = page.waitForResponse(r -> {
                            try {
                                return r.url() != null && r.url().contains("/wapi/zpgeek/job/detail.json")
                                        && "GET".equalsIgnoreCase(r.request().method());
                            } catch (Throwable ignore) { return false; }
                        }, firstCard::click);
                    } else {
                        final Locator cardToClick = cards.nth(i);
                        detailResp = page.waitForResponse(r -> {
                            try {
                                return r.url() != null && r.url().contains("/wapi/zpgeek/job/detail.json")
                                        && "GET".equalsIgnoreCase(r.request().method());
                            } catch (Throwable ignore) { return false; }
                        }, cardToClick::click);
                    }
                } catch (Throwable ignore) {
                }
                PlaywrightUtil.sleep(1);
                ensureBossSession(page);
                if (isAuthenticationResponse(detailResp)) {
                    throw new BossAuthenticationExpiredException("Boss 投递接口返回认证失败");
                }

                // 统一从请求返回的 JSON 中获取数据并做过滤
                String jobName = null;
                String jobSalary = null;
                java.util.List<String> tags = new java.util.ArrayList<>();
                String jobDesc = null;
                String bossName = null;
                String bossActive = null;
                String bossCompany = null;
                String bossJobTitle = null;

                if (detailResp != null) {
                    try {
                        String body = detailResp.text();
                        // 保存原始 JSON 便于调试
                        appendRawJson(body);
                        // 解析并入库（仅在点击卡片触发时执行）
                        processJobDetailJsonAndInsert(body);

                        // 从 JSON 构建用于投递与过滤的字段
                        org.json.JSONObject root = new org.json.JSONObject(body);
                        org.json.JSONObject zpData = root.optJSONObject("zpData");
                        org.json.JSONObject jobInfo = zpData != null ? zpData.optJSONObject("jobInfo") : null;
                        org.json.JSONObject brand = zpData != null ? zpData.optJSONObject("brandComInfo") : null;
                        org.json.JSONObject boss = zpData != null ? zpData.optJSONObject("bossInfo") : null;

                        if (jobInfo != null) {
                            jobName = jobInfo.optString("jobName", "");
                            jobSalary = jobInfo.optString("salaryDesc", "");
                            String city = jobInfo.optString("locationName", "");
                            String exp = jobInfo.optString("experienceName", "");
                            String deg = jobInfo.optString("degreeName", "");
                            if (!city.isEmpty()) tags.add(city);
                            if (!exp.isEmpty()) tags.add(exp);
                            if (!deg.isEmpty()) tags.add(deg);
                            jobDesc = jobInfo.optString("postDescription", "");
                        }

                        if (boss != null) {
                            bossName = boss.optString("name", "");
                            bossActive = boss.optString("activeTimeDesc", "");
                            bossJobTitle = boss.optString("title", "");
                        }

                        if (brand != null) {
                            bossCompany = brand.optString("brandName", "");
                        }
                    } catch (Throwable e) {
                        log.debug("点击卡片后解析岗位详情用于过滤失败：{}", e.getMessage());
                    }
                }

                if (!isValidString(jobName)) {
                    Locator currentCard = cards.nth(i);
                    jobName = safeText(currentCard, JOB_NAME);
                    jobSalary = isValidString(jobSalary) ? jobSalary : safeText(currentCard, "span.salary");
                    if (!isValidString(jobName)) {
                        try {
                            jobName = currentCard.innerText().lines()
                                    .map(String::trim)
                                    .filter(line -> !line.isEmpty())
                                    .findFirst()
                                    .orElse("");
                        } catch (RuntimeException ignored) {
                            // Keep the explicit skip below when even the rendered card is unavailable.
                        }
                    }
                }
                if (!isValidString(jobName)) {
                    log.warn("未获得岗位详情，跳过当前卡片，避免向未知岗位发起沟通 | 序号：{}/{}", i + 1, count);
                    progressCallback.accept("岗位详情未加载，已跳过", i + 1, count);
                    continue;
                }

                // 过滤（全部基于 JSON 字段），并输出过滤原因
                if (jobName != null && blackJobs != null && blackJobs.stream().anyMatch(jobName::contains)) {
                    String term = findMatchedTerm(blackJobs, jobName);
                    log.info("被过滤：职位黑名单命中 | 公司：{} | 岗位：{} | 关键词：{}", bossCompany != null ? bossCompany : "", jobName, term != null ? term : "");
                    continue;
                }
                // HR活跃状态过滤：当开启过滤开关且活跃描述包含“年”时，视为不活跃
                boolean hrInactiveByYear = bossActive != null && bossActive.contains("年");
                if (Boolean.TRUE.equals(config.getFilterDeadHR()) && hrInactiveByYear) {
                    log.info("被过滤：HR活跃状态包含‘年’ | 公司：{} | 岗位：{} | 活跃：{}", bossCompany != null ? bossCompany : "", jobName != null ? jobName : "", bossActive);
                    continue;
                }
                if (bossCompany != null && blackCompanies != null && blackCompanies.stream().anyMatch(bossCompany::contains)) {
                    String term = findMatchedTerm(blackCompanies, bossCompany);
                    log.info("被过滤：公司黑名单命中 | 公司：{} | 岗位：{} | 关键词：{}", bossCompany, jobName != null ? jobName : "", term != null ? term : "");
                    continue;
                }
                if (bossJobTitle != null && blackRecruiters != null && blackRecruiters.stream().anyMatch(bossJobTitle::contains)) {
                    String term = findMatchedTerm(blackRecruiters, bossJobTitle);
                    log.info("被过滤：招聘者黑名单命中 | 公司：{} | 岗位：{} | 招聘者：{} | 关键词：{}", bossCompany != null ? bossCompany : "", jobName != null ? jobName : "", bossJobTitle, term != null ? term : "");
                    continue;
                }

                // 创建Job对象（全部基于 JSON 字段）
                Job job = new Job();
                job.setJobName(jobName != null ? jobName : "");
                job.setSalary(jobSalary != null ? jobSalary : "");
                job.setJobArea(String.join(", ", tags));
                job.setCompanyName(bossCompany != null ? bossCompany : "");
                job.setRecruiter(bossName != null ? bossName : "");
                job.setJobInfo(jobDesc != null ? jobDesc : "");

                // 输出
                progressCallback.accept("正在投递：" + jobName, i + 1, count);
                resumeSubmission(keyword, job);
                postCount++;

                // 为避免点击下面的卡片触发页面刷新：在点击5个卡片之后，每次点击后适度下滑
                try {
                    if (i >= 5) {
                        page.evaluate("window.scrollBy(0, 140);");
                        PlaywrightUtil.sleep(1);
                    }
                } catch (Throwable ignore) {}
            }
            log.info("【{}】岗位已投递完毕！已投递岗位数量:{}", keyword, postCount);
        }
    }

    private void waitForSearchResults() {
        page.waitForSelector(JOB_LIST_SELECTOR, new Page.WaitForSelectorOptions().setTimeout(60_000));
    }

    static boolean isNavigationRace(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            String message = cause.getMessage();
            if (message != null) {
                String normalized = message.toLowerCase(Locale.ROOT);
                if (normalized.contains("execution context was destroyed")
                        || normalized.contains("element was detached from the dom")) {
                    return true;
                }
            }
        }
        return false;
    }

    static boolean isAuthenticationStatus(int status) {
        return status == 401 || status == 403;
    }

    private static boolean isAuthenticationResponse(Response response) {
        try {
            return response != null && isAuthenticationStatus(response.status());
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private void ensureBossSession(Page targetPage) {
        if (authenticationResponseDetected) {
            throw new BossAuthenticationExpiredException("Boss 页面收到认证失败响应");
        }
        String url;
        try {
            url = targetPage.url();
        } catch (RuntimeException error) {
            if (isNavigationRace(error)) {
                return;
            }
            throw error;
        }
        String normalizedUrl = url == null ? "" : url.toLowerCase(Locale.ROOT);
        if (normalizedUrl.contains("login") || normalizedUrl.contains("passport")) {
            throw new BossAuthenticationExpiredException("Boss 页面已跳转到登录页");
        }

        try {
            Locator body = targetPage.locator("body");
            if (body.count() > 0) {
                String text = body.first().innerText();
                if (text != null && (text.contains("立即登录，享受优质服务") || text.contains("登录/注册"))) {
                    throw new BossAuthenticationExpiredException("Boss 页面出现登录提示");
                }
            }

            Locator loginEntry = targetPage.locator(ERROR_PAGE_LOGIN);
            if (loginEntry.count() > 0 && loginEntry.first().isVisible()) {
                throw new BossAuthenticationExpiredException("Boss 页面出现登录入口");
            }
        } catch (RuntimeException error) {
            if (!isNavigationRace(error)) {
                throw error;
            }
        }
    }

    static boolean isPlatformDeliveryLimitMessage(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        return text.contains("今日沟通人数已达上限")
                || text.contains("今日沟通已达上限")
                || text.contains("沟通人数已达上限")
                || text.contains("您已达到沟通上限")
                || text.contains("已达到沟通上限")
                || text.contains("今日打招呼人数已达上限")
                || text.contains("今日打招呼已达上限")
                || text.contains("已达沟通上限")
                || text.contains("沟通次数已达上限")
                || text.contains("今日已与150位BOSS沟通")
                || text.contains("今天已与150位BOSS沟通");
    }

    /**
     * 解析岗位详情 JSON 并进行入库与黑名单处理（只在点击卡片时调用）。
     */
    private void processJobDetailJsonAndInsert(String body) {
        if (body == null || body.isEmpty()) return;
        try {
            JSONObject root = new JSONObject(body);
            JSONObject zpData = root.optJSONObject("zpData");
            if (zpData == null) return;

            JSONObject jobInfo = zpData.optJSONObject("jobInfo");
            JSONObject brand = zpData.optJSONObject("brandComInfo");
            JSONObject bossInfo = zpData.optJSONObject("bossInfo");
            if (jobInfo == null) return;

            String encryptId = jobInfo.optString("encryptId", null);
            String encryptUserId = jobInfo.optString("encryptUserId", null);
            if (encryptUserId == null && bossInfo != null) {
                // 兼容部分页面字段落在 bossInfo 内
                encryptUserId = bossInfo.optString("encryptUserId", null);
                if (encryptUserId == null) {
                    // 进一步兼容可能的字段命名
                    encryptUserId = bossInfo.optString("encryptBossId", null);
                }
            }
            if (encryptId != null && encryptUserId != null) {
                encryptIdToUserId.put(encryptId, encryptUserId);
            }

            com.getjobs.application.entity.BossJobDataEntity entity = new com.getjobs.application.entity.BossJobDataEntity();
            entity.setJobName(jobInfo.optString("jobName", null));
            entity.setSalary(jobInfo.optString("salaryDesc", null));
            entity.setLocation(jobInfo.optString("locationName", null));
            entity.setExperience(jobInfo.optString("experienceName", null));
            entity.setDegree(jobInfo.optString("degreeName", null));
            entity.setJobDescription(jobInfo.optString("postDescription", null));
            entity.setRecruitmentStatus(jobInfo.optString("jobStatusDesc", null));
            entity.setCompanyAddress(jobInfo.optString("address", null));
            entity.setEncryptId(encryptId);
            entity.setEncryptUserId(encryptUserId);

            entity.setCompanyName(brand != null ? brand.optString("brandName", null) : null);
            entity.setIndustry(brand != null ? brand.optString("industryName", null) : null);
            entity.setIntroduce(brand != null ? brand.optString("introduce", null) : null);
            entity.setFinancingStage(brand != null ? brand.optString("stageName", null) : null);
            entity.setCompanyScale(brand != null ? brand.optString("scaleName", null) : null);

            entity.setHrName(bossInfo != null ? bossInfo.optString("name", null) : null);
            entity.setHrPosition(bossInfo != null ? bossInfo.optString("title", null) : null);
            entity.setHrActiveStatus(bossInfo != null ? bossInfo.optString("activeTimeDesc", null) : null);

            if (encryptId != null && !encryptId.isEmpty()) {
                entity.setJobUrl("https://www.zhipin.com/job_detail/" + encryptId + ".html");
            }

            // 黑名单处理
            boolean filtered = false;
            String companyName = entity.getCompanyName() != null ? entity.getCompanyName() : "";
            String positionName = entity.getJobName() != null ? entity.getJobName() : "";
            String hrPosition = entity.getHrPosition() != null ? entity.getHrPosition() : "";
            try {
                if (blackCompanies != null && blackCompanies.stream().anyMatch(companyName::contains)) filtered = true;
                if (!filtered && blackJobs != null && blackJobs.stream().anyMatch(positionName::contains)) filtered = true;
                if (!filtered && blackRecruiters != null && blackRecruiters.stream().anyMatch(hrPosition::contains)) filtered = true;
            } catch (Throwable ignore) {}

            // HR活跃状态过滤：开启过滤且活跃描述包含“年”，则标记为已过滤，但仍入库
            if (!filtered && Boolean.TRUE.equals(config.getFilterDeadHR())) {
                String hrActive = entity.getHrActiveStatus();
                if (hrActive != null && hrActive.contains("年")) {
                    filtered = true;
                }
            }

            entity.setDeliveryStatus(filtered ? "已过滤" : "未投递");

            // 入库（若不存在），优先以 encrypt_id + encrypt_user_id 去重；若 userId 缺失，则以 encrypt_id 去重
            if (encryptId != null) {
                try {
                    boolean exists = false;
                    if (encryptUserId != null) {
        exists = bossService.existsBossJob(encryptId, encryptUserId);
                    } else {
        exists = bossService.existsBossJobByEncryptId(encryptId);
                    }
                    if (!exists) {
        bossService.insertBossJob(entity);
                        log.debug("岗位入库：{} | 公司：{} | HR：{} | 状态：{}", entity.getJobName(), entity.getCompanyName(), entity.getHrName(), entity.getDeliveryStatus());
                    }
                } catch (Exception e) {
                    log.warn("岗位入库失败：{}", e.getMessage());
                }
            }
        } catch (Throwable e) {
            log.debug("解析岗位详情 JSON 失败：{}", e.getMessage());
        }
    }

    public String decodeSalary(String text) {
        Map<Character, Character> fontMap = new HashMap<>();
        fontMap.put('\uE8F0', '0');
        fontMap.put('\uE8F1', '1');
        fontMap.put('\uE8F2', '2');
        fontMap.put('\uE8F3', '3');
        fontMap.put('\uE8F4', '4');
        fontMap.put('\uE8F5', '5');
        fontMap.put('\uE8F6', '6');
        fontMap.put('\uE8F7', '7');
        fontMap.put('\uE8F8', '8');
        fontMap.put('\uE8F9', '9');
        StringBuilder result = new StringBuilder();
        for (char c : text.toCharArray()) {
            result.append(fontMap.getOrDefault(c, c));
        }
        return result.toString();
    }

    // 安全获取单个文本内容
    public String safeText(Locator root, String selector) {
        Locator node = root.locator(selector);
        try {
            if (node.count() > 0 && node.innerText() != null) {
                return node.innerText().trim();
            }
        } catch (Exception e) {
            // ignore
        }
        return "";
    }

    // 安全获取多个文本内容
    public List<String> safeAllText(Locator root, String selector) {
        try {
            return root.locator(selector).allInnerTexts();
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    // Boss姓名+活跃状态拆分
    public String[] splitBossName(String raw) {
        String[] bossParts = raw.trim().split("\\s+");
        String bossName = bossParts[0];
        String bossActive = bossParts.length > 1 ? String.join(" ", Arrays.copyOfRange(bossParts, 1, bossParts.length)) : "";
        return new String[]{bossName, bossActive};
    }

    // Boss公司+职位拆分
    public String[] splitBossTitle(String raw) {
        String[] parts = raw.trim().split(" · ");
        String company = parts[0];
        String job = parts.length > 1 ? parts[1] : "";
        return new String[]{company, job};
    }

    // 匹配命中词条（用于日志输出过滤原因）
    private String findMatchedTerm(java.util.Collection<String> patterns, String text) {
        if (patterns == null || text == null) return null;
        try {
            for (String p : patterns) {
                if (p != null && !p.isEmpty() && text.contains(p)) {
                    return p;
                }
            }
        } catch (Exception ignore) {
        }
        return null;
    }

    public static String buildSearchUrl(BossConfig config, String cityCode) {
        String baseUrl = "https://www.zhipin.com/web/geek/jobs";
        if (config == null) {
            return baseUrl;
        }
        List<String> params = new ArrayList<>();
        addParam(params, JobUtils.appendParam("city", cityCode));
        addParam(params, JobUtils.appendParam("jobType", config.getJobType()));
        addParam(params, JobUtils.appendListParam("salary", config.getSalary()));
        addParam(params, JobUtils.appendListParam("experience", config.getExperience()));
        addParam(params, JobUtils.appendListParam("degree", config.getDegree()));
        addParam(params, JobUtils.appendListParam("scale", config.getScale()));
        addParam(params, JobUtils.appendListParam("industry", config.getIndustry()));
        addParam(params, JobUtils.appendListParam("stage", config.getStage()));
        if (params.isEmpty()) {
            return baseUrl;
        }
        return baseUrl + "?" + String.join("&", params);
    }

    private static void addParam(List<String> params, String param) {
        if (param == null || param.isEmpty()) {
            return;
        }
        params.add(param.startsWith("&") ? param.substring(1) : param);
    }

    private String getSearchUrl(String cityCode) {
        return buildSearchUrl(config, cityCode);
    }

    /**
     * 备注：目前Boss无法通过新标签页打开立即沟通按钮，所以只能点击更多详情，然后从更多详情里打开聊天按钮
     */
    @SneakyThrows
    private void resumeSubmission(String keyword, Job job) {
        // 若收到停止指令，直接短路返回
        if (deliveryLimitReached() || shouldStopCallback != null && Boolean.TRUE.equals(shouldStopCallback.get())) {
            log.info("停止指令已触发，跳过投递 | 公司：{} | 岗位：{}", job.getCompanyName(), job.getJobName());
            return;
        }
        // 调试模式：仅遍历不投递
        if (Boolean.TRUE.equals(config.getDebugger())) {
            log.info("调试模式：仅遍历岗位，不投递 | 公司：{} | 岗位：{}", job.getCompanyName(), job.getJobName());
            return;
        }

        // 1. 查找"查看更多信息"按钮（必须存在且新开页）
        Locator moreInfoBtn = page.locator("a.more-job-btn");
        if (moreInfoBtn.count() == 0) {
            log.warn("未找到\"查看更多信息\"按钮，跳过...");
            return;
        }
        // 强制用js新开tab
        String href = moreInfoBtn.first().getAttribute("href");
        if (href == null || !href.startsWith("/job_detail/")) {
            log.warn("未获取到岗位详情链接，跳过...");
            return;
        }
        String detailUrl = "https://www.zhipin.com" + href;
        // 2. 在新窗口打开详情页
        Page detailPage = page.context().newPage();
        boolean communicationAccepted = false;
        try {
        detailPage.navigate(detailUrl);
        PlaywrightUtil.sleep(1);
        ensureBossSession(detailPage);

        // 3. 查找"立即沟通"按钮
        Locator chatBtn = detailPage.locator(
                "a.btn-startchat, a.op-btn-chat, a:has-text('立即沟通'), button:has-text('立即沟通'), [role='button']:has-text('立即沟通')");
        boolean foundChatBtn = false;
        for (int i = 0; i < 5; i++) {
            if (shouldStopCallback != null && Boolean.TRUE.equals(shouldStopCallback.get())) {
                log.info("停止指令已触发，结束查找聊天按钮 | 公司：{} | 岗位：{}", job.getCompanyName(), job.getJobName());
                try { detailPage.close(); } catch (Exception ignore) {}
                return;
            }
            for (int candidateIndex = 0; candidateIndex < chatBtn.count(); candidateIndex++) {
                Locator candidate = chatBtn.nth(candidateIndex);
                if (candidate.isVisible() && candidate.textContent() != null
                        && candidate.textContent().contains("立即沟通")) {
                    chatBtn = candidate;
                    foundChatBtn = true;
                    break;
                }
            }
            if (foundChatBtn) {
                break;
            }
            PlaywrightUtil.sleep(1);
        }
        if (!foundChatBtn) {
            log.warn("未找到立即沟通按钮，跳过岗位: {}", job.getJobName());
            // 关闭详情页
            try {
                detailPage.close();
            } catch (Exception ignore) {
            }
            return;
        }
        Locator exactChatBtn = detailPage.locator("a.btn-startchat").first();
        if (exactChatBtn.count() > 0 && exactChatBtn.isVisible()) {
            chatBtn = exactChatBtn;
        }

        // AI 请求可能耗时较长，必须在打开聊天会话前完成，避免聊天页在等待期间失效。
        String aiMessage = null;
        if (config.getEnableAI()) {
            String jd = job.getJobInfo();
            if (jd != null && !jd.isEmpty()) {
                aiMessage = generateAiMessage(keyword, job.getJobName(), jd);
            }
        }
        String message = isValidString(aiMessage) ? aiMessage : config.getSayHi();

        deliveryAttempts++;
        String chatRedirectUrl = chatBtn.getAttribute("redirect-url");
        if (!isValidString(chatRedirectUrl)) {
            chatRedirectUrl = detailPage.locator("a.btn-startchat[redirect-url], a[redirect-url]").first()
                    .getAttribute("redirect-url");
        }
        if (!isValidString(chatRedirectUrl)) {
            String chatHtml = String.valueOf(chatBtn.evaluate("el => el.outerHTML"));
            String marker = "redirect-url=\"";
            int start = chatHtml.indexOf(marker);
            if (start >= 0) {
                start += marker.length();
                int end = chatHtml.indexOf('"', start);
                if (end > start) {
                    chatRedirectUrl = chatHtml.substring(start, end).replace("&amp;", "&");
                }
            }
        }
        Locator chatButtonForClick = chatBtn.first();
        Response friendAddResponse = null;
        boolean[] chatClickTriggered = {false};
        try {
            friendAddResponse = detailPage.waitForResponse(response -> {
                try {
                    return response.url() != null && response.url().contains("/wapi/zpgeek/friend/add.json");
                } catch (RuntimeException ignored) {
                    return false;
                }
            }, new Page.WaitForResponseOptions().setTimeout(15_000), () -> {
                chatClickTriggered[0] = true;
                chatButtonForClick.click();
            });
        } catch (RuntimeException responseError) {
            log.debug("Boss 好友接口未在等待窗口内返回，继续检查聊天页 | 原因：{}", responseError.getMessage());
        }
        if (!chatClickTriggered[0]) {
            chatButtonForClick.click();
        }
        ensureBossSession(detailPage);
        if (detectPlatformDeliveryLimit(detailPage)) {
            markPlatformDeliveryLimit();
        }
        String friendAddBody = null;
        if (friendAddResponse != null) {
            if (isAuthenticationResponse(friendAddResponse)) {
                throw new BossAuthenticationExpiredException("Boss 投递接口返回认证失败");
            }
            friendAddBody = friendAddResponse.text();
            if (isPlatformDeliveryLimitMessage(friendAddBody)) {
                markPlatformDeliveryLimit();
                try {
                    detailPage.close();
                } catch (RuntimeException ignored) {
                }
                return;
            }
            log.debug("friend/add 响应 | status:{} | body:{}", friendAddResponse.status(), friendAddBody);
            communicationAccepted = isSoftCommunicationAccepted(friendAddBody);
        }
        try {
            for (Page candidatePage : detailPage.context().pages()) {
                if (candidatePage != detailPage && candidatePage.url() != null
                        && candidatePage.url().contains("/web/geek/chat")) {
                    try {
                        detailPage.close();
                    } catch (RuntimeException ignored) {
                    }
                    detailPage = candidatePage;
                    break;
                }
            }
        } catch (RuntimeException ignored) {
            // 页面未打开新标签页，继续使用当前详情页。
        }
        long continueDeadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < continueDeadline) {
            ensureBossSession(detailPage);
            if (detectPlatformDeliveryLimit(detailPage)) {
                markPlatformDeliveryLimit();
            }
            Locator continueChatBtn = detailPage.locator(".greet-boss-pop .dialog-container")
                    .getByText("继续沟通", new Locator.GetByTextOptions().setExact(true));
            if (continueChatBtn.count() > 0 && continueChatBtn.first().isVisible()) {
                continueChatBtn.first().click();
                break;
            }
            PlaywrightUtil.sleep(1);
        }
        if (!detailPage.url().contains("/web/geek/chat")) {
            try {
                detailPage.waitForURL("**/web/geek/chat**", new Page.WaitForURLOptions().setTimeout(10_000));
            } catch (RuntimeException ignored) {
                // Native click did not navigate; use the captured redirect URL below.
            }
        }
        if (isValidString(chatRedirectUrl)) {
            String chatUrl = chatRedirectUrl.startsWith("http")
                    ? chatRedirectUrl
                    : "https://www.zhipin.com" + chatRedirectUrl;
            if (!chatUrl.equals(detailPage.url())) {
                log.info("Boss 聊天会话导航 | redirect-url:{}", chatUrl);
                detailPage.navigate(chatUrl);
                ensureBossSession(detailPage);
                log.info("Boss 聊天会话导航完成 | URL:{}", detailPage.url());
            }
        }
        detailPage.waitForURL("**/web/geek/chat**", new Page.WaitForURLOptions().setTimeout(15_000));
        ensureBossSession(detailPage);
        communicationAccepted = true;

        // 4. 沟通已建立；AI 文案是补充消息，发送失败不能撤销本次求职。
        String inputSelector = "#chat-input, textarea.input-area, div.chat-input[contenteditable], [contenteditable='true'][role='textbox']";
        if (!waitForChatInputAndFill(detailPage, inputSelector, message, job)) {
            log.warn("沟通已建立，但 AI 消息未能填入 | 公司：{} | 岗位：{}", job.getCompanyName(), job.getJobName());
            try {
                detailPage.close();
            } catch (Exception ignore) {
            }
            if (communicationAccepted) {
                recordSuccessfulDelivery(detailUrl, job, resultList);
            }
            return;
        }
        ensureBossSession(detailPage);

        // 7. 点击发送按钮（div.send-message 或 button.btn-send）
        Locator sendText = detailPage.locator("div.send-message, button[type='send'].btn-send, button.btn-send");
        boolean sendSuccess = false;
        if (sendText.count() > 0) {
            Locator sendButton = sendText.first();
            for (int attempt = 1; attempt <= 2 && !sendSuccess; attempt++) {
                long sendDeadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(15);
                while (System.nanoTime() < sendDeadline && !isSendButtonReady(sendButton)) {
                    ensureBossSession(detailPage);
                    if (detectPlatformDeliveryLimit(detailPage)) {
                        markPlatformDeliveryLimit();
                        return;
                    }
                    PlaywrightUtil.sleep(1);
                }
                if (!isSendButtonReady(sendButton)) {
                    log.warn("发送按钮不可用（第{}次），沟通仍记为成功 | 公司：{} | 岗位：{}",
                            attempt, job.getCompanyName(), job.getJobName());
                    break;
                }
                sendButton.click();
                PlaywrightUtil.sleep(1);
                ensureBossSession(detailPage);
                if (detectPlatformDeliveryLimit(detailPage)) {
                    markPlatformDeliveryLimit();
                    try {
                        detailPage.close();
                    } catch (Exception ignore) {
                    }
                    return;
                }
                sendSuccess = isChatInputEmpty(detailPage, inputSelector);
                if (!sendSuccess && attempt == 1) {
                    waitForChatInputAndFill(detailPage, inputSelector, message, job);
                }
            }
        } else {
            log.warn("未找到发送按钮，沟通仍记为成功 | 岗位：{}", job.getJobName());
        }

        if (sendSuccess) {
            try {
                Locator closeButton = detailPage.locator("i.icon-close");
                if (closeButton.count() > 0 && closeButton.first().isVisible()) {
                    closeButton.first().click();
                }
            } catch (Exception e) {
                log.debug("发送文本后关闭浮层失败，继续后续流程：{}", e.getMessage());
            }
        } else {
            log.warn("沟通已建立，但 AI 补充消息发送失败，仍记录投递成功 | 公司：{} | 岗位：{}",
                    job.getCompanyName(), job.getJobName());
        }

        // 8. 发送图片简历（可选）
        boolean imgResume = false;
        if (sendSuccess && Boolean.TRUE.equals(config.getSendImgResume())) {
            imgResume = sendImageResume(detailPage);
        }
        ensureBossSession(detailPage);

        log.info("投递完成 | 公司：{} | 岗位：{} | 薪资：{} | 招呼语：{} | 图片简历：{}", job.getCompanyName(), job.getJobName(), job.getSalary(), message, imgResume ? "已发送" : "未发送");

        // 9. 关闭新打开的详情页
        try {
            detailPage.close();
        } catch (Exception ignore) {
        }
        PlaywrightUtil.sleep(1);

        // 10. 已建立沟通即更新数据库投递状态 & 加入成功结果
        if (communicationAccepted) {
            recordSuccessfulDelivery(detailUrl, job, resultList);
        }
        } catch (RuntimeException error) {
            try {
                detailPage.close();
            } catch (Exception ignore) {
            }
            if (error instanceof BossAuthenticationExpiredException
                    || error instanceof BossDailyDeliveryLimitReachedException) {
                throw error;
            }
            if (communicationAccepted) {
                recordSuccessfulDelivery(detailUrl, job, resultList);
                log.warn("沟通已建立，但补充消息流程异常，仍记录投递成功 | 公司：{} | 岗位：{} | 原因：{}",
                        job.getCompanyName(), job.getJobName(), error.getMessage());
                return;
            }
            log.warn("当前岗位投递失败，继续下一个 | 公司：{} | 岗位：{} | 原因：{}",
                    job.getCompanyName(), job.getJobName(), error.getMessage());
            if (progressCallback != null) {
                progressCallback.accept("当前岗位投递失败，继续下一个：" + job.getJobName(), null, null);
            }
        }
    }

    private boolean detectPlatformDeliveryLimit(Page detailPage) {
        try {
            Locator body = detailPage.locator("body");
            return body.count() > 0 && isPlatformDeliveryLimitMessage(body.first().innerText());
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    static boolean isSoftCommunicationAccepted(String responseBody) {
        return responseBody != null
                && responseBody.contains("chatRemindDialog")
                && !isPlatformDeliveryLimitMessage(responseBody);
    }

    private void recordSuccessfulDelivery(String detailUrl, Job job, List<Job> resultList) {
        String encryptId = extractEncryptId(detailUrl);
        String encryptUserId = encryptId != null ? encryptIdToUserId.get(encryptId) : null;
        if (encryptId != null && encryptUserId != null) {
            try {
                bossService.updateDeliveryStatus(encryptId, encryptUserId, "已投递");
                log.info("投递成功 | 公司：{} | 岗位：{} | encryptId：{} | encryptUserId：{}",
                        job.getCompanyName(), job.getJobName(), encryptId, encryptUserId);
            } catch (Exception e) {
                log.warn("更新投递状态为已投递失败：{}", e.getMessage());
            }
        } else {
            log.debug("未能找到 encryptId/encryptUserId 用于更新投递状态，detailUrl: {}", detailUrl);
        }
        resultList.add(job);
    }

    private boolean isSendButtonReady(Locator sendButton) {
        if (!sendButton.isVisible() || !sendButton.isEnabled()) {
            return false;
        }
        String className = sendButton.getAttribute("class");
        return className == null || !className.contains("disabled");
    }

    private boolean waitForChatInputAndFill(Page detailPage, String selector, String message, Job job) {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(CHAT_INPUT_TIMEOUT_MS);
        while (System.nanoTime() < deadline) {
            ensureBossSession(detailPage);
            if (shouldStopCallback != null && Boolean.TRUE.equals(shouldStopCallback.get())) {
                log.info("停止指令已触发，结束等待聊天输入框 | 公司：{} | 岗位：{}", job.getCompanyName(), job.getJobName());
                return false;
            }
            if (detectPlatformDeliveryLimit(detailPage)) {
                markPlatformDeliveryLimit();
                return false;
            }

            long remainingMs = Math.max(1, Math.min(CHAT_INPUT_POLL_MS,
                    java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime())));
            try {
                Locator inputs = detailPage.locator(selector);
                for (int i = 0; i < inputs.count(); i++) {
                    Locator input = inputs.nth(i);
                    if (!input.isVisible()) {
                        continue;
                    }
                    input.waitFor(new Locator.WaitForOptions().setTimeout(remainingMs));
                    input.fill(message, new Locator.FillOptions().setTimeout(remainingMs));
                    input.dispatchEvent("input");
                    return true;
                }
            } catch (RuntimeException error) {
                if (!isChatReadinessRetry(error)) {
                    throw error;
                }
                log.debug("聊天窗口仍在加载，继续等待 | 岗位：{} | 原因：{}", job.getJobName(), error.getMessage());
            }
            PlaywrightUtil.sleep(1);
        }
        return false;
    }

    private boolean isChatInputEmpty(Page detailPage, String selector) {
        try {
            Locator inputs = detailPage.locator(selector);
            for (int i = 0; i < inputs.count(); i++) {
                Locator input = inputs.nth(i);
                if (input.isVisible()) {
                    Object value = input.evaluate("el => el.value ?? el.textContent ?? ''");
                    return value == null || String.valueOf(value).trim().isEmpty();
                }
            }
        } catch (RuntimeException ignored) {
        }
        return false;
    }

    private static boolean isChatReadinessRetry(Throwable error) {
        if (isNavigationRace(error)) {
            return true;
        }
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            String message = cause.getMessage();
            if (message != null && message.toLowerCase(Locale.ROOT).contains("timeout")) {
                return true;
            }
        }
        return false;
    }

    private void markPlatformDeliveryLimit() {
        platformDeliveryLimitReached = true;
        throw new BossDailyDeliveryLimitReachedException("Boss 今日沟通已达上限");
    }

    

    /**
     * 注册页面响应监听：拦截 /wapi/zpgeek/job/detail.json 请求并解析写库
     */
    private void attachJobDetailResponseListener() {
        if (page == null) return;
        page.onResponse(resp -> {
            try {
                String url = resp.url();
                if (url == null) return;
                // 仅处理 Boss 岗位详情接口（GET）
                if (url.contains("/wapi/zpgeek/job/detail.json") &&
                        "GET".equalsIgnoreCase(resp.request().method())) {
                    String body = null;
                    try {
                        body = resp.text();
                    } catch (Throwable ignore) {
                        // 某些情况下可能拿不到文本，忽略
                    }
                    if (body == null || body.isEmpty()) return;

                    // 保存原始 JSON 到 target/job.txt
                    appendRawJson(body);

                    // 仅记录映射与原始 JSON；入库逻辑已移动到点击卡片时
                    JSONObject root = new JSONObject(body);
                    JSONObject zpData = root.optJSONObject("zpData");
                    if (zpData == null) return;
                    JSONObject jobInfo = zpData.optJSONObject("jobInfo");
                    if (jobInfo == null) return;
                    String encryptId = jobInfo.optString("encryptId", null);
                    String encryptUserId = jobInfo.optString("encryptUserId", null);
                    if (encryptId != null && encryptUserId != null) {
                        encryptIdToUserId.put(encryptId, encryptUserId);
                    }
                }
            } catch (Throwable e) {
                log.debug("监听岗位详情响应处理异常：{}", e.getMessage());
            }
        });
    }


    /**
     * 追加保存原始 JSON 到 target/job.txt
     */
    private void appendRawJson(String body) {
        try {
            java.io.File dir = new java.io.File("target");
            if (!dir.exists()) dir.mkdirs();
            java.io.File file = new java.io.File(dir, "job.txt");
            try (java.io.FileWriter fw = new java.io.FileWriter(file, true)) {
                fw.write(body);
                fw.write(System.lineSeparator());
                fw.write("\n");
            }
        } catch (Exception e) {
            log.debug("写入 target/job.txt 失败：{}", e.getMessage());
        }
    }

    /**
     * 从详情页 URL 中提取 encrypt_id
     */
    private String extractEncryptId(String detailUrl) {
        try {
            if (detailUrl == null) return null;
            String key = "/job_detail/";
            int idx = detailUrl.indexOf(key);
            if (idx < 0) return null;
            int start = idx + key.length();
            int end = detailUrl.indexOf(".html", start);
            if (end < 0) end = detailUrl.length();
            return detailUrl.substring(start, end);
        } catch (Exception e) {
            return null;
        }
    }

    public boolean isValidString(String str) {
        return str != null && !str.isEmpty();
    }

    private boolean sendImageResume(Page page) {
        try {
            // 0) 资源存在性校验，避免后续无效操作
            URL resourceUrlCheck = Boss.class.getResource("/resume.jpg");
            if (resourceUrlCheck == null) {
                log.error("资源文件 resume.jpg 不存在，跳过发送图片简历");
                return false;
            }

            // 进入聊天页
            if (!page.url().contains("/web/geek/chat")) {
                Locator chatBtn = page.locator("a.btn-startchat, a.op-btn-chat");
                if (chatBtn.count() == 0) {
                    log.warn("未找到【继续沟通/立即沟通】按钮，跳过发送图片简历");
                    return false;
                }
                chatBtn.first().click();
                page.waitForURL("**/web/geek/chat**", new Page.WaitForURLOptions().setTimeout(15_000));
            }

            // 1) 解析图片路径（在可能触发文件选择器前就准备好）
            java.nio.file.Path imagePath = resolveResumeImage();

            // 精准定位聊天工具栏内的图片输入，避免匹配到页面其他上传控件
            Locator imgContainer = page.locator("div.btn-sendimg[aria-label='发送图片'], div[aria-label='发送图片'].btn-sendimg");
            Locator imageInput = imgContainer.locator("input[type='file'][accept*='image']").first();
            if (imageInput.count() == 0) {
                // 若未渲染，尝试拦截系统文件选择器；若未出现则普通点击促使 input 出现
                if (imgContainer.count() == 0) {
                    log.warn("未找到图片上传控件，跳过发送图片简历");
                    return false;
                }
                try {
                    com.microsoft.playwright.FileChooser chooser = page.waitForFileChooser(() -> {
                        imgContainer.first().click();
                    });
                    chooser.setFiles(imagePath);
                    log.info("通过 FileChooser 直接提交图片文件，避免系统窗口阻塞");
                    PlaywrightUtil.sleep(1);
                    return true;
                } catch (com.microsoft.playwright.PlaywrightException ignore) {
                    PlaywrightUtil.sleep(1);
                    imageInput = imgContainer.locator("input[type='file'][accept*='image']").first();
                }
            }
            if (imageInput.count() == 0) {
                log.warn("未找到图片文件输入框，跳过发送图片简历");
                return false;
            }

            // file input 通常是隐藏控件，setInputFiles 不要求元素可见。
            imageInput.setInputFiles(imagePath);
            PlaywrightUtil.sleep(1);
            return true;
        } catch (Throwable e) {
            log.error("发送图片简历失败：{}", e.getMessage(), e);
            return false;
        }
    }

    private java.nio.file.Path resolveResumeImage() throws Exception {
        URL resourceUrl = Boss.class.getResource("/resume.jpg");
        if (resourceUrl == null) {
            throw new IllegalStateException("资源文件 /resume.jpg 未找到，请将图片放置到 src/main/resources 目录下");
        }
        if ("file".equalsIgnoreCase(resourceUrl.getProtocol())) {
            return java.nio.file.Paths.get(resourceUrl.toURI());
        }
        java.nio.file.Path temp = java.nio.file.Files.createTempFile("resume-", ".jpg");
        try (java.io.InputStream in = Boss.class.getResourceAsStream("/resume.jpg")) {
            if (in == null) {
                throw new IllegalStateException("无法从类路径读取 /resume.jpg 资源");
            }
            java.nio.file.Files.copy(in, temp, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        return temp;
    }

    /**
     * 检查岗位薪资是否符合预期
     *
     * @return boolean
     * true 不符合预期
     * false 符合预期
     * 期望的最低薪资如果比岗位最高薪资还小，则不符合（薪资给的太少）
     * 期望的最高薪资如果比岗位最低薪资还小，则不符合(要求太高满足不了)
     */
    private boolean isSalaryNotExpected(String salary) {
        try {
            // 1. 如果没有期望薪资范围，直接返回 false，表示"薪资并非不符合预期"
            List<Integer> expectedSalary = config.getExpectedSalary();
            if (!hasExpectedSalary(expectedSalary)) {
                return false;
            }

            // 2. 清理薪资文本（比如去掉 "·15薪"）
            salary = removeYearBonusText(salary);

            // 3. 如果薪资格式不符合预期（如缺少 "K" / "k"），直接返回 true，表示"薪资不符合预期"
            if (!isSalaryInExpectedFormat(salary)) {
                return true;
            }

            // 4. 进一步清理薪资文本，比如去除 "K"、"k"、"·" 等
            salary = cleanSalaryText(salary);

            // 5. 判断是 "月薪" 还是 "日薪"
            String jobType = detectJobType(salary);
            salary = removeDayUnitIfNeeded(salary); // 如果是按天，则去除 "元/天"

            // 6. 解析薪资范围并检查是否超出预期
            Integer[] jobSalaryRange = parseSalaryRange(salary);
            return isSalaryOutOfRange(jobSalaryRange,
                    getMinimumSalary(expectedSalary),
                    getMaximumSalary(expectedSalary),
                    jobType);

        } catch (Exception e) {
            log.error("岗位薪资获取异常！薪资文本【{}】,异常信息【{}】", salary, e.getMessage(), e);
            // 出错时，您可根据业务需求决定返回 true 或 false
            // 这里假设出错时无法判断，视为不满足预期 => 返回 true
            return true;
        }
    }

    /**
     * 是否存在有效的期望薪资范围
     */
    private boolean hasExpectedSalary(List<Integer> expectedSalary) {
        return expectedSalary != null && !expectedSalary.isEmpty();
    }

    /**
     * 去掉年终奖信息，如 "·15薪"、"·13薪"。
     */
    private String removeYearBonusText(String salary) {
        if (salary.contains("薪")) {
            // 使用正则去除 "·任意数字薪"
            return salary.replaceAll("·\\d+薪", "");
        }
        return salary;
    }

    /**
     * 判断是否是按天计薪，如发现 "元/天" 则认为是日薪
     */
    private String detectJobType(String salary) {
        if (salary.contains("元/天")) {
            return "day";
        }
        return "mouth";
    }

    /**
     * 如果是日薪，则去除 "元/天"
     */
    private String removeDayUnitIfNeeded(String salary) {
        if (salary.contains("元/天")) {
            return salary.replaceAll("元/天", "");
        }
        return salary;
    }

    private Integer getMinimumSalary(List<Integer> expectedSalary) {
        return expectedSalary != null && !expectedSalary.isEmpty() ? expectedSalary.get(0) : null;
    }

    private Integer getMaximumSalary(List<Integer> expectedSalary) {
        return expectedSalary != null && expectedSalary.size() > 1 ? expectedSalary.get(1) : null;
    }

    private boolean isSalaryInExpectedFormat(String salaryText) {
        return salaryText.contains("K") || salaryText.contains("k") || salaryText.contains("元/天");
    }

    private String cleanSalaryText(String salaryText) {
        salaryText = salaryText.replace("K", "").replace("k", "");
        int dotIndex = salaryText.indexOf('·');
        if (dotIndex != -1) {
            salaryText = salaryText.substring(0, dotIndex);
        }
        return salaryText;
    }

    private boolean isSalaryOutOfRange(Integer[] jobSalary, Integer miniSalary, Integer maxSalary,
                                       String jobType) {
        if (jobSalary == null) {
            return true;
        }
        if (miniSalary == null) {
            return false;
        }
        if (Objects.equals("day", jobType)) {
            // 期望薪资转为平均每日的工资
            maxSalary = BigDecimal.valueOf(maxSalary).multiply(BigDecimal.valueOf(1000))
                    .divide(BigDecimal.valueOf(21.75), 0, RoundingMode.HALF_UP).intValue();
            miniSalary = BigDecimal.valueOf(miniSalary).multiply(BigDecimal.valueOf(1000))
                    .divide(BigDecimal.valueOf(21.75), 0, RoundingMode.HALF_UP).intValue();
        }
        // 如果职位薪资下限低于期望的最低薪资，返回不符合
        if (jobSalary[1] < miniSalary) {
            return true;
        }
        // 如果职位薪资上限高于期望的最高薪资，返回不符合
        return maxSalary != null && jobSalary[0] > maxSalary;
    }

    public boolean containsDeadStatus(String activeTimeText, List<String> deadStatus) {
        for (String status : deadStatus) {
            if (activeTimeText.contains(status)) {
                return true;// 一旦找到包含的值，立即返回 true
            }
        }
        return false;// 如果没有找到，返回 false
    }

    private String generateAiMessage(String keyword, String jobName, String jd) {
        AiEntity aiConfig = aiService.getAiConfig();
        String introduce = (aiConfig != null && aiConfig.getIntroduce() != null) ? aiConfig.getIntroduce() : "";
        String prompt = (aiConfig != null) ? aiConfig.getPrompt() : null;

        String requestMessage = (prompt != null)
                ? String.format(prompt, introduce, keyword, jobName, jd, config.getSayHi())
                : buildDefaultPrompt(introduce, keyword, jobName, jd);

        try {
            String result = aiService.sendRequest(requestMessage);
            if (result == null) {
                return config.getSayHi();
            }
            return result.toLowerCase().contains("false") ? config.getSayHi() : result;
        } catch (Exception e) {
            log.warn("AI请求失败，使用原有打招呼语: {}", e.getMessage());
            return config.getSayHi();
        }
    }

    private String buildDefaultPrompt(String introduce, String keyword, String jobName, String jd) {
        return "请基于以下信息生成简洁友好的中文打招呼语，不超过60字：\n" +
                "个人介绍：" + introduce + "\n" +
                "关键词：" + keyword + "\n" +
                "职位名称：" + jobName + "\n" +
                "职位描述：" + jd + "\n" +
                "参考语：" + config.getSayHi();
    }

    private Integer[] parseSalaryRange(String salaryText) {
        try {
            return Arrays.stream(salaryText.split("-")).map(s -> s.replaceAll("[^0-9]", "")) // 去除非数字字符
                    .map(Integer::parseInt) // 转换为Integer
                    .toArray(Integer[]::new); // 转换为Integer数组
        } catch (Exception e) {
            log.error("薪资解析异常！{}", e.getMessage(), e);
        }
        return null;
    }

    private void waitForSliderVerify(Page page) {
        String SLIDER_URL = "https://www.zhipin.com/web/user/safe/verify-slider";
        // 最多等待5分钟（防呆，防止死循环）
        long start = System.currentTimeMillis();
        while (true) {
            String url = page.url();
            if (url != null && url.startsWith(SLIDER_URL)) {
                progressCallback.accept("请手动完成Boss直聘滑块验证，通过后在控制台回车继续...", 0, 0);
                System.out.println("\n【滑块验证】请手动完成Boss直聘滑块验证，通过后在控制台回车继续…");
                try {
                    System.in.read();
                } catch (Exception e) {
                    log.error("等待滑块验证输入异常: {}", e.getMessage());
                }
                PlaywrightUtil.sleep(1);
                // 验证通过后页面url会变，循环再检测一次
                continue;
            }
            if ((System.currentTimeMillis() - start) > 5 * 60 * 1000) {
                throw new RuntimeException("滑块验证超时！");
            }
            break;
        }
    }


    private boolean isLoginRequired() {
        try {
            Locator buttonLocator = page.locator(LOGIN_BTNS);
            if (buttonLocator.count() > 0 && buttonLocator.textContent().contains("登录")) {
                return true;
            }
        } catch (Exception e) {
            try {
                page.locator(PAGE_HEADER).waitFor();
                Locator errorLoginLocator = page.locator(ERROR_PAGE_LOGIN);
                if (errorLoginLocator.count() > 0) {
                    errorLoginLocator.click();
                }
                return true;
            } catch (Exception ex) {
                log.info("没有出现403访问异常");
            }
            log.info("cookie有效，已登录...");
            return false;
        }
        return false;
    }

}
