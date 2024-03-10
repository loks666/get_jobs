package boss;

import lombok.SneakyThrows;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.SeleniumUtil;
import utils.TelegramNotificationBot;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static utils.Constant.*;

/**
 * @author loks666
 * Boss直聘自动投递
 */
public class Boss {
    private static final Logger log = LoggerFactory.getLogger(Boss.class);
    static boolean EnableNotifications = false;
    static Integer page = 1;
    static Integer maxPage = 50;
    static String homeUrl = "https://www.zhipin.com";
    static String baseUrl = "https://www.zhipin.com/web/geek/job?query=%s&city=101020100&page=";
    static List<String> blackCompanies = List.of("复深蓝", "途虎", "哈啰", "得物", "睿服", "滴滴");
    static List<String> blackRecruiters = List.of("猎头");
    static List<String> blackJobs = List.of("外包", "外派");
    static String sayHi = "您好，我有7年的工作经验，有Java，Python，Golang，大模型的相关项目经验，希望应聘这个岗位，期待可以与您进一步沟通，谢谢！";
    static List<Job> returnList = new ArrayList<>();
    static String keyword = "Java";
    static String cookiePath = "./src/main/java/boss/cookie.json";


    public static void main(String[] args) {
        SeleniumUtil.initDriver();
        Date start = new Date();
        login();
        for (int i = page; i <= maxPage; i++) {
            log.info("第{}页", i);
            if (resumeSubmission(String.format(baseUrl, keyword) + i) == -1) {
                log.info("今日沟通人数已达上限，请明天再试");
                break;
            }
        }
        Date end = new Date();
        log.info(returnList.isEmpty() ? "未发起新的聊天..." : "新发起聊天公司如下:\n{}", returnList);
        long durationSeconds = (end.getTime() - start.getTime()) / 1000;
        long minutes = durationSeconds / 60;
        long seconds = durationSeconds % 60;
        String message = "共发起 " + returnList.size() + " 个聊天,用时" + minutes + "分" + seconds + "秒";
        log.info(message);
        if (EnableNotifications) {
            new TelegramNotificationBot().sendMessageWithList(message, returnList.stream().map(Job::toString).toList(), "Boss直聘投递");
        }
        CHROME_DRIVER.close();
        CHROME_DRIVER.quit();
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
            job.setTag(tag.substring(0, tag.length() - 1)); // 删除最后一个 "·"
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
            WebElement btn = CHROME_DRIVER.findElement(By.cssSelector("[class*='btn btn-startchat']"));
            if ("立即沟通".equals(btn.getText())) {
                btn.click();
                if (isLimit()) {
                    SeleniumUtil.sleep(1);
                    return -1;
                }
                try {
                    WebElement input = WAIT.until(ExpectedConditions.presenceOfElementLocated(By.xpath("//*[@id=\"chat-input\"]")));
                    input.click();
                    input.sendKeys(sayHi);
                    WebElement send = WAIT.until(ExpectedConditions.presenceOfElementLocated(By.xpath("//*[@id=\"container\"]/div/div/div[2]/div[3]/div/div[3]/button")));
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
                    TimeUnit.MILLISECONDS.sleep(1500);
                } catch (Exception e) {
                    log.error("发送消息失败:{}", e.getMessage(), e);
                }
            }
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
        WAIT.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("[class*='btn-sign-switch ewm-switch']"))).click();
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
