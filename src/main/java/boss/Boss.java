package boss;


import ai.AiFilter;
import ai.UnifiedAiService;
import com.microsoft.playwright.ElementHandle;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.WaitForSelectorState;
import lombok.SneakyThrows;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import utils.ConfigFileUtil;
import utils.Job;
import utils.JobUtils;
import utils.PlaywrightUtil;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

import static boss.BossElementLocators.*;
import static utils.Bot.sendMessageByTime;
import static utils.JobUtils.formatDuration;

/**
 * @author loks666
 *         项目链接: <a href=
 *         "https://github.com/loks666/get_jobs">https://github.com/loks666/get_jobs</a>
 *         Boss直聘自动投递
 */
public class Boss {
    private static final Logger log = LoggerFactory.getLogger(Boss.class);
    static String homeUrl = "https://www.zhipin.com";
    static String baseUrl = "https://www.zhipin.com/web/geek/job?";
    static Set<String> blackCompanies;
    static Set<String> blackRecruiters;
    static Set<String> blackJobs;
    static List<Job> resultList = new ArrayList<>();

    static String dataPath = ConfigFileUtil.getDataFilePath();
    static String cookiePath = ConfigFileUtil.getCookieFilePath();
    static Date startDate;
    public static BossConfig config = BossConfig.getInstance();
    // 默认推荐岗位集合
    static List<Job> recommendJobs = new ArrayList<>();

    static Set<String> jobHrefSet = new HashSet<>();

    static {
        try {
            initializeDataFiles();
        } catch (IOException e) {
            BossLogger.logSystemError("文件初始化", e);
        }
    }

    /**
     * 初始化数据文件
     */
    private static void initializeDataFiles() throws IOException {
        // 使用统一的文件初始化方法
        ConfigFileUtil.initializeAllFiles();
        BossLogger.logFileOperation("初始化所有文件", "data目录", true);
    }

    public static void main(String[] args) {
        long startTime = System.currentTimeMillis();
        BossLogger.logApplicationStart();

        // 启动配置界面
        if (args == null || args.length == 0) {
            BossConfigApplication.launch();
            return;
        }

        try {
            // 配置界面点击确认后的逻辑
            loadData(dataPath);
            PlaywrightUtil.init();
            startDate = new Date();
            login();
            config.getCityCode().forEach(Boss::postJobByCityByPlaywright);
            // if (config.getH5Jobs()) {
            // h5Config.getCityCode().forEach(Boss::postH5JobByCityByPlaywright);
            // }
            if (recommendJobs.isEmpty() && config.getRecommendJobs()) {
                getRecommendJobs();
                // 处理推荐职位
                processRecommendJobs();
            }

            printResult();
        } catch (Exception e) {
            BossLogger.logSystemError("应用执行", e);
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            BossLogger.logApplicationComplete(resultList.size(), duration);
        }
    }

    private static void printResult() {
        if (resultList.isEmpty()) {
            BossLogger.logStatistics("投递结果", 0, "未发起新的聊天");
        } else {
            String companies = resultList.stream()
                    .map(Job::getCompanyName)
                    .collect(Collectors.joining(", "));
            BossLogger.logStatistics("投递结果", resultList.size(), "新发起聊天的公司: " + companies);
        }

        String message = String.format("Boss投递完成，共发起%d个聊天，用时%s",
                resultList.size(), formatDuration(startDate, new Date()));
        sendMessageByTime(message);
        saveData(dataPath);
        resultList.clear();
        PlaywrightUtil.close();
    }

    /**
     * 推荐岗位
     */
    private static void getRecommendJobs() {
        Page page = PlaywrightUtil.getPageObject();
        PlaywrightUtil.loadCookies(cookiePath);
        page.navigate("https://www.zhipin.com/web/geek/jobs");

        // 等待页面加载
        page.waitForLoadState();

        try {
            // 等待元素出现，最多等待10秒
            page.waitForSelector("a.expect-item", new Page.WaitForSelectorOptions().setTimeout(10000));

            // 获取a标签且class是expect-item的元素
            ElementHandle activeElement = page.querySelector("a.expect-item");

            if (activeElement != null) {
                log.debug("找到推荐岗位入口，准备点击");
                // 点击该元素
                activeElement.click();
                // 点击后等待页面响应
                page.waitForLoadState();

                if (isJobsPresent()) {
                    // 滚动加载推荐岗位
                    int totalJobs = loadJobsWithScroll(page, "推荐岗位");
                    BossLogger.logStatistics("推荐岗位加载", totalJobs);

                    // 解析推荐岗位
                    parseRecommendJobs(page);
                    BossLogger.logStatistics("推荐岗位解析", recommendJobs.size());
                }
            } else {
                BossLogger.logBusinessError("推荐岗位获取", "未找到推荐岗位入口元素");
            }
        } catch (Exception e) {
            BossLogger.logSystemError("推荐岗位获取", e);
        }
    }

    /**
     * 滚动加载岗位数据
     */
    private static int loadJobsWithScroll(Page page, String jobType) {
        int previousJobCount = 0;
        int currentJobCount = 0;
        int unchangedCount = 0;

        while (unchangedCount < 2) {
            // 获取所有岗位卡片
            List<ElementHandle> jobCards = page.querySelectorAll(JOB_LIST_SELECTOR);
            currentJobCount = jobCards.size();

            BossLogger.logPageLoad(currentJobCount, "滚动加载中");

            // 判断是否有新增岗位
            if (currentJobCount > previousJobCount) {
                previousJobCount = currentJobCount;
                unchangedCount = 0;

                // 滚动到页面底部加载更多
                safeEvaluateJavaScript(page, "window.scrollTo(0, document.body.scrollHeight)");
                log.debug("{}下拉页面加载更多...", jobType);

                // 等待新内容加载
                page.waitForTimeout(2000);
            } else {
                unchangedCount++;
                if (unchangedCount < 2) {
                    log.debug("{}下拉后岗位数量未增加，再次尝试...", jobType);
                    // 再次尝试滚动
                    safeEvaluateJavaScript(page, "window.scrollTo(0, document.body.scrollHeight)");
                    page.waitForTimeout(2000);
                }
            }
        }

        log.debug("{}加载完成，总计岗位数: {}", jobType, currentJobCount);
        return currentJobCount;
    }

