package boss;

import ai.AiConfig;
import ai.AiFilter;
import ai.AiService;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import lombok.SneakyThrows;
import org.json.JSONObject;
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
import java.util.*;
import java.util.stream.Collectors;
import java.util.Scanner;

import static boss.Locators.*;
import static utils.Bot.sendMessageByTime;
import static utils.JobUtils.formatDuration;

/**
 * @author loks666
 * 项目链接: <a href=
 * "https://github.com/loks666/get_jobs">https://github.com/loks666/get_jobs</a>
 * Boss直聘自动投递
 */
public class Boss {
    private static final Logger log = LoggerFactory.getLogger(Boss.class);
    static String homeUrl = "https://www.zhipin.com";
    static String baseUrl = "https://www.zhipin.com/web/geek/job?";
    static Set<String> blackCompanies;
    static Set<String> blackRecruiters;
    static Set<String> blackJobs;
    static List<Job> resultList = new ArrayList<>();
    static String dataPath = "src/main/java/boss/data.json";
    static String cookiePath = "src/main/java/boss/cookie.json";
    static Date startDate;
    static BossConfig config = BossConfig.init();

    static {
        try {
            // 检查dataPath文件是否存在，不存在则创建
            File dataFile = new File(dataPath);
            if (!dataFile.exists()) {
                // 确保父目录存在
                if (!dataFile.getParentFile().exists()) {
                    dataFile.getParentFile().mkdirs();
                }
                // 创建文件并写入初始JSON结构
                Map<String, Set<String>> initialData = new HashMap<>();
                initialData.put("blackCompanies", new HashSet<>());
                initialData.put("blackRecruiters", new HashSet<>());
                initialData.put("blackJobs", new HashSet<>());
                String initialJson = customJsonFormat(initialData);
                Files.write(Paths.get(dataPath), initialJson.getBytes());
                log.info("创建数据文件: {}", dataPath);
            }

            // 检查cookiePath文件是否存在，不存在则创建
            File cookieFile = new File(cookiePath);
            if (!cookieFile.exists()) {
                // 确保父目录存在
                if (!cookieFile.getParentFile().exists()) {
                    cookieFile.getParentFile().mkdirs();
                }
                // 创建空的cookie文件
                Files.write(Paths.get(cookiePath), "[]".getBytes());
                log.info("创建cookie文件: {}", cookiePath);
            }
        } catch (IOException e) {
            log.error("创建文件时发生异常: {}", e.getMessage());
        }
    }

