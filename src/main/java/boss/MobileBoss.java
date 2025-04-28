package boss;

import ai.AiConfig;
import ai.AiFilter;
import ai.AiService;
import lombok.SneakyThrows;
import org.json.JSONObject;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;
import utils.Job;
import utils.JobUtils;
import utils.ProjectRootResolver;
import utils.SeleniumUtil;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

import static utils.Bot.sendMessageByTime;
import static utils.Constant.*;
import static utils.Constant.WAIT;
import static utils.JobUtils.formatDuration;

public class MobileBoss {
    private static final Logger log = LoggerFactory.getLogger(Boss.class);
    static String homeUrl = "https://www.zhipin.com";
    static String baseUrl = "https://www.zhipin.com";
    static Set<String> blackCompanies;
    static Set<String> blackRecruiters;
    static Set<String> blackJobs;
    static List<Job> resultList = new ArrayList<>();
    static List<String> deadStatus = List.of("2周内活跃","本月活跃","2月内活跃","半年前活跃");
    static String dataPath = ProjectRootResolver.rootPath + "/src/main/java/boss/data.json";
    static String cookiePath = ProjectRootResolver.rootPath + "/src/main/java/boss/cookie.json";
    static Date startDate;
    static MobileBossConfig config = MobileBossConfig.init();

    public static void main(String[] args) {
        loadData(dataPath);
        SeleniumUtil.initDriver();
        startDate = new Date();
        login();
        // 最好先填1个，多个城市的情况不确定会不会有什么问题或者导致请求过于频繁出现风险拦截
        config.getCityCode().forEach(MobileBoss::postJobByCity);
        log.info(resultList.isEmpty() ? "未发起新的聊天..." : "新发起聊天公司如下:\n{}", resultList.stream().map(Object::toString).collect(Collectors.joining("\n")));
        printResult();
        // 添加优雅的阻塞实现，避免程序自动退出
        log.info("程序执行完毕，等待手动终止...");
        Object lock = new Object();
        synchronized (lock) {
            try {
                lock.wait();
            } catch (InterruptedException e) {
                log.info("程序被中断");
            }
        }
    }

    private static void printResult() {
        String message = String.format("\nBoss投递完成，共发起%d个聊天，用时%s", resultList.size(), formatDuration(startDate, new Date()));
        log.info(message);
        sendMessageByTime(message);
        saveData(dataPath);
        resultList.clear();
        if(!config.getDebugger()){
            CHROME_DRIVER.close();
            CHROME_DRIVER.quit();
            MOBILE_CHROME_DRIVER.close();
            MOBILE_CHROME_DRIVER.quit();
        }
    }

    private static void postJobByCity(String cityCode) {

        for (String keyword : config.getKeywords()) {
            String searchUrl = getSearchUrl(cityCode,keyword);
            log.info("查询url:{}", searchUrl);
            WebDriverWait wait = new WebDriverWait(MOBILE_CHROME_DRIVER, 40);
            String url = searchUrl;
            log.info("开始投递，页面url：{}", url);
            MOBILE_CHROME_DRIVER.get(url);
            // 点击立即沟通，建立chat窗口
            if (isMobileJobsPresent(wait)) {
                JavascriptExecutor js = MOBILE_CHROME_DRIVER;

                int previousCount = 0;
                int retry = 0;
                // 向下滚动到底部
                while (true) {
                    // 当前页面中 class="item" 的 li 元素数量
                    List<WebElement> items = MOBILE_CHROME_DRIVER.findElements(By.cssSelector("li.item"));
                    int currentCount = items.size();

                    // 滚动到底部
                    // js.executeScript("window.scrollTo(0, document.body.scrollHeight);");
                    // js.executeScript("window.scrollTo(0, document.documentElement.scrollHeight)")
                    // 滚动到比页面高度更大的值，确保触发加载
                    js.executeScript("window.scrollTo(0, document.documentElement.scrollHeight + 100)");
                    SeleniumUtil.sleep(10); // 等待数据加载

                    // 检查数量是否变化
                    if (currentCount == previousCount) {
                        retry++;
                        log.info("第{}次下拉重试" + retry);
                        if (retry >= 2) {
                            log.info("尝试2次下拉后无新增岗位，退出");
                            break; // 连续两次未加载新数据，认为加载完毕
                        }
                    } else {
                        retry = 0; // 重置尝试次数
                    }

                    previousCount = currentCount;

                    if(config.getDebugger()){
                        break;
                    }

                }
                log.info("已加载全部岗位，总数量: " + previousCount);
            }

            // chat页面进行消息沟通
            resumeSubmission(config.getKeywords().getFirst());
        }

    }