    /**
     * 解析推荐岗位
     */
    private static void parseRecommendJobs(Page page) {
        // 使用page.locator方法获取所有匹配的元素
        Locator jobLocators = BossElementFinder.getPlaywrightLocator(page, JOB_CARD_BOX);
        // 获取元素总数
        int count = jobLocators.count();

        // 遍历所有找到的job卡片
        for (int i = 0; i < count; i++) {
            try {
                Locator jobCard = jobLocators.nth(i);
                String jobName = jobCard.locator(JOB_NAME).textContent();
                if (blackJobs.stream().anyMatch(jobName::contains)) {
                    // 排除黑名单岗位
                    continue;
                }
                String companyName = jobCard.locator(COMPANY_NAME).textContent();
                if (blackCompanies.stream().anyMatch(companyName::contains)) {
                    // 排除黑名单公司
                    continue;
                }

                String href = jobCard.locator(JOB_NAME).getAttribute("href");
                if (jobHrefSet.contains(href)) {
                    log.debug("推荐岗位重复，跳过：{} - {}", companyName, jobName);
                    continue;
                } else {
                    jobHrefSet.add(href);
                }

                Job job = new Job();
                job.setHref(href);
                job.setCompanyName(companyName);
                job.setJobName(jobName);
                job.setJobArea(jobCard.locator(JOB_AREA).textContent());
                // 获取标签列表
                Locator tagElements = jobCard.locator(TAG_LIST);
                int tagCount = tagElements.count();
                StringBuilder tag = new StringBuilder();
                for (int j = 0; j < tagCount; j++) {
                    tag.append(tagElements.nth(j).textContent()).append("·");
                }
                if (tag.length() > 0) {
                    job.setCompanyTag(tag.substring(0, tag.length() - 1));
                } else {
                    job.setCompanyTag("");
                }

                recommendJobs.add(job);
            } catch (Exception e) {
                log.debug("处理推荐岗位卡片失败: {}", e.getMessage());
            }
        }
    }

    private static void postJobByCityByPlaywright(String cityCode) {
        String searchUrl = getSearchUrl(cityCode);
        for (String keyword : config.getKeywords()) {
            // 使用 URLEncoder 对关键词进行编码
            String encodedKeyword = URLEncoder.encode(keyword, StandardCharsets.UTF_8);
            String url = searchUrl + "&query=" + encodedKeyword;

            BossLogger.setSearchContext(keyword, cityCode);
            BossLogger.logSearchStart(keyword, url);

            Page page = PlaywrightUtil.getPageObject();
            PlaywrightUtil.loadCookies(cookiePath);
            page.navigate(url);

            if (isJobsPresent()) {
                try {
                    // 滚动加载更多岗位
                    int totalJobs = loadJobsWithScroll(page, "搜索岗位");
                    BossLogger.logStatistics("搜索岗位加载", totalJobs, "关键词: " + keyword);
                } catch (Exception e) {
                    BossLogger.logSystemError("滚动加载岗位数据", e);
                    break;
                }
            }

            resumeSubmission(keyword);
            BossLogger.clearContext();
        }
    }

    private static boolean isJobsPresent() {
        try {
            // 判断页面是否存在岗位的元素
            PlaywrightUtil.waitForElement(JOB_LIST_CONTAINER);
            return true;
        } catch (Exception e) {
            BossLogger.logBusinessError("岗位页面检查", "页面无岗位元素");
            return false;
        }
    }

    private static String getSearchUrl(String cityCode) {
        return baseUrl + JobUtils.appendParam("city", cityCode) +
                JobUtils.appendParam("jobType", config.getJobType()) +
                JobUtils.appendParam("salary", config.getSalary()) +
                JobUtils.appendListParam("experience", config.getExperience()) +
                JobUtils.appendListParam("degree", config.getDegree()) +
                JobUtils.appendListParam("scale", config.getScale()) +
                JobUtils.appendListParam("industry", config.getIndustry()) +
                JobUtils.appendListParam("stage", config.getStage());
    }

    private static void saveData(String path) {
        try {
            updateListData();
            Map<String, Set<String>> data = new HashMap<>();
            data.put("blackCompanies", blackCompanies);
            data.put("blackRecruiters", blackRecruiters);
            data.put("blackJobs", blackJobs);
            String json = customJsonFormat(data);
            Files.write(Paths.get(path), json.getBytes());
            BossLogger.logFileOperation("保存黑名单数据", path, true);
        } catch (IOException e) {
            BossLogger.logFileOperation("保存黑名单数据", path, false);
            BossLogger.logSystemError("数据保存", e);
        }
    }

