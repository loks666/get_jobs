package job51;

import lombok.SneakyThrows;
import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.SeleniumUtil;

import java.util.*;
import java.util.concurrent.TimeUnit;

import static utils.Constant.*;

/**
 * 前程无忧自动投递简历
 *
 * @author loks666
 */
public class Job51 {
    private static final Logger log = LoggerFactory.getLogger(Job51.class);

    static boolean EnableNotifications = true;
    static Integer page = 1;
    static Integer maxPage = 50;
    static String cookiePath = "./src/main/java/job51/cookie.json";
    static String homeUrl = "https://www.51job.com";
    static String loginUrl = "https://login.51job.com/login.php?lang=c&url=http%3A%2F%2Fwww.51job.com%2F&qrlogin=2";
    static String baseUrl = "https://we.51job.com/pc/search?keyword=%s&jobArea=020000&searchType=2&sortType=0&metro=";
    static Map<Integer, String> jobs = new HashMap<>() {{
        put(0, "综合排序");
        put(1, "活跃职位优先");
        put(2, "最新优先");
        put(3, "薪资优先");
        put(4, "距离优先");
    }};
    static List<String> returnList = new ArrayList<>();
    static Map<Integer, String> keywords = new HashMap<>() {{
        put(0, "java");
        put(1, "python");
        put(2, "go");
        put(3, "golang");
        put(4, "大模型");
        put(5, "软件工程师");
    }};

    public static void main(String[] args) {
        SeleniumUtil.initDriver();
        Date sdate = new Date();
        Login();

        resume(String.format(baseUrl, keywords.get(0)));
        Date edate = new Date();
        log.info("共投递{}个简历,用时{}分", returnList.size(),
                ((edate.getTime() - sdate.getTime()) / 1000) / 60);
        if (EnableNotifications) {
            String message = "共投递" + returnList.size() + "个简历," +
                    "用时" + ((edate.getTime() - sdate.getTime()) / 1000) / 60 + "分";
            System.out.println(message);
//            new TelegramNotificationBot().sendMessageWithList(message, returnList, "前程无忧投递");
        }
        try {
            TimeUnit.SECONDS.sleep(30);
        } catch (InterruptedException e) {
            log.error("投完简历休息期间出现异常:", e);
        } finally {
            CHROME_DRIVER.quit();
        }
    }

