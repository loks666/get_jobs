package getjobs.service.impl;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.WaitForSelectorState;
import getjobs.boss.BossElementFinder;
import getjobs.boss.BossElementLocators;
import getjobs.dto.BossConfigDTO;
import getjobs.enums.RecruitmentPlatformEnum;
import getjobs.service.RecruitmentService;
import getjobs.dto.JobDTO;
import getjobs.utils.JobUtils;
import getjobs.utils.PlaywrightUtil;
import getjobs.service.ConfigService;
import getjobs.service.BossApiMonitorService;
import getjobs.entity.ConfigEntity;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.json.JSONArray;
import org.springframework.stereotype.Service;

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

import static getjobs.boss.BossElementLocators.*;

/**
 * Boss直聘招聘服务实现类
 * 
 * @author loks666
 *         项目链接: <a href=
 *         "https://github.com/loks666/get_jobs">https://github.com/loks666/get_jobs</a>
 */
@Slf4j
@Service
public class BossRecruitmentServiceImpl implements RecruitmentService {

    private static final String HOME_URL = RecruitmentPlatformEnum.BOSS_ZHIPIN.getHomeUrl();
    private static final String GEEK_JOB_URL = HOME_URL + "/web/geek/job?";
    private static final String GEEK_CHAT_URL = HOME_URL + "/web/geek/chat";

    private Set<String> blackCompanies = new HashSet<>();
    private Set<String> blackRecruiters = new HashSet<>();

    private Set<String> blackJobs = new HashSet<>();
    private Set<String> jobHrefSet = new HashSet<>();

    private final ConfigService configService;
    private final BossApiMonitorService bossApiMonitorService;

    public BossRecruitmentServiceImpl(ConfigService configService, BossApiMonitorService bossApiMonitorService) {
        this.configService = configService;
        this.bossApiMonitorService = bossApiMonitorService;
    }

    @Override
    public RecruitmentPlatformEnum getPlatform() {
        return RecruitmentPlatformEnum.BOSS_ZHIPIN;
    }

    @Override
    public boolean login(BossConfigDTO config) {
        log.info("开始Boss直聘登录检查");

        try {
            // 使用Playwright打开网站
            Page page = PlaywrightUtil.getPageObject();
            page.navigate(HOME_URL);

            // 检查并加载Cookie
            String cookieData = getCookieFromConfig();
            if (isCookieValid(cookieData)) {
                loadCookiesFromString(cookieData);
                page.reload();
                PlaywrightUtil.sleep(2);
            }

            // 检查是否需要登录
            if (isLoginRequired()) {
                log.info("Cookie失效，开始扫码登录");
                return scanLogin();
            } else {
                log.info("Boss直聘已登录");
                return true;
            }
        } catch (Exception e) {
            log.error("Boss直聘登录失败", e);
            return false;
        }
    }

    @Override
    public List<JobDTO> collectJobs(BossConfigDTO config) {
        log.info("开始Boss直聘岗位采集");
        List<JobDTO> allJobDTOS = new ArrayList<>();

        try {
            // 按城市和关键词搜索岗位
            for (String cityCode : config.getCityCodeCodes()) {
                for (String keyword : config.getKeywordsList()) {
                    List<JobDTO> cityJobDTOS = collectJobsByCity(cityCode, keyword, config);
                    allJobDTOS.addAll(cityJobDTOS);
                }
            }

            log.info("Boss直聘岗位采集完成，共采集{}个岗位", allJobDTOS.size());
            return allJobDTOS;
        } catch (Exception e) {
            log.error("Boss直聘岗位采集失败", e);
            return allJobDTOS;
        }
    }

