package liepin;

import lombok.SneakyThrows;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.SeleniumUtil;

import java.util.*;

import static utils.Constant.*;
import static utils.Constant.CHROME_DRIVER;
import static utils.SeleniumUtil.findElement;
import static utils.SeleniumUtil.isCookieValid;

public class SubmitLiepin {
    private static final Logger log = LoggerFactory.getLogger(SubmitLiepin.class);

    static String homeUrl = "https://www.liepin.com/";
    static String cityCode = "020";
    static String cookiePath = "./src/main/java/liepin/cookie.json";
    static int maxPage = 50;
    static List<String> keywords = List.of("Java", "Python", "Golang", "大模型");
    static String search = "https://www.liepin.com/zhaopin/?dq=%s&currentPage=%s&key=%s";
    static int jobCount = 0;


    public static void main(String[] args) {
        SeleniumUtil.initDriver();
        login();
        for (String keyword : keywords) {
            submit(keyword);
        }
        log.info("投递完成,共投递 {} 个岗位！", jobCount);
    }

    @SneakyThrows
    private static void submit(String keyword) {
        String searchUrl = search.formatted(cityCode, 0, keyword);
        CHROME_DRIVER.get(searchUrl);
        WAIT.until(ExpectedConditions.presenceOfElementLocated(By.className("list-pagination-box")));
        WebElement div = CHROME_DRIVER.findElement(By.className("list-pagination-box"));
        List<WebElement> lis = div.findElements(By.tagName("li"));
        setMaxPage(lis);
        for (int i = 0; i < maxPage; i++) {
            WAIT.until(ExpectedConditions.presenceOfElementLocated(By.className("subscribe-card-box")));
            log.info("正在投递第【{}】页...", i + 1);
            submitJob();
            div = CHROME_DRIVER.findElement(By.className("list-pagination-box"));
            WebElement nextPage = div.findElement(By.xpath(".//li[@title='Next Page']"));
            if (nextPage.getAttribute("disabled") == null) {
                nextPage.click();
                SeleniumUtil.sleep(2);
            }
        }
        log.info("【{}】投递完成！", keyword);
    }


    private static void setMaxPage(List<WebElement> lis) {
        try {
            int page = Integer.parseInt(lis.get(lis.size() - 2).getText());
            if (page > 1) {
                maxPage = page;
            }
        } catch (Exception ignored) {
        }
    }

    private static void submitJob() {
        int count = CHROME_DRIVER.findElements(By.cssSelector("div.job-list-box div[style*='margin-bottom']")).size();
        List<WebElement> recruiters = CHROME_DRIVER.findElements(By.xpath("//*[contains(@class, 'recruiter-name')]"));
        for (int i = 0; i < count; i++) {
            WebElement job = CHROME_DRIVER.findElements(By.cssSelector("div.job-list-box div[style*='margin-bottom']")).get(i);
            String info = job.getText().replaceAll("[\\n\\r]", " ");
            WebElement recruiter = recruiters.get(i);
            System.out.println(recruiter.getText());
            ACTIONS.moveToElement(recruiter).perform();
            try {
                WebElement button = CHROME_DRIVER.findElement(By.xpath("//button"));
                String text = button.getText();
                if (text.contains("聊一聊")) {
                    System.out.println();
//                    button.click();
                } else {
                    log.info("公司【{}】没有聊一聊按钮,他的按钮是: 【{}】", info, text);
                }
                ACTIONS.sendKeys(Keys.ARROW_DOWN)
                        .sendKeys(Keys.ARROW_DOWN)
                        .sendKeys(Keys.ARROW_DOWN)
                        .sendKeys(Keys.ARROW_DOWN)
                        .sendKeys(Keys.ARROW_DOWN)
                        .perform();
                ACTIONS.moveByOffset(120, 0).perform();
            } catch (Exception e) {
                log.error("公司【{}】没有聊天按钮", info);
            }
        }
        log.info("已提交所有职位");
    }