    private static void updateListData() {
        Page page = PlaywrightUtil.getPageObject();
        page.navigate("https://www.zhipin.com/web/geek/chat");
        PlaywrightUtil.sleep(3);

        boolean shouldBreak = false;
        int processedItems = 0;
        int newBlackCompanies = 0;

        while (!shouldBreak) {
            try {
                Locator bottomElement = page.locator(FINISHED_TEXT);
                if (bottomElement.isVisible() && "没有更多了".equals(bottomElement.textContent())) {
                    shouldBreak = true;
                }
            } catch (Exception ignore) {
            }

            Locator items = page.locator(CHAT_LIST_ITEM);
            int itemCount = items.count();

            for (int i = 0; i < itemCount; i++) {
                processedItems++;
                try {
                    Locator companyElements = page.locator(COMPANY_NAME_IN_CHAT);
                    Locator messageElements = page.locator(LAST_MESSAGE);

                    String companyName = null;
                    String message = null;
                    int retryCount = 0;

                    while (retryCount < 2) {
                        try {
                            if (i < companyElements.count() && i < messageElements.count()) {
                                companyName = companyElements.nth(i).textContent();
                                message = messageElements.nth(i).textContent();
                                break; // 成功获取文本，跳出循环
                            } else {
                                log.debug("聊天记录元素索引超出范围");
                                break;
                            }
                        } catch (Exception e) {
                            retryCount++;
                            if (retryCount >= 2) {
                                log.debug("获取聊天记录文本失败，跳过");
                                break;
                            }
                            log.debug("页面元素已变更，重试获取聊天记录文本...");
                            // 等待短暂时间后重试
                            PlaywrightUtil.sleep(1);
                        }
                    }

                    // 只有在成功获取文本的情况下才继续处理
                    if (companyName != null && message != null) {
                        boolean match = message.contains("不") || message.contains("感谢") || message.contains("但")
                                || message.contains("遗憾") || message.contains("需要本") || message.contains("对不");
                        boolean nomatch = message.contains("不是") || message.contains("不生");
                        if (match && !nomatch) {
                            if (!blackCompanies.stream().anyMatch(companyName::contains)) {
                                companyName = companyName.replaceAll("\\.{3}", "");
                                if (companyName.matches(".*(\\p{IsHan}{2,}|[a-zA-Z]{4,}).*")) {
                                    blackCompanies.add(companyName);
                                    newBlackCompanies++;
                                    log.debug("新增黑名单公司：{} - 拒绝信息：{}", companyName, message);
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    log.debug("处理聊天记录异常：{}", e.getMessage());
                }
            }

            try {
                // 尝试找到加载更多的元素
                Locator loadMoreElement = page.locator(SCROLL_LOAD_MORE);
                if (loadMoreElement.isVisible()) {
                    // 滚动到加载更多元素
                    loadMoreElement.scrollIntoViewIfNeeded();
                    PlaywrightUtil.sleep(1);
                } else {
                    // 如果找不到特定元素，尝试滚动到页面底部
                    safeEvaluateJavaScript(page, "window.scrollTo(0, document.body.scrollHeight)");
                    PlaywrightUtil.sleep(1);
                }
            } catch (Exception e) {
                log.debug("聊天记录滚动加载完成");
                break;
            }
        }

        BossLogger.logStatistics("聊天记录分析", processedItems,
                String.format("新增黑名单公司：%d，总黑名单公司：%d", newBlackCompanies, blackCompanies.size()));
    }

    private static String customJsonFormat(Map<String, Set<String>> data) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        for (Map.Entry<String, Set<String>> entry : data.entrySet()) {
            sb.append("    \"").append(entry.getKey()).append("\": [\n");
            sb.append(entry.getValue().stream().map(s -> "        \"" + s + "\"").collect(Collectors.joining(",\n")));

            sb.append("\n    ],\n");
        }
        sb.delete(sb.length() - 2, sb.length());
        sb.append("\n}");
        return sb.toString();
    }

    private static void loadData(String path) {
        try {
            String json = new String(Files.readAllBytes(Paths.get(path)));
            parseJson(json);
            BossLogger.logFileOperation("加载黑名单数据", path, true);
            BossLogger.logStatistics("黑名单数据", blackCompanies.size() + blackRecruiters.size() + blackJobs.size(),
                    String.format("公司:%d, 招聘者:%d, 岗位:%d", blackCompanies.size(), blackRecruiters.size(),
                            blackJobs.size()));
        } catch (IOException e) {
            BossLogger.logFileOperation("加载黑名单数据", path, false);
            BossLogger.logSystemError("数据加载", e);
        }
    }

    private static void parseJson(String json) {
        try {
            JSONObject jsonObject = new JSONObject(json);
            blackCompanies = jsonObject.getJSONArray("blackCompanies").toList().stream().map(Object::toString)
                    .collect(Collectors.toSet());
            blackRecruiters = jsonObject.getJSONArray("blackRecruiters").toList().stream().map(Object::toString)
                    .collect(Collectors.toSet());
            blackJobs = jsonObject.getJSONArray("blackJobs").toList().stream().map(Object::toString)
                    .collect(Collectors.toSet());
        } catch (Exception e) {
            BossLogger.logSystemError("JSON解析", e);
            // 初始化空集合避免空指针
            blackCompanies = new HashSet<>();
            blackRecruiters = new HashSet<>();
            blackJobs = new HashSet<>();
        }
    }

    @SneakyThrows
    private static Integer resumeSubmission(String keyword) {
        // 查找所有job卡片元素
        Page page = PlaywrightUtil.getPageObject();
        // 使用page.locator方法获取所有匹配的元素
        Locator jobLocators = BossElementFinder.getPlaywrightLocator(page, JOB_CARD_BOX);
        // 获取元素总数
        int count = jobLocators.count();

        List<Job> jobs = new ArrayList<>();
        int filteredCount = 0;

        // 遍历所有找到的job卡片
        for (int i = 0; i < count; i++) {
            try {
                Locator jobCard = jobLocators.nth(i);
                String jobName = jobCard.locator(JOB_NAME).textContent();
                String companyName = jobCard.locator(COMPANY_NAME).textContent();
                String jobArea = jobCard.locator(JOB_AREA).textContent();

                Job job = new Job();
                job.setHref(jobCard.locator(JOB_NAME).getAttribute("href"));
                job.setCompanyName(companyName);
                job.setJobName(jobName);
                job.setJobArea(jobArea);
                // 获取标签列表
                Locator tagElements = jobCard.locator(TAG_LIST);
                int tagCount = tagElements.count();
                StringBuilder tag = new StringBuilder();
                for (int j = 0; j < tagCount; j++) {
                    tag.append(tagElements.nth(j).textContent()).append("·");
                }
                if (tag.length() > 0) {
                    job.setCompanyTag(tag.substring(0, tag.length() - 1));
                } else {
                    job.setCompanyTag("");
                }

                // 各种过滤逻辑
                if (blackJobs.stream().anyMatch(jobName::contains) || !isTargetJob(keyword, jobName)) {
                    BossLogger.logJobFiltered("黑名单岗位或不匹配", companyName, jobName);
                    filteredCount++;
                    continue;
                }

                if (blackCompanies.stream().anyMatch(companyName::contains)) {
                    BossLogger.logJobFiltered("黑名单公司", companyName, jobName);
                    filteredCount++;
                    continue;
                }

                if (config.getKeyFilter()) {
                    if (!jobName.toLowerCase().contains(keyword.toLowerCase())) {
                        BossLogger.logJobFiltered("关键词不匹配", companyName, jobName, keyword);
                        filteredCount++;
                        continue;
                    }
                }

                if (config.getCheckStateOwned()) {
                    try {
                        UnifiedAiService aiService = UnifiedAiService.getInstance();
                        UnifiedAiService.StateOwnedResponse stateOwnedResponse = aiService.checkStateOwnedEnterprise(
                                companyName, UnifiedAiService.AiPlatform.DEEPSEEK);

                        if (stateOwnedResponse.isSuccess() && stateOwnedResponse.getData()) {
                            log.debug("公司 {} 是国有企业", companyName);
                            // 继续处理国企相关业务逻辑
                        } else {
                            log.debug("公司 {} 不是国有企业，跳过", companyName);
                            BossLogger.logJobFiltered("非国有企业", companyName, jobName);
                            filteredCount++;
                            continue;
                        }
                    } catch (Exception e) {
                        BossLogger.logSystemError("国企检查", e);
                        // 发生异常时的处理策略：可以选择跳过或继续处理
                        filteredCount++;
                        continue;
                    }
                }

                jobs.add(job);
            } catch (Exception e) {
                log.debug("处理岗位卡片失败: {}", e.getMessage());
                filteredCount++;
            }
        }

        BossLogger.logStatistics("岗位筛选", jobs.size(),
                String.format("总岗位:%d, 过滤:%d, 待处理:%d", count, filteredCount, jobs.size()));

        // 处理每个职位详情
        int result = processJobList(jobs, keyword);
        if (result < 0) {
            return result;
        }

        return resultList.size();
    }

    /**
     * 处理推荐职位列表
     *
     * @return 处理结果，负数表示出错
     */
    @SneakyThrows
    private static int processRecommendJobs() {
        for (Job job : recommendJobs) {
            // 使用Playwright在新标签页中打开链接
            Page jobPage = PlaywrightUtil.getPageObject().context().newPage();
            jobPage.navigate(homeUrl + job.getHref());

            try {
                // 等待聊天按钮出现
                Locator chatButton = jobPage.locator(CHAT_BUTTON);
                if (!chatButton.nth(0).isVisible(new Locator.IsVisibleOptions().setTimeout(5000))) {
                    Locator errorElement = jobPage.locator(ERROR_CONTENT);
                    if (errorElement.isVisible() && errorElement.textContent().contains("异常访问")) {
                        jobPage.close();
                        return -2;
                    }
                }
            } catch (Exception e) {
                if (config.getDebugger()) {
                    e.printStackTrace();
                }
                log.error("无法加载岗位详情页: {}", e.getMessage());
                jobPage.close();
                continue;
            }

            // 过滤不活跃HR
            if (isDeadHR(jobPage)) {
                jobPage.close();
                PlaywrightUtil.sleep(1);
                continue;
            }

            try {
                // 获取职位描述标签
                Locator tagElements = jobPage.locator(JOB_KEYWORD_LIST);
                int tagCount = tagElements.count();
                StringBuilder tag = new StringBuilder();
                for (int j = 0; j < tagCount; j++) {
                    tag.append(tagElements.nth(j).textContent()).append("·");
                }
                job.setJobKeywordTag(tag.toString());
            } catch (Exception e) {
                log.info("获取职位描述标签失败:{}", e.getMessage());
            }

            // 推荐岗位 职位名称/职位描述关键字其中一个必须匹配一个岗位关键词才投递
            List<String> keywords = config.getKeywords();
            String jobName = job.getJobName();
            String jobKeywordTag = job.getJobKeywordTag();

            // 检查jobKeywordTag或jobName是否包含关键字列表中的任意一个
            boolean containsKeyword = false;
            if (keywords != null && !keywords.isEmpty()) {
                // 检查jobName
                if (isValidString(jobName)) {
                    for (String keywordItem : keywords) {
                        if (jobName.contains(keywordItem)) {
                            containsKeyword = true;
                            break;
                        }
                    }
                }

                // 如果jobName不包含关键字，检查jobKeywordTag
                if (!containsKeyword && isValidString(jobKeywordTag)) {
                    for (String keywordItem : keywords) {
                        if (jobKeywordTag.contains(keywordItem)) {
                            containsKeyword = true;
                            break;
                        }
                    }
                }
            }

            // 如果不包含任何关键字，则跳过此职位
            if (!keywords.isEmpty() && !containsKeyword) {
                log.info("已过滤:【{}】公司【{}】岗位不包含任何关键字", job.getCompanyName(), jobName);
                jobPage.close();
                continue;
            }

            try {
                // 处理职位详情页
                int result = processJobDetail(jobPage, job, null);
                if (result < 0) {
                    return result;
                }

            } catch (Exception e) {
                log.error("处理职位详情页失败: {}", e.getMessage());
            }

            if (config.getDebugger()) {
                break;
            }
        }
        return 0;
    }

    /**
     * 处理职位列表
     *
     * @param jobs    职位列表
     * @param keyword 搜索关键词
     * @return 处理结果，负数表示出错
     */
    @SneakyThrows
    private static int processJobList(List<Job> jobs, String keyword) {
        for (Job job : jobs) {
            // 使用Playwright在新标签页中打开链接
            Page jobPage = PlaywrightUtil.getPageObject().context().newPage();
            try {
                jobPage.navigate(homeUrl + job.getHref());
                // 等待聊天按钮出现
                Locator chatButton = jobPage.locator(CHAT_BUTTON);
                if (!chatButton.nth(0).isVisible(new Locator.IsVisibleOptions().setTimeout(5000))) {
                    Locator errorElement = jobPage.locator(ERROR_CONTENT);
                    if (errorElement.isVisible() && errorElement.textContent().contains("异常访问")) {
                        jobPage.close();
                        return -2;
                    }
                }
            } catch (Exception e) {
                if (config.getDebugger()) {
                    e.printStackTrace();
                }
                log.error("无法加载岗位详情页: {}", e.getMessage());
                jobPage.close();
                continue;
            }

            // 过滤不活跃HR
            if (isDeadHR(jobPage)) {
                jobPage.close();
                PlaywrightUtil.sleep(1);
                continue;
            }

            try {
                // 获取职位描述标签
                Locator tagElements = jobPage.locator(JOB_KEYWORD_LIST);
                int tagCount = tagElements.count();
                StringBuilder tag = new StringBuilder();
                for (int j = 0; j < tagCount; j++) {
                    tag.append(tagElements.nth(j).textContent()).append("·");
                }
                job.setJobKeywordTag(tag.toString());
            } catch (Exception e) {
                log.info("获取职位描述标签失败:{}", e.getMessage());
            }

            if (config.getKeyFilter()) {
                if (!job.getJobName().toLowerCase().contains(keyword.toLowerCase())) {
                    if (!job.getJobKeywordTag().toLowerCase().contains(keyword.toLowerCase())) {
                        log.info("已过滤：岗位【{}】描述不包含关键字【{}】", job.getJobName(), keyword);
                        jobPage.close();
                        continue;
                    }
                }
            }

            // 处理职位详情页
            int result = processJobDetail(jobPage, job, keyword);
            if (result < 0) {
                return result;
            }

            if (config.getDebugger()) {
                break;
            }
        }
        return 0;
    }

    /**
     * 处理单个职位详情页 - 共同处理流程
     *
     * @param jobPage 职位详情页面
     * @param job     职位信息
     * @param keyword 搜索关键词（可能为空）
     * @return 处理结果，负数表示出错
     */
    @SneakyThrows
    private static int processJobDetail(Page jobPage, Job job, String keyword) {
        BossLogger.setJobContext(job.getCompanyName(), job.getJobName());

        // 获取薪资
        try {
            Locator salaryElement = jobPage.locator(JOB_DETAIL_SALARY);
            if (salaryElement.isVisible()) {
                String salaryText = salaryElement.textContent();
                job.setSalary(salaryText);
                if (isSalaryNotExpected(salaryText)) {
                    // 过滤薪资
                    BossLogger.logJobFiltered("薪资不符合", job.getCompanyName(), job.getJobName(), salaryText);
                    jobPage.close();
                    return 0;
                }
            }
        } catch (Exception ignore) {
            log.debug("获取岗位薪资失败: {}", ignore.getMessage());
        }

        // 获取招聘人员信息
        try {
            Locator recruiterElement = jobPage.locator(RECRUITER_INFO);
            if (recruiterElement.isVisible()) {
                String recruiterName = recruiterElement.textContent();
                job.setRecruiter(recruiterName.replaceAll("\\r|\\n", ""));
                if (blackRecruiters.stream().anyMatch(recruiterName::contains)) {
                    // 排除黑名单招聘人员
                    BossLogger.logJobFiltered("黑名单招聘人员", job.getCompanyName(), job.getJobName(), recruiterName);
                    jobPage.close();
                    return 0;
                }
            }
        } catch (Exception ignore) {
            log.debug("获取招聘人员信息失败: {}", ignore.getMessage());
        }

        // 模拟用户浏览行为
        simulateUserBrowsingBehavior(jobPage);

        Locator chatBtn = jobPage.locator(CHAT_BUTTON);
        chatBtn = chatBtn.nth(0);

        // 每次点击沟通前都休眠5秒 减少调用频率
        PlaywrightUtil.sleep(5);

        log.debug("准备点击立即沟通按钮");
        if (chatBtn.isVisible() && "立即沟通".equals(chatBtn.textContent().replaceAll("\\s+", ""))) {
            String waitTime = config.getWaitTime();
            int sleepTime = 10; // 默认等待10秒

            if (waitTime != null) {
                try {
                    sleepTime = Integer.parseInt(waitTime);
                } catch (NumberFormatException e) {
                    log.warn("等待时间配置错误，使用默认值10秒");
                }
            }

            PlaywrightUtil.sleep(sleepTime);

            AiFilter filterResult = null;
            String jd = "";
            if (config.getEnableAIJobMatchDetection() && keyword != null) {
                // AI检测岗位是否匹配并
                Locator jdElement = jobPage.locator(JOB_DESCRIPTION);
                if (jdElement.isVisible()) {
                    jd = jdElement.textContent();
                    filterResult = checkJob(keyword, job.getJobName(), jd);

                    // 如果AI判定岗位描述和岗位名称不符，则跳过该职位
                    if (filterResult != null && !filterResult.getResult()) {
                        BossLogger.logJobFiltered("AI判定不匹配", job.getCompanyName(), job.getJobName());
                        jobPage.close();
                        return 0;
                    }

                    if (filterResult != null && filterResult.getResult()) {
                        BossLogger.logAIProcess("岗位匹配检测", true, "匹配成功");
                    }
                }
            }

            chatBtn.click();

            if (isLimit()) {
                PlaywrightUtil.sleep(1);
                jobPage.close();
                return -1;
            }

            // 沟通对话框处理
            try {
                // 处理可能出现的弹框
                try {
                    Locator dialogTitle = jobPage.locator(DIALOG_TITLE);
                    if (dialogTitle.nth(0).isVisible()) {
                        Locator closeBtn = jobPage.locator(DIALOG_CLOSE);
                        if (closeBtn.nth(0).isVisible()) {
                            closeBtn.nth(0).click();
                            chatBtn.nth(0).click();
                        }
                    }
                } catch (Exception ignore) {
                }

                // 对话文本录入框
                Locator input = jobPage.locator(CHAT_INPUT);
                input = input.nth(0);

                try {
                    input.waitFor(new Locator.WaitForOptions()
                            .setState(WaitForSelectorState.VISIBLE)
                            .setTimeout(10000));
                    log.debug("聊天输入框已显示");
                } catch (PlaywrightException e) {
                    log.debug("聊天输入框等待超时，实际状态: {}", input.isVisible());
                }

                if (input.isVisible(new Locator.IsVisibleOptions().setTimeout(10000))) {
                    input.click();
                    Locator dialogElement = jobPage.locator(DIALOG_CONTAINER);
                    dialogElement = dialogElement.nth(0);
                    if (dialogElement.isVisible() && "不匹配".equals(dialogElement.textContent())) {
                        jobPage.close();
                        return 0;
                    }

                    // 准备打招呼内容
                    String greetingMessage = config.getSayHi().replaceAll("\\r|\\n", "");

                    // 如果启用了AI打招呼功能，使用AI生成的求职问候语
                    if (config.getEnableAIGreeting()) {
                        try {
                            // 获取简历内容，优先从配置获取，否则使用默认内容
                            String resumeContent = getResumeContent();

                            // 调用AI生成求职问候语
                            UnifiedAiService aiService = UnifiedAiService.getInstance();
                            UnifiedAiService.JobGreetingResponse response = aiService.generateJobGreeting(
                                    jd, resumeContent, UnifiedAiService.AiPlatform.DEEPSEEK, 1, "professional");

                            if (response != null && response.isSuccess() && response.getData() != null
                                    && response.getData().getGreetings() != null
                                    && !response.getData().getGreetings().isEmpty()) {
                                String aiGreeting = response.getData().getGreetings().get(0).getContent();
                                if (isValidString(aiGreeting) && aiGreeting.length() > 10) {
                                    greetingMessage = aiGreeting.replaceAll("\\r|\\n", "");
                                    BossLogger.logAIProcess("求职问候语生成", true, "使用AI生成的求职问候语");
                                    log.debug("使用AI生成的求职问候语，长度: {}", greetingMessage.length());
                                } else {
                                    BossLogger.logAIProcess("求职问候语生成", false, "AI问候语内容无效，使用默认配置");
                                    log.debug("AI生成的求职问候语内容无效，使用配置的默认内容");
                                }
                            } else {
                                BossLogger.logAIProcess("求职问候语生成", false, "AI响应无效，使用默认配置");
                                log.debug("AI求职问候语生成响应无效，使用配置的默认内容");
                            }
                        } catch (Exception e) {
                            BossLogger.logSystemError("AI求职问候语生成", e);
                            log.debug("AI求职问候语生成失败，使用配置的默认内容: {}", e.getMessage());
                        }
                    } else {
                        log.debug("使用配置的默认打招呼内容");
                    }

                    input.fill(greetingMessage);

                    Locator sendBtn = jobPage.locator(SEND_BUTTON);
                    sendBtn = sendBtn.nth(0);
                    if (sendBtn.isVisible(new Locator.IsVisibleOptions().setTimeout(5000))) {
                        // 点击发送打招呼内容
                        sendBtn.click();
                        PlaywrightUtil.sleep(3);

                        String recruiter = job.getRecruiter();
                        String company = job.getCompanyName();
                        String position = job.getJobName() + " " + job.getSalary() + " " + job.getJobArea();

                        // 发送简历图片
                        Boolean imgResume = false;
                        if (config.getSendImgResume()) {
                            imgResume = sendResumeImage(jobPage);
                        }

                        PlaywrightUtil.sleep(3);

                        // 投递成功日志
                        BossLogger.logJobSuccess(company, job.getJobName(),
                                recruiter.replaceAll("\\r|\\n", ""), position);

                        if (imgResume) {
                            log.debug("图片简历发送成功");
                        }

                        resultList.add(job);
                    } else {
                        log.debug("未找到发送按钮");
                    }
                } else {
                    log.debug("聊天输入框不可见，状态: {}", input.isVisible());
                }
            } catch (Exception e) {
                BossLogger.logSystemError("发送消息", e);
            }
        }

        jobPage.close();

        BossLogger.clearContext();
        return 0;
    }

    /**
     * 模拟用户浏览行为
     * 安全地执行页面滚动，避免执行上下文被销毁的错误
     */
    private static void simulateUserBrowsingBehavior(Page jobPage) {
        try {
            // 检查页面是否仍然有效
            if (!isPageValid(jobPage)) {
                BossLogger.logBusinessError("页面浏览模拟", "页面状态无效，跳过滚动操作");
                return;
            }

            // 第一次滚动
            safeEvaluateJavaScript(jobPage, "window.scrollBy(0, 300)");
            PlaywrightUtil.sleep(1);

            // 再次检查页面状态
            if (!isPageValid(jobPage)) {
                return;
            }

            // 第二次滚动
            safeEvaluateJavaScript(jobPage, "window.scrollBy(0, 300)");
            PlaywrightUtil.sleep(1);

            // 最后检查页面状态
            if (!isPageValid(jobPage)) {
                return;
            }

            // 回到顶部
            safeEvaluateJavaScript(jobPage, "window.scrollTo(0, 0)");
            PlaywrightUtil.sleep(1);

        } catch (Exception e) {
            BossLogger.logSystemError("页面浏览行为模拟", e);
        }
    }

    /**
     * 安全地执行JavaScript代码
     */
    private static void safeEvaluateJavaScript(Page page, String script) {
        try {
            // 检查页面状态
            if (!isPageValid(page)) {
                log.debug("页面状态无效，跳过JavaScript执行: {}", script);
                return;
            }

            page.evaluate(script);

        } catch (Exception e) {
            // 捕获执行上下文被销毁的异常
            if (e.getMessage() != null && e.getMessage().contains("Execution context was destroyed")) {
                BossLogger.logBusinessError("JavaScript执行", "页面执行上下文被销毁，可能发生了页面跳转");
            } else {
                log.debug("JavaScript执行失败: {} - {}", script, e.getMessage());
            }
        }
    }

    /**
     * 安全地执行JavaScript代码并返回结果
     */
    private static Object safeEvaluateJavaScriptWithResult(Page page, String script, Object defaultResult)

    {
        try {
            // 检查页面状态
            if (!isPageValid(page)) {
                log.debug("页面状态无效，跳过JavaScript执行: {}", script);
                return defaultResult;
            }

            return page.evaluate(script);

        } catch (Exception e) {
            // 捕获执行上下文被销毁的异常
            if (e.getMessage() != null && e.getMessage().contains("Execution context was destroyed")) {
                BossLogger.logBusinessError("JavaScript执行", "页面执行上下文被销毁，可能发生了页面跳转");
            } else {
                log.debug("JavaScript执行失败: {} - {}", script, e.getMessage());
            }
            return defaultResult;
        }
    }

    /**
     * 检查页面是否仍然有效
     */
    private static boolean isPageValid(Page page) {
        try {
            // 尝试获取页面URL来检查页面是否仍然有效
            String url = page.url();
            // 检查是否是正常的岗位详情页面
            return url != null && url.contains("zhipin.com") && !url.contains("error");
        } catch (Exception e) {
            log.debug("页面状态检查失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 发送简历图片
     */
    private static boolean sendResumeImage(Page jobPage) {
        try {
            // 从配置文件读取简历图片路径
            String resumePath = config.getResumeImagePath();
            if (resumePath != null && !resumePath.isEmpty()) {
                File imageFile = new File(resumePath);
                if (imageFile.exists() && imageFile.isFile()) {
                    // 使用Playwright上传文件
                    Locator fileInput = jobPage.locator(IMAGE_UPLOAD);
                    if (fileInput.isVisible()) {
                        fileInput.setInputFiles(new Path[] { Paths.get(imageFile.getPath()) });
                        // 等待发送按钮并点击
                        Locator imageSendBtn = jobPage.locator(".image-uploader-btn");
                        if (imageSendBtn.isVisible(new Locator.IsVisibleOptions().setTimeout(2000))) {
                            // 发送简历图片
                            imageSendBtn.click();
                            return true;
                        }
                    }
                } else {
                    BossLogger.logBusinessError("简历图片发送", "文件不存在", resumePath);
                }
            } else {
                log.debug("未配置简历图片路径，跳过发送");
            }
        } catch (Exception e) {
            BossLogger.logSystemError("简历图片发送", e);
        }
        return false;
    }

    public static boolean isValidString(String str) {
        return str != null && !str.isEmpty();
    }

    /**
     * 获取简历内容
     * 优先从配置获取，如果没有配置则使用默认简历内容
     * 
     * @return 简历内容
     */
    private static String getResumeContent() {
        // 优先从配置中获取简历内容
        String resumeContent = config.getResumeContent();
        if (isValidString(resumeContent) && resumeContent.trim().length() > 10) {
            return resumeContent;
        }

        // 如果配置中没有简历内容或内容太短，使用默认模板
        return "Java开发工程师\n" +
                "工作经验：5年\n" +
                "技能专长：熟练掌握Java、Spring Boot、Spring Cloud、MySQL、Redis等技术栈\n" +
                "项目经验：具有微服务架构设计和开发经验，参与过多个大型企业级项目\n" +
                "教育背景：计算机科学与技术本科\n" +
                "个人优势：具备良好的编码规范、团队协作能力和快速学习能力";
    }

    /**
     * 检查岗位薪资是否符合预期
     *
     * @return boolean
     *         true 不符合预期
     *         false 符合预期
     *         期望的最低薪资如果比岗位最高薪资还小，则不符合（薪资给的太少）
     *         期望的最高薪资如果比岗位最低薪资还小，则不符合(要求太高满足不了)
     */
    private static boolean isSalaryNotExpected(String salary) {
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
            BossLogger.logSystemError("薪资解析", e);
            // 出错时，返回 true 表示不满足预期
            return true;
        }
    }

    /**
     * 是否存在有效的期望薪资范围
     */
    private static boolean hasExpectedSalary(List<Integer> expectedSalary) {
        return expectedSalary != null && !expectedSalary.isEmpty();
    }

    /**
     * 去掉年终奖信息，如 "·15薪"、"·13薪"。
     */
    private static String removeYearBonusText(String salary) {
        if (salary.contains("薪")) {
            // 使用正则去除 "·任意数字薪"
            return salary.replaceAll("·\\d+薪", "");
        }
        return salary;
    }

    /**
     * 判断是否是按天计薪，如发现 "元/天" 则认为是日薪
     */
    private static String detectJobType(String salary) {
        if (salary.contains("元/天")) {
            return "day";
        }
        return "mouth";
    }

    /**
     * 如果是日薪，则去除 "元/天"
     */
    private static String removeDayUnitIfNeeded(String salary) {
        if (salary.contains("元/天")) {
            return salary.replaceAll("元/天", "");
        }
        return salary;
    }

    private static Integer getMinimumSalary(List<Integer> expectedSalary) {
        return expectedSalary != null && !expectedSalary.isEmpty() ? expectedSalary.get(0) : null;
    }

    private static Integer getMaximumSalary(List<Integer> expectedSalary) {
        return expectedSalary != null && expectedSalary.size() > 1 ? expectedSalary.get(1) : null;
    }

    private static boolean isSalaryInExpectedFormat(String salaryText) {
        return salaryText.contains("K") || salaryText.contains("k") || salaryText.contains("元/天");
    }

    private static String cleanSalaryText(String salaryText) {
        salaryText = salaryText.replace("K", "").replace("k", "");
        int dotIndex = salaryText.indexOf('·');
        if (dotIndex != -1) {
            salaryText = salaryText.substring(0, dotIndex);
        }
        return salaryText;
    }

    private static boolean isSalaryOutOfRange(Integer[] jobSalary, Integer miniSalary, Integer maxSalary,
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

    private static boolean isDeadHR(Page page) {
        if (!config.getFilterDeadHR()) {
            return false;
        }
        try {
            // 尝试获取 HR 的活跃时间
            Locator activeTimeElement = page.locator(HR_ACTIVE_TIME);
            activeTimeElement = activeTimeElement.nth(0);

            if (activeTimeElement.isVisible(new Locator.IsVisibleOptions().setTimeout(5000))) {
                String activeTimeText = activeTimeElement.textContent();
                String companyHR = getCompanyAndHR(page).replaceAll("\\s+", "");
                boolean isDeadHR = containsDeadStatus(activeTimeText, config.getDeadStatus());

                BossLogger.logHRStatus(companyHR, activeTimeText, !isDeadHR);
                return isDeadHR;
            }
        } catch (Exception e) {
            String companyHR = getCompanyAndHR(page).replaceAll("\\s+", "");
            log.debug("未找到{}的活跃状态，默认投递", companyHR);
        }
        return false;
    }

    public static boolean containsDeadStatus(String activeTimeText, List<String> deadStatus) {
        for (String status : deadStatus) {
            if (activeTimeText.contains(status)) {
                return true;// 一旦找到包含的值，立即返回 true
            }
        }
        return false;// 如果没有找到，返回 false
    }

    private static String getCompanyAndHR(Page page) {
        try {
            Locator element = page.locator(RECRUITER_INFO);
            element = element.nth(0);
            if (element.isVisible(new Locator.IsVisibleOptions().setTimeout(2000))) {
                return element.textContent().replaceAll("\n", "");
            }
        } catch (Exception e) {
            log.debug("获取公司和HR信息失败: {}", e.getMessage());
        }
        return "未知公司和HR";
    }

    private static AiFilter checkJob(String keyword, String jobName, String jd) {
        try {
            UnifiedAiService aiService = UnifiedAiService.getInstance();
            UnifiedAiService.JobMatchResult result = aiService.checkJobMatch(keyword, jobName, jd);

            if (result.isMatched()) {
                // AI已经返回了生成的打招呼内容，直接使用
                String aiGreeting = result.getMessage();
                if (isValidString(aiGreeting) && aiGreeting.length() > 10) {
                    log.debug("AI生成打招呼内容成功，长度: {}", aiGreeting.length());
                    return new AiFilter(true, aiGreeting);
                } else {
                    log.debug("AI响应内容无效，将使用默认配置");
                    return new AiFilter(true, null); // 匹配但内容无效
                }
            } else {
                return new AiFilter(false);
            }
        } catch (Exception e) {
            BossLogger.logSystemError("AI职位匹配检测", e);
            // 发生异常时返回false，不匹配
            return new AiFilter(false);
        }
    }

    private static boolean isTargetJob(String keyword, String jobName) {
        boolean keywordIsAI = false;
        for (String target : new String[] { "大模型", "AI" }) {
            if (keyword.contains(target)) {
                keywordIsAI = true;
                break;
            }
        }

        boolean jobIsDesign = false;
        for (String designOrVision : new String[] { "设计", "视觉", "产品", "运营" }) {
            if (jobName.contains(designOrVision)) {
                jobIsDesign = true;
                break;
            }
        }

        boolean jobIsAI = false;
        for (String target : new String[] { "AI", "人工智能", "大模型", "生成" }) {
            if (jobName.contains(target)) {
                jobIsAI = true;
                break;
            }
        }

        if (keywordIsAI) {
            if (jobIsDesign) {
                return false;
            } else if (!jobIsAI) {
                return true;
            }
        }
        return true;
    }

    private static Integer[] parseSalaryRange(String salaryText) {
        try {
            return Arrays.stream(salaryText.split("-")).map(s -> s.replaceAll("[^0-9]", "")) // 去除非数字字符
                    .map(Integer::parseInt) // 转换为Integer
                    .toArray(Integer[]::new); // 转换为Integer数组
        } catch (Exception e) {
            log.debug("薪资解析失败: {}", e.getMessage());
            return null;
        }
    }

    private static boolean isLimit() {
        try {
            PlaywrightUtil.sleep(1);
            Page page = PlaywrightUtil.getPageObject();
            Locator dialogElement = page.locator(DIALOG_CON);
            if (dialogElement.isVisible(new Locator.IsVisibleOptions().setTimeout(2000))) {
                String text = dialogElement.textContent();
                boolean isLimit = text.contains("已达上限");
                if (isLimit) {
                    BossLogger.logBusinessError("投递限制", "今日投递已达上限");
                }
                return isLimit;
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    @SneakyThrows
    private static void login() {
        BossLogger.logLoginStatus("开始登录检查");

        // 使用Playwright打开网站
        Page page = PlaywrightUtil.getPageObject();
        page.navigate(homeUrl);
        Page h5Page = PlaywrightUtil.getPageObject(PlaywrightUtil.DeviceType.MOBILE);
        if (!ObjectUtils.isEmpty(h5Page)) {
            h5Page.navigate(homeUrl);
        }

        // 检查并加载Cookie
        if (isCookieValid(cookiePath)) {
            PlaywrightUtil.loadCookies(cookiePath);
            page.reload();
            if (!ObjectUtils.isEmpty(h5Page)) {
                h5Page.reload();
            }
            PlaywrightUtil.sleep(2);
        }

        // 检查是否需要登录
        if (isLoginRequired()) {
            BossLogger.logLoginStatus("Cookie失效，开始扫码登录");
            scanLogin();
        } else {
            BossLogger.logLoginStatus("已登录");
        }
    }

    // 检查cookie是否有效的方法，替换SeleniumUtil的实现
    private static boolean isCookieValid(String cookiePath) {
        try {
            String cookieContent = new String(Files.readAllBytes(Paths.get(cookiePath)));
            return cookieContent != null && !cookieContent.equals("[]") && cookieContent.contains("name");
        } catch (Exception e) {
            BossLogger.logSystemError("Cookie文件读取", e);
            return false;
        }
    }

    private static boolean isLoginRequired() {
        try {
            Page page = PlaywrightUtil.getPageObject();

            // 检查是否有登录按钮
            Locator loginButton = page.locator(LOGIN_BTNS);
            if (loginButton.isVisible() && loginButton.textContent().contains("登录")) {
                return true;
            }

            // 检查是否有错误页面
            try {
                Locator pageHeader = page.locator(PAGE_HEADER);
                if (pageHeader.isVisible()) {
                    Locator errorPageLogin = page.locator(ERROR_PAGE_LOGIN);
                    if (errorPageLogin.isVisible()) {
                        errorPageLogin.click();
                        return true;
                    }
                }
            } catch (Exception ex) {
                log.debug("无访问异常页面");
            }

            return false;
        } catch (Exception e) {
            BossLogger.logSystemError("登录状态检查", e);
            return true; // 遇到错误，默认需要登录
        }
    }

    @SneakyThrows
    private static void scanLogin() {
        // 使用Playwright进行登录操作
        Page page = PlaywrightUtil.getPageObject();
        // 访问登录页面
        page.navigate(homeUrl + "/web/user/?ka=header-login");
        PlaywrightUtil.sleep(3);

        // 1. 如果已经登录，则直接返回
        try {
            Locator loginBtn = page.locator(LOGIN_BTN);
            if (loginBtn.isVisible() && !loginBtn.textContent().equals("登录")) {
                BossLogger.logLoginStatus("检测到已登录状态");
                return;
            }
        } catch (Exception ignored) {
        }

        BossLogger.logLoginStatus("等待扫码登录");

        // 2. 定位二维码登录的切换按钮
        Locator scanButton = page.locator(LOGIN_SCAN_SWITCH);
        boolean scanButtonVisible = scanButton.isVisible(new Locator.IsVisibleOptions().setTimeout(30000));
        if (!scanButtonVisible) {
            BossLogger.logBusinessError("扫码登录", "未找到二维码登录按钮");
            return;
        }

        // 3. 登录逻辑
        boolean login = false;

        // 4. 记录开始时间，用于判断10分钟超时
        long startTime = System.currentTimeMillis();
        final long TIMEOUT = 10 * 60 * 1000; // 10分钟

        // 5. 用于监听用户是否在控制台回车
        Scanner scanner = new Scanner(System.in);

        while (!login) {
            // 如果已经超过10分钟，退出程序
            long elapsed = System.currentTimeMillis() - startTime;
            if (elapsed >= TIMEOUT) {
                BossLogger.logBusinessError("扫码登录", "超过10分钟未完成登录，程序退出");
                System.exit(1);
            }

            try {
                // 尝试点击二维码按钮并等待页面出现已登录的元素
                scanButton.click();
                // 等待登录成功标志
                boolean loginSuccess = page.locator(LOGIN_SUCCESS_HEADER)
                        .isVisible(new Locator.IsVisibleOptions().setTimeout(2000));

                // 如果找到登录成功元素，说明登录成功
                if (loginSuccess) {
                    login = true;
                    BossLogger.logLoginStatus("登录成功，保存Cookie");
                } else {
                    // 登录失败，等待用户操作或2秒后重试
                    boolean userInput = waitForUserInputOrTimeout(scanner);
                    if (userInput) {
                        log.debug("检测到用户输入，继续尝试登录");
                    }
                }
            } catch (Exception e) {
                // scanButton.click() 可能已经登录成功，没有这个扫码登录按钮
                boolean loginSuccess = page.locator(LOGIN_SUCCESS_HEADER)
                        .isVisible(new Locator.IsVisibleOptions().setTimeout(2000));
                if (loginSuccess) {
                    login = true;
                    BossLogger.logLoginStatus("登录成功，保存Cookie");
                }
            }
        }

        // 登录成功后，保存Cookie
        PlaywrightUtil.saveCookies(cookiePath);
    }

    /**
     * 在指定的毫秒数内等待用户输入回车；若在等待时间内用户按回车则返回 true，否则返回 false。
     *
     * @param scanner 用于读取控制台输入
     * @return 用户是否在指定时间内按回车
     */
    private static boolean waitForUserInputOrTimeout(Scanner scanner) {
        long end = System.currentTimeMillis() + 2000;
        while (System.currentTimeMillis() < end) {
            try {
                // 判断输入流中是否有可用字节
                if (System.in.available() > 0) {
                    // 读取一行（用户输入）
                    scanner.nextLine();
                    return true;
                }
            } catch (IOException e) {
                // 读取输入流异常，直接忽略
            }

            // 小睡一下，避免 CPU 空转
            PlaywrightUtil.sleep(1);
        }
        return false;
    }





}