    private static boolean isMobileJobsPresent(WebDriverWait wait) {
        try {
            // 判断页面是否存在岗位的元素
            WebElement jobList = wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath("//div[@class='job-list job-list-new']/ul")));
            List<WebElement> jobCards = jobList.findElements(By.className("item"));
            return !jobCards.isEmpty();
        } catch (Exception e) {
            log.error("未能找到岗位元素:{}", e.getMessage());
            return false;
        }
    }


    private static String getSearchUrl(String cityCode,String keyword) {
        // 经验
        List<String> experience = config.getExperience();
        // 学历
        List<String> degree = config.getDegree();
        // 薪资
        String salary = config.getSalary();
        // 规模
        List<String> scale = config.getScale();

        String searchUrl = baseUrl;

        log.info("cityCode:{}", cityCode);
        log.info("experience:{}", experience);
        log.info("degree:{}", degree);
        log.info("salary:{}", salary);
        if (!MobileBossEnum.CityCode.NULL.equals(cityCode)) {
            searchUrl = searchUrl + "/" + cityCode + "/";
        }

        Set<String> ydeSet = new LinkedHashSet<>();
        if(!experience.isEmpty()){
            if (!MobileBossEnum.Salary.NULL.equals(salary)) {
                ydeSet.add(salary);
            }
        }

        if(!degree.isEmpty()){
            String degreeStr = degree.stream().findFirst().get();
            if (!MobileBossEnum.Degree.NULL.equals(degreeStr)) {
                ydeSet.add(degreeStr);
            }
        }
        if(!experience.isEmpty()){
            String experienceStr = experience.stream().findFirst().get();
            if (!MobileBossEnum.Experience.NULL.equals(experienceStr)) {
                ydeSet.add(experienceStr);
            }
        }

        if(!scale.isEmpty()){
            String scaleStr = scale.stream().findFirst().get();
            if (!MobileBossEnum.Scale.NULL.equals(scaleStr)) {
                ydeSet.add(scaleStr);
            }
        }


        String yde = ydeSet.stream().collect(Collectors.joining("-"));
        log.info("yde:{}", yde);
        if (StringUtils.hasLength(yde)) {
            if (!searchUrl.endsWith("/")) {
                searchUrl = searchUrl + "/" + yde + "/";
            } else {
                searchUrl = searchUrl + yde + "/";
            }
        }

        searchUrl = searchUrl + "?query=" + keyword;
        searchUrl = searchUrl + "&ka=sel-salary-" + salary.split("_")[1];
        return searchUrl;
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
                    WebElement companyElement = CHROME_DRIVER.findElements(By.xpath("//div[@class='title-box']/span[@class='name-box']//span[2]")).get(i);
                    WebElement messageElement = CHROME_DRIVER.findElements(By.xpath("//div[@class='gray last-msg']/span[@class='last-msg-text']")).get(i);
                    
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
                                companyElement = CHROME_DRIVER.findElements(By.xpath("//div[@class='title-box']/span[@class='name-box']//span[2]")).get(i);
                                messageElement = CHROME_DRIVER.findElements(By.xpath("//div[@class='gray last-msg']/span[@class='last-msg-text']")).get(i);
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
                        boolean match = message.contains("不") || message.contains("感谢") || message.contains("但") || message.contains("遗憾") || message.contains("需要本") || message.contains("对不");
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
                    log.error("寻找黑名单公司异常...",e);
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
        blackCompanies = jsonObject.getJSONArray("blackCompanies").toList().stream().map(Object::toString).collect(Collectors.toSet());
        blackRecruiters = jsonObject.getJSONArray("blackRecruiters").toList().stream().map(Object::toString).collect(Collectors.toSet());
        blackJobs = jsonObject.getJSONArray("blackJobs").toList().stream().map(Object::toString).collect(Collectors.toSet());
    }


    @SneakyThrows
    private static Integer resumeSubmission(String keyword) {
        List<WebElement> jobCards = MOBILE_CHROME_DRIVER.findElements(By.cssSelector("ul li.item"));
        List<Job> jobs = new ArrayList<>();
        for (WebElement jobCard : jobCards) {
            // 获取完整HTML
            String outerHtml = jobCard.getAttribute("outerHTML");
            // 获取招聘者信息
            WebElement recruiterElement = jobCard.findElement(By.cssSelector("div.recruiter div.name"));
            String recruiterText = recruiterElement.getText();

            String salary = jobCard.findElement(By.cssSelector("div.title span.salary")).getText();

            if (blackRecruiters.stream().anyMatch(recruiterText::contains)) {
                // 排除黑名单招聘人员
                continue;
            }
            String jobName = jobCard.findElement(By.cssSelector("div.title span.title-text")).getText();
            if (blackJobs.stream().anyMatch(jobName::contains) || !isTargetJob(keyword, jobName)) {
                // 排除黑名单岗位
                continue;
            }
            String companyName = jobCard.findElement(By.cssSelector("div.name span.company")).getText();
            if (blackCompanies.stream().anyMatch(companyName::contains)) {
                // 排除黑名单公司
                continue;
            }
            if (isSalaryNotExpected(salary)) {
                // 过滤薪资
                log.info("已过滤:【{}】公司【{}】岗位薪资【{}】不符合投递要求", companyName, jobName, salary);
                continue;
            }

            if(!jobName.toLowerCase().contains(keyword.toLowerCase())){
                log.info("已过滤：岗位【{}】名称不包含关键字【{}】",jobName,keyword);
                continue;
            }
            Job job = new Job();
            // 获取职位链接
            job.setHref(jobCard.findElement(By.cssSelector("a")).getAttribute("href"));
            // 获取职位名称
            job.setJobName(jobName);
            // 获取工作地点
            job.setJobArea(jobCard.findElement(By.cssSelector("div.name span.workplace")).getText());
            // 获取薪资
            job.setSalary(salary);
            // 获取标签
            List<WebElement> tagElements = jobCard.findElements(By.cssSelector("div.labels span"));
            StringBuilder tag = new StringBuilder();
            for (WebElement tagElement : tagElements) {
                tag.append(tagElement.getText()).append("·");
            }
            if (tag.length() > 0) {
                job.setCompanyTag(tag.substring(0, tag.length() - 1));
            } else {
                job.setCompanyTag("");
            }
            // 获取公司名称
            job.setCompanyName(companyName);
            // 设置招聘者信息
            job.setRecruiter(recruiterText);
            jobs.add(job);
        }

        for (Job job : jobs) {
            // 打开新的标签页
            JavascriptExecutor jse = CHROME_DRIVER;
            // jse.executeScript("window.open(arguments[0], '_blank')", job.getHref());
            // 使用JavaScript控制焦点，避免了每次打开新页签时浏览器窗口自动切换到前台的问题。
            jse.executeScript("var newTab = window.open(arguments[0], '_blank'); newTab.blur(); window.focus();", job.getHref());
            // 切换到新的标签页
            ArrayList<String> tabs = new ArrayList<>(CHROME_DRIVER.getWindowHandles());
            CHROME_DRIVER.switchTo().window(tabs.getLast());
            try {
                // 等待聊天按钮出现
                WAIT.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("[class*='btn btn-startchat']")));
            } catch (Exception e) {
                Optional<WebElement> element = SeleniumUtil.findElement("//div[@class='error-content']", "");
                if (element.isPresent() && element.get().getText().contains("异常访问")) {
                    return -2;
                }
            }
            //过滤不活跃HR
            if (isDeadHR()) {
                closeWindow(tabs);
                log.info("该HR已过滤");
                SeleniumUtil.sleep(1);
                continue;
            }
            simulateWait();
            WebElement btn = null;
            try{
                btn = CHROME_DRIVER.findElement(By.cssSelector("[class*='btn btn-startchat']"));
            }catch (Exception e){
                log.info("没有获取到立即沟通按钮");
            }

            boolean debug = config.getDebugger();

            // 休息下，请求太频繁了
            SeleniumUtil.sleep(5);
            if (!debug&&Objects.nonNull(btn)&&"立即沟通".equals(btn.getText())) {
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
                    String jd = CHROME_DRIVER.findElement(By.xpath("//div[@class='job-sec-text']")).getText();
                    filterResult = checkJob(keyword, job.getJobName(), jd);
                }
                btn.click();
                if (isLimit()) {
                    SeleniumUtil.sleep(1);
                    return -1;
                }
                try {
                    try {
                        CHROME_DRIVER.findElement(By.xpath("//div[@class='dialog-title']"));
                        WebElement close = CHROME_DRIVER.findElement(By.xpath("//i[@class='icon-close']"));
                        close.click();
                        btn.click();
                    } catch (Exception ignore) {
                    }
                    WebElement input = WAIT.until(ExpectedConditions.presenceOfElementLocated(By.xpath("//div[@id='chat-input']")));
                    input.click();
                    WebElement element = CHROME_DRIVER.findElement(By.xpath("//div[@class='dialog-container']"));
                    if ("不匹配".equals(element.getText())) {
                        CHROME_DRIVER.close();
                        CHROME_DRIVER.switchTo().window(tabs.getFirst());
                        continue;
                    }
                    input.sendKeys(filterResult != null && filterResult.getResult() && isValidString(filterResult.getMessage()) ? filterResult.getMessage() : config.getSayHi());
                    WebElement send = WAIT.until(ExpectedConditions.presenceOfElementLocated(By.xpath("//button[@type='send']")));
                    send.click();
                    SeleniumUtil.sleep(5);
                    // 没必要通过聊天窗口再获取了 以名称为例 可能出现 //div[@class='base-info']//span[@class='name-text'] 的情况，boss界面不是固定的结构
//                    WebElement recruiterNameElement = CHROME_DRIVER.findElement(By.xpath("//div[@class='base-info']//span[@class='name-text']"));
//                    WebElement recruiterTitleElement = CHROME_DRIVER.findElement(By.xpath("//div[@class='base-info']/span[@class='base-title']"));
//                    String recruiter = recruiterNameElement.getText() + " " + recruiterTitleElement.getText();
                    String recruiter = job.getRecruiter();

                    String company = job.getCompanyName();

//                    WebElement positionNameElement = CHROME_DRIVER.findElement(By.xpath("//div[@class='position-main']//span[@class='position-name']"));
//                    WebElement salaryElement = CHROME_DRIVER.findElement(By.xpath("//div[@class='position-main']//span[@class='salary']"));
//                    WebElement cityElement = CHROME_DRIVER.findElement(By.xpath("//div[@class='position-main']//span[@class='city']"));
//
                    String position = job.getJobName() + " " + job.getSalary() + " " + job.getJobArea();
                    Boolean imgResume = sendResume(company);
                    SeleniumUtil.sleep(2);
                    log.info("正在投递【{}】公司，【{}】职位，招聘官:【{}】{}", company, position, recruiter, imgResume ? "发送图片简历成功！" : "");
                    resultList.add(job);
                } catch (Exception e) {
                    log.error("发送消息失败:{}", e.getMessage(), e);
                }
            }
            closeWindow(tabs);
            if(debug){
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
            WebElement fileInput = CHROME_DRIVER.findElement(By.xpath("//div[@aria-label='发送图片']//input[@type='file']"));

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
     * true 不符合预期
     * false 符合预期
     * 期望的最低薪资如果比岗位最高薪资还小，则不符合（薪资给的太少）
     * 期望的最高薪资如果比岗位最低薪资还小，则不符合(要求太高满足不了)
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
        return salaryText.contains("K") || salaryText.contains("k");
    }

    private static String cleanSalaryText(String salaryText) {
        salaryText = salaryText.replace("K", "").replace("k", "");
        int dotIndex = salaryText.indexOf('·');
        if (dotIndex != -1) {
            salaryText = salaryText.substring(0, dotIndex);
        }
        return salaryText;
    }

    private static boolean isSalaryOutOfRange(Integer[] jobSalary, Integer miniSalary, Integer maxSalary, String jobType) {
        if (jobSalary == null) {
            return true;
        }
        if (miniSalary == null) {
            return false;
        }
        if (Objects.equals("day", jobType)) {
            // 期望薪资转为平均每日的工资
            maxSalary = BigDecimal.valueOf(maxSalary).multiply(BigDecimal.valueOf(1000)).divide(BigDecimal.valueOf(21.75), 0, RoundingMode.HALF_UP).intValue();
            miniSalary = BigDecimal.valueOf(miniSalary).multiply(BigDecimal.valueOf(1000)).divide(BigDecimal.valueOf(21.75), 0, RoundingMode.HALF_UP).intValue();
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
            MOBILE_ACTIONS.sendKeys(" ").perform();
            SeleniumUtil.sleep(1);
        }
        ACTIONS.keyDown(Keys.CONTROL)
                .sendKeys(Keys.HOME)
                .keyUp(Keys.CONTROL)
                .perform();
        MOBILE_ACTIONS.keyDown(Keys.CONTROL)
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
            String activeTimeText = CHROME_DRIVER.findElement(By.xpath("//span[@class='boss-active-time']")).getText();
            log.info("{}：{}", getCompanyAndHR(), activeTimeText);
            // 如果 HR 活跃状态符合预期，则返回 true
            return containsDeadStatus(activeTimeText, config.getDeadStatus());
        } catch (Exception e) {
            log.info("没有找到【{}】的活跃状态, 默认此岗位将会投递...", getCompanyAndHR());
            return false;
        }
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
        try {
            return CHROME_DRIVER.findElement(By.xpath("//div[@class='boss-info-attr']")).getText().replaceAll("\n", "");
        } catch (Exception e) {
            log.info("未能获取公司和HR信息");
            return "";
        }
    }

    private static void closeWindow(ArrayList<String> tabs) {
        SeleniumUtil.sleep(1);
        CHROME_DRIVER.close();
        CHROME_DRIVER.switchTo().window(tabs.get(0));
    }

    private static AiFilter checkJob(String keyword, String jobName, String jd) {
        AiConfig aiConfig = AiConfig.init();
        String requestMessage = String.format(aiConfig.getPrompt(), aiConfig.getIntroduce(), keyword, jobName, jd, config.getSayHi());
        String result = AiService.sendRequest(requestMessage);
        return result.contains("false") ? new AiFilter(false) : new AiFilter(true, result);
    }

    private static boolean isTargetJob(String keyword, String jobName) {
        boolean keywordIsAI = false;
        for (String target : new String[]{"大模型", "AI"}) {
            if (keyword.contains(target)) {
                keywordIsAI = true;
                break;
            }
        }

        boolean jobIsDesign = false;
        for (String designOrVision : new String[]{"设计", "视觉", "产品", "运营"}) {
            if (jobName.contains(designOrVision)) {
                jobIsDesign = true;
                break;
            }
        }

        boolean jobIsAI = false;
        for (String target : new String[]{"AI", "人工智能", "大模型", "生成"}) {
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
            return Arrays.stream(salaryText.split("-")).map(s -> s.replaceAll("[^0-9]", ""))  // 去除非数字字符
                    .map(Integer::parseInt)               // 转换为Integer
                    .toArray(Integer[]::new);             // 转换为Integer数组
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
        // 避免首次加载请求过于频繁
        SeleniumUtil.sleep(10);
        MOBILE_CHROME_DRIVER.get(homeUrl);
        if (SeleniumUtil.isCookieValid(cookiePath)) {
            SeleniumUtil.loadCookie(cookiePath);
            CHROME_DRIVER.navigate().refresh();
            MOBILE_CHROME_DRIVER.navigate().refresh();
            SeleniumUtil.sleep(2);
        }
        if (isLoginRequired()) {
            log.error("cookie失效，尝试扫码登录...");
            scanLogin();
            SeleniumUtil.loadCookie(cookiePath);
            MOBILE_CHROME_DRIVER.navigate().refresh();
            SeleniumUtil.sleep(2);
        }
    }


    private static boolean isLoginRequired() {
        try {
            String text = CHROME_DRIVER.findElement(By.className("btns")).getText();
            return text != null && text.contains("登录");
        } catch (Exception e) {
            try {
                CHROME_DRIVER.findElement(By.xpath("//h1")).getText();
                CHROME_DRIVER.findElement(By.xpath("//a[@ka='403_login']")).click();
                return true;
            } catch (Exception ex) {
                log.info("没有出现403访问异常");
            }
            log.info("cookie有效，已登录...");
            return false;
        }
    }

    @SneakyThrows
    private static void scanLogin() {
        log.info("访问登录页面:{}", homeUrl + "/web/user/?ka=header-login");
        // 访问登录页面
        CHROME_DRIVER.get(homeUrl + "/web/user/?ka=header-login");
        SeleniumUtil.sleep(3);

        // 1. 如果已经登录，则直接返回
        try {
            String text = CHROME_DRIVER.findElement(By.xpath("//li[@class='nav-figure']")).getText();
            if (!Objects.equals(text, "登录")) {
                log.info("已经登录，直接开始投递...");
                return;
            }
        } catch (Exception ignored) {
        }

        log.info("等待登录...");

        // 2. 定位二维码登录的切换按钮
        WebElement app = WAIT.until(ExpectedConditions.presenceOfElementLocated(
                By.xpath("//div[@class='btn-sign-switch ewm-switch']")));

        // 3. 登录逻辑
        boolean login = false;

        // 4. 记录开始时间，用于判断10分钟超时
        long startTime = System.currentTimeMillis();
        final long TIMEOUT = 10 * 60 * 1000;  // 10分钟

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
                app.click();
                WAIT.until(ExpectedConditions.presenceOfElementLocated(
                        By.xpath("//*[@id=\"header\"]/div[1]/div[1]/a")));
                WAIT.until(ExpectedConditions.presenceOfElementLocated(
                        By.xpath("//*[@id=\"wrap\"]/div[2]/div[1]/div/div[1]/a[2]")));

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