    private static void Login() {
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
            String text = CHROME_DRIVER.findElement(By.xpath("//p[@class=\"tit\"]")).getText();
            return text != null && text.contains("登录");
        } catch (Exception e) {
            log.info("cookie有效，已登录...");
            return false;
        }
    }


    static boolean isLatest = false;

    @SneakyThrows
    private static void resume(String url) {
        CHROME_DRIVER.get(url);
        Thread.sleep(1000);
        int i = 0;
        try {
            CHROME_DRIVER.findElements(By.className("ss")).get(i).click();
        } catch (Exception e) {
            findAnomaly();
        }
        for (int j = page; j <= maxPage; j++) {
            findAnomaly();
            while (true) {
                try {
                    TimeUnit.SECONDS.sleep(1);
                    WebElement mytxt = WAIT.until(ExpectedConditions.visibilityOfElementLocated(By.id("jump_page")));
                    mytxt.click();
                    mytxt.clear();
                    mytxt.sendKeys(String.valueOf(j));
                    WAIT.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("#app > div > div.post > div > div > div.j_result > div > div:nth-child(2) > div > div.bottom-page > div > div > span.jumpPage"))).click();
                    ACTIONS.keyDown(Keys.CONTROL).sendKeys(Keys.HOME).keyUp(Keys.CONTROL).perform();
                    log.info("{} 中，第 {} 页", jobs.get(i), j);
                    break;
                } catch (Exception e) {
                    TimeUnit.SECONDS.sleep(1);
                    log.error("mytxt.clear()可能异常！信息:{},完整异常:", e.getMessage(), e);
                    CHROME_DRIVER.navigate().refresh();
                }
            }
            if (!page()) {
                break;
            }
        }
    }


    private static void findAnomaly() {
        try {
            String verify = CHROME_DRIVER.findElement(By.cssSelector("#WAF_NC_WRAPPER > p.waf-nc-title")).getText();
            if (verify.contains("访问验证")) {
                //关闭弹窗
                log.error("出现访问验证了！");
                CHROME_DRIVER.quit(); // 关闭之前的ChromeCHROME_DRIVER实例
                System.exit(0);
            }
        } catch (Exception ignored) {
            log.info("未出现访问验证，继续运行！");
        }
    }


    @SneakyThrows
    private static Boolean page() {
        SeleniumUtil.sleep(10);
        // 选择所有岗位，批量投递
        List<WebElement> checkboxes = CHROME_DRIVER.findElements(By.cssSelector("div.ick"));
        if (checkboxes.isEmpty()) {
            return true;
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
        Thread.sleep(3000);
        ACTIONS.keyDown(Keys.CONTROL).sendKeys(Keys.HOME).keyUp(Keys.CONTROL).perform();
        boolean success = false;
        while (!success) {
            try {
                // 查询按钮是否存在
                WebElement parent = CHROME_DRIVER.findElement(By.cssSelector("div.tabs_in"));
                List<WebElement> button = parent.findElements(By.cssSelector("button.p_but"));
                // 如果按钮存在，则点击
                if (button != null && !button.isEmpty()) {
                    Thread.sleep(1000);
                    button.get(1).click();
                    success = true;
                }
            } catch (ElementClickInterceptedException e) {
                log.error("失败，1s后重试..");
                Thread.sleep(1000);
            }
        }

        Thread.sleep(1000);
        try {
            String text = CHROME_DRIVER.findElement(By.cssSelector("[class*='van-popup van-popup--center']")).getText();
            if (text.contains("快来扫码下载~")) {
                //关闭弹窗
                CHROME_DRIVER.findElement(By.cssSelector("[class*='van-icon van-icon-cross van-popup__close-icon van-popup__close-icon--top-right']")).click();
                return true;
            }
        } catch (Exception ignored) {
            log.info("未找到投递成功弹窗！可能为单独投递申请弹窗！");
        }
        try {
            String particularly = CHROME_DRIVER.findElement(By.xpath("//div[@class='el-dialog__body']/span")).getText();
            if (particularly.contains("需要到企业招聘平台单独申请")) {
                //关闭弹窗
                CHROME_DRIVER.findElement(By.cssSelector("#app > div > div.post > div > div > div.j_result > div > div:nth-child(2) > div > div:nth-child(2) > div:nth-child(2) > div > div.el-dialog__header > button > i")).click();
                log.info("关闭单独投递申请弹窗成功！");
                return true;
            }
        } catch (Exception ignored) {
            CHROME_DRIVER.navigate().refresh();
            TimeUnit.SECONDS.sleep(1);
            return true;
        }
        log.error("非常规投递弹窗！");
        return true;
    }


    private static void scanLogin() {
        log.info("等待扫码登陆..");
        CHROME_DRIVER.get(loginUrl);
        WAIT.until(ExpectedConditions.presenceOfElementLocated(By.id("hasresume")));
        SeleniumUtil.saveCookie(cookiePath);
    }

    private static void inputLogin() {
        CHROME_DRIVER.get(loginUrl);
        log.info("等待登陆..");
        CHROME_DRIVER.findElement(By.cssSelector("i[data-sensor-id='sensor_login_wechatScan']")).click();
        CHROME_DRIVER.findElement(By.cssSelector("a[data-sensor-id='sensor_login_passwordLogin']")).click();
        CHROME_DRIVER.findElement(By.id("loginname")).sendKeys("你的账号");
        CHROME_DRIVER.findElement(By.id("password")).sendKeys("你的密码");
        CHROME_DRIVER.findElement(By.id("isread_em")).click();
        CHROME_DRIVER.findElement(By.id("login_btn_withPwd")).click();
        // 手动点击登录按钮过验证登录
        WAIT.until(ExpectedConditions.presenceOfElementLocated(By.id("hasresume")));
    }

}
