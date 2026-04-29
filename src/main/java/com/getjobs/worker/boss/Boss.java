package com.getjobs.worker.boss;

import com.getjobs.application.entity.AiEntity;
import com.getjobs.application.service.AiService;
import com.getjobs.application.service.BossService;
import com.getjobs.application.service.ResumeOptimizationService;
import com.getjobs.application.service.ResumeOptimizationService.ResumeOptimizationRequest;
import com.getjobs.application.service.ResumeOptimizationService.ResumeOptimizationResponse;
import com.getjobs.worker.utils.Job;
import com.getjobs.worker.utils.JobUtils;
import com.getjobs.worker.utils.PlaywrightUtil;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.Response;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
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
    private static final String JOB_LIST_XPATH = "//ul[contains(@class, 'rec-job-list')]";
    private static final String SECURITY_PAGE_FRAGMENT = "/web/passport/zp/security.html";
    private static final String SLIDER_PAGE_FRAGMENT = "/web/user/safe/verify-slider";
    private static final String SECURITY_CHECK_PARAM = "_security_check=";

    @Setter
    private Page page;
    @Setter
    private BossConfig config;
    private final BossService bossService;
    private final AiService aiService;
    private final ResumeOptimizationService resumeOptimizationService;
    private Set<String> blackCompanies;
    private Set<String> blackRecruiters;
    private Set<String> blackJobs;
    // 记录 encryptId -> encryptUserId 的映射，用于后续更新投递状态
    private final ConcurrentMap<String, String> encryptIdToUserId = new ConcurrentHashMap<>();
    @Setter
    private ProgressCallback progressCallback;
    @Setter
    private Supplier<Boolean> shouldStopCallback;
    @Setter
    private String bossResumeText;

    private final Map<String, ResumeOptimizationResponse> resumeOptimizationCache = new HashMap<>();
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
            page.navigate(url, new Page.NavigateOptions()
                    .setWaitUntil(com.microsoft.playwright.options.WaitUntilState.DOMCONTENTLOADED)
                    .setTimeout(15_000));
            // 等待列表容器出现，确保页面完成首屏渲染
            if (!waitForJobListOrSecurityCheck(keyword, url)) {
                return;
            }

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
                } catch (PlaywrightException e) {
                    if (!handleNavigationChangingDuringSearch(e, keyword, url)) {
                        throw e;
                    }
                    return;
                }
                // 按视口高度的90%渐进滚动，触发懒加载
                if (!safePageEvaluate("() => window.scrollBy(0, Math.floor(window.innerHeight * 1.5))", keyword, url)) {
                    return;
                }

                // 获取卡片数量变化，判断是否需要强制触底
                int currentCount;
                try {
                    Locator cardsProbe = page.locator(JOB_LIST_XPATH + "//li[contains(@class, 'job-card-box')]");
                    currentCount = cardsProbe.count();
                } catch (PlaywrightException e) {
                    if (!handleNavigationChangingDuringSearch(e, keyword, url)) {
                        throw e;
                    }
                    return;
                }
                if (currentCount == lastCount) {
                    stableTries++;
                } else {
                    stableTries = 0;
                }
                lastCount = currentCount;

                if (stableTries >= 3) { // 连续多次无新增，则强制触底一次
                    if (!safePageEvaluate("() => window.scrollTo(0, document.body.scrollHeight)", keyword, url)) {
                        return;
                    }
                    // 触底不再等待，继续检测 footer 出现
                }
            }
            // 统计最终岗位数量
            Locator cardsFinal = page.locator(JOB_LIST_XPATH + "//li[contains(@class, 'job-card-box')]");
            int loadedCount = cardsFinal.count();
            log.info("【{}】岗位已全部加载，总数:{}", keyword, loadedCount);
            progressCallback.accept("岗位加载完成：" + keyword, 0, loadedCount);

            // 2. 回到页面顶部
            if (!safePageEvaluate("window.scrollTo(0, 0);", keyword, url)) {
                return;
            }
            PlaywrightUtil.sleep(1);

            // 3. 逐个遍历所有岗位
            Locator cards = page.locator(JOB_LIST_XPATH + "//li[contains(@class, 'job-card-box')]");
            int count = cards.count();
            for (int i = 0; i < count; i++) {
                // 检查是否需要停止
                if (shouldStopCallback != null && Boolean.TRUE.equals(shouldStopCallback.get())) {
                    progressCallback.accept("用户取消投递", i, count);
                    return;
                }

                // 重新获取卡片，避免元素过期
                cards = page.locator(JOB_LIST_XPATH + "//li[contains(@class, 'job-card-box')]");
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
                            String address = jobInfo.optString("address", "");
                            String businessDistrict = firstNonBlank(
                                    jobInfo.optString("businessDistrict", ""),
                                    jobInfo.optString("districtName", ""),
                                    jobInfo.optString("areaDistrict", ""),
                                    extractShanghaiDistrict(address));
                            String exp = jobInfo.optString("experienceName", "");
                            String deg = jobInfo.optString("degreeName", "");
                            if (!city.isEmpty()) tags.add(city);
                            if (!businessDistrict.isEmpty()) tags.add(businessDistrict);
                            if (!address.isEmpty()) tags.add(address);
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

                if (!matchesDistrictFilter(job.getJobArea())) {
                    log.info("被过滤：岗位地区不在区级筛选范围 | 公司：{} | 岗位：{} | 地区：{} | 筛选区：{}",
                            job.getCompanyName(), job.getJobName(), job.getJobArea(), config.getDistrictFilter());
                    continue;
                }

                // 输出
                progressCallback.accept("正在投递：" + jobName, i + 1, count);
                boolean submitted = resumeSubmission(keyword, job);
                if (submitted) {
                    postCount++;
                }

                // 为避免点击下面的卡片触发页面刷新：在点击5个卡片之后，每次点击后适度下滑
                try {
                    if (i >= 5) {
                        safePageEvaluate("window.scrollBy(0, 140);", keyword, url);
                        PlaywrightUtil.sleep(1);
                    }
                } catch (Throwable ignore) {}
            }
            log.info("【{}】岗位已投递完毕！已投递岗位数量:{}", keyword, postCount);
        }
    }


    private boolean matchesDistrictFilter(String jobArea) {
        List<String> districts = getEffectiveDistrictFilters();
        if (districts.isEmpty()) {
            return true;
        }
        String area = jobArea == null ? "" : jobArea.trim();
        if (area.isEmpty()) {
            return true;
        }
        String normalizedArea = normalizeDistrictText(area);
        boolean containsDistrictLevelInfo = normalizedArea.contains("区") || normalizedArea.contains("县") || normalizedArea.contains("镇");
        if (!containsDistrictLevelInfo) {
            log.debug("岗位地区没有区县信息，暂不过滤 | 地区：{} | 筛选区：{}", area, districts);
            return true;
        }
        return districts.stream().anyMatch(normalizedArea::contains);
    }

    private List<String> getEffectiveDistrictFilters() {
        List<String> districts = config != null ? config.getDistrictFilter() : null;
        if (districts == null || districts.isEmpty()) {
            return Collections.emptyList();
        }
        return districts.stream()
                .filter(Objects::nonNull)
                .flatMap(value -> Arrays.stream(value.replace('，', ',').replace('、', ',').replace(';', ',').replace('；', ',').split(",")))
                .map(this::normalizeDistrictText)
                .filter(value -> !value.isEmpty() && !"不限".equals(value) && !"0".equals(value))
                .distinct()
                .collect(java.util.stream.Collectors.toList());
    }

    private String normalizeDistrictText(String value) {
        return value == null ? "" : value.replaceAll("[\\s　]+", "").trim();
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return "";
    }

    private String extractShanghaiDistrict(String address) {
        if (address == null || address.isBlank()) {
            return "";
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("(浦东新区|黄浦区|徐汇区|长宁区|静安区|普陀区|虹口区|杨浦区|闵行区|宝山区|嘉定区|金山区|松江区|青浦区|奉贤区|崇明区)")
                .matcher(address);
        return matcher.find() ? matcher.group(1) : "";
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

    private boolean waitForJobListOrSecurityCheck(String keyword, String searchUrl) {
        long startTime = System.currentTimeMillis();
        long timeoutMs = 5 * 60 * 1000L;
        boolean notified = false;
        int homeRetryCount = 0;
        long lastHomeRetryTime = 0L;
        int searchRetryCount = 0;
        long lastSearchRetryTime = 0L;
        long lastWaitingMessageTime = 0L;

        while (System.currentTimeMillis() - startTime < timeoutMs) {
            if (shouldStopCallback != null && Boolean.TRUE.equals(shouldStopCallback.get())) {
                progressCallback.accept("用户取消投递", 0, 0);
                return false;
            }

            String currentUrl = page.url();
            if (isBossSecurityPage(currentUrl)) {
                if (!notified) {
                    String message = "Boss触发安全验证，请在浏览器中手动完成验证，完成后系统会自动继续...";
                    progressCallback.accept(message, 0, 0);
                    log.warn("{} 当前URL：{}", message, currentUrl);
                    notified = true;
                }
                PlaywrightUtil.sleep(2);
                continue;
            }

            if (isBossHomePage(currentUrl)) {
                long now = System.currentTimeMillis();
                if (homeRetryCount == 0 || now - lastHomeRetryTime >= 10_000L) {
                    homeRetryCount++;
                    lastHomeRetryTime = now;
                    progressCallback.accept("Boss安全验证后回到首页，正在第" + homeRetryCount + "次重新打开搜索结果页...", 0, 0);
                    log.info("Boss安全验证后回到首页，重新导航搜索页 | retry={} | keyword={} | searchUrl={}", homeRetryCount, keyword, searchUrl);
                    try {
                        page.navigate(searchUrl, new Page.NavigateOptions()
                                .setWaitUntil(com.microsoft.playwright.options.WaitUntilState.DOMCONTENTLOADED)
                                .setTimeout(15_000));
                    } catch (Exception e) {
                        log.warn("Boss重新打开搜索结果页失败，稍后重试：{}", e.getMessage());
                    }
                }
                PlaywrightUtil.sleep(2);
                continue;
            }

            if (!isBossSearchPage(currentUrl)) {
                long now = System.currentTimeMillis();
                if (now - lastSearchRetryTime >= 10_000L) {
                    searchRetryCount++;
                    lastSearchRetryTime = now;
                    progressCallback.accept("Boss当前不在搜索结果页，正在第" + searchRetryCount + "次重新打开搜索结果页...", 0, 0);
                    log.info("Boss当前不在搜索结果页，重新导航搜索页 | retry={} | keyword={} | currentUrl={} | searchUrl={}", searchRetryCount, keyword, currentUrl, searchUrl);
                    navigateToSearchUrl(searchUrl);
                }
                PlaywrightUtil.sleep(2);
                continue;
            }

            try {
                Locator jobList = page.locator(JOB_LIST_XPATH).first();
                jobList.waitFor(new Locator.WaitForOptions().setTimeout(1000));
                if (jobList.count() > 0) {
                    if (notified) {
                        progressCallback.accept("Boss安全验证已通过，继续投递关键词：" + keyword, 0, 0);
                    }
                    return true;
                }
            } catch (PlaywrightException e) {
                if (isNavigationChangingError(e)) {
                    log.debug("等待Boss岗位列表时页面正在跳转：{}", e.getMessage());
                } else {
                    log.debug("等待Boss岗位列表未就绪：{}", e.getMessage());
                }
            } catch (Exception e) {
                log.debug("等待Boss岗位列表时页面状态变化：{}", e.getMessage());
            }

            long now = System.currentTimeMillis();
            if (now - lastWaitingMessageTime >= 15_000L) {
                lastWaitingMessageTime = now;
                progressCallback.accept("Boss搜索结果页尚未加载岗位列表，正在刷新重试...", 0, 0);
                log.info("Boss搜索结果页尚未加载岗位列表，刷新重试 | keyword={} | url={}", keyword, page.url());
                navigateToSearchUrl(searchUrl);
            }

            PlaywrightUtil.sleep(1);
        }

        progressCallback.accept("等待Boss岗位列表超时，可能仍停留在安全验证页，请完成验证后重新开始投递。", 0, 0);
        log.warn("等待Boss岗位列表超时 | keyword={} | url={}", keyword, page.url());
        return false;
    }

    private boolean safePageEvaluate(String expression, String keyword, String searchUrl) {
        try {
            page.evaluate(expression);
            return true;
        } catch (PlaywrightException e) {
            return handleNavigationChangingDuringSearch(e, keyword, searchUrl);
        }
    }

    private boolean handleNavigationChangingDuringSearch(PlaywrightException e, String keyword, String searchUrl) {
        if (!isNavigationChangingError(e)) {
            return false;
        }
        log.warn("Boss页面正在跳转，暂停当前页面操作并重新等待搜索结果页 | keyword={} | url={} | error={}", keyword, page.url(), e.getMessage());
        progressCallback.accept("Boss页面正在跳转，等待搜索结果页重新稳定...", 0, 0);
        return waitForJobListOrSecurityCheck(keyword, searchUrl);
    }

    private boolean isBossSecurityPage(String url) {
        return url != null && (url.contains(SECURITY_PAGE_FRAGMENT) || url.contains(SLIDER_PAGE_FRAGMENT) || url.contains(SECURITY_CHECK_PARAM));
    }

    private boolean isBossHomePage(String url) {
        if (url == null) return false;
        String normalized = url.endsWith("/") ? url : url + "/";
        return "https://www.zhipin.com/".equals(normalized);
    }

    private boolean isBossSearchPage(String url) {
        return url != null && url.contains("/web/geek/jobs");
    }

    private void navigateToSearchUrl(String searchUrl) {
        try {
            page.navigate(searchUrl, new Page.NavigateOptions()
                    .setWaitUntil(com.microsoft.playwright.options.WaitUntilState.DOMCONTENTLOADED)
                    .setTimeout(15_000));
        } catch (Exception e) {
            log.warn("Boss重新打开搜索结果页失败，稍后重试：{}", e.getMessage());
        }
    }

    private boolean isNavigationChangingError(Throwable e) {
        String message = e == null ? "" : String.valueOf(e.getMessage());
        return message.contains("Execution context was destroyed") ||
                message.contains("navigation") ||
                message.contains("Cannot find context with specified id");
    }

    /**
     * 备注：目前Boss无法通过新标签页打开立即沟通按钮，所以只能点击更多详情，然后从更多详情里打开聊天按钮
     */
    private boolean resumeSubmission(String keyword, Job job) {
        Page detailPage = null;
        String detailUrl = null;
        boolean sendSuccess = false;

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

        try {
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
            detailUrl = "https://www.zhipin.com" + href;
            // 2. 在新窗口打开详情页
            detailPage = page.context().newPage();
            detailPage.navigate(detailUrl, new Page.NavigateOptions()
                    .setWaitUntil(com.microsoft.playwright.options.WaitUntilState.DOMCONTENTLOADED)
                    .setTimeout(30_000));
            PlaywrightUtil.sleep(1);

            // 3. 查找"立即沟通"按钮。每轮重新定位，避免页面刷新/跳转后旧 frame 上的 locator 失效。
            boolean foundChatBtn = false;
            for (int i = 0; i < 5; i++) {
                if (shouldStopCallback != null && Boolean.TRUE.equals(shouldStopCallback.get())) {
                    log.info("停止指令已触发，结束查找聊天按钮 | 公司：{} | 岗位：{}", job.getCompanyName(), job.getJobName());
                    return false;
                }
                Locator chatBtn = detailPage.locator("a.btn-startchat, a.op-btn-chat");
                if (chatBtn.count() > 0 && chatBtn.first().isVisible() && chatBtn.first().textContent().contains("立即沟通")) {
                    foundChatBtn = true;
                    break;
                }
                PlaywrightUtil.sleep(1);
            }
            if (!foundChatBtn) {
                log.warn("未找到立即沟通按钮，跳过岗位: {}", job.getJobName());
                return false;
            }

            detailPage.locator("a.btn-startchat, a.op-btn-chat").first().click();
            PlaywrightUtil.sleep(1);

            // 4. 等待聊天输入框。点击沟通后页面可能跳转到聊天页，需要重新创建 locator。
            boolean inputReady = false;
            for (int i = 0; i < 10; i++) {
                if (shouldStopCallback != null && Boolean.TRUE.equals(shouldStopCallback.get())) {
                    log.info("停止指令已触发，结束等待聊天输入框 | 公司：{} | 岗位：{}", job.getCompanyName(), job.getJobName());
                    return false;
                }
                Locator inputLocator = detailPage.locator("div#chat-input.chat-input[contenteditable='true'], textarea.input-area");
                if (inputLocator.count() > 0 && inputLocator.first().isVisible()) {
                    inputReady = true;
                    break;
                }
                PlaywrightUtil.sleep(1);
            }
            if (!inputReady) {
                log.warn("聊天输入框未出现，跳过: {}", job.getJobName());
                return false;
            }

            // 5. 根据目标JD优化Boss简历与打招呼语
            ResumeOptimizationResponse optimizedResume = null;
            String aiMessage = null;
            if (config.getEnableAI()) {
                String jd = job.getJobInfo();
                if (isValidString(bossResumeText)) {
                    optimizedResume = optimizeResumeForJob(keyword, job);
                    if (optimizedResume != null && isValidString(optimizedResume.getBossGreeting())) {
                        aiMessage = optimizedResume.getBossGreeting();
                    }
                }
                if (!isValidString(aiMessage) && jd != null && !jd.isEmpty()) {
                    aiMessage = generateAiMessage(keyword, job.getJobName(), jd);
                }
            }
            String message = isValidString(aiMessage) ? aiMessage : config.getSayHi();

            // 6. 发送打招呼语
            sendSuccess = sendChatText(detailPage, message);
            if (!sendSuccess) {
                log.warn("未找到发送按钮，自动跳过！岗位：{}", job.getJobName());
            }

            // 7. 发送按当前JD优化后的简历摘要（可选，失败不影响本次投递结果）
            boolean optimizedResumeSent = false;
            String optimizedResumeMessage = buildOptimizedResumeMessage(optimizedResume);
            if (sendSuccess && isValidString(optimizedResumeMessage)) {
                try {
                    optimizedResumeSent = sendChatText(detailPage, optimizedResumeMessage);
                } catch (Exception e) {
                    log.warn("发送JD优化简历摘要失败，继续后续投递 | 公司：{} | 岗位：{} | 错误：{}", job.getCompanyName(), job.getJobName(), e.getMessage());
                }
            }
            try {
                detailPage.locator("i.icon-close").first().click();
            } catch (Exception e) {
                log.debug("发送文本小窗口关闭失败：{}", e.getMessage());
            }

            // 8. 发送图片简历（可选）
            boolean imgResume = false;
            if (Boolean.TRUE.equals(config.getSendImgResume())) {
                imgResume = sendImageResume(detailPage);
            }

            log.info("投递完成 | 公司：{} | 岗位：{} | 薪资：{} | 招呼语：{} | JD优化简历：{} | 图片简历：{}",
                    job.getCompanyName(), job.getJobName(), job.getSalary(), message, optimizedResumeSent ? "已发送" : "未发送", imgResume ? "已发送" : "未发送");

            return sendSuccess;
        } catch (Exception e) {
            log.warn("Boss单个岗位投递异常，已跳过继续执行 | 公司：{} | 岗位：{} | 错误：{}", job.getCompanyName(), job.getJobName(), e.getMessage());
            return false;
        } finally {
            try {
                if (detailPage != null && !detailPage.isClosed()) {
                    detailPage.close();
                }
            } catch (Exception ignore) {
            }
            PlaywrightUtil.sleep(1);

            String encryptId = extractEncryptId(detailUrl);
            String encryptUserId = encryptId != null ? encryptIdToUserId.get(encryptId) : null;
            if (sendSuccess) {
                try {
                    if (encryptId != null && encryptUserId != null) {
                        bossService.updateDeliveryStatus(encryptId, encryptUserId, "已投递");
                        log.info("投递成功 | 公司：{} | 岗位：{} | encryptId：{} | encryptUserId：{}", job.getCompanyName(), job.getJobName(), encryptId, encryptUserId);
                    } else {
                        log.debug("未能找到 encryptId/encryptUserId 用于更新投递状态，detailUrl: {}", detailUrl);
                    }
                } catch (Exception e) {
                    log.warn("更新投递状态为已投递失败：{}", e.getMessage());
                }
                resultList.add(job);
            } else if (encryptId != null && encryptUserId != null) {
                try {
                    bossService.updateDeliveryStatus(encryptId, encryptUserId, "投递失败");
                    log.warn("投递失败 | 公司：{} | 岗位：{} | encryptId：{} | encryptUserId：{}", job.getCompanyName(), job.getJobName(), encryptId, encryptUserId);
                } catch (Exception e) {
                    log.warn("更新投递状态为投递失败异常：{}", e.getMessage());
                }
            }
        }
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
                if (imgContainer.count() > 0) {
                    boolean chooserHandled = false;
                    try {
                        com.microsoft.playwright.FileChooser chooser = page.waitForFileChooser(() -> {
                            imgContainer.first().click();
                        });
                        chooser.setFiles(imagePath);
                        chooserHandled = true;
                        log.info("通过 FileChooser 直接提交图片文件，避免系统窗口阻塞");
                    } catch (com.microsoft.playwright.PlaywrightException ignore) {
                        // 未弹出系统文件选择器，继续常规流程
                    }
                    if (!chooserHandled) {
                        PlaywrightUtil.sleep(1);
                        imageInput = imgContainer.locator("input[type='file'][accept*='image']").first();
                    }
                }
            }
            imageInput.waitFor(new Locator.WaitForOptions().setTimeout(10_000));

            // 上传图片
            imageInput.setInputFiles(imagePath);
            PlaywrightUtil.sleep(1);
            return true;
        } catch (Throwable e) {
            log.error("发送图片简历失败：{}", e.getMessage(), e);
            return false;
        }
    }

    private boolean sendChatText(Page detailPage, String message) {
        if (!isValidString(message)) {
            return false;
        }
        Locator input = detailPage.locator("div#chat-input.chat-input[contenteditable='true'], textarea.input-area").first();
        input.click();
        Object tagObj = input.evaluate("el => el.tagName.toLowerCase()");
        if (tagObj instanceof String && ((String) tagObj).equals("textarea")) {
            input.fill(message);
        } else {
            input.evaluate("(el, msg) => { el.innerText = msg; el.dispatchEvent(new Event('input')); }", message);
        }

        Locator sendText = detailPage.locator("div.send-message, button[type='send'].btn-send, button.btn-send");
        if (sendText.count() == 0) {
            return false;
        }
        sendText.first().click();
        PlaywrightUtil.sleep(1);
        return true;
    }

    private ResumeOptimizationResponse optimizeResumeForJob(String keyword, Job job) {
        try {
            String jd = job.getJobInfo();
            if (!isValidString(bossResumeText)) {
                return null;
            }
            String safeJd = isValidString(jd) ? jd : "";
            String cacheKey = job.getCompanyName() + "|" + job.getJobName() + "|" + Integer.toHexString(safeJd.hashCode());
            ResumeOptimizationResponse cached = resumeOptimizationCache.get(cacheKey);
            if (cached != null) {
                return cached;
            }
            if (progressCallback != null) {
                progressCallback.accept("正在根据JD优化简历：" + job.getJobName(), null, null);
            }
            ResumeOptimizationRequest request = new ResumeOptimizationRequest();
            request.setResumeText(bossResumeText);
            request.setUseBossResume(false);
            request.setJobDescription(safeJd);
            request.setTargetRole(isValidString(job.getJobName()) ? job.getJobName() : keyword);
            request.setPlatform("Boss直聘");
            request.setLanguage("中文");
            request.setExtraRequirements(isValidString(safeJd)
                    ? "用于Boss直聘自动投递。请输出适合当前岗位JD的精简中文简历版本和不超过60字的boss_greeting；不得编造经历、数字、学历或证书。"
                    : "用于Boss直聘自动投递。当前岗位JD为空，请执行通用简历优化，输出精简中文简历版本和不超过60字的boss_greeting；不得编造经历、数字、学历或证书。");
            ResumeOptimizationResponse response = resumeOptimizationService.optimize(request);
            resumeOptimizationCache.put(cacheKey, response);
            return response;
        } catch (Exception e) {
            log.warn("根据JD优化简历失败，回退原有打招呼逻辑 | 公司：{} | 岗位：{} | 错误：{}", job.getCompanyName(), job.getJobName(), e.getMessage());
            return null;
        }
    }

    private String buildOptimizedResumeMessage(ResumeOptimizationResponse response) {
        if (response == null || !isValidString(response.getFinalResume())) {
            return null;
        }
        String finalResume = cleanOptimizedResumeText(response.getFinalResume());
        int maxLength = 1_800;
        if (finalResume.length() > maxLength) {
            finalResume = finalResume.substring(0, maxLength) + "...";
        }
        return finalResume;
    }

    private String cleanOptimizedResumeText(String resumeText) {
        if (!isValidString(resumeText)) {
            return "";
        }
        return resumeText.trim()
                .replaceFirst("^(?s)\\s*这是我根据[^\\n：:]{0,80}[：:]\\s*", "")
                .replaceFirst("^(?s)\\s*以下是[^\\n：:]{0,80}[：:]\\s*", "")
                .replaceFirst("^(?s)\\s*根据[^\\n：:]{0,80}(?:整理|优化|生成)[^\\n：:]{0,80}[：:]\\s*", "")
                .replaceFirst("^(?s)\\s*(?:匹配)?简历摘要[：:]\\s*", "")
                .trim();
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
