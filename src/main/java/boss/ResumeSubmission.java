import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import utils.TelegramNotificationBot;

import java.io.*;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @author BeamStark
 * Boss直聘自动投递
 * @date 2023-05-01-04:16
 */
@Slf4j
public class ResumeSubmission {
    static boolean EnableNotifications = true;
    static Integer page = 1;
    static Integer maxPage = 50;
    static String loginUrl = "https://www.zhipin.com/web/user/?ka=header-login";
    static String baseUrl = "https://www.zhipin.com/web/geek/job?query=Java&city=101020100&page=";
    static String sayHi = "您好，我有7年的工作经验，有Java，Python，Golang，大模型的相关项目经验，希望应聘这个岗位，期待可以与您进一步沟通，谢谢！";
    static Actions actions;
    static ChromeDriver driver;
    static WebDriverWait wait15s;
    static List<String> returnList = new ArrayList<>();

    public static void main(String[] args) {
        initDriver();
        Date sdate = new Date();
        login();
        for (int i = page; i <= maxPage; i++) {
            log.info("第{}页", i);
            if (resumeSubmission(baseUrl + i) == -1) {
                log.info("今日沟通人数已达上限，请明天再试");
                break;
            }
        }
        Date edate = new Date();
        log.info("共投递{}个简历,用时{}分", returnList.size(),
                ((edate.getTime() - sdate.getTime()) / 1000) / 60);

        if (EnableNotifications) {
            String message = "共投递" + returnList.size() + "个简历,用时" + ((edate.getTime() - sdate.getTime()) / 1000) / 60 + "分";
            log.info("投递信息:{}", message);
            log.info("岗位信息:{}", returnList);
//            new TelegramNotificationBot().sendMessageWithList(message, listParameter, "Boss直聘投递");
        }
        driver.close();
    }

    private static void initDriver() {
        ChromeOptions options = new ChromeOptions();
        options.setBinary("C:/Program Files/Google/Chrome/Application/chrome.exe");
        System.setProperty("webdriver.chrome.driver", "./src/chromedriver.exe");
        options.addArguments("--window-position=2600,750"); // 将窗口移动到副屏的起始位置
        options.addArguments("--window-size=1600,1000"); // 设置窗口大小以适应副屏分辨率
        options.addArguments("--start-maximized"); // 最大化窗口
        driver = new ChromeDriver(options);
        actions = new Actions(driver);
        wait15s = new WebDriverWait(driver, 15000);
    }

    @SneakyThrows
    private static Integer resumeSubmission(String url) {
        driver.get(url);
        wait15s.until(ExpectedConditions.presenceOfElementLocated(
                By.cssSelector("[class*='job-title clearfix']")));
        List<WebElement> jobCards = driver.findElements(By.cssSelector("li.job-card-wrapper"));
        List<Job> jobs = new ArrayList<>();
        for (WebElement jobCard : jobCards) {
            WebElement infoPublic = jobCard.findElement(By.cssSelector("div.info-public"));
            String recruiterText = infoPublic.getText();
            String recruiterName = infoPublic.findElement(By.cssSelector("em")).getText();
            if (recruiterName.contains("猎头")) {
                continue;
            }
            Job job = new Job();
            job.setRecruiter(recruiterText.replace(recruiterName, "") + ":" + recruiterName);
            job.setHref(jobCard.findElement(By.cssSelector("a")).getAttribute("href"));
            job.setJobName(jobCard.findElement(By.cssSelector("div.job-title span.job-name")).getText());
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
            JavascriptExecutor jse = driver;
            jse.executeScript("window.open(arguments[0], '_blank')", job.getHref());

            // 切换到新的标签页
            ArrayList<String> tabs = new ArrayList<>(driver.getWindowHandles());
            driver.switchTo().window(tabs.get(tabs.size() - 1));

            wait15s.until(ExpectedConditions.presenceOfElementLocated(
                    By.cssSelector("[class*='btn btn-startchat']")));

            WebElement btn = driver.findElement(By.cssSelector("[class*='btn btn-startchat']"));

            if ("立即沟通".equals(btn.getText())) {
                btn.click();
                try {
                    WebElement input = wait15s.until(ExpectedConditions.presenceOfElementLocated(By.xpath("//*[@id=\"chat-input\"]")));
                    input.click();
                    input.sendKeys(sayHi);
                    WebElement send = wait15s.until(ExpectedConditions.presenceOfElementLocated(By.xpath("//*[@id=\"container\"]/div/div/div[2]/div[3]/div/div[3]/button")));
                    send.click();
//                String text = driver.findElement(By.className("dialog-con")).getText();
//                if (text.contains("已达上限")) {
//                    return -1;
//                }
                    WebElement recruiterNameElement = driver.findElement(By.xpath("//p[@class='base-info fl']/span[@class='name']"));
                    WebElement recruiterTitleElement = driver.findElement(By.xpath("//p[@class='base-info fl']/span[@class='base-title']"));
                    String recruiter = recruiterNameElement.getText() + " " + recruiterTitleElement.getText();

                    WebElement companyElement = driver.findElement(By.xpath("//p[@class='base-info fl']/span[not(@class)]"));
                    String company = companyElement.getText();

                    WebElement positionNameElement = driver.findElement(By.xpath("//a[@class='position-content']/span[@class='position-name']"));
                    WebElement salaryElement = driver.findElement(By.xpath("//a[@class='position-content']/span[@class='salary']"));
                    WebElement cityElement = driver.findElement(By.xpath("//a[@class='position-content']/span[@class='city']"));
                    String position = positionNameElement.getText() + " " + salaryElement.getText() + " " + cityElement.getText();
                    log.info("投递【{}】公司，【{}】职位，招聘官:【{}】", company, position, recruiter);
                    driver.close();
                    Thread.sleep(1500);
                } catch (Exception e) {
                    log.error("发送消息失败", e);
                }
            }
        }
        return returnList.size();
    }

    @SneakyThrows
    private static void login() {
        driver.get(loginUrl);
        wait15s.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("[class*='btn-sign-switch ewm-switch']"))).click();
        log.info("等待登陆..");
        boolean login = false;
        while (!login) {
            try {
                wait15s.until(ExpectedConditions.presenceOfElementLocated(By.xpath("//*[@id=\"header\"]/div[1]/div[1]/a")));
                wait15s.until(ExpectedConditions.presenceOfElementLocated(By.xpath("//*[@id=\"wrap\"]/div[2]/div[1]/div/div[1]/a[2]")));
                login = true;
                log.info("登录成功！执行下一步...");
            } catch (Exception e) {
                log.error("登陆失败，正在等待...");
            } finally {
                TimeUnit.SECONDS.sleep(2);
            }
        }
    }
}
