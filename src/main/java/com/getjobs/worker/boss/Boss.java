package com.getjobs.worker.boss;

import com.getjobs.application.entity.AiEntity;
import com.getjobs.application.service.AiService;
import com.getjobs.application.service.BossResumeImageService;
import com.getjobs.application.service.BossService;
import com.getjobs.worker.utils.Job;
import com.getjobs.worker.utils.JobUtils;
import com.getjobs.worker.utils.PlaywrightUtil;
import com.microsoft.playwright.Frame;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.options.WaitForSelectorState;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ThreadLocalRandom;
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

    @Setter
    private Page page;
    @Setter
    private BossConfig config;
    private final BossService bossService;
    private final AiService aiService;
    private final BossResumeImageService bossResumeImageService;
    private Set<String> blackCompanies;
    private Set<String> blackRecruiters;
    private Set<String> blackJobs;
    // 记录 encryptId -> encryptUserId 的映射，用于后续更新投递状态
    private final ConcurrentMap<String, String> encryptIdToUserId = new ConcurrentHashMap<>();
    @Setter
    private ProgressCallback progressCallback;
    @Setter
    private Supplier<Boolean> shouldStopCallback;

    private final List<Job> resultList = new ArrayList<>();

    /** Boss 首页，UI 搜索的入口 */
    private static final String BOSS_HOME_URL = "https://www.zhipin.com";
    /**
     * true=先回首页、在搜索框里做一次真实 UI 搜索再进列表页；false=直接拼 URL 跳转。
     * <p>
     * 默认关掉。实测 Boss 首页在受控标签页里会一直转圈加载不完，navigate 要 60 秒才返回，
     * 期间 evaluate / locator 全部无限期阻塞（这两个 API 没有默认超时）—— 启动慢、投递卡死、
     * 管理页面按钮没反应都出在这。而岗位列表页在同一个浏览器里是秒开的。
     * 想重新试 UI 搜索路径时再打开。
     */
    private static final boolean USE_UI_SEARCH = false;
    /** 等待页面加载状态的超时（毫秒），绝不能不设 —— 见 waitForPageSettled 的说明 */
    private static final double LOAD_STATE_TIMEOUT = 10_000;
    /** 图片选择完成后，等待聊天区出现本人图片消息的最长时间 */
    private static final int IMAGE_SEND_CONFIRM_TIMEOUT_SECONDS = 45;

    /**
     * 进度回调接口
     */
    @FunctionalInterface
    public interface ProgressCallback {
        void accept(String message, Integer current, Integer total);
    }

    // 通过 Lombok @RequiredArgsConstructor 使用构造器注入 bossService 与 aiService

    public void prepare() {
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
            if (shouldStopCallback != null && Boolean.TRUE.equals(shouldStopCallback.get())) {
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
            if (shouldStopCallback.get()) {
                progressCallback.accept("用户取消投递", 0, 0);
                return;
            }

            int postCount = 0;
            // 使用 URLEncoder 对关键词进行编码
            String encodedKeyword = URLEncoder.encode(keyword, StandardCharsets.UTF_8);

            String url = searchUrl + (searchUrl.contains("?") ? "&" : "?") + "query=" + encodedKeyword;
            // 单个关键词失败不该让整个投递任务中止，下面整段都包在 try 里
            try {
            // 进列表页 + 等列表渲染，整段带重试
            openJobListWithRetry(keyword, url, cityCode);

            // 1. 基于 footer 出现滚动到底，确保加载全部岗位
            int lastCount = -1;
            int stableTries = 0;
            int staleHits = 0;
            // 上限 300 轮：原来写的是 5000，而且 stableTries 只触发强制触底、从不退出循环，
            // 一旦 footer 选择器匹配不到就会空转几千轮，每轮一次 evaluate + count，
            // 能把 playwright 线程占死几十分钟 —— 整个应用跟着卡住、投递任务也永远结束不了。
            boolean loadedAll = false;
            for (int i = 0; i < 300; i++) {
                // 停止检查：滚动加载过程中也要及时响应
                if (shouldStopCallback != null && Boolean.TRUE.equals(shouldStopCallback.get())) {
                    progressCallback.accept("用户取消投递", 0, 0);
                    return;
                }
                // 滚动加载期间 Boss 会频繁增删 frame，Playwright 派发这些事件时可能
                // 引用到已销毁的 frame（Object doesn't exist: frame@...），异常会顺着
                // 当时在飞的那个调用抛出来。这类异常和调用本身无关，跳过这一轮继续滚就行。
                try {
                    // footer 可见不能立刻就当作"加载完了"：窗口最大化时首屏很短，
                    // 第一轮 footer 就是可见的，会导致只拿到首屏 15 个岗位就退出
                    // （实测同样的搜索条件，正常滚完是 300 个）。
                    // 必须先滚一段、并且连着几轮没有新增岗位，footer 才算数。
                    boolean footerVisible = false;
                    Locator footer = page.locator("div#footer, #footer");
                    if (footer.count() > 0 && footer.first().isVisible()) {
                        footerVisible = true;
                    }
                    if (footerVisible && stableTries >= 2) {
                        log.info("【{}】已滚动到底部且连续 {} 轮无新增，判定加载完毕", keyword, stableTries);
                        loadedAll = true;
                        break;
                    }
                    // 按视口高度的90%渐进滚动，触发懒加载
                    page.evaluate("() => window.scrollBy(0, Math.floor(window.innerHeight * 1.5))");

                    // 获取卡片数量变化，判断是否需要强制触底
                    Locator cardsProbe = page.locator("//ul[contains(@class, 'rec-job-list')]//li[contains(@class, 'job-card-box')]");
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
                    // 强制触底之后仍然连着好几轮没有新岗位，就认定加载完了。
                    // 不能只靠 footer —— Boss 有些版式根本没有 #footer，只等它就是死循环。
                    if (stableTries >= 8) {
                        log.info("【{}】连续 {} 轮没有新增岗位，判定已加载完毕", keyword, stableTries);
                        loadedAll = true;
                        break;
                    }
                } catch (Exception e) {
                    if (!isStaleObjectError(e)) {
                        throw e;
                    }
                    staleHits++;
                    if (staleHits > 20) {
                        log.warn("【{}】滚动期间反复出现失效对象异常({}次)，停止继续加载", keyword, staleHits);
                        break;
                    }
                    PlaywrightUtil.sleep(1);
                }
            }
            if (!loadedAll) {
                log.warn("【{}】滚动到达 300 轮上限仍未确认加载完毕，按当前已加载的岗位继续", keyword);
            }
            // 统计最终岗位数量
            int loadedCount = countJobCards();
            log.info("【{}】岗位已全部加载，总数:{}", keyword, loadedCount);
            progressCallback.accept("岗位加载完成：" + keyword, 0, loadedCount);

            // 2. 回到页面顶部
            page.evaluate("window.scrollTo(0, 0);");
            PlaywrightUtil.sleep(1);

            // 3. 逐个遍历所有岗位
            Locator cards = page.locator("//ul[contains(@class, 'rec-job-list')]//li[contains(@class, 'job-card-box')]");
            int count = cards.count();
            for (int i = 0; i < count; i++) {
                // 检查是否需要停止
                if (shouldStopCallback != null && Boolean.TRUE.equals(shouldStopCallback.get())) {
                    progressCallback.accept("用户取消投递", i, count);
                    return;
                }

                // 重新获取卡片，避免元素过期
                cards = page.locator("//ul[contains(@class, 'rec-job-list')]//li[contains(@class, 'job-card-box')]");
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
                if (resumeSubmission(keyword, job)) {
                    postCount++;
                }

                // 为避免点击下面的卡片触发页面刷新：在点击5个卡片之后，每次点击后适度下滑
                try {
                    if (i >= 5) {
                        page.evaluate("window.scrollBy(0, 140);");
                        PlaywrightUtil.sleep(1);
                    }
                } catch (Throwable ignore) {}

                // 按 wait_time 降速，别把风控刷出来
                pauseBetweenJobs();

                // 停顿期间可能已经被弹到安全验证页，发现了就停下来等人工过验证，
                // 而不是继续闷头点下去（继续点只会让后面的关键词全部失败）
                if (isSecurityVerifyUrl(safeUrl())) {
                    log.warn("【{}】遍历过程中被跳转到安全验证页：{}", keyword, safeUrl());
                    if (progressCallback != null) {
                        progressCallback.accept("触发Boss安全校验，请在浏览器中手动完成验证", i + 1, count);
                    }
                    waitForSliderVerify(page);
                }
            }
            log.info("【{}】岗位已投递完毕！已投递岗位数量:{}", keyword, postCount);
            } catch (Exception e) {
                log.error("【{}】处理失败，跳过该关键词继续下一个：{}", keyword, e.getMessage(), e);
                if (progressCallback != null) {
                    progressCallback.accept("关键词[" + keyword + "]失败已跳过：" + e.getMessage(), 0, 0);
                }
            }
        }
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
     * 进入岗位列表页。
     * <p>
     * 优先模拟真人路径：回首页 -> 在搜索框里逐字输入关键词 -> 点搜索按钮，
     * 而不是冷启动直接把拼好的列表页 URL 丢给浏览器。
     * <p>
     * UI 搜索只能带上关键词和首页当前城市，config 里的城市/薪资/经验等筛选项仍然只能靠 URL，
     * 所以落地之后如果条件对不上，会再做一次同标签页跳转 —— 此时已经是热会话 + 同源 referer，
     * 和冷启动直闯不是一回事。UI 搜索任何一步失败都会退回原来的直接跳转，不影响主流程。
     */
    private void openJobList(String keyword, String targetUrl, String cityCode) {
        boolean uiSearchDone = USE_UI_SEARCH && searchFromHomePage(keyword);
        if (!uiSearchDone) {
            navigateToJobList(targetUrl);
        } else if (needsUrlFilters(targetUrl, cityCode)) {
            log.info("【{}】UI搜索已落地，补充筛选条件跳转：{}", keyword, targetUrl);
            navigateToJobList(targetUrl);
        }
        warnIfSecurityCheck(keyword);
    }

    /**
     * 在 Boss 首页搜索框里做一次真实的 UI 搜索。
     *
     * @return 是否成功落到岗位列表页
     */
    private boolean searchFromHomePage(String keyword) {
        try {
            String current = page.url();
            if (current == null || !current.startsWith(BOSS_HOME_URL) || current.contains("/web/geek/")) {
                navigateTo(BOSS_HOME_URL);
            }
            // 首页落地后还会自己跳一次（/shanghai/?seoRefer=index 之类），
            // 不等它跳完就找搜索框，会卡在 "waiting for navigation to finish" 直到超时
            waitForPageSettled();

            Locator input = page.locator(HOME_SEARCH_INPUT).first();
            input.waitFor(new Locator.WaitForOptions().setTimeout(10_000));
            input.click();
            input.fill("");
            // 逐字输入，模拟真人打字节奏；一次性 fill 在输入行为层面太干净了
            input.pressSequentially(keyword, new Locator.PressSequentiallyOptions().setDelay(140));
            PlaywrightUtil.sleep(1);

            Locator searchBtn = page.locator(HOME_SEARCH_BUTTON).first();
            if (searchBtn.count() > 0) {
                searchBtn.click();
            } else {
                input.press("Enter");
            }
            // 表单提交是当前标签页跳转；若 Boss 改成新开标签页，这里会超时并退回直接跳转
            page.waitForURL("**/web/geek/jobs**", new Page.WaitForURLOptions().setTimeout(15_000));
            log.info("【{}】已通过首页搜索框进入岗位列表：{}", keyword, page.url());
            return true;
        } catch (Exception e) {
            log.warn("【{}】首页UI搜索失败，退回直接跳转：{}", keyword, e.getMessage());
            return false;
        }
    }

    /**
     * UI 搜索落地后，判断还需不需要用 URL 把筛选条件补上。
     */
    private boolean needsUrlFilters(String targetUrl, String cityCode) {
        String landed = page.url();
        if (landed == null) {
            return true;
        }
        // 首页搜索用的是首页当前城市，不一定等于配置里的城市
        if (cityCode != null && !cityCode.isEmpty() && !landed.contains("city=" + cityCode)) {
            return true;
        }
        // 除 city/query 外还有别的筛选项（薪资、经验、学历……），UI 搜索带不上
        int queryStart = targetUrl.indexOf('?');
        if (queryStart < 0) {
            return false;
        }
        for (String param : targetUrl.substring(queryStart + 1).split("&")) {
            int eq = param.indexOf('=');
            String name = eq < 0 ? param : param.substring(0, eq);
            String value = eq < 0 ? "" : param.substring(eq + 1);
            if (value.isEmpty() || "city".equals(name) || "query".equals(name)) {
                continue;
            }
            return true;
        }
        return false;
    }

    private void navigateToJobList(String url) {
        navigateTo(url);
    }

    /**
     * 导航并重试一次。
     * <p>
     * Boss 页面自己会做客户端跳转，撞上时 Playwright 报 net::ERR_ABORTED；
     * 这类失败重试一次基本就过了，不该让整个投递任务因此中止。
     */
    private void navigateTo(String url) {
        // Boss 的 SPA 在网络不佳或被限流时，15 秒常常不够，之前实测连着两次都超时
        Page.NavigateOptions options = new Page.NavigateOptions()
                .setWaitUntil(com.microsoft.playwright.options.WaitUntilState.DOMCONTENTLOADED)
                .setTimeout(45_000);
        try {
            page.navigate(url, options);
        } catch (Exception first) {
            // 导航期间页面自己又跳了一次时，Playwright 会抛 "Object doesn't exist: request@/frame@"，
            // 但页面其实已经到位了。先看落地 URL，别白白重试一遍。
            if (landedOn(url)) {
                log.debug("导航报了失效对象异常但页面已到位，忽略：{}", first.getMessage());
                settleAfterNavigation();
                return;
            }
            log.warn("导航失败，1秒后重试一次：{} | {}", url, first.getMessage());
            PlaywrightUtil.sleep(1);
            try {
                page.navigate(url, options);
            } catch (Exception second) {
                if (!landedOn(url)) {
                    throw second;
                }
                log.debug("重试同样报失效对象异常但页面已到位，忽略：{}", second.getMessage());
            }
        }
        settleAfterNavigation();
    }

    /**
     * 判断页面是否已经落在目标地址上（只比较路径，查询参数会被 Boss 改写）。
     */
    private boolean landedOn(String targetUrl) {
        try {
            String current = page.url();
            if (current == null) {
                return false;
            }
            String targetPath = targetUrl.split("\\?")[0];
            return current.startsWith(targetPath);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 等页面真正稳定下来再继续。
     * <p>
     * DOMCONTENTLOADED 返回之后 Boss 还会自己做客户端跳转，此时 frame 被替换，
     * 后续任何 locator 调用都会报 "Object doesn't exist: frame@..."。
     */
    private void settleAfterNavigation() {
        waitForPageSettled();
    }

    /**
     * 等页面进入 domcontentloaded，最多等 {@value #LOAD_STATE_TIMEOUT} 毫秒。
     * <p>
     * 两个坑都踩过了：
     * 一是不能等 LOAD/NETWORKIDLE —— Boss 是 SPA + WebSocket 长连接，这两个状态可能永远不到；
     * 二是必须显式给超时 —— 不给超时就是无限期挂起，而"卡住"不是异常，
     * 外面包 try/catch 完全没用（实测把线程挂了两分钟以上还在等）。
     */
    private void waitForPageSettled() {
        try {
            page.waitForLoadState(com.microsoft.playwright.options.LoadState.DOMCONTENTLOADED,
                    new Page.WaitForLoadStateOptions().setTimeout(LOAD_STATE_TIMEOUT));
        } catch (Exception ignore) {
            // 等不到就算了，后面的 locator 调用自己有超时
        }
        PlaywrightUtil.sleep(2);
    }

    /**
     * 判断是不是 Playwright 的"对象已失效"异常。
     * <p>
     * 页面频繁增删 frame 时，Playwright Java 在派发事件时会引用到已经销毁的对象，
     * 抛 "Object doesn't exist: frame@..."。这个异常和当时在飞的那个调用没有因果关系，
     * 连接本身仍然可用，重试即可。
     */
    private static boolean isStaleObjectError(Throwable e) {
        String message = e.getMessage();
        if (message == null) {
            return false;
        }
        // 两类都是"页面在动"导致的瞬时异常，跟调用本身没有因果关系，重试就好：
        // - Object doesn't exist: frame@/request@  事件派发时引用到已销毁的对象
        // - Execution context was destroyed         求值期间页面发生了导航
        return message.contains("Object doesn't exist")
                || message.contains("Execution context was destroyed");
    }

    /**
     * 进入岗位列表并等列表渲染出来，整段带重试。
     * Boss 首页/列表页在登录态下会连着跳好几次，一次失败很正常。
     */
    private void openJobListWithRetry(String keyword, String targetUrl, String cityCode) {
        // 只重试一次：每次都要走导航(最长45秒×2) + 等列表(15秒×3)，
        // 试三轮的话一个关键词失败要耗掉好几分钟，界面上看着就是"卡住不动"
        RuntimeException last = null;
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                openJobList(keyword, targetUrl, cityCode);
                waitForJobList();
                return;
            } catch (RuntimeException e) {
                last = e;
                log.warn("【{}】进入岗位列表失败（第{}/2次）：{}", keyword, attempt,
                        e.getMessage() == null ? e.toString() : e.getMessage().split("\n")[0]);
                if (attempt < 2) {
                    PlaywrightUtil.sleep(3);
                }
            }
        }
        throw last;
    }

    /**
     * 统计当前列表里的岗位卡片数量，对失效对象异常做重试。
     */
    private int countJobCards() {
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                return page.locator(JOB_LIST_SELECTOR).count();
            } catch (Exception e) {
                if (!isStaleObjectError(e) || attempt == 3) {
                    if (isStaleObjectError(e)) {
                        log.warn("统计岗位数量始终失败，按 0 处理：{}", e.getMessage());
                        return 0;
                    }
                    throw e;
                }
                PlaywrightUtil.sleep(1);
            }
        }
        return 0;
    }

    /**
     * 等待岗位列表容器出现，带重试。
     * 页面在这期间可能还在跳转，一次失败不代表真的没有列表。
     */
    private void waitForJobList() {
        // Boss 改版频繁，推荐页和搜索结果页的容器类名不一样。
        // 用逗号把候选选择器拼成一个，让 Playwright 一次性等"任意一个先出现"，
        // 不要逐个 8 秒串行试 —— 那样一轮就要 48 秒，页面正常时也慢得像卡死。
        String containers = String.join(", ",
                "ul.rec-job-list",
                "ul.job-list-box",
                ".job-list-box",
                ".search-job-result",
                "li.job-card-box",
                "li.job-card-wrapper");

        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                page.waitForSelector(containers,
                        new Page.WaitForSelectorOptions().setTimeout(15_000));
                log.info("岗位列表容器已出现（第{}次尝试）", attempt);
                return;
            } catch (Exception e) {
                log.warn("等待岗位列表失败（第{}/3次），当前页面: {}", attempt, safeUrl());
                settleAfterNavigation();
            }
        }
        dumpListPageStructure();
        throw new IllegalStateException("岗位列表始终未出现，当前页面: " + safeUrl());
    }

    private String safeUrl() {
        try {
            return page.url();
        } catch (Exception e) {
            return "(取不到URL)";
        }
    }

    /**
     * 列表容器一个都没命中时，把页面上的候选列表结构打出来，方便修选择器。
     */
    private void dumpListPageStructure() {
        try {
            Object info = page.evaluate("""
                    () => {
                      const uls = Array.from(document.querySelectorAll('ul,div'))
                        .filter(el => el.className && typeof el.className === 'string'
                                   && /job|list|rec/i.test(el.className))
                        .slice(0, 15)
                        .map(el => el.tagName.toLowerCase() + '.' + el.className.trim().replace(/\\s+/g, '.')
                                 + ' (children=' + el.childElementCount + ')');
                      return { title: document.title, url: location.href, candidates: uls };
                    }""");
            log.warn("列表页结构快照: {}", info);
        } catch (Exception e) {
            log.warn("抓取列表页结构失败: {}", e.getMessage());
        }
    }

    /**
     * 落地后检查是否被弹到风控/验证页，日志里能直接看出是哪一步触发的。
     */
    private void warnIfSecurityCheck(String keyword) {
        String url = page.url();
        if (url == null) {
            return;
        }
        if (isSecurityVerifyUrl(url)) {
            log.warn("【{}】进入岗位列表时被跳转到验证页：{}", keyword, url);
            if (progressCallback != null) {
                progressCallback.accept("触发Boss安全校验，请手动完成验证", 0, 0);
            }
            waitForSliderVerify(page);
        }
    }

    /**
     * 备注：目前Boss无法通过新标签页打开立即沟通按钮，所以只能点击更多详情，然后从更多详情里打开聊天按钮
     */
    @SneakyThrows
    private boolean resumeSubmission(String keyword, Job job) {
        // 若收到停止指令，直接短路返回
        if (shouldStopCallback != null && Boolean.TRUE.equals(shouldStopCallback.get())) {
            log.info("停止指令已触发，跳过投递 | 公司：{} | 岗位：{}", job.getCompanyName(), job.getJobName());
            return false;
        }
        // 调试模式：仅遍历不投递
        if (Boolean.TRUE.equals(config.getDebugger())) {
            log.info("调试模式：仅遍历岗位，不投递 | 公司：{} | 岗位：{}", job.getCompanyName(), job.getJobName());
            return false;
        }

        // 1. 查找"查看更多信息"按钮（必须存在且新开页）
        Locator moreInfoBtn = page.locator("a.more-job-btn");
        if (moreInfoBtn.count() == 0) {
            log.warn("未找到\"查看更多信息\"按钮，跳过...");
            return false;
        }
        // 强制用js新开tab
        String href = moreInfoBtn.first().getAttribute("href");
        if (href == null || !href.startsWith("/job_detail/")) {
            log.warn("未获取到岗位详情链接，跳过...");
            return false;
        }
        String detailUrl = "https://www.zhipin.com" + href;
        // 2. 在新窗口打开详情页
        Page detailPage = page.context().newPage();
        detailPage.navigate(detailUrl);
        PlaywrightUtil.sleep(1);

        // 3. 查找"立即沟通"按钮
        Locator chatBtn = detailPage.locator("a.btn-startchat, a.op-btn-chat");
        boolean foundChatBtn = false;
        for (int i = 0; i < 5; i++) {
            if (shouldStopCallback != null && Boolean.TRUE.equals(shouldStopCallback.get())) {
                log.info("停止指令已触发，结束查找聊天按钮 | 公司：{} | 岗位：{}", job.getCompanyName(), job.getJobName());
                try { detailPage.close(); } catch (Exception ignore) {}
                return false;
            }
            if (chatBtn.count() > 0 && (chatBtn.first().textContent().contains("立即沟通"))) {
                foundChatBtn = true;
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
            return false;
        }
        chatBtn.first().click();
        PlaywrightUtil.sleep(1);

        // 4. 等待聊天输入框
        Locator inputLocator = detailPage.locator("div#chat-input.chat-input[contenteditable='true'], textarea.input-area");
        boolean inputReady = false;
        for (int i = 0; i < 10; i++) {
            if (shouldStopCallback != null && Boolean.TRUE.equals(shouldStopCallback.get())) {
                log.info("停止指令已触发，结束等待聊天输入框 | 公司：{} | 岗位：{}", job.getCompanyName(), job.getJobName());
                try { detailPage.close(); } catch (Exception ignore) {}
                return false;
            }
            if (inputLocator.count() > 0 && inputLocator.first().isVisible()) {
                inputReady = true;
                break;
            }
            PlaywrightUtil.sleep(1);
        }
        if (!inputReady) {
            log.warn("聊天输入框未出现，跳过: {}", job.getJobName());
            // 关闭详情页
            try {
                detailPage.close();
            } catch (Exception ignore) {
            }
            return false;
        }

        // 5. AI智能生成打招呼语
        String aiMessage = null;
        if (Boolean.TRUE.equals(config.getEnableAI())) {
            String jd = job.getJobInfo();
            if (jd != null && !jd.isEmpty()) {
                AiMessageDecision decision = generateAiMessage(keyword, job.getJobName(), jd);
                if (decision.skipCurrentJob()) {
                    log.info("AI返回false，跳过当前岗位投递 | 公司：{} | 岗位：{}",
                            job.getCompanyName(), job.getJobName());
                    if (progressCallback != null) {
                        progressCallback.accept("AI判断不匹配，跳过当前岗位：" + job.getJobName(), 0, 0);
                    }
                    try { detailPage.close(); } catch (Exception ignore) {}
                    return false;
                }
                aiMessage = decision.message();
            }
        }
        String message = isValidString(aiMessage) ? aiMessage : config.getSayHi();

        // 6. 输入打招呼语
        Locator input = inputLocator.first();
        input.click();
        Object tagObj = input.evaluate("el => el.tagName.toLowerCase()");
        if (tagObj instanceof String && ((String) tagObj).equals("textarea")) {
            input.fill(message);
        } else {
            // 对 contenteditable 节点写入文本并派发 input 事件
            input.evaluate("(el, msg) => { el.innerText = msg; el.dispatchEvent(new Event('input')); }", message);
        }

        // 7. 点击发送按钮（div.send-message 或 button.btn-send）
        Locator sendText = detailPage.locator("div.send-message, button[type='send'].btn-send, button.btn-send");
        boolean sendSuccess = false;
        if (sendText.count() > 0) {
            sendText.first().click();
            PlaywrightUtil.sleep(1);
            sendSuccess = true;
        } else {
            log.warn("未找到发送按钮，自动跳过！岗位：{}", job.getJobName());
        }

        // 8. 发送图片简历（可选）
        boolean imgResume = false;
        if (sendSuccess && Boolean.TRUE.equals(config.getSendImgResume())) {
            imgResume = sendImageResume(detailPage);
        }

        log.info("投递完成 | 公司：{} | 岗位：{} | 薪资：{} | 招呼语：{} | 图片简历：{}", job.getCompanyName(), job.getJobName(), job.getSalary(), message, imgResume ? "已发送" : "未发送");

        // 9. 关闭新打开的详情页
        try {
            detailPage.close();
        } catch (Exception ignore) {
        }
        PlaywrightUtil.sleep(1);

        // 10. 更新数据库投递状态 & 成功投递加入结果
        if (sendSuccess) {
            // 从详情链接提取 encrypt_id，并映射到 encrypt_user_id
            String encryptId = extractEncryptId(detailUrl);
            String encryptUserId = encryptId != null ? encryptIdToUserId.get(encryptId) : null;
            if (encryptId != null && encryptUserId != null) {
                try {
        bossService.updateDeliveryStatus(encryptId, encryptUserId, "已投递");
                    log.info("投递成功 | 公司：{} | 岗位：{} | encryptId：{} | encryptUserId：{}", job.getCompanyName(), job.getJobName(), encryptId, encryptUserId);
                } catch (Exception e) {
                    log.warn("更新投递状态为已投递失败：{}", e.getMessage());
                }
            } else {
                log.debug("未能找到 encryptId/encryptUserId 用于更新投递状态，detailUrl: {}", detailUrl);
            }
            resultList.add(job);
        } else {
            // 若发生发送失败，也进行状态更新
            String encryptId = extractEncryptId(detailUrl);
            String encryptUserId = encryptId != null ? encryptIdToUserId.get(encryptId) : null;
            if (encryptId != null && encryptUserId != null) {
                try {
        bossService.updateDeliveryStatus(encryptId, encryptUserId, "投递失败");
                    log.warn("投递失败 | 公司：{} | 岗位：{} | encryptId：{} | encryptUserId：{}", job.getCompanyName(), job.getJobName(), encryptId, encryptUserId);
                } catch (Exception e) {
                    log.warn("更新投递状态为投递失败异常：{}", e.getMessage());
                }
            }
        }
        return sendSuccess;
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
            Optional<java.nio.file.Path> resumeImage = bossResumeImageService.getCurrentImagePath();
            if (resumeImage.isEmpty()) {
                log.warn("未上传图片简历，跳过图片发送；请先在 Boss 管理页面上传");
                return false;
            }
            java.nio.file.Path imagePath = resumeImage.get();

            Locator imageInput = prepareImageUploadInput(page);
            if (imageInput == null) {
                log.error("发送图片简历失败：当前聊天层和重新进入的完整聊天页中均未找到图片上传控件，页面：{}",
                        safePageUrl(page));
                return false;
            }

            // 必须在完整聊天页就绪后再记录基线；否则重新进入聊天时加载出的历史图片会被误判为本次发送。
            JSONObject beforeState = readOwnImageMessageState(page);
            int imageMessageCountBefore = beforeState.optInt("imageCount", 0);

            imageInput.setInputFiles(imagePath);
            log.info("已选择图片简历，等待 Boss 确认发送：{}", imagePath.getFileName());

            // 必须等到聊天记录中出现新的本人图片消息，并且上传/发送状态已经结束。
            // 这一步完成前不能关闭详情页，否则大图片或网络较慢时上传会被中断。
            return waitForImageMessageSent(page, imageMessageCountBefore, imagePath.getFileName().toString());
        } catch (Throwable e) {
            log.error("发送图片简历失败：{}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 获取可用的图片上传控件。
     * <p>
     * Boss 第一次“立即沟通”有时只打开简化打招呼层，发完文字后该层不会提供图片按钮。
     * 此时关闭简化层并重新点击“继续沟通”，等待完整聊天 DOM；不等待 URL，避免导航期间
     * Patchright 持有旧 frame 而出现 Object doesn't exist: frame@...。
     */
    private Locator prepareImageUploadInput(Page page) {
        Locator currentInput = waitForImageUploadInput(page, 2);
        if (currentInput != null) {
            log.info("当前聊天层已找到图片上传控件");
            return currentInput;
        }

        log.info("当前打招呼层没有图片上传控件，尝试重新进入完整聊天页面");
        closeLightweightChatPanel(page);

        Locator chatButton = findVisibleChatButton(page);
        if (chatButton == null) {
            log.warn("重新进入完整聊天失败：未找到【继续沟通/立即沟通】按钮，页面：{}", safePageUrl(page));
            return null;
        }

        try {
            String buttonText = Optional.ofNullable(chatButton.textContent()).orElse("").trim();
            chatButton.click(new Locator.ClickOptions().setTimeout(10_000));
            log.info("已点击【{}】，等待完整聊天页面的图片控件", buttonText.isEmpty() ? "继续沟通" : buttonText);
        } catch (Throwable clickError) {
            // 点击可能已经触发导航，只是旧 frame 在回包时被销毁；继续通过新 Locator 探测 DOM。
            log.debug("点击沟通按钮时页面发生切换，将继续检查新聊天页面：{}", clickError.getMessage());
        }

        Locator reopenedInput = waitForImageUploadInput(page, 15);
        if (reopenedInput != null) {
            log.info("重新进入完整聊天页面后已找到图片上传控件");
        }
        return reopenedInput;
    }

    private void closeLightweightChatPanel(Page page) {
        try {
            Locator closeButtons = page.locator("i.icon-close");
            int count = closeButtons.count();
            for (int i = 0; i < count; i++) {
                Locator closeButton = closeButtons.nth(i);
                if (closeButton.isVisible()) {
                    closeButton.click(new Locator.ClickOptions().setTimeout(5_000));
                    PlaywrightUtil.sleep(1);
                    log.debug("已关闭不含图片控件的简化打招呼层");
                    return;
                }
            }
        } catch (Throwable closeError) {
            log.debug("关闭简化打招呼层失败，将直接尝试重新进入聊天：{}", closeError.getMessage());
        }
    }

    private Locator findVisibleChatButton(Page page) {
        try {
            Locator buttons = page.locator("a.btn-startchat, a.op-btn-chat");
            int count = buttons.count();
            for (int i = 0; i < count; i++) {
                Locator button = buttons.nth(i);
                if (button.isVisible()) {
                    return button;
                }
            }
        } catch (Throwable findError) {
            log.debug("查找沟通按钮失败：{}", findError.getMessage());
        }
        return null;
    }

    /** 每轮都重新创建 Locator，以适应点击沟通按钮后发生的主 frame 替换。 */
    private Locator waitForImageUploadInput(Page page, int timeoutSeconds) {
        for (int second = 0; second < timeoutSeconds; second++) {
            if (page.isClosed()) {
                return null;
            }
            Locator input = findAttachedImageUploadInput(page);
            if (input != null) {
                return input;
            }
            PlaywrightUtil.sleep(1);
        }
        return null;
    }

    private Locator findAttachedImageUploadInput(Page page) {
        List<String> selectors = List.of(
                "div.btn-sendimg[aria-label='发送图片'] input[type='file']",
                "div[aria-label='发送图片'].btn-sendimg input[type='file']",
                "[aria-label='发送图片'] input[type='file']",
                "input[type='file'][accept*='image/gif'][accept*='image/jpeg']"
        );

        try {
            // 完整聊天页在不同版本中可能位于主 frame 或子 frame，因此逐个检查。
            for (Frame frame : page.frames()) {
                if (frame.isDetached()) {
                    continue;
                }
                for (String selector : selectors) {
                    try {
                        Locator candidate = frame.locator(selector).first();
                        if (candidate.count() > 0) {
                            // 文件输入框通常隐藏，只需确认已挂载，不能要求 visible。
                            candidate.waitFor(new Locator.WaitForOptions()
                                    .setState(WaitForSelectorState.ATTACHED)
                                    .setTimeout(1_000));
                            return candidate;
                        }
                    } catch (Throwable staleFrame) {
                        // 页面切换中的旧 frame 会失效，下一轮会重新获取 page.frames()。
                    }
                }
            }
        } catch (Throwable frameListError) {
            log.debug("检查图片上传控件时页面正在切换：{}", frameListError.getMessage());
        }
        return null;
    }

    private String safePageUrl(Page page) {
        try {
            return page.url();
        } catch (Throwable ignore) {
            return "无法读取";
        }
    }

    /**
     * 等待图片真正进入本人消息列表，而不是仅确认文件已选择。
     */
    private boolean waitForImageMessageSent(Page page, int imageMessageCountBefore, String filename) {
        JSONObject lastState = null;
        int stableSeconds = 0;
        for (int second = 1; second <= IMAGE_SEND_CONFIRM_TIMEOUT_SECONDS; second++) {
            if (page.isClosed()) {
                log.error("图片简历发送确认失败：聊天页面已关闭，文件：{}", filename);
                return false;
            }

            try {
                lastState = readOwnImageMessageState(page);
                int currentCount = lastState.optInt("imageCount", 0);
                boolean failed = lastState.optBoolean("failed", false);
                boolean sending = lastState.optBoolean("sending", false);
                String status = lastState.optString("status", "");

                if (currentCount > imageMessageCountBefore && failed) {
                    log.error("图片简历发送失败，聊天消息显示失败状态，文件：{}，状态：{}", filename, status);
                    return false;
                }
                if (currentCount > imageMessageCountBefore && !sending) {
                    stableSeconds++;
                    // 新图片消息连续稳定三秒后再关闭页面，避免刚插入的临时消息仍在后台上传。
                    if (stableSeconds >= 3) {
                        log.info("图片简历已出现在本人聊天记录中，确认发送完成，文件：{}，状态：{}",
                                filename, status);
                        return true;
                    }
                } else {
                    stableSeconds = 0;
                }
            } catch (Throwable probeError) {
                stableSeconds = 0;
                log.debug("等待图片简历发送确认时读取聊天状态失败（第{}秒）：{}", second, probeError.getMessage());
            }
            PlaywrightUtil.sleep(1);
        }

        log.error("图片简历已选择，但{}秒内没有确认出现在本人聊天记录中，按发送失败处理。文件：{}，最后状态：{}",
                IMAGE_SEND_CONFIRM_TIMEOUT_SECONDS,
                filename,
                lastState != null ? lastState.toString() : "无法读取");
        return false;
    }

    /**
     * 读取 Boss 聊天区本人图片消息的数量及最后一条图片消息状态。
     * item-myself 是本人消息，item-friend 是招聘者消息；只检查 message-content，避免把头像误判为图片消息。
     */
    private JSONObject readOwnImageMessageState(Page page) {
        String probeScript = """
                () => {
                  const root = document.querySelector('.chat-message .im-list')
                    || document.querySelector('.chat-record');
                  if (!root) {
                    return JSON.stringify({ imageCount: 0, sending: false, failed: false, status: '', rootFound: false });
                  }

                  const ownMessages = Array.from(root.querySelectorAll('li.message-item.item-myself'));
                  const imageMessages = ownMessages.filter(item => {
                    const content = item.querySelector('.message-content');
                    if (!content) return false;
                    return Boolean(content.querySelector(
                      'img, [class*="image-content"], [class*="message-image"], [class*="message-img"], ' +
                      '[class*="picture"], [style*="background-image"]'
                    ));
                  });

                  const last = imageMessages.length > 0 ? imageMessages[imageMessages.length - 1] : null;
                  if (!last) {
                    return JSON.stringify({ imageCount: 0, sending: false, failed: false, status: '', rootFound: true });
                  }

                  const statusEl = last.querySelector('.message-status');
                  const status = statusEl ? (statusEl.textContent || '').trim() : '';
                  const classText = [last, ...last.querySelectorAll('*')]
                    .map(el => typeof el.className === 'string' ? el.className : '')
                    .join(' ')
                    .toLowerCase();
                  const messageText = (last.textContent || '').trim();
                  const sending = /uploading|sending|upload-progress|send-progress/.test(classText)
                    || /上传中|发送中/.test(messageText + status);
                  const failed = /upload-fail|upload-error|send-fail|send-error|message-fail/.test(classText)
                    || /上传失败|发送失败|重新发送|点击重试/.test(messageText + status);

                  return JSON.stringify({
                    imageCount: imageMessages.length,
                    sending,
                    failed,
                    status,
                    rootFound: true
                  });
                }
                """;

        try {
            for (Frame frame : page.frames()) {
                if (frame.isDetached()) {
                    continue;
                }
                try {
                    Object stateJson = frame.evaluate(probeScript);
                    JSONObject state = new JSONObject(String.valueOf(stateJson));
                    if (state.optBoolean("rootFound", false)) {
                        return state;
                    }
                } catch (Throwable staleFrame) {
                    // frame 正在替换时忽略，本轮按未找到聊天记录处理。
                }
            }
        } catch (Throwable frameListError) {
            log.debug("读取聊天图片状态时页面正在切换：{}", frameListError.getMessage());
        }
        return new JSONObject()
                .put("imageCount", 0)
                .put("sending", false)
                .put("failed", false)
                .put("status", "")
                .put("rootFound", false);
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

    /** AI 生成结果：skipCurrentJob=true 表示只跳过当前岗位，不影响同企业的其他岗位。 */
    private record AiMessageDecision(boolean skipCurrentJob, String message) {}

    private AiMessageDecision generateAiMessage(String keyword, String jobName, String jd) {
        AiEntity aiConfig = aiService.getAiConfig();
        String introduce = (aiConfig != null && aiConfig.getIntroduce() != null) ? aiConfig.getIntroduce() : "";
        String prompt = (aiConfig != null) ? aiConfig.getPrompt() : null;

        String requestMessage = (prompt != null)
                ? String.format(prompt, introduce, keyword, jobName, jd, config.getSayHi())
                : buildDefaultPrompt(introduce, keyword, jobName, jd);

        try {
            String result = aiService.sendRequest(requestMessage);
            if (result == null) {
                return new AiMessageDecision(false, config.getSayHi());
            }
            if (isAiRejectResponse(result)) {
                return new AiMessageDecision(true, null);
            }
            return new AiMessageDecision(false, result);
        } catch (Exception e) {
            log.warn("AI请求失败，使用原有打招呼语: {}", e.getMessage());
            return new AiMessageDecision(false, config.getSayHi());
        }
    }

    /**
     * 只把独立的 false（允许引号、反引号和常见句末标点）识别为拒绝，
     * 避免正常招呼语中偶然出现 false 字样时误跳过企业。
     */
    private boolean isAiRejectResponse(String result) {
        return result != null
                && result.trim().matches("(?i)^[*`'\"\\s]*false[*`'\"。.!！\\s]*$");
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

    /** wait_time 没配或配得不合法时用的秒数 */
    private static final int DEFAULT_WAIT_TIME_SECONDS = 10;

    /**
     * 取配置里的 wait_time（秒），非法值一律退回默认值。
     */
    private int resolveWaitTimeSeconds() {
        try {
            String raw = config == null ? null : config.getWaitTime();
            if (raw != null && !raw.isBlank()) {
                int parsed = Integer.parseInt(raw.trim());
                if (parsed > 0) {
                    return parsed;
                }
            }
        } catch (NumberFormatException ignored) {
            // 配置里塞了非数字，用默认值
        }
        return DEFAULT_WAIT_TIME_SECONDS;
    }

    /**
     * 每处理完一个岗位后的停顿。
     * <p>
     * 上一轮实测 7 分钟连刷 297 个岗位详情，直接把 Boss 风控触发了，
     * 后续关键词全部被弹到安全验证页。这里按 wait_time 降速：
     * 正常模式在 [wait_time/2, wait_time] 之间随机，避免固定节奏本身成为特征；
     * 调试模式固定用最大值 wait_time，方便观察。
     */
    private void pauseBetweenJobs() {
        int waitTime = resolveWaitTimeSeconds();
        int seconds;
        if (Boolean.TRUE.equals(config.getDebugger())) {
            seconds = waitTime;
        } else {
            int min = Math.max(1, waitTime / 2);
            seconds = min >= waitTime ? waitTime
                    : ThreadLocalRandom.current().nextInt(min, waitTime + 1);
        }
        log.debug("岗位间停顿 {} 秒（wait_time={}，debugger={}）", seconds, waitTime, config.getDebugger());
        PlaywrightUtil.sleep(seconds);
    }

    /**
     * 判断是不是 Boss 的安全验证页。
     * <p>
     * 实测密集遍历后会被弹到 /web/passport/zp/verify.html（极验滑块，页面上是
     * div.geetest_success_correct 那一套），而原来的判断只认 verify-slider，
     * 导致真正遇到验证时程序把关键词当失败跳过，人也不知道要去过验证。
     */
    private static boolean isSecurityVerifyUrl(String url) {
        if (url == null) {
            return false;
        }
        return url.contains("/web/user/safe/verify-slider")
                || url.contains("/web/passport/zp/verify")
                || url.contains("/web/passport/zp/security")
                || url.contains("security-check");
    }

    private void waitForSliderVerify(Page page) {
        // 最多等待5分钟（防呆，防止死循环）
        long start = System.currentTimeMillis();
        while (true) {
            String url = page.url();
            if (isSecurityVerifyUrl(url)) {
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