    public static void main(String[] args) {
        loadData(dataPath);
        // 使用 PlayWright 获取岗位
        PlaywrightUtil.init();
        startDate = new Date();
        login();
        config.getCityCode().forEach(Boss::postJobByCity);
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
            PlaywrightUtil.close();
        }
    }

    private static void postJobByCity(String cityCode) {
        String searchUrl = getSearchUrl(cityCode);
        for (String keyword : config.getKeywords()) {
            int postCount = 0;
            // 使用 URLEncoder 对关键词进行编码
            String encodedKeyword = URLEncoder.encode(keyword, StandardCharsets.UTF_8);

            String url = searchUrl + "&query=" + encodedKeyword;
            log.info("投递地址:{}", searchUrl + "&query=" + keyword);
            com.microsoft.playwright.Page page = PlaywrightUtil.getPageObject();
            page.navigate(url);

            // 1. 滚动到底部，加载所有岗位卡片
            int lastCount = -1;
            while (true) {
                // 滑动到底部
                page.evaluate("window.scrollTo(0, document.body.scrollHeight);");
                PlaywrightUtil.sleep(1); // 等待加载（可根据速度调整）

                // 获取所有卡片数
                Locator cards = page.locator("//ul[contains(@class, 'rec-job-list')]//li[contains(@class, 'job-card-box')]");
                int currentCount = cards.count();

                // 判断是否继续滑动
                if (currentCount == lastCount) {
                    break; // 没有新内容，跳出循环
                }
                lastCount = currentCount;
            }
            log.info("【{}】岗位已全部加载，总数:{}", keyword, lastCount);

            // 2. 回到页面顶部
            page.evaluate("window.scrollTo(0, 0);");
            PlaywrightUtil.sleep(1);

            // 3. 逐个遍历所有岗位
            Locator cards = page.locator("//ul[contains(@class, 'rec-job-list')]//li[contains(@class, 'job-card-box')]");
            int count = cards.count();
            for (int i = 0; i < count; i++) {
                // 重新获取卡片，避免元素过期
                cards = page.locator("//ul[contains(@class, 'rec-job-list')]//li[contains(@class, 'job-card-box')]");
                cards.nth(i).click();
                PlaywrightUtil.sleep(1);

                // 等待详情内容加载
                page.waitForSelector("div[class*='job-detail-box']", new Page.WaitForSelectorOptions().setTimeout(4000));
                Locator detailBox = page.locator("div[class*='job-detail-box']");

                // 岗位名称
                String jobName = safeText(detailBox, "span[class*='job-name']");
                if (blackJobs.stream().anyMatch(jobName::contains)) continue;
                // 薪资(原始)
                String jobSalaryRaw = safeText(detailBox, "span.job-salary");
                String jobSalary = decodeSalary(jobSalaryRaw);
                // 城市/经验/学历
                List<String> tags = safeAllText(detailBox, "ul[class*='tag-list'] > li");
                // 标签 (暂时不使用)
                // List<String> jobLabels = safeAllText(detailBox, "ul[class*='job-label-list'] > li");
                // 岗位描述
                String jobDesc = safeText(detailBox, "p.desc");
                // Boss姓名、活跃
                String bossNameRaw = safeText(detailBox, "h2[class*='name']");
                String[] bossInfo = splitBossName(bossNameRaw);
                String bossName = bossInfo[0];
                String bossActive = bossInfo[1];
                if (config.getDeadStatus().stream().anyMatch(bossActive::contains)) continue;
                // Boss公司/职位
                String bossTitleRaw = safeText(detailBox, "div[class*='boss-info-attr']");
                String[] bossTitleInfo = splitBossTitle(bossTitleRaw);
                String bossCompany = bossTitleInfo[0];
                if (blackCompanies.stream().anyMatch(bossCompany::contains)) continue;
                String bossJobTitle = bossTitleInfo[1];
                if (blackRecruiters.stream().anyMatch(bossJobTitle::contains)) continue;

                // 创建Job对象
                Job job = new Job();
                job.setJobName(jobName);
                job.setSalary(jobSalary);
                job.setJobArea(String.join(", ", tags));
                job.setCompanyName(bossCompany);
                job.setRecruiter(bossName);
                job.setJobInfo(jobDesc);

                // 输出
//                log.info("正在投递：第{}条 | 岗位名称：{} | 薪资：{} | 城市/经验/学历：{} | Boss姓名：{} | 活跃状态：{} | 公司：{} | 职位：{}", (i + 1), jobName, jobSalary, tags, bossName, bossActive, bossCompany, bossJobTitle);
                resumeSubmission(page, keyword, job);
                postCount++;
            }
            log.info("【{}】岗位已投递完毕！已投递岗位数量:{}", keyword, postCount);
        }
    }

    public static String decodeSalary(String text) {
        Map<Character, Character> fontMap = new HashMap<>();
        fontMap.put('', '0');
        fontMap.put('', '1');
        fontMap.put('', '2');
        fontMap.put('', '3');
        fontMap.put('', '4');
        fontMap.put('', '5');
        fontMap.put('', '6');
        fontMap.put('', '7');
        fontMap.put('', '8');
        fontMap.put('', '9');
        StringBuilder result = new StringBuilder();
        for (char c : text.toCharArray()) {
            result.append(fontMap.getOrDefault(c, c));
        }
        return result.toString();
    }

    // 安全获取单个文本内容
    public static String safeText(Locator root, String selector) {
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
    public static List<String> safeAllText(Locator root, String selector) {
        try {
            return root.locator(selector).allInnerTexts();
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    // Boss姓名+活跃状态拆分
    public static String[] splitBossName(String raw) {
        String[] bossParts = raw.trim().split("\\s+");
        String bossName = bossParts[0];
        String bossActive = bossParts.length > 1 ? String.join(" ", Arrays.copyOfRange(bossParts, 1, bossParts.length)) : "";
        return new String[]{bossName, bossActive};
    }

    // Boss公司+职位拆分
    public static String[] splitBossTitle(String raw) {
        String[] parts = raw.trim().split(" · ");
        String company = parts[0];
        String job = parts.length > 1 ? parts[1] : "";
        return new String[]{company, job};
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
        com.microsoft.playwright.Page page = PlaywrightUtil.getPageObject();
        page.navigate("https://www.zhipin.com/web/geek/chat");
        PlaywrightUtil.sleep(3);

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

                    while (retryCount < 2) {
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
    private static void resumeSubmission(com.microsoft.playwright.Page page, String keyword, Job job) {
        PlaywrightUtil.sleep(1);

        // 1. 查找“查看更多信息”按钮（必须存在且新开页）
        Locator moreInfoBtn = page.locator("a.more-job-btn");
        if (moreInfoBtn.count() == 0) {
            log.warn("未找到“查看更多信息”按钮，跳过...");
            return;
        }
        // 强制用js新开tab
        String href = moreInfoBtn.first().getAttribute("href");
        if (href == null || !href.startsWith("/job_detail/")) {
            log.warn("未获取到岗位详情链接，跳过...");
            return;
        }
        String detailUrl = "https://www.zhipin.com" + href;

        // 2. 新开详情页
        com.microsoft.playwright.Page detailPage = page.context().newPage();
        detailPage.navigate(detailUrl);
        PlaywrightUtil.sleep(1); // 页面加载

        // 3. 查找“立即沟通”按钮
        Locator chatBtn = detailPage.locator("a.btn-startchat, a.op-btn-chat");
        boolean foundChatBtn = false;
        for (int i = 0; i < 5; i++) {
            if (chatBtn.count() > 0 && (chatBtn.first().textContent().contains("立即沟通"))) {
                foundChatBtn = true;
                break;
            }
            PlaywrightUtil.sleep(1);
        }
        if (!foundChatBtn) {
            log.warn("未找到立即沟通按钮，跳过岗位: {}", job.getJobName());
            detailPage.close();
            return;
        }
        chatBtn.first().click();
        PlaywrightUtil.sleep(1);

        // 4. 等待聊天输入框
        Locator inputLocator = detailPage.locator("div#chat-input.chat-input[contenteditable='true'], textarea.input-area");
        boolean inputReady = false;
        for (int i = 0; i < 10; i++) {
            if (inputLocator.count() > 0 && inputLocator.first().isVisible()) {
                inputReady = true;
                break;
            }
            PlaywrightUtil.sleep(1);
        }
        if (!inputReady) {
            log.warn("聊天输入框未出现，跳过: {}", job.getJobName());
            detailPage.close();
            return;
        }

        // 5. AI智能生成打招呼语
        AiFilter aiResult = null;
        if (config.getEnableAI()) {
            String jd = job.getJobInfo();
            if (jd != null && !jd.isEmpty()) {
                aiResult = checkJob(keyword, job.getJobName(), jd);
            }
        }
        String sayHi = config.getSayHi().replaceAll("[\\r\\n]", "");
        String message = (aiResult != null && aiResult.getResult() && isValidString(aiResult.getMessage()))
                ? aiResult.getMessage() : sayHi;

        // 6. 输入打招呼语
        Locator input = inputLocator.first();
        input.click();
        if (input.evaluate("el => el.tagName.toLowerCase()") instanceof String tag && tag.equals("textarea")) {
            input.fill(message);
        } else {
            input.evaluate("(el, msg) => el.innerText = msg", message);
        }

        // 7. 发送图片简历（可选）
        boolean imgResume = false;
        if (config.getSendImgResume()) {
            try {
                URL resourceUrl = Boss.class.getResource("/resume.jpg");
                if (resourceUrl != null) {
                    File imageFile = new File(resourceUrl.toURI());
                    Locator fileInput = detailPage.locator("//div[@aria-label='发送图片']//input[@type='file']");
                    if (fileInput.count() > 0) {
                        fileInput.setInputFiles(imageFile.toPath());
                        imgResume = true;
                    }
                }
            } catch (Exception e) {
                log.error("发送图片简历失败: {}", e.getMessage());
            }
        }

        // 8. 点击发送按钮（div.send-message 或 button.btn-send）
        Locator sendBtn = detailPage.locator("div.send-message, button[type='send'].btn-send, button.btn-send");
        boolean sendSuccess = false;
        if (sendBtn.count() > 0) {
            sendBtn.first().click();
            PlaywrightUtil.sleep(1);
            sendSuccess = true;
        } else {
            log.warn("未找到发送按钮，自动跳过！岗位：{}", job.getJobName());
        }

        log.info("投递完成 | 岗位：{} | 招呼语：{} | 图片简历：{}", job.getJobName(), message, imgResume ? "已发送" : "未发送");

        // 9. 关闭详情页，回到主页面
        detailPage.close();
        PlaywrightUtil.sleep(1);

        // 10. 成功投递加入结果
        if (sendSuccess) {
            resultList.add(job);
        }
    }

    public static boolean isValidString(String str) {
        return str != null && !str.isEmpty();
    }

    public static Boolean sendResume(String company) {
        log.warn("sendResume方法已废弃，请直接在主逻辑中使用playwright实现文件上传");
        return false;
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
            log.error("岗位薪资获取异常！薪资文本【{}】,异常信息【{}】", salary, e.getMessage(), e);
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
        PlaywrightUtil.sleep(JobUtils.getRandomNumberInRange(3, 20));
    }

    private static void simulateWait() {
        com.microsoft.playwright.Page page = PlaywrightUtil.getPageObject();
        for (int i = 0; i < 3; i++) {
            page.keyboard().press(" ");
            PlaywrightUtil.sleep(1);
        }
        page.keyboard().press("Control+Home");
        PlaywrightUtil.sleep(1);
    }

    private static boolean isDeadHR(com.microsoft.playwright.Page page) {
        if (!config.getFilterDeadHR()) {
            return false;
        }
        try {
            // 尝试获取 HR 的活跃时间
            Locator activeTimeLocator = page.locator(HR_ACTIVE_TIME);
            if (activeTimeLocator.count() > 0) {
                String activeTimeText = activeTimeLocator.textContent();
                log.info("{}：{}", getCompanyAndHR(page), activeTimeText);
                // 如果 HR 活跃状态符合预期，则返回 true
                return containsDeadStatus(activeTimeText, config.getDeadStatus());
            }
        } catch (Exception e) {
            log.info("没有找到【{}】的活跃状态, 默认此岗位将会投递...", getCompanyAndHR(page));
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

    private static String getCompanyAndHR(com.microsoft.playwright.Page page) {
        Locator recruiterLocator = page.locator(RECRUITER_INFO);
        if (recruiterLocator.count() > 0) {
            return recruiterLocator.textContent().replaceAll("\n", "");
        }
        return "未知公司和HR";
    }

    private static void closeWindow(ArrayList<String> tabs) {
        log.warn("closeWindow方法已废弃，请使用playwright的page.close()方法");
        // 该方法已废弃，在playwright中直接使用page.close()
    }

    private static AiFilter checkJob(String keyword, String jobName, String jd) {
        AiConfig aiConfig = AiConfig.init();
        String requestMessage = String.format(aiConfig.getPrompt(), aiConfig.getIntroduce(), keyword, jobName, jd,
                config.getSayHi());
        String result = AiService.sendRequest(requestMessage);
        return result.contains("false") ? new AiFilter(false) : new AiFilter(true, result);
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

    private static boolean isLimit(com.microsoft.playwright.Page page) {
        try {
            PlaywrightUtil.sleep(1);
            Locator dialogLocator = page.locator(DIALOG_CON);
            if (dialogLocator.count() > 0) {
                String text = dialogLocator.textContent();
                return text.contains("已达上限");
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    @SneakyThrows
    private static void login() {
        log.info("打开Boss直聘网站中...");

        com.microsoft.playwright.Page page = PlaywrightUtil.getPageObject();
        page.navigate(homeUrl);
        PlaywrightUtil.sleep(1);
        // 检查滑块验证
        waitForSliderVerify(page);

        if (PlaywrightUtil.isCookieValid(cookiePath)) {
            PlaywrightUtil.loadCookies(cookiePath);
            page.reload();
            PlaywrightUtil.sleep(1);
            waitForSliderVerify(page);
            // 启用反检测模式
            PlaywrightUtil.initStealth();
        }

        if (isLoginRequired()) {
            log.error("cookie失效，尝试扫码登录...");
            scanLogin();
        }
    }

    private static void waitForSliderVerify(com.microsoft.playwright.Page page) {
        String SLIDER_URL = "https://www.zhipin.com/web/user/safe/verify-slider";
        // 最多等待5分钟（防呆，防止死循环）
        long start = System.currentTimeMillis();
        while (true) {
            String url = page.url();
            if (url != null && url.startsWith(SLIDER_URL)) {
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


    private static boolean isLoginRequired() {
        try {
            com.microsoft.playwright.Page page = PlaywrightUtil.getPageObject();
            Locator buttonLocator = page.locator(LOGIN_BTNS);
            if (buttonLocator.count() > 0 && buttonLocator.textContent().contains("登录")) {
                return true;
            }
        } catch (Exception e) {
            try {
                com.microsoft.playwright.Page page = PlaywrightUtil.getPageObject();
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

    @SneakyThrows
    private static void scanLogin() {
        // 访问登录页面
        com.microsoft.playwright.Page page = PlaywrightUtil.getPageObject();
        page.navigate(homeUrl + "/web/user/?ka=header-login");
        PlaywrightUtil.sleep(1);

        // 1. 如果已经登录，则直接返回
        try {
            Locator loginBtnLocator = page.locator(LOGIN_BTN);
            if (loginBtnLocator.count() > 0 && !Objects.equals(loginBtnLocator.textContent(), "登录")) {
                log.info("已经登录，直接开始投递...");
                return;
            }
        } catch (Exception ignored) {
        }

        log.info("等待登录...");

        // 2. 定位二维码登录的切换按钮
        try {
            Locator scanButton = page.locator(LOGIN_SCAN_SWITCH);
            scanButton.click();

            // 3. 登录逻辑
            boolean login = false;

            // 4. 记录开始时间，用于判断10分钟超时
            long startTime = System.currentTimeMillis();
            final long TIMEOUT = 10 * 60 * 1000; // 10分钟

            while (!login) {
                // 判断是否超时
                long elapsed = System.currentTimeMillis() - startTime;
                if (elapsed >= TIMEOUT) {
                    log.error("超过10分钟未完成登录，程序退出...");
                    System.exit(1);
                }

                try {
                    // 判断页面上是否出现职位列表容器
                    Locator jobList = page.locator("div.job-list-container");
                    if (jobList.isVisible()) {
                        login = true;
                        log.info("用户已登录！");
                        // 登录成功，保存Cookie
                        PlaywrightUtil.saveCookies(cookiePath);
                        break;
                    }
                } catch (Exception e) {
                    log.error("检测元素时异常: {}", e.getMessage());
                }
                // 每2秒检查一次
                Thread.sleep(2000);
            }


        } catch (Exception e) {
            log.error("未找到二维码登录按钮，登录失败", e);
        }
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
