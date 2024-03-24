package boss;

import lombok.SneakyThrows;
import org.json.JSONObject;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.Job;
import utils.SeleniumUtil;
import utils.TelegramNotificationBot;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

import static utils.Constant.*;

/**
 * @author loks666
 * Boss直聘自动投递
 */
public class Boss {
    private static final Logger log = LoggerFactory.getLogger(Boss.class);
    static boolean EnableNotifications = false;
    static Integer page = 1;
    static String homeUrl = "https://www.zhipin.com";
    static String baseUrl = "https://www.zhipin.com/web/geek/job?query=%s&experience=%s&city=%s&page=%s";
    static List<String> blackCompanies;
    static List<String> blackRecruiters;
    static List<String> blackJobs;
    static List<Job> returnList = new ArrayList<>();
    static List<String> keywords = List.of("大模型工程师", "Java", "Python", "Golang");
    static String dataPath = "./src/main/java/boss/data.json";
    static String cookiePath = "./src/main/java/boss/cookie.json";
    static final int noJobMaxPages = 10; // 无岗位最大页数
    static int noJobPages;
    static int lastSize;

    static Map<String, String> experience = new HashMap<>() {
        {
            put("在校生", "108");
            put("应届生", "102");
            put("经验不限", "101");
            put("一年以内", "103");
            put("1-3年", "104");
            put("3-5年", "105");
            put("5-10年", "106");
            put("10年以上", "107");
        }
    };

    static Map<String, String> cityCode = new HashMap<>() {
        {
            put("全国", "100010000");
            put("北京", "101010100");
            put("上海", "101020100");
            put("广州", "101280100");
            put("深圳", "101280600");
            put("成都", "101270100");
        }
    };


    public static void main(String[] args) {
        loadData(dataPath);
        SeleniumUtil.initDriver();
        Date start = new Date();
        login();
        endSubmission:
        for (String keyword : keywords) {
            page = 1;
            noJobPages = 0;
            lastSize = -1;
            while (true) {
                log.info("投递【{}】关键词第【{}】页", keyword, page);
                String url = String.format(baseUrl, keyword, setYear(List.of()), cityCode.get("上海"), page);
                int startSize = returnList.size();
                Integer resultSize = resumeSubmission(url);
                if (resultSize == -1) {
                    log.info("今日沟通人数已达上限，请明天再试");
                    break endSubmission;
                } else {
                    if (startSize == resultSize) {
                        noJobPages++;
                        if (noJobPages >= noJobMaxPages) {
                            log.info("【{}】关键词已经连续【{}】页无岗位，结束该关键词的投递...", keyword, noJobPages);
                            break;
                        } else {
                            log.info("【{}】关键词第【{}】页无岗位,目前已连续【{}】页无岗位...", keyword, page, noJobPages);
                        }
                    } else {
                        lastSize = resultSize;
                        noJobPages = 0;
                    }
                    page++;
                }
            }
        }
        Date end = new Date();
        log.info(returnList.isEmpty() ? "未发起新的聊天..." : "新发起聊天公司如下:\n{}", returnList.stream().map(Object::toString).collect(Collectors.joining("\n")));
        long durationSeconds = (end.getTime() - start.getTime()) / 1000;
        long minutes = durationSeconds / 60;
        long seconds = durationSeconds % 60;
        String message = "共发起 " + returnList.size() + " 个聊天,用时" + minutes + "分" + seconds + "秒";
        if (EnableNotifications) {
            new TelegramNotificationBot().sendMessageWithList(message, returnList.stream().map(Job::toString).toList(), "Boss直聘投递");
        }
//        saveData(dataPath);
        log.info(message);
        CHROME_DRIVER.close();
        CHROME_DRIVER.quit();
    }

    private static String setYear(List<String> params) {
        if (params == null || params.isEmpty()) {
            return "";
        }
        return params.stream().map(experience::get).collect(Collectors.joining(","));
    }