    @Override
    public List<JobDTO> collectRecommendJobs(BossConfigDTO config) {
        log.info("开始Boss直聘推荐岗位采集");
        List<JobDTO> recommendJobDTOS = new ArrayList<>();

        try {
            Page page = PlaywrightUtil.getPageObject();

            // 设置推荐岗位接口监听器
            bossApiMonitorService.startMonitoring();

            String cookieData = getCookieFromConfig();
            if (isCookieValid(cookieData)) {
                loadCookiesFromString(cookieData);
            }
            page.navigate(GEEK_JOB_URL);

            // 等待页面加载
            page.waitForLoadState();

            // 等待元素出现，最多等待10秒
            page.waitForSelector("a.expect-item", new Page.WaitForSelectorOptions().setTimeout(10000.0));

            // 获取a标签且class是expect-item的元素
            ElementHandle activeElement = page.querySelector("a.expect-item");

            if (activeElement != null) {
                log.debug("找到推荐岗位入口，准备点击");
                activeElement.click();
                page.waitForLoadState();

                if (isJobsPresent()) {
                    // 滚动加载推荐岗位
                    int totalJobs = loadJobsWithScroll(page, "推荐岗位");
                    log.info("推荐岗位加载，总计: {}", totalJobs);
                }
            }
        } catch (Exception e) {
            log.error("Boss直聘推荐岗位采集失败", e);
        }

        return recommendJobDTOS;
    }

    @Override
    public List<JobDTO> filterJobs(List<JobDTO> jobDTOS, BossConfigDTO config) {
        log.info("开始Boss直聘岗位过滤，原始岗位数量: {}", jobDTOS.size());

        List<JobDTO> filteredJobDTOS = jobDTOS.stream()
                .filter(job -> !isJobInBlacklist(job))
                .filter(job -> !isCompanyInBlacklist(job))
                .filter(job -> !isRecruiterInBlacklist(job))
                .filter(job -> isSalaryExpected(job, config))
                .collect(Collectors.toList());

        log.info("Boss直聘岗位过滤完成，过滤后岗位数量: {}", filteredJobDTOS.size());
        return filteredJobDTOS;
    }

    @Override
    public int deliverJobs(List<JobDTO> jobDTOS, BossConfigDTO config) {
        log.info("开始Boss直聘岗位投递，待投递岗位数量: {}", jobDTOS.size());
        int successCount = 0;

        for (JobDTO jobDTO : jobDTOS) {
            try {
                if (isDeliveryLimitReached()) {
                    log.warn("达到投递上限，停止投递");
                    break;
                }

                boolean delivered = deliverSingleJob(jobDTO, config);
                if (delivered) {
                    successCount++;
                    log.info("投递成功: {} - {}", jobDTO.getCompanyName(), jobDTO.getJobName());
                } else {
                    log.warn("投递失败: {} - {}", jobDTO.getCompanyName(), jobDTO.getJobName());
                }

                // 投递间隔
                PlaywrightUtil.sleep(15);

                if (config.getDebugger()) {
                    break; // 调试模式只投递一个
                }
            } catch (Exception e) {
                log.error("投递岗位失败: {} - {}", jobDTO.getCompanyName(), jobDTO.getJobName(), e);
            }
        }

        log.info("Boss直聘岗位投递完成，成功投递: {}", successCount);
        return successCount;
    }

