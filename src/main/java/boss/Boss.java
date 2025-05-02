package boss;

import ai.AiConfig;
import ai.AiFilter;
import ai.AiService;
import com.microsoft.playwright.ElementHandle;
import com.microsoft.playwright.Locator;
import lombok.SneakyThrows;
import org.json.JSONObject;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.devtools.DevTools;
import org.openqa.selenium.devtools.v135.page.Page;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.*;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

import static boss.BossElementLocators.*;
import static utils.Bot.sendMessageByTime;
import static utils.Constant.*;
import static utils.JobUtils.formatDuration;
import static utils.SeleniumUtil.*;

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
    static String dataPath = ProjectRootResolver.rootPath + "/src/main/java/boss/data.json";
    static String cookiePath = ProjectRootResolver.rootPath + "/src/main/java/boss/cookie.json";
    static Date startDate;
    static BossConfig config = BossConfig.init();

    public static void main(String[] args) {
        loadData(dataPath);
        // 暂时使用 PlayWright 获取岗位，后续直接复用原来逻辑，后期优化全面替换 selenium，全部改为PlayWright
        SeleniumUtil.initDriver();
        PlaywrightUtil.init();
        startDate = new Date();
        login();
        config.getCityCode().forEach(Boss::postJobByCityByPlaywright);
        log.info(resultList.isEmpty() ? "未发起新的聊天..." : "新发起聊天公司如下:\n{}",
                resultList.stream().map(Object::toString).collect(Collectors.joining("\n")));
        if (!config.getDebugger()) {
            printResult();
        }
    }

    private static void printResult() {
        String message = String.format("\nBoss投递完成，共发起%d个聊天，用时%s", resultList.size(),
                formatDuration(startDate, new Date()));
        log.info(message);
        sendMessageByTime(message);
        saveData(dataPath);
        resultList.clear();
        if (!config.getDebugger()) {
            CHROME_DRIVER.close();
            CHROME_DRIVER.quit();
        }
    }

    private static void postJobByCityByPlaywright(String cityCode) {
        String searchUrl = getSearchUrl(cityCode);
        for (String keyword : config.getKeywords()) {
            // 使用 URLEncoder 对关键词进行编码
            String encodedKeyword = URLEncoder.encode(keyword, StandardCharsets.UTF_8);

            String url = searchUrl + "&query=" + encodedKeyword;
            log.info("查询岗位链接:{}", url);
            com.microsoft.playwright.Page page = PlaywrightUtil.getPageObject();
            PlaywrightUtil.loadCookies(cookiePath);
            page.navigate(url);

            // 记录下拉前后的岗位数量
            int previousJobCount = 0;
            int currentJobCount = 0;
            int unchangedCount = 0;

            if (isJobsPresent()) {
                // 尝试滚动页面加载更多数据
                try {
                    // 获取岗位列表并下拉加载更多
                    log.info("开始获取岗位信息...");

                    while (unchangedCount < 2) {
                        // 获取所有岗位卡片
                        List<ElementHandle> jobCards = page.querySelectorAll("ul.rec-job-list li.job-card-box");
                        currentJobCount = jobCards.size();

                        System.out.println("当前已加载岗位数量: " + currentJobCount);

                        // 判断是否有新增岗位
                        if (currentJobCount > previousJobCount) {
                            previousJobCount = currentJobCount;
                            unchangedCount = 0;

                            // 滚动到页面底部加载更多
                            PlaywrightUtil.evaluate("window.scrollTo(0, document.body.scrollHeight)");
                            log.info("下拉页面加载更多...");

                            // 等待新内容加载
                            page.waitForTimeout(2000);
                        } else {
                            unchangedCount++;
                            if (unchangedCount < 2) {
                                System.out.println("下拉后岗位数量未增加，再次尝试...");
                                // 再次尝试滚动
                                page.evaluate("window.scrollTo(0, document.body.scrollHeight)");
                                page.waitForTimeout(2000);
                            } else {
                                break;
                            }
                        }
                    }

                    log.info("已获取所有可加载岗位，共计: " + currentJobCount + " 个");

                    log.info("继续滚动加载更多岗位");
                } catch (Exception e) {
                    log.error("滚动加载数据异常: {}", e.getMessage());
                    break;
                }
            }

            resumeSubmission(keyword);
        }
    }

    /**
     * selenium 默认暴露 navigator.webdriver，boss security-check.html
     * 页面在DOM加载后立即运行一系列检测脚本
     * 可能 在<head> 里就执行JS
     * 页面有多个连续重定向或iframe跳转
     * 注入的js没赶上运行时机，无法隐藏 navigator.webdriver 属性
     *
     * @param cityCode
     */
    @Deprecated
    private static void postJobByCity(String cityCode) {
        String searchUrl = getSearchUrl(cityCode);
        // WebDriverWait wait = new WebDriverWait(CHROME_DRIVER, 40);
        WebDriverWait wait = new WebDriverWait(CHROME_DRIVER, Duration.ofSeconds(40));
        for (String keyword : config.getKeywords()) {
            // 使用 URLEncoder 对关键词进行编码
            String encodedKeyword = URLEncoder.encode(keyword, StandardCharsets.UTF_8);

            String url = searchUrl + "&query=" + encodedKeyword;
            log.info("查询岗位链接:{}", url);

            DevTools devTools = CHROME_DRIVER.getDevTools();
            devTools.createSession();
            setDefaultHeaders(devTools);
            injectStealthJs(devTools);

            CHROME_DRIVER.get(url);
            while (true) {
                log.info("等待页面加载完成");

                // 确保页面加载完成
                wait.until(
                        ExpectedConditions.visibilityOfElementLocated(By.xpath("//div[@class='job-list-container']")));

                // 尝试滚动页面加载更多数据
                try {
                    JavascriptExecutor js = CHROME_DRIVER;
                    // 滚动到页面底部
                    // js.executeScript("window.scrollTo(0, document.body.scrollHeight)");
                    // 等待新内容加载
                    SeleniumUtil.sleep(5);

                    // 检查是否到底部了（是否有"没有更多了"的提示）
                    try {
                        WebElement bottomElement = CHROME_DRIVER.findElement(
                                By.xpath("//div[contains(text(), '没有更多了') or contains(@class, 'job-list-empty')]"));
                        if (bottomElement != null && bottomElement.isDisplayed()) {
                            log.info("已滚动到底部，没有更多数据");
                            break;
                        }
                    } catch (Exception e) {
                        // 未找到底部元素，继续滚动
                    }

                    // 加载一定数量的岗位后可以选择跳出循环
                    List<WebElement> jobCards = CHROME_DRIVER.findElements(By.className("job-card-box"));
                    for (WebElement jobCard : jobCards) {

                    }

                    log.info("继续滚动加载更多岗位");
                } catch (Exception e) {
                    log.error("滚动加载数据异常: {}", e.getMessage());
                    break;
                }
            }
        }
    }

    private static boolean isJobsPresent() {
        try {
            // 判断页面是否存在岗位的元素
            PlaywrightUtil.waitForElement(JOB_LIST_CONTAINER);
            return true;
        } catch (Exception e) {
            log.error("加载岗位区块失败:{}", e.getMessage());
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
        } catch (IOException e) {
            log.error("保存【{}】数据失败！", path);
        }
    }

    private static void updateListData() {
        CHROME_DRIVER.get("https://www.zhipin.com/web/geek/chat");
        SeleniumUtil.getWait(3);

        JavascriptExecutor js = CHROME_DRIVER;
        boolean shouldBreak = false;
        while (!shouldBreak) {
            try {
                WebElement bottom = CHROME_DRIVER.findElement(By.xpath("//div[@class='finished']"));
                if ("没有更多了".equals(bottom.getText())) {
                    shouldBreak = true;
                }
            } catch (Exception ignore) {
            }
            List<WebElement> items = CHROME_DRIVER.findElements(By.xpath("//li[@role='listitem']"));
            for (int i = 0; i < items.size(); i++) {
                try {
                    WebElement companyElement = CHROME_DRIVER
                            .findElements(By.xpath("//div[@class='title-box']/span[@class='name-box']//span[2]"))
                            .get(i);
                    WebElement messageElement = CHROME_DRIVER
                            .findElements(By.xpath("//div[@class='gray last-msg']/span[@class='last-msg-text']"))
                            .get(i);

                    String companyName = null;
                    String message = null;
                    int retryCount = 0;

                    while (retryCount < 2) {
                        try {
                            companyName = companyElement.getText();
                            message = messageElement.getText();
                            break; // 成功获取文本，跳出循环
                        } catch (org.openqa.selenium.StaleElementReferenceException e) {
                            retryCount++;
                            if (retryCount >= 2) {
                                log.info("尝试获取元素文本2次失败，放弃本次获取");
                                break;
                            }
                            log.info("页面元素已变更，正在重试第{}次获取元素文本...", retryCount);
                            // 重新获取元素
                            try {
                                companyElement = CHROME_DRIVER
                                        .findElements(
                                                By.xpath("//div[@class='title-box']/span[@class='name-box']//span[2]"))
                                        .get(i);
                                messageElement = CHROME_DRIVER
                                        .findElements(
                                                By.xpath("//div[@class='gray last-msg']/span[@class='last-msg-text']"))
                                        .get(i);
                                // 等待短暂时间后重试
                                SeleniumUtil.sleep(1);
                            } catch (Exception ex) {
                                log.info("重新获取元素失败，放弃本次获取");
                                break;
                            }
                        }
                    }

                    // 只有在成功获取文本的情况下才继续处理
                    if (companyName != null && message != null) {
                        boolean match = message.contains("不") || message.contains("感谢") || message.contains("但")
                                || message.contains("遗憾") || message.contains("需要本") || message.contains("对不");
                        boolean nomatch = message.contains("不是") || message.contains("不生");
                        if (match && !nomatch) {
                            log.info("黑名单公司：【{}】，信息：【{}】", companyName, message);
                            if (blackCompanies.stream().anyMatch(companyName::contains)) {
                                continue;
                            }
                            companyName = companyName.replaceAll("\\.{3}", "");
                            if (companyName.matches(".*(\\p{IsHan}{2,}|[a-zA-Z]{4,}).*")) {
                                blackCompanies.add(companyName);
                            }
                        }
                    }
                } catch (Exception e) {
                    log.error("寻找黑名单公司异常...", e);
                }
            }
            WebElement element;
            try {
                WAIT.until(ExpectedConditions.presenceOfElementLocated(By.xpath("//div[contains(text(), '滚动加载更多')]")));
                element = CHROME_DRIVER.findElement(By.xpath("//div[contains(text(), '滚动加载更多')]"));
            } catch (Exception e) {
                log.info("没找到滚动条...");
                break;
            }

            if (element != null) {
                try {
                    js.executeScript("arguments[0].scrollIntoView();", element);
                } catch (Exception e) {
                    log.error("滚动到元素出错", e);
                }
            } else {
                try {
                    js.executeScript("window.scrollTo(0, document.body.scrollHeight);");
                } catch (Exception e) {
                    log.error("滚动到页面底部出错", e);
                }
            }
        }
        log.info("黑名单公司数量：{}", blackCompanies.size());
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
        } catch (IOException e) {
            log.error("读取【{}】数据失败！", path);
        }
    }

    private static void parseJson(String json) {
        JSONObject jsonObject = new JSONObject(json);
        blackCompanies = jsonObject.getJSONArray("blackCompanies").toList().stream().map(Object::toString)
                .collect(Collectors.toSet());
        blackRecruiters = jsonObject.getJSONArray("blackRecruiters").toList().stream().map(Object::toString)
                .collect(Collectors.toSet());
        blackJobs = jsonObject.getJSONArray("blackJobs").toList().stream().map(Object::toString)
                .collect(Collectors.toSet());
    }

    @SneakyThrows
    private static Integer resumeSubmission(String keyword) {
        // 查找所有job卡片元素
        com.microsoft.playwright.Page page = PlaywrightUtil.getPageObject();
        // 使用page.locator方法获取所有匹配的元素
        Locator jobLocators = BossElementFinder.getPlaywrightLocator(page, BossElementLocators.JOB_CARD_BOX);
        // 获取元素总数
        int count = jobLocators.count();

        List<Job> jobs = new ArrayList<>();
        // 遍历所有找到的job卡片
        for (int i = 0; i < count; i++) {
            try {
                Locator jobCard = jobLocators.nth(i);
                String jobName = jobCard.locator(BossElementLocators.JOB_NAME).textContent();
                if (blackJobs.stream().anyMatch(jobName::contains) || !isTargetJob(keyword, jobName)) {
                    // 排除黑名单岗位
                    continue;
                }
                String companyName = jobCard.locator(BossElementLocators.COMPANY_NAME).textContent();
                if (blackCompanies.stream().anyMatch(companyName::contains)) {
                    // 排除黑名单公司
                    continue;
                }

                Job job = new Job();
                job.setHref(jobCard.locator(BossElementLocators.JOB_NAME).getAttribute("href"));
                job.setCompanyName(companyName);
                job.setJobName(jobName);
                job.setJobArea(jobCard.locator(BossElementLocators.JOB_AREA).textContent());
                // 获取标签列表
                Locator tagElements = jobCard.locator(BossElementLocators.TAG_LIST);
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
                jobs.add(job);
            } catch (Exception e) {
                log.debug("处理岗位卡片失败: {}", e.getMessage());
            }
        }

        for (Job job : jobs) {
            // 打开新的标签页
            ArrayList<String> tabs = BossPageOperations.openLinkInNewTab(job.getHref());

            try {
                // 等待聊天按钮出现
                Optional<WebElement> chatButton = BossElementFinder
                        .waitForElementVisible(BossElementLocators.CHAT_BUTTON);
                if (chatButton.isEmpty()) {
                    Optional<WebElement> errorElement = BossElementFinder
                            .findElement(BossElementLocators.ERROR_CONTENT);
                    if (errorElement.isPresent() && errorElement.get().getText().contains("异常访问")) {
                        return -2;
                    }
                }
            } catch (Exception e) {
                log.error("无法加载岗位详情页: {}", e.getMessage());
                BossPageOperations.closeCurrentTabAndSwitchTo(tabs, 0);
                continue;
            }

            // 过滤不活跃HR
            if (isDeadHR()) {
                BossPageOperations.closeCurrentTabAndSwitchTo(tabs, 0);
                log.info("该HR已过滤");
                SeleniumUtil.sleep(1);
                continue;
            }

            // 获取薪资
            try {
                Optional<WebElement> salaryElement = BossElementFinder
                        .findElement(BossElementLocators.JOB_DETAIL_SALARY);
                if (salaryElement.isPresent()) {
                    String salaryText = salaryElement.get().getText();
                    job.setSalary(salaryText);
                    if (isSalaryNotExpected(salaryText)) {
                        // 过滤薪资
                        log.info("已过滤:【{}】公司【{}】岗位薪资【{}】不符合投递要求", job.getCompanyName(), job.getJobName(), salaryText);
                        BossPageOperations.closeCurrentTabAndSwitchTo(tabs, 0);
                        continue;
                    }
                }
            } catch (Exception ignore) {
                log.info("获取岗位薪资失败:{}", ignore.getMessage());
            }

            // 获取招聘人员信息
            try {
                Optional<WebElement> recruiterElement = BossElementFinder
                        .findElement(BossElementLocators.RECRUITER_INFO);
                if (recruiterElement.isPresent()) {
                    String recruiterName = recruiterElement.get().getText();
                    job.setRecruiter(recruiterName);
                    if (blackRecruiters.stream().anyMatch(recruiterName::contains)) {
                        // 排除黑名单招聘人员
                        BossPageOperations.closeCurrentTabAndSwitchTo(tabs, 0);
                        continue;
                    }
                }
            } catch (Exception ignore) {
                log.info("获取招聘人员信息失败:{}", ignore.getMessage());
            }

            BossPageOperations.simulateUserBrowsing();
            Optional<WebElement> chatBtn = BossElementFinder.findElement(BossElementLocators.CHAT_BUTTON);

            boolean debug = config.getDebugger();

            // 休息下，请求太频繁了
            SeleniumUtil.sleep(5);
            if (!debug && chatBtn.isPresent() && "立即沟通".equals(chatBtn.get().getText())) {
                String waitTime = config.getWaitTime();
                int sleepTime = 10; // 默认等待10秒

                if (waitTime != null) {
                    try {
                        sleepTime = Integer.parseInt(waitTime);
                    } catch (NumberFormatException e) {
                        log.error("等待时间转换异常！！");
                    }
                }

                SeleniumUtil.sleep(sleepTime);

                AiFilter filterResult = null;
                if (config.getEnableAI()) {
                    // AI检测岗位是否匹配
                    Optional<WebElement> jdElement = BossElementFinder.findElement(BossElementLocators.JOB_DESCRIPTION);
                    if (jdElement.isPresent()) {
                        String jd = jdElement.get().getText();
                        filterResult = checkJob(keyword, job.getJobName(), jd);
                    }
                }

                chatBtn.get().click();

                if (isLimit()) {
                    SeleniumUtil.sleep(1);
                    return -1;
                }

                try {
                    try {
                        Optional<WebElement> dialogTitle = BossElementFinder
                                .findElement(BossElementLocators.DIALOG_TITLE);
                        if (dialogTitle.isPresent()) {
                            Optional<WebElement> closeBtn = BossElementFinder
                                    .findElement(BossElementLocators.DIALOG_CLOSE);
                            if (closeBtn.isPresent()) {
                                closeBtn.get().click();
                                chatBtn.get().click();
                            }
                        }
                    } catch (Exception ignore) {
                    }

                    Optional<WebElement> input = BossElementFinder
                            .waitForElementVisible(BossElementLocators.CHAT_INPUT);
                    if (input.isPresent()) {
                        input.get().click();
                        Optional<WebElement> dialogElement = BossElementFinder
                                .findElement(BossElementLocators.DIALOG_CONTAINER);
                        if (dialogElement.isPresent() && "不匹配".equals(dialogElement.get().getText())) {
                            BossPageOperations.closeCurrentTabAndSwitchTo(tabs, 0);
                            continue;
                        }

                        input.get().sendKeys(
                                filterResult != null && filterResult.getResult()
                                        && isValidString(filterResult.getMessage())
                                                ? filterResult.getMessage()
                                                : config.getSayHi());

                        Optional<WebElement> sendBtn = BossElementFinder
                                .waitForElementClickable(BossElementLocators.SEND_BUTTON);
                        if (sendBtn.isPresent()) {
                            sendBtn.get().click();
                            SeleniumUtil.sleep(5);

                            String recruiter = job.getRecruiter();
                            String company = job.getCompanyName();
                            String position = job.getJobName() + " " + job.getSalary() + " " + job.getJobArea();

                            // 发送简历图片
                            Boolean imgResume = false;
                            if (config.getSendImgResume()) {
                                try {
                                    // 从类路径加载 resume.jpg
                                    URL resourceUrl = Boss.class.getResource("/resume.jpg");
                                    if (resourceUrl != null) {
                                        File imageFile = new File(resourceUrl.toURI());
                                        imgResume = BossPageOperations.sendResumeImage(imageFile.getAbsolutePath());
                                    }
                                } catch (Exception e) {
                                    log.error("获取简历图片路径失败: {}", e.getMessage());
                                }
                            }

                            SeleniumUtil.sleep(2);
                            log.info("正在投递【{}】公司，【{}】职位，招聘官:【{}】{}", company, position, recruiter,
                                    imgResume ? "发送图片简历成功！" : "");
                            resultList.add(job);
                        }
                    }
                } catch (Exception e) {
                    log.error("发送消息失败:{}", e.getMessage(), e);
                }
            }

            if (!debug) {
                BossPageOperations.closeCurrentTabAndSwitchTo(tabs, 0);
            }
            if (debug) {
                break;
            }
        }
        return resultList.size();
    }

    public static boolean isValidString(String str) {
        return str != null && !str.isEmpty();
    }

    public static Boolean sendResume(String company) {
        // 如果 config.getSendImgResume() 为 true，再去找图片
        if (!config.getSendImgResume()) {
            return false;
        }

        try {
            // 从类路径加载 resume.jpg
            URL resourceUrl = Boss.class.getResource("/resume.jpg");
            if (resourceUrl == null) {
                log.error("在类路径下未找到 resume.jpg 文件！");
                return false;
            }

            // 将 URL 转为 File 对象
            File imageFile = new File(resourceUrl.toURI());
            log.info("简历图片路径：{}", imageFile.getAbsolutePath());

            if (!imageFile.exists()) {
                log.error("简历图片不存在！: {}", imageFile.getAbsolutePath());
                return false;
            }

            // 使用 XPath 定位 <input type="file"> 元素
            WebElement fileInput = CHROME_DRIVER
                    .findElement(By.xpath("//div[@aria-label='发送图片']//input[@type='file']"));

            // 上传图片
            fileInput.sendKeys(imageFile.getAbsolutePath());
            return true;
        } catch (Exception e) {
            log.error("发送简历图片时出错：{}", e.getMessage());
            return false;
        }
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
            log.error("岗位薪资获取异常！{}", e.getMessage(), e);
            // 出错时，您可根据业务需求决定返回 true 或 false
            // 这里假设出错时无法判断，视为不满足预期 => 返回 true
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

    private static void RandomWait() {
        SeleniumUtil.sleep(JobUtils.getRandomNumberInRange(3, 20));
    }

    private static void simulateWait() {
        for (int i = 0; i < 3; i++) {
            ACTIONS.sendKeys(" ").perform();
            SeleniumUtil.sleep(1);
        }
        ACTIONS.keyDown(Keys.CONTROL)
                .sendKeys(Keys.HOME)
                .keyUp(Keys.CONTROL)
                .perform();
        SeleniumUtil.sleep(1);
    }

    private static boolean isDeadHR() {
        if (!config.getFilterDeadHR()) {
            return false;
        }
        try {
            // 尝试获取 HR 的活跃时间
            Optional<WebElement> activeTimeElement = BossElementFinder.findElement(HR_ACTIVE_TIME);
            if (activeTimeElement.isPresent()) {
                String activeTimeText = activeTimeElement.get().getText();
                log.info("{}：{}", getCompanyAndHR(), activeTimeText);
                // 如果 HR 活跃状态符合预期，则返回 true
                return containsDeadStatus(activeTimeText, config.getDeadStatus());
            }
        } catch (Exception e) {
            log.info("没有找到【{}】的活跃状态, 默认此岗位将会投递...", getCompanyAndHR());
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

    private static String getCompanyAndHR() {
        Optional<WebElement> element = BossElementFinder.findElement(RECRUITER_INFO);
        return element.map(webElement -> webElement.getText().replaceAll("\n", "")).orElse("未知公司和HR");
    }

    private static void closeWindow(ArrayList<String> tabs) {
        SeleniumUtil.sleep(1);
        CHROME_DRIVER.close();
        CHROME_DRIVER.switchTo().window(tabs.get(0));
    }

    private static AiFilter checkJob(String keyword, String jobName, String jd) {
        AiConfig aiConfig = AiConfig.init();
        String requestMessage = String.format(aiConfig.getPrompt(), aiConfig.getIntroduce(), keyword, jobName, jd,
                config.getSayHi());
        String result = AiService.sendRequest(requestMessage);
        return result.contains("false") ? new AiFilter(false) : new AiFilter(true, result);
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
            log.error("薪资解析异常！{}", e.getMessage(), e);
        }
        return null;
    }

    private static boolean isLimit() {
        try {
            SeleniumUtil.sleep(1);
            String text = CHROME_DRIVER.findElement(By.className("dialog-con")).getText();
            return text.contains("已达上限");
        } catch (Exception e) {
            return false;
        }
    }

    @SneakyThrows
    private static void login() {
        log.info("打开Boss直聘网站中...");

        CHROME_DRIVER.get(homeUrl);
        if (SeleniumUtil.isCookieValid(cookiePath)) {
            SeleniumUtil.loadCookie(cookiePath);
            CHROME_DRIVER.navigate().refresh();
            SeleniumUtil.sleep(2);

            DevTools devTools = CHROME_DRIVER.getDevTools();
            devTools.createSession();
            // 注入脚本：隐藏 navigator.webdriver
            devTools.send(
                    Page.addScriptToEvaluateOnNewDocument(
                            "Object.defineProperty(navigator, 'injected', {get: () => 123})",
                            Optional.empty(), // worldName（可空）
                            Optional.of(false), // includeCommandLineAPI
                            Optional.of(true) // runImmediately
                    ));
        }
        if (isLoginRequired()) {
            log.error("cookie失效，尝试扫码登录...");
            scanLogin();
        }
    }

    private static boolean isLoginRequired() {
        try {
            Optional<WebElement> buttonElement = BossElementFinder.findElement("//div[@class='btns']");
            if (buttonElement.isPresent() && buttonElement.get().getText().contains("登录")) {
                return true;
            }
        } catch (Exception e) {
            try {
                BossElementFinder.findElement("//h1");
                BossElementFinder.findElement("//a[@ka='403_login']").ifPresent(WebElement::click);
                return true;
            } catch (Exception ex) {
                log.info("没有出现403访问异常");
            }
            log.info("cookie有效，已登录...");
            return false;
        }
        return false;
    }

    @SneakyThrows
    private static void scanLogin() {
        // 访问登录页面
        CHROME_DRIVER.get(homeUrl + "/web/user/?ka=header-login");
        SeleniumUtil.sleep(3);

        // 1. 如果已经登录，则直接返回
        try {
            Optional<WebElement> element = BossElementFinder.findElement(LOGIN_BTN);
            if (element.isPresent() && !Objects.equals(element.get().getText(), "登录")) {
                log.info("已经登录，直接开始投递...");
                return;
            }
        } catch (Exception ignored) {
        }

        log.info("等待登录...");

        // 2. 定位二维码登录的切换按钮
        Optional<WebElement> scanButton = BossElementFinder.waitForElementClickable(LOGIN_SCAN_SWITCH, 30);
        if (scanButton.isEmpty()) {
            log.error("未找到二维码登录按钮，登录失败");
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
                log.error("超过10分钟未完成登录，程序退出...");
                System.exit(1);
            }

            try {
                // 尝试点击二维码按钮并等待页面出现已登录的元素
                scanButton.get().click();
                BossElementFinder.waitForElementVisible(LOGIN_SUCCESS_HEADER, 2);
                BossElementFinder.waitForElementVisible(LOGIN_SUCCESS_INDICATOR, 2);

                // 如果上述元素都能找到，说明登录成功
                login = true;
                log.info("登录成功！保存cookie...");
            } catch (Exception e) {
                // 登录失败
                log.error("登录失败，等待用户操作或者 2 秒后重试...");

                // 每次登录失败后，等待2秒，同时检查用户是否按了回车
                boolean userInput = waitForUserInputOrTimeout(scanner);
                if (userInput) {
                    log.info("检测到用户输入，继续尝试登录...");
                }
            }
        }

        // 登录成功后，保存Cookie
        SeleniumUtil.saveCookie(cookiePath);
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
            SeleniumUtil.sleep(1);
        }
        return false;
    }

}