    private static void saveData(String path) {
        try {
            updateListData();
            Map<String, List<String>> data = new HashMap<>();
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
        WAIT.until(ExpectedConditions.presenceOfElementLocated(By.xpath("//li[@role='listitem']")));
        SeleniumUtil.getWait(3);
        JavascriptExecutor js = CHROME_DRIVER;
        boolean shouldBreak = false;
        while (!shouldBreak) {
            try {
                WebElement bottom = CHROME_DRIVER.findElement(By.xpath("//div[@class='finished']"));
                if ("没有更多了".equals(bottom.getText())) {
                    shouldBreak = true;
                }
            } catch (Exception e) {
//                log.info("还未到底");
            }
            List<WebElement> items = CHROME_DRIVER.findElements(By.xpath("//li[@role='listitem']"));
            items.forEach(info -> {
                try {
                    WebElement companyElement = info.findElement(By.xpath(".//span[@class='name-box']//span[2]"));
                    String companyName = companyElement.getText();
                    WebElement messageElement = info.findElement(By.xpath(".//span[@class='last-msg-text']"));
                    String message = messageElement.getText();
                    boolean match = message.contains("不") || message.contains("感谢") || message.contains("但") || message.contains("遗憾") || message.contains("需要本") || message.contains("对不");
                    boolean nomatch = message.contains("不是") || message.contains("不生");
                    if (match && !nomatch) {
                        log.info("黑名单公司：【{}】，信息：【{}】", companyName, message);
                        if (blackCompanies.stream().anyMatch(companyName::contains)) {
                            return;
                        }
                        blackCompanies.add(companyName);
                    }
                } catch (Exception e) {
//                    log.error("元素没找到...");
                }
            });
            WebElement element = null;
            try {
                WAIT.until(ExpectedConditions.presenceOfElementLocated(By.xpath("//div[contains(text(), '滚动加载更多')]")));
                element = CHROME_DRIVER.findElement(By.xpath("//div[contains(text(), '滚动加载更多')]"));
            } catch (Exception e) {
                log.info("没找到滚动条...");
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


    private static String customJsonFormat(Map<String, List<String>> data) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        for (Map.Entry<String, List<String>> entry : data.entrySet()) {
            sb.append("    \"").append(entry.getKey()).append("\": ");
            sb.append(entry.getValue().stream().map(s -> "\"" + s + "\"").collect(Collectors.joining(", ", "[", "]")));
            sb.append(",\n");
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
        blackCompanies = jsonObject.getJSONArray("blackCompanies").toList().stream().map(Object::toString).collect(Collectors.toList());
        blackRecruiters = jsonObject.getJSONArray("blackRecruiters").toList().stream().map(Object::toString).collect(Collectors.toList());
        blackJobs = jsonObject.getJSONArray("blackJobs").toList().stream().map(Object::toString).collect(Collectors.toList());
    }

    @SneakyThrows
    private static Integer resumeSubmission(String url) {
        CHROME_DRIVER.get(url);
        WAIT.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("[class*='job-title clearfix']")));
        List<WebElement> jobCards = CHROME_DRIVER.findElements(By.cssSelector("li.job-card-wrapper"));
        List<Job> jobs = new ArrayList<>();
        for (WebElement jobCard : jobCards) {
            WebElement infoPublic = jobCard.findElement(By.cssSelector("div.info-public"));
            String recruiterText = infoPublic.getText();
            String recruiterName = infoPublic.findElement(By.cssSelector("em")).getText();
            if (blackRecruiters.stream().anyMatch(recruiterName::contains)) {
                // 排除黑名单招聘人员
                continue;
            }
            String jobName = jobCard.findElement(By.cssSelector("div.job-title span.job-name")).getText();
            if (blackJobs.stream().anyMatch(jobName::contains)) {
                // 排除黑名单岗位
                continue;
            }
            String companyName = jobCard.findElement(By.cssSelector("div.company-info h3.company-name")).getText();
            if (blackCompanies.stream().anyMatch(companyName::contains)) {
                // 排除黑名单公司
                continue;
            }
            Job job = new Job();
            job.setRecruiter(recruiterText.replace(recruiterName, "") + ":" + recruiterName);
            job.setHref(jobCard.findElement(By.cssSelector("a")).getAttribute("href"));
            job.setJobName(jobName);
            job.setJobArea(jobCard.findElement(By.cssSelector("div.job-title span.job-area")).getText());
            job.setSalary(jobCard.findElement(By.cssSelector("div.job-info span.salary")).getText());
            List<WebElement> tagElements = jobCard.findElements(By.cssSelector("div.job-info ul.tag-list li"));
            StringBuilder tag = new StringBuilder();
            for (WebElement tagElement : tagElements) {
                tag.append(tagElement.getText()).append("·");
            }
            job.setCompanyTag(tag.substring(0, tag.length() - 1)); // 删除最后一个 "·"
            jobs.add(job);
        }
        for (Job job : jobs) {
            // 打开新的标签页并打开链接
            JavascriptExecutor jse = CHROME_DRIVER;
            jse.executeScript("window.open(arguments[0], '_blank')", job.getHref());

            // 切换到新的标签页
            ArrayList<String> tabs = new ArrayList<>(CHROME_DRIVER.getWindowHandles());
            CHROME_DRIVER.switchTo().window(tabs.get(tabs.size() - 1));
            WAIT.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("[class*='btn btn-startchat']")));
            SeleniumUtil.sleep(1);
            WebElement btn = CHROME_DRIVER.findElement(By.cssSelector("[class*='btn btn-startchat']"));
            if ("立即沟通".equals(btn.getText())) {
                btn.click();
                if (isLimit()) {
                    SeleniumUtil.sleep(1);
                    return -1;
                }
                try {
                    WebElement input = WAIT.until(ExpectedConditions.presenceOfElementLocated(By.xpath("//div[@id='chat-input']")));
                    input.click();
                    SeleniumUtil.sleepByMilliSeconds(500);
                    try {
                        // 是否出现不匹配的对话框
                        WebElement element = CHROME_DRIVER.findElement(By.xpath("//div[@class='dialog-container']"));
                        if ("不匹配".equals(element.getText())) {
                            CHROME_DRIVER.close();
                            CHROME_DRIVER.switchTo().window(tabs.get(0));
                            continue;
                        }
                    } catch (Exception e) {
                        log.debug("岗位匹配，下一步发送消息...");
                    }
                    input.sendKeys(SAY_HI);
                    WebElement send = WAIT.until(ExpectedConditions.presenceOfElementLocated(By.xpath("//button[@type='send']")));
                    send.click();

                    WebElement recruiterNameElement = CHROME_DRIVER.findElement(By.xpath("//p[@class='base-info fl']/span[@class='name']"));
                    WebElement recruiterTitleElement = CHROME_DRIVER.findElement(By.xpath("//p[@class='base-info fl']/span[@class='base-title']"));
                    String recruiter = recruiterNameElement.getText() + " " + recruiterTitleElement.getText();

                    WebElement companyElement;
                    try {
                        companyElement = CHROME_DRIVER.findElement(By.xpath("//p[@class='base-info fl']/span[not(@class)]"));
                    } catch (Exception e) {
                        WAIT.until(ExpectedConditions.presenceOfElementLocated(By.xpath("//p[@class='base-info fl']/span[not(@class)]")));
                        companyElement = CHROME_DRIVER.findElement(By.xpath("//p[@class='base-info fl']/span[not(@class)]"));

                    }
                    String company = null;
                    if (companyElement != null) {
                        company = companyElement.getText();
                        job.setCompanyName(company);
                    }

                    WebElement positionNameElement = CHROME_DRIVER.findElement(By.xpath("//a[@class='position-content']/span[@class='position-name']"));
                    WebElement salaryElement = CHROME_DRIVER.findElement(By.xpath("//a[@class='position-content']/span[@class='salary']"));
                    WebElement cityElement = CHROME_DRIVER.findElement(By.xpath("//a[@class='position-content']/span[@class='city']"));
                    String position = positionNameElement.getText() + " " + salaryElement.getText() + " " + cityElement.getText();
                    log.info("投递【{}】公司，【{}】职位，招聘官:【{}】", company == null ? "未知公司: " + job.getHref() : company, position, recruiter);
                    returnList.add(job);
                    noJobPages = 0;
                } catch (Exception e) {
                    log.error("发送消息失败:{}", e.getMessage(), e);
                }
            }
            SeleniumUtil.sleep(1);
            CHROME_DRIVER.close();
            CHROME_DRIVER.switchTo().window(tabs.get(0));
        }
        return returnList.size();
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
        }