    private static int tryClick(WebElement element, int i) throws InterruptedException {
        boolean isClicked = false;
        int maxRetryCount = 3;
        int retryCount = 0;

        while (!isClicked && retryCount < maxRetryCount) {
            try {
                element.click();
                isClicked = true;
            } catch (Exception e) {
                retryCount++;
                log.error("element.click() 点击失败，正在尝试重新点击...(正在尝试：第 {} 次)", retryCount);
                SeleniumUtil.sleep(5);
                try {
                    CHROME_DRIVER.findElements(By.id("openWinPostion")).get(i).click();
                    isClicked = true;
                } catch (Exception ex) {
                    log.error("get(i).click() 重试失败，尝试使用Actions点击...(正在尝试：第 {} 次)", retryCount);
                    SeleniumUtil.sleep(5);
                    try {
                        ACTIONS.keyDown(Keys.CONTROL).click(element).keyUp(Keys.CONTROL).build().perform();
                        isClicked = true;
                    } catch (Exception exc) {
                        log.error("使用Actions点击也失败，等待10秒后再次尝试...(正在尝试：第 {} 次)", retryCount);
                        SeleniumUtil.sleep(10);
                    }
                }
            }
        }
        if (!isClicked) {
            log.error("已尝试 {} 次，已达最大重试次数，少侠请重新来过！", maxRetryCount);
            log.info("已投递 {} 次，正在退出...", jobCount);
            CHROME_DRIVER.quit();
            return -1;
        } else {
            return 0;
        }
    }

    @SneakyThrows
    private static void newTab(int index) {
        String windowHandle = CHROME_DRIVER.getWindowHandle();
        String company = CHROME_DRIVER.findElement(By.cssSelector(".company-name__2-SjF a")).getText();

        String jobTitle = CHROME_DRIVER.findElement(By.cssSelector(".p-top__1F7CL a")).getText();
        CHROME_DRIVER.findElements(By.id("openWinPostion")).get(index).click();
        WAIT.until(ExpectedConditions.presenceOfElementLocated(By.className("resume-deliver")));

        Set<String> windowHandles = CHROME_DRIVER.getWindowHandles();
        windowHandles.remove(windowHandle);
        String newWindowHandle = windowHandles.iterator().next();
        CHROME_DRIVER.switchTo().window(newWindowHandle);
        WAIT.until(ExpectedConditions.presenceOfElementLocated(By.className("resume-deliver")));

        if (!"已投递".equals(CHROME_DRIVER.findElements(By.className("resume-deliver")).get(0).getText())) {
            CHROME_DRIVER.findElements(By.className("resume-deliver")).get(0).click();
            SeleniumUtil.sleep(1);
            WAIT.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("button.lg-design-btn.lg-design-btn-primary"))).click();
            log.info("投递【{}】公司: 【{}】岗位", company, jobTitle);
        }
        CHROME_DRIVER.close();
        CHROME_DRIVER.switchTo().window(windowHandle);
    }

    @SneakyThrows
    private static void login() {
        CHROME_DRIVER.get(homeUrl);
        if (isCookieValid(cookiePath)) {
            SeleniumUtil.loadCookie(cookiePath);
            CHROME_DRIVER.navigate().refresh();
        }
        WAIT.until(ExpectedConditions.presenceOfElementLocated(By.id("header-logo-box")));
        if (isLoginRequired()) {
            log.error("cookie失效，尝试扫码登录...");
            scanLogin();
            SeleniumUtil.saveCookie(cookiePath);
        }
    }

    private static boolean isLoginRequired() {
        String currentUrl = CHROME_DRIVER.getCurrentUrl();
        return !currentUrl.contains("c.liepin.com");
    }

    private static void scanLogin() {
        try {
            SeleniumUtil.click(By.className("btn-sign-switch"));
            log.info("等待扫码..");
            WAIT.until(ExpectedConditions.presenceOfElementLocated(By.xpath("//*[@id=\"main-container\"]/div/div[3]/div[2]/div[3]/div[1]/div[1]")));
        } catch (Exception e) {
            log.error("scanLogin() 失败: {}", e.getMessage());
        }
    }

}
