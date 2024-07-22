package job51;

import lombok.SneakyThrows;
import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.JobUtils;
import utils.SeleniumUtil;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static utils.Constant.*;

/**
 * 前程无忧自动投递简历
 *
 * @author loks666
 */
public class Job51 {
    private static final Logger log = LoggerFactory.getLogger(Job51.class);

    static Integer page = 1;
    static Integer maxPage = 50;
    static String cookiePath = "./src/main/java/job51/cookie.json";
    static String homeUrl = "https://www.51job.com";
    static String loginUrl = "https://login.51job.com/login.php?lang=c&url=https://www.51job.com/&qrlogin=2";
    static String baseUrl = "https://we.51job.com/pc/search?";
    static List<String> returnList = new ArrayList<>();
    static Job51Config config = Job51Config.init();

    public static void main(String[] args) {
        String searchUrl = getSearchUrl();
        SeleniumUtil.initDriver();
        Date sdate = new Date();
        Login();
        config.getKeywords().forEach(keyword -> resume(searchUrl + "&keyword=" + keyword));
        Date edate = new Date();
        log.info("共投递{}个简历,用时{}分", returnList.size(),
                ((edate.getTime() - sdate.getTime()) / 1000) / 60);
        try {
            TimeUnit.SECONDS.sleep(30);
        } catch (InterruptedException e) {
            log.error("投完简历休息期间出现异常:", e);
        } finally {
            CHROME_DRIVER.quit();
        }
    }

    private static String getSearchUrl() {
        return baseUrl +
                JobUtils.appendListParam("jobArea", config.getJobArea()) +
                JobUtils.appendListParam("salary", config.getSalary());
    }

    private static void Login() {
        CHROME_DRIVER.get(homeUrl);
        if (SeleniumUtil.isCookieValid(cookiePath)) {
            SeleniumUtil.loadCookie(cookiePath);
            CHROME_DRIVER.navigate().refresh();
            SeleniumUtil.sleep(1);
        }
        if (isLoginRequired()) {
            log.error("cookie失效，尝试扫码登录...");
            scanLogin();
        }
    }

    private static boolean isLoginRequired() {
        try {
            String text = CHROME_DRIVER.findElement(By.xpath("//p[@class=\"tit\"]")).getText();
            return text != null && text.contains("登录");
        } catch (Exception e) {
            log.info("cookie有效，已登录...");
            return false;
        }
    }