    @Override
    public boolean isDeliveryLimitReached() {
        try {
            PlaywrightUtil.sleep(1);
            Page page = PlaywrightUtil.getPageObject();
            Locator dialogElement = page.locator(DIALOG_CON);
            if (dialogElement.isVisible(new Locator.IsVisibleOptions().setTimeout(2000.0))) {
                String text = dialogElement.textContent();
                boolean isLimit = text.contains("已达上限");
                if (isLimit) {
                    log.warn("投递限制：今日投递已达上限");
                }
                return isLimit;
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void saveData(String dataPath) {
        try {
            updateBlacklistFromChat();
            Map<String, Set<String>> data = new HashMap<>();
            data.put("blackCompanies", blackCompanies);
            data.put("blackRecruiters", blackRecruiters);
            data.put("blackJobs", blackJobs);
            String json = customJsonFormat(data);
            Files.write(Paths.get(dataPath), json.getBytes());
            log.info("保存Boss直聘黑名单数据: {}", dataPath);
        } catch (IOException e) {
            log.error("保存Boss直聘黑名单数据失败: {}", dataPath, e);
        }
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 按城市采集岗位
     */
    private List<JobDTO> collectJobsByCity(String cityCode, String keyword, BossConfigDTO config) {
        String searchUrl = getSearchUrl(cityCode, config);
        String encodedKeyword = URLEncoder.encode(keyword, StandardCharsets.UTF_8);
        String url = searchUrl + "&query=" + encodedKeyword;

        log.info("开始采集，城市: {}，关键词: {}，URL: {}", cityCode, keyword, url);

        Page page = PlaywrightUtil.getPageObject();

        // 设置岗位搜索接口监听器
        bossApiMonitorService.startMonitoring();

        String cookieData = getCookieFromConfig();
        if (isCookieValid(cookieData)) {
            loadCookiesFromString(cookieData);
        }
        page.navigate(url);

        List<JobDTO> jobDTOS = new ArrayList<>();

        if (isJobsPresent()) {
            try {
                // 滚动加载更多岗位
                int totalJobs = loadJobsWithScroll(page, "搜索岗位");
                log.info("搜索岗位加载完成: {}，关键词: {}", totalJobs, keyword);
            } catch (Exception e) {
                log.error("滚动加载岗位数据出错", e);
            }
        }

        BossElementLocators.clickAllJobCards(page, 5000);

        return jobDTOS;
    }

    /**
     * 构建搜索URL
     */
    private String getSearchUrl(String cityCode, BossConfigDTO config) {
        return GEEK_JOB_URL + JobUtils.appendParam("city", cityCode) +
                JobUtils.appendParam("jobType", config.getJobTypeCode()) +
                JobUtils.appendParam("salary", config.getSalaryCode()) +
                JobUtils.appendListParam("experience", config.getExperienceCodes()) +
                JobUtils.appendListParam("degree", config.getDegreeCodes()) +
                JobUtils.appendListParam("scale", config.getScaleCodes()) +
                JobUtils.appendListParam("industry", config.getIndustryCodes()) +
                JobUtils.appendListParam("stage", config.getStageCodes());
    }

    /**
     * 检查是否存在岗位
     */
    private boolean isJobsPresent() {
        try {
            PlaywrightUtil.waitForElement(JOB_LIST_CONTAINER);
            return true;
        } catch (Exception e) {
            log.warn("岗位页面检查：页面无岗位元素");
            return false;
        }
    }

    /**
     * 滚动加载岗位数据
     */
    private int loadJobsWithScroll(Page page, String jobType) {
        int previousJobCount = 0;
        int currentJobCount = 0;
        int unchangedCount = 0;

        while (unchangedCount < 2) {
            List<ElementHandle> jobCards = page.querySelectorAll(JOB_LIST_SELECTOR);
            currentJobCount = jobCards.size();

            log.debug("滚动加载中，当前岗位数: {}", currentJobCount);

            if (currentJobCount > previousJobCount) {
                previousJobCount = currentJobCount;
                unchangedCount = 0;

                safeEvaluateJavaScript(page, "window.scrollTo(0, document.body.scrollHeight)");
                log.debug("{}下拉页面加载更多...", jobType);
                page.waitForTimeout(2000);
            } else {
                unchangedCount++;
                if (unchangedCount < 2) {
                    log.debug("{}下拉后岗位数量未增加，再次尝试...", jobType);
                    safeEvaluateJavaScript(page, "window.scrollTo(0, document.body.scrollHeight)");
                    page.waitForTimeout(2000);
                }
            }
        }

        log.debug("{}加载完成，总计岗位数: {}", jobType, currentJobCount);
        return currentJobCount;
    }

    /**
     * 投递单个岗位
     */
    @SneakyThrows
    private boolean deliverSingleJob(JobDTO jobDTO, BossConfigDTO config) {
        // 在新标签页中打开岗位详情
        Page jobPage = PlaywrightUtil.getPageObject().context().newPage();

        try {
            jobPage.navigate(jobDTO.getHref());

            // 等待聊天按钮出现
            Locator chatButton = jobPage.locator(CHAT_BUTTON);
            if (!chatButton.nth(0).isVisible(new Locator.IsVisibleOptions().setTimeout(5000.0))) {
                Locator errorElement = jobPage.locator(ERROR_CONTENT);
                if (errorElement.isVisible() && errorElement.textContent().contains("异常访问")) {
                    return false;
                }
            }

            // 模拟用户浏览行为
            simulateUserBrowsingBehavior(jobPage);

            // 执行投递
            return performDelivery(jobPage, jobDTO, config);

        } finally {
            jobPage.close();
        }
    }

    /**
     * 执行具体的投递操作
     */
    private boolean performDelivery(Page jobPage, JobDTO jobDTO, BossConfigDTO config) {
        try {
            Locator chatBtn = jobPage.locator(CHAT_BUTTON).nth(0);

            // 等待并点击沟通按钮
            String waitTime = config.getWaitTime();
            int sleepTime = 10;
            if (waitTime != null) {
                try {
                    sleepTime = Integer.parseInt(waitTime);
                } catch (NumberFormatException e) {
                    log.warn("等待时间配置错误，使用默认值10秒");
                }
            }
            PlaywrightUtil.sleep(sleepTime);

            chatBtn.click();

            if (isDeliveryLimitReached()) {
                return false;
            }

            // 处理可能出现的弹框
            handlePossibleDialog(jobPage, chatBtn);

            // 处理输入框和发送消息
            return handleChatInput(jobPage, jobDTO, config);

        } catch (Exception e) {
            log.error("执行投递操作失败", e);
            return false;
        }
    }

    /**
     * 处理可能出现的弹框
     */
    private void handlePossibleDialog(Page jobPage, Locator chatBtn) {
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
            // 忽略弹框处理异常
        }
    }

    /**
     * 处理聊天输入框
     */
    private boolean handleChatInput(Page jobPage, JobDTO jobDTO, BossConfigDTO config) {
        try {
            Locator input = jobPage.locator(CHAT_INPUT).nth(0);

            input.waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.VISIBLE)
                    .setTimeout(10000.0));

            if (input.isVisible(new Locator.IsVisibleOptions().setTimeout(10000.0))) {
                input.click();

                Locator dialogElement = jobPage.locator(DIALOG_CONTAINER).nth(0);
                if (dialogElement.isVisible() && "不匹配".equals(dialogElement.textContent())) {
                    return false;
                }

                // 准备打招呼内容
                String greetingMessage = config.getSayHi().replaceAll("\\r|\\n", "");
                input.fill(greetingMessage);

                Locator sendBtn = jobPage.locator(SEND_BUTTON).nth(0);
                if (sendBtn.isVisible(new Locator.IsVisibleOptions().setTimeout(5000.0))) {
                    sendBtn.click();
                    PlaywrightUtil.sleep(3);

                    // 发送简历图片
                    if (config.getSendImgResume()) {
                        sendResumeImage(jobPage, config);
                    }

                    PlaywrightUtil.sleep(3);
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            log.error("处理聊天输入框失败", e);
            return false;
        }
    }

    /**
     * 发送简历图片
     */
    private boolean sendResumeImage(Page jobPage, BossConfigDTO config) {
        try {
            String resumePath = config.getResumeImagePath();
            if (resumePath != null && !resumePath.isEmpty()) {
                File imageFile = new File(resumePath);
                if (imageFile.exists() && imageFile.isFile()) {
                    Locator fileInput = jobPage.locator(IMAGE_UPLOAD);
                    if (fileInput.isVisible()) {
                        fileInput.setInputFiles(new Path[] { Paths.get(imageFile.getPath()) });
                        Locator imageSendBtn = jobPage.locator(".image-uploader-btn");
                        if (imageSendBtn.isVisible(new Locator.IsVisibleOptions().setTimeout(2000.0))) {
                            imageSendBtn.click();
                            return true;
                        }
                    }
                } else {
                    log.warn("简历图片发送：文件不存在 -> {}", resumePath);
                }
            }
        } catch (Exception e) {
            log.error("简历图片发送出错", e);
        }
        return false;
    }

    /**
     * 模拟用户浏览行为
     */
    private void simulateUserBrowsingBehavior(Page jobPage) {
        try {
            if (!isPageValid(jobPage)) {
                log.warn("页面浏览模拟：页面状态无效，跳过滚动操作");
                return;
            }

            safeEvaluateJavaScript(jobPage, "window.scrollBy(0, 300)");
            PlaywrightUtil.sleep(1);

            if (!isPageValid(jobPage)) {
                return;
            }

            safeEvaluateJavaScript(jobPage, "window.scrollBy(0, 300)");
            PlaywrightUtil.sleep(1);

            if (!isPageValid(jobPage)) {
                return;
            }

            safeEvaluateJavaScript(jobPage, "window.scrollTo(0, 0)");
            PlaywrightUtil.sleep(1);

        } catch (Exception e) {
            log.error("页面浏览行为模拟出错", e);
        }
    }

    /**
     * 安全地执行JavaScript代码
     */
    private void safeEvaluateJavaScript(Page page, String script) {
        try {
            if (!isPageValid(page)) {
                log.debug("页面状态无效，跳过JavaScript执行: {}", script);
                return;
            }
            page.evaluate(script);
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("Execution context was destroyed")) {
                log.warn("JavaScript执行：页面执行上下文被销毁，可能发生了页面跳转");
            } else {
                log.debug("JavaScript执行失败: {} - {}", script, e.getMessage());
            }
        }
    }

    /**
     * 检查页面是否仍然有效
     */
    private boolean isPageValid(Page page) {
        try {
            String url = page.url();
            return url != null && url.contains("zhipin.com") && !url.contains("error");
        } catch (Exception e) {
            log.debug("页面状态检查失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 检查岗位是否在黑名单中
     */
    private boolean isJobInBlacklist(JobDTO jobDTO) {
        return blackJobs.stream().anyMatch(blackJob -> jobDTO.getJobName().contains(blackJob));
    }

    /**
     * 检查公司是否在黑名单中
     */
    private boolean isCompanyInBlacklist(JobDTO jobDTO) {
        return blackCompanies.stream().anyMatch(blackCompany -> jobDTO.getCompanyName().contains(blackCompany));
    }

    /**
     * 检查招聘者是否在黑名单中
     */
    private boolean isRecruiterInBlacklist(JobDTO jobDTO) {
        return blackRecruiters.stream()
                .anyMatch(blackRecruiter -> jobDTO.getRecruiter() != null
                        && jobDTO.getRecruiter().contains(blackRecruiter));
    }

    /**
     * 检查薪资是否符合预期
     */
    private boolean isSalaryExpected(JobDTO jobDTO, BossConfigDTO config) {
        if (jobDTO.getSalary() == null || jobDTO.getSalary().isEmpty()) {
            return true; // 没有薪资信息时默认通过
        }

        try {
            List<Integer> expectedSalary = config.getExpectedSalary();
            if (expectedSalary == null || expectedSalary.isEmpty()) {
                return true; // 没有期望薪资时默认通过
            }

            return !isSalaryNotExpected(jobDTO.getSalary(), expectedSalary);
        } catch (Exception e) {
            log.debug("薪资验证失败: {}", e.getMessage());
            return true; // 验证失败时默认通过
        }
    }

    /**
     * 检查薪资是否不符合预期
     */
    private boolean isSalaryNotExpected(String salary, List<Integer> expectedSalary) {
        try {
            // 清理薪资文本
            salary = removeYearBonusText(salary);

            if (!isSalaryInExpectedFormat(salary)) {
                return true;
            }

            salary = cleanSalaryText(salary);
            String jobType = detectJobType(salary);
            salary = removeDayUnitIfNeeded(salary);

            Integer[] jobSalaryRange = parseSalaryRange(salary);
            return isSalaryOutOfRange(jobSalaryRange,
                    getMinimumSalary(expectedSalary),
                    getMaximumSalary(expectedSalary),
                    jobType);
        } catch (Exception e) {
            log.error("薪资解析出错", e);
            return true;
        }
    }

    /**
     * 去掉年终奖信息
     */
    private String removeYearBonusText(String salary) {
        if (salary.contains("薪")) {
            return salary.replaceAll("·\\d+薪", "");
        }
        return salary;
    }

    /**
     * 判断薪资格式是否符合预期
     */
    private boolean isSalaryInExpectedFormat(String salaryText) {
        return salaryText.contains("K") || salaryText.contains("k") || salaryText.contains("元/天");
    }

    /**
     * 清理薪资文本
     */
    private String cleanSalaryText(String salaryText) {
        salaryText = salaryText.replace("K", "").replace("k", "");
        int dotIndex = salaryText.indexOf('·');
        if (dotIndex != -1) {
            salaryText = salaryText.substring(0, dotIndex);
        }
        return salaryText;
    }

    /**
     * 判断是否是按天计薪
     */
    private String detectJobType(String salary) {
        if (salary.contains("元/天")) {
            return "day";
        }
        return "month";
    }

    /**
     * 如果是日薪，则去除"元/天"
     */
    private String removeDayUnitIfNeeded(String salary) {
        if (salary.contains("元/天")) {
            return salary.replaceAll("元/天", "");
        }
        return salary;
    }

    /**
     * 解析薪资范围
     */
    private Integer[] parseSalaryRange(String salaryText) {
        try {
            return Arrays.stream(salaryText.split("-"))
                    .map(s -> s.replaceAll("[^0-9]", ""))
                    .map(Integer::parseInt)
                    .toArray(Integer[]::new);
        } catch (Exception e) {
            log.debug("薪资解析失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 检查薪资是否超出范围
     */
    private boolean isSalaryOutOfRange(Integer[] jobSalary, Integer miniSalary, Integer maxSalary, String jobType) {
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

    private Integer getMinimumSalary(List<Integer> expectedSalary) {
        return expectedSalary != null && !expectedSalary.isEmpty() ? expectedSalary.get(0) : null;
    }

    private Integer getMaximumSalary(List<Integer> expectedSalary) {
        return expectedSalary != null && expectedSalary.size() > 1 ? expectedSalary.get(1) : null;
    }

    /**
     * 检查是否是不活跃HR
     */
    @Deprecated
    private boolean isDeadHR(Page page, BossConfigDTO config) {
        if (!config.getFilterDeadHR()) {
            return false;
        }
        try {
            Locator activeTimeElement = page.locator(HR_ACTIVE_TIME).nth(0);

            if (activeTimeElement.isVisible(new Locator.IsVisibleOptions().setTimeout(5000.0))) {
                String activeTimeText = activeTimeElement.textContent();
                String companyHR = getCompanyAndHR(page).replaceAll("\\s+", "");
                boolean isDeadHR = containsDeadStatus(activeTimeText, config.getDeadStatus());

                log.info("HR活跃状态：{}，文本：{}，isActive={}", companyHR, activeTimeText, !isDeadHR);
                return isDeadHR;
            }
        } catch (Exception e) {
            String companyHR = getCompanyAndHR(page).replaceAll("\\s+", "");
            log.debug("未找到{}的活跃状态，默认投递", companyHR);
        }
        return false;
    }

    /**
     * 检查是否包含不活跃状态
     */
    private boolean containsDeadStatus(String activeTimeText, List<String> deadStatus) {
        for (String status : deadStatus) {
            if (activeTimeText.contains(status)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 获取公司和HR信息
     */
    private String getCompanyAndHR(Page page) {
        try {
            Locator element = page.locator(RECRUITER_INFO).nth(0);
            if (element.isVisible(new Locator.IsVisibleOptions().setTimeout(2000.0))) {
                return element.textContent().replaceAll("\n", "");
            }
        } catch (Exception e) {
            log.debug("获取公司和HR信息失败: {}", e.getMessage());
        }
        return "未知公司和HR";
    }

    /**
     * 更新黑名单数据从聊天记录
     */
    private void updateBlacklistFromChat() {
        Page page = PlaywrightUtil.getPageObject();
        page.navigate(GEEK_CHAT_URL);
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
                                break;
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
                            PlaywrightUtil.sleep(1);
                        }
                    }

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
                Locator loadMoreElement = page.locator(SCROLL_LOAD_MORE);
                if (loadMoreElement.isVisible()) {
                    loadMoreElement.scrollIntoViewIfNeeded();
                    PlaywrightUtil.sleep(1);
                } else {
                    safeEvaluateJavaScript(page, "window.scrollTo(0, document.body.scrollHeight)");
                    PlaywrightUtil.sleep(1);
                }
            } catch (Exception e) {
                log.debug("聊天记录滚动加载完成");
                break;
            }
        }

        log.info("聊天记录分析：处理{}条，新增黑名单公司：{}，总黑名单公司：{}", processedItems, newBlackCompanies, blackCompanies.size());
    }

    /**
     * 自定义JSON格式化
     */
    private String customJsonFormat(Map<String, Set<String>> data) {
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

    /**
     * 检查Cookie是否有效
     */
    private boolean isCookieValid(String cookieData) {
        if (cookieData == null || cookieData.trim().isEmpty()) {
            return false;
        }
        try {
            return !cookieData.equals("[]") && cookieData.contains("name");
        } catch (Exception e) {
            log.error("Cookie数据验证出错", e);
            return false;
        }
    }

    /**
     * 检查是否需要登录
     */
    private boolean isLoginRequired() {
        try {
            Page page = PlaywrightUtil.getPageObject();

            Locator loginButton = page.locator(LOGIN_BTNS);
            if (loginButton.isVisible() && loginButton.textContent().contains("登录")) {
                return true;
            }

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
            log.error("登录状态检查出错", e);
            return true;
        }
    }

    /**
     * 扫码登录
     */
    @SneakyThrows
    private boolean scanLogin() {
        Page page = PlaywrightUtil.getPageObject();
        page.navigate(HOME_URL + "/web/user/?ka=header-login");
        PlaywrightUtil.sleep(5);

        try {
            Locator loginBtn = page.locator(LOGIN_BTN);
            if (loginBtn.isVisible() && !loginBtn.textContent().equals("登录")) {
                log.info("检测到已登录状态");
                return true;
            }
        } catch (Exception ignored) {
        }

        log.info("等待扫码登录");

        // Locator scanButton = page.locator(LOGIN_SCAN_SWITCH);
        // boolean scanButtonVisible = scanButton.isVisible(new
        // Locator.IsVisibleOptions().setTimeout(30000.0));
        // if (!scanButtonVisible) {
        // log.warn("扫码登录：未找到二维码登录按钮");
        // return false;
        // }

        boolean login = false;
        long startTime = System.currentTimeMillis();
        final long TIMEOUT = 10 * 60 * 1000; // 10分钟

        Scanner scanner = new Scanner(System.in);

        boolean loginSuccess = page.locator(LOGIN_SUCCESS_HEADER)
                .isVisible(new Locator.IsVisibleOptions().setTimeout(2000.0));

        while (!login) {
            long elapsed = System.currentTimeMillis() - startTime;
            if (elapsed >= TIMEOUT) {
                log.warn("扫码登录：超过10分钟未完成登录，程序退出");
                System.exit(1);
            }

            try {
                // scanButton.click();
                loginSuccess = page.locator(LOGIN_SUCCESS_HEADER)
                        .isVisible(new Locator.IsVisibleOptions().setTimeout(2000.0));

                if (loginSuccess) {
                    login = true;
                    log.info("登录成功，保存Cookie");
                } else {
                    boolean userInput = waitForUserInputOrTimeout(scanner);
                    if (userInput) {
                        log.debug("检测到用户输入，继续尝试登录");
                    }
                }
            } catch (Exception e) {
                loginSuccess = page.locator(LOGIN_SUCCESS_HEADER)
                        .isVisible(new Locator.IsVisibleOptions().setTimeout(2000.0));
                if (loginSuccess) {
                    login = true;
                    log.info("登录成功，保存Cookie");
                }
            }
        }

        saveCookieToConfig();
        return true;
    }

    /**
     * 等待用户输入或超时
     */
    private boolean waitForUserInputOrTimeout(Scanner scanner) {
        long end = System.currentTimeMillis() + 2000;
        while (System.currentTimeMillis() < end) {
            try {
                if (System.in.available() > 0) {
                    scanner.nextLine();
                    return true;
                }
            } catch (IOException e) {
                // 忽略异常
            }
            PlaywrightUtil.sleep(1);
        }
        return false;
    }

    /**
     * 从配置实体中获取Cookie数据
     */
    private String getCookieFromConfig() {
        try {
            ConfigEntity config = configService.load();
            return config != null ? config.getCookieData() : null;
        } catch (Exception e) {
            log.error("从配置中获取Cookie失败", e);
            return null;
        }
    }

    /**
     * 保存Cookie到配置实体
     */
    private void saveCookieToConfig() {
        try {
            ConfigEntity config = configService.load();
            if (config == null) {
                config = new ConfigEntity();
            }

            // 获取当前浏览器的Cookie
            String cookieJson = getCurrentCookiesAsJson();
            config.setCookieData(cookieJson);

            configService.save(config);
            log.info("Cookie已保存到配置实体");
        } catch (Exception e) {
            log.error("保存Cookie到配置失败", e);
        }
    }

    /**
     * 获取当前浏览器Cookie并转换为JSON字符串
     */
    private String getCurrentCookiesAsJson() {
        try {
            Page page = PlaywrightUtil.getPageObject();
            List<com.microsoft.playwright.options.Cookie> cookies = page.context().cookies();
            JSONArray jsonArray = new JSONArray();

            for (com.microsoft.playwright.options.Cookie cookie : cookies) {
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("name", cookie.name);
                jsonObject.put("value", cookie.value);
                jsonObject.put("domain", cookie.domain);
                jsonObject.put("path", cookie.path);
                if (cookie.expires != null) {
                    jsonObject.put("expires", cookie.expires);
                }
                jsonObject.put("secure", cookie.secure);
                jsonObject.put("httpOnly", cookie.httpOnly);
                jsonArray.put(jsonObject);
            }

            return jsonArray.toString();
        } catch (Exception e) {
            log.error("获取当前Cookie失败", e);
            return "[]";
        }
    }

    /**
     * 从JSON字符串加载Cookie到浏览器
     */
    private void loadCookiesFromString(String cookieData) {
        try {
            JSONArray jsonArray = new JSONArray(cookieData);
            List<com.microsoft.playwright.options.Cookie> cookies = new ArrayList<>();

            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject jsonObject = jsonArray.getJSONObject(i);

                com.microsoft.playwright.options.Cookie cookie = new com.microsoft.playwright.options.Cookie(
                        jsonObject.getString("name"),
                        jsonObject.getString("value"));

                if (!jsonObject.isNull("domain")) {
                    cookie.domain = jsonObject.getString("domain");
                }

                if (!jsonObject.isNull("path")) {
                    cookie.path = jsonObject.getString("path");
                }

                if (!jsonObject.isNull("expires")) {
                    cookie.expires = jsonObject.getDouble("expires");
                }

                if (!jsonObject.isNull("secure")) {
                    cookie.secure = jsonObject.getBoolean("secure");
                }

                if (!jsonObject.isNull("httpOnly")) {
                    cookie.httpOnly = jsonObject.getBoolean("httpOnly");
                }

                cookies.add(cookie);
            }

            PlaywrightUtil.getPageObject().context().addCookies(cookies);
            log.info("已从配置加载Cookie，共{}个", cookies.size());
        } catch (Exception e) {
            log.error("从配置加载Cookie失败", e);
        }
    }
}