        if (isLoginRequired()) {
            log.error("cookie失效，尝试扫码登录...");
            scanLogin();
        }
    }


    private static boolean isLoginRequired() {
        try {
            String text = CHROME_DRIVER.findElement(By.className("btns")).getText();
            return text != null && text.contains("登录");
        } catch (Exception e) {
            log.info("cookie有效，已登录...");
            return false;
        }
    }

    @SneakyThrows
    private static void scanLogin() {
        CHROME_DRIVER.get(homeUrl + "/web/user/?ka=header-login");
        log.info("等待登陆..");
        WAIT.until(ExpectedConditions.presenceOfElementLocated(By.xpath("//a[@ka='header-home-logo']")));
        boolean login = false;
        while (!login) {
            try {
                WAIT.until(ExpectedConditions.presenceOfElementLocated(By.xpath("//*[@id=\"header\"]/div[1]/div[1]/a")));
                WAIT.until(ExpectedConditions.presenceOfElementLocated(By.xpath("//*[@id=\"wrap\"]/div[2]/div[1]/div/div[1]/a[2]")));
                login = true;
                log.info("登录成功！保存cookie...");
            } catch (Exception e) {
                log.error("登陆失败，两秒后重试...");
            } finally {
                SeleniumUtil.sleep(2);
            }
        }
        SeleniumUtil.saveCookie(cookiePath);
    }
}