    @SneakyThrows
    private static void resume(String url) {
        CHROME_DRIVER.get(url);
        SeleniumUtil.sleep(1);

        // 再次判断是否登录
        WebElement login = WAIT.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//a[contains(@class, 'uname')]")));
        if (login != null && isNotNullOrEmpty(login.getText()) && login.getText().contains("登录")) {
            login.click();
            WAIT.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//i[contains(@class, 'passIcon')]"))).click();
            log.info("请扫码登录...");
            WAIT.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//div[contains(@class, 'joblist')]")));
            SeleniumUtil.saveCookie(cookiePath);
        }

        //由于51更新，每投递一页之前，停止10秒
        SeleniumUtil.sleep(10);

        int i = 0;
        try {
            CHROME_DRIVER.findElements(By.className("ss")).get(i).click();
        } catch (Exception e) {
            findAnomaly();
        }
        for (int j = page; j <= maxPage; j++) {
            while (true) {
                try {
                    WebElement mytxt = WAIT.until(ExpectedConditions.visibilityOfElementLocated(By.id("jump_page")));
                    SeleniumUtil.sleep(5);
                    mytxt.click();
                    mytxt.clear();
                    mytxt.sendKeys(String.valueOf(j));
                    WAIT.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("#app > div > div.post > div > div > div.j_result > div > div:nth-child(2) > div > div.bottom-page > div > div > span.jumpPage"))).click();
                    ACTIONS.keyDown(Keys.CONTROL).sendKeys(Keys.HOME).keyUp(Keys.CONTROL).perform();
                    log.info("第 {} 页", j);
                    break;
                } catch (Exception e) {
                    log.error("mytxt.clear()可能异常...");
                    SeleniumUtil.sleep(1);
                    findAnomaly();
                    CHROME_DRIVER.navigate().refresh();
                }
            }
            postCurrentJob();
        }
    }
    public static boolean isNullOrEmpty(String str) {
        return str == null || str.isBlank();
    }

    public static boolean isNotNullOrEmpty(String str) {
        return !isNullOrEmpty(str);
    }


    @SneakyThrows
    private static void postCurrentJob() {
        SeleniumUtil.sleep(1);
        // 选择所有岗位，批量投递
        List<WebElement> checkboxes = CHROME_DRIVER.findElements(By.cssSelector("div.ick"));
        if (checkboxes.isEmpty()) {
            return;
        }
        List<WebElement> titles = CHROME_DRIVER.findElements(By.cssSelector("[class*='jname text-cut']"));
        List<WebElement> companies = CHROME_DRIVER.findElements(By.cssSelector("[class*='cname text-cut']"));
        JavascriptExecutor executor = CHROME_DRIVER;
        for (int i = 0; i < checkboxes.size(); i++) {
            WebElement checkbox = checkboxes.get(i);
            executor.executeScript("arguments[0].click();", checkbox);
            String title = titles.get(i).getText();
            String company = companies.get(i).getText();
            returnList.add(company + " | " + title);
            log.info("选中:{} | {} 职位", company, title);
        }
        SeleniumUtil.sleep(1);
        ACTIONS.keyDown(Keys.CONTROL).sendKeys(Keys.HOME).keyUp(Keys.CONTROL).perform();
        boolean success = false;
        while (!success) {
            try {
                // 查询按钮是否存在
                WebElement parent = CHROME_DRIVER.findElement(By.cssSelector("div.tabs_in"));
                List<WebElement> button = parent.findElements(By.cssSelector("button.p_but"));
                // 如果按钮存在，则点击
                if (button != null && !button.isEmpty()) {
                    SeleniumUtil.sleep(1);
                    button.get(1).click();
                    success = true;
                }
            } catch (ElementClickInterceptedException e) {
                log.error("失败，1s后重试..");
                SeleniumUtil.sleep(1);
            }
        }

        try {
            SeleniumUtil.sleep(3);
            String text = CHROME_DRIVER.findElement(By.xpath("//div[@class='successContent']")).getText();
            if (text.contains("快来扫码下载~")) {
                //关闭弹窗
                CHROME_DRIVER.findElement(By.cssSelector("[class*='van-icon van-icon-cross van-popup__close-icon van-popup__close-icon--top-right']")).click();
            }
        } catch (Exception ignored) {
            log.info("未找到投递成功弹窗！可能为单独投递申请弹窗！");
        }
        String particularly = null;
        try {
            particularly = CHROME_DRIVER.findElement(By.xpath("//div[@class='el-dialog__body']/span")).getText();
        } catch (Exception ignored) {
        }
        if (particularly != null && particularly.contains("需要到企业招聘平台单独申请")) {
            //关闭弹窗
            CHROME_DRIVER.findElement(By.cssSelector("#app > div > div.post > div > div > div.j_result > div > div:nth-child(2) > div > div:nth-child(2) > div:nth-child(2) > div > div.el-dialog__header > button > i")).click();
            log.info("关闭单独投递申请弹窗成功！");
        }
    }

    private static void findAnomaly() {
        try {
            String verify = CHROME_DRIVER.findElement(By.cssSelector("#WAF_NC_WRAPPER > p.waf-nc-title")).getText();
            String limit = CHROME_DRIVER.findElement(By.xpath("//div[contains(@class, 'van-toast')]")).getText();
            if (verify.contains("访问验证") || limit.contains("投递太多")) {
                //关闭弹窗
                log.error("出现访问验证了！程序退出...");
                CHROME_DRIVER.close();
                CHROME_DRIVER.quit(); // 关闭之前的ChromeCHROME_DRIVER实例
                System.exit(-2);
            }

        } catch (Exception ignored) {
            log.info("未出现访问验证，继续运行...");
        }
    }

    private static void scanLogin() {
        log.info("等待扫码登陆..");
        CHROME_DRIVER.get(loginUrl);
        WAIT.until(ExpectedConditions.presenceOfElementLocated(By.id("hasresume")));
        SeleniumUtil.saveCookie(cookiePath);
    }

}
