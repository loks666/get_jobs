package liepin;

import lombok.SneakyThrows;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.SeleniumUtil;

import java.util.*;

import static utils.Constant.*;
import static utils.Constant.CHROME_DRIVER;
import static utils.SeleniumUtil.isCookieValid;

public class Liepin {
    private static final Logger log = LoggerFactory.getLogger(Liepin.class);
    static String homeUrl = "https://www.liepin.com/";
    static String cookiePath = "./src/main/java/liepin/cookie.json";
    static int maxPage = 50;
    static String cityCode = "020";
    static List<String> keywords = List.of("AIGC", "Python", "Golang", "大模型", "Java");
    static List<String> resultList = new ArrayList<>();
    static String search = "https://www.liepin.com/zhaopin/?dq=%s&currentPage=%s&key=%s";
    static boolean isSayHi = false;
    static boolean isStop = false;


    public static void main(String[] args) {
        SeleniumUtil.initDriver();
        login();
        for (String keyword : keywords) {
            submit(keyword);
        }
        printResult();
        CHROME_DRIVER.close();
        CHROME_DRIVER.quit();
    }

    private static void printResult() {
        log.info("投递完成,共投递 {} 个岗位！", resultList.size());
        log.info("今日投递岗位:\n{}", String.join("\n", resultList));
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
            log.info("正在投递【{}】第【{}】页...", keyword, i + 1);
            submitJob();
            div = CHROME_DRIVER.findElement(By.className("list-pagination-box"));
            WebElement nextPage = div.findElement(By.xpath(".//li[@title='Next Page']"));
            if (nextPage.getAttribute("disabled") == null) {
                nextPage.click();
            }
            log.info("已投递第【{}】页所有的岗位...\n", i + 1);
        }
        log.info("【{}】关键词投递完成！", keyword);
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
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            String jobName = CHROME_DRIVER.findElements(By.xpath("//*[contains(@class, 'job-title-box')]")).get(i).getText().replaceAll("\n", " ").replaceAll("【 ", "[").replaceAll(" 】", "]");
            String companyName = CHROME_DRIVER.findElements(By.xpath("//*[contains(@class, 'company-name')]")).get(i).getText().replaceAll("\n", " ");
            String salary = CHROME_DRIVER.findElements(By.xpath("//*[contains(@class, 'job-salary')]")).get(i).getText().replaceAll("\n", " ");
            String recruiterName = null;
            WebElement name = null;
            try {
                name = CHROME_DRIVER.findElements(By.xpath("//*[contains(@class, 'recruiter-name')]")).get(i);
                recruiterName = name.getText();
            } catch (Exception e) {
                log.error("{}", e.getMessage());
            }
            WebElement title;
            String recruiterTitle = null;
            try {
                title = CHROME_DRIVER.findElements(By.xpath("//*[contains(@class, 'recruiter-title')]")).get(i);
                recruiterTitle = title.getText();
            } catch (Exception e) {
                log.info("【{}】招聘人员:【{}】没有职位描述", companyName, recruiterName);
            }
            try {
                ACTIONS.moveToElement(name).perform();
            } catch (Exception e) {
                log.error("鼠标移动到HR标签异常...");
            }
            WebElement button;
            try {
                button = CHROME_DRIVER.findElement(By.xpath("//button"));
            } catch (Exception e) {
                log.error("公司【{}】没有聊天按钮", companyName);
                continue;
            }
            String text = button.getText();
            if (text.contains("聊一聊")) {
                button.click();
                WAIT.until(ExpectedConditions.presenceOfElementLocated(By.className("__im_basic__header-wrap")));
                WAIT.until(ExpectedConditions.presenceOfElementLocated(By.xpath("//textarea[contains(@class, '__im_basic__textarea')]")));
                WebElement input = CHROME_DRIVER.findElement(By.xpath("//textarea[contains(@class, '__im_basic__textarea')]"));
                input.click();
                if (isSayHi) {
                    input.sendKeys(SAY_HI);
                    WebElement send = WAIT.until(ExpectedConditions.presenceOfElementLocated(By.xpath("//button[contains(@class, '__im_basic__basic-send-btn')]")));
                    send.click();
                    try {
                        WebElement result = CHROME_DRIVER.findElement(By.xpath("//div[@class='__im_basic__message']"));
                        if (result.getText().contains("已达上限")) {
                            if (isStop) {
                                printResult();
                                CHROME_DRIVER.close();
                                CHROME_DRIVER.quit();
                                System.exit(0);
                            }
                            log.info("发起会话已达上限，将开始使用系统使用默认打招呼方式...");
                            isSayHi = false;
                        }
                    } catch (Exception ignored) {
                    }
                }
                SeleniumUtil.sleep(1);
                WebElement close = CHROME_DRIVER.findElement(By.cssSelector("div.__im_basic__contacts-title svg"));
                close.click();
                WAIT.until(ExpectedConditions.presenceOfElementLocated(By.xpath("//div[contains(@class, 'recruiter-info-box')]")));

                resultList.add(sb.append("【").append(companyName).append(" ").append(jobName).append(" ").append(salary).append(" ").append(recruiterName).append(" ").append(recruiterTitle).append("】").toString());
                sb.setLength(0);
                log.info("发起新聊天:【{}】的【{}·{}】岗位, 【{}:{}】", companyName, jobName, salary, recruiterName, recruiterTitle);
            }
            else {
//                log.info("【{}】的【{}】已经聊过,可以和TA:【{}】", companyName, recruiterName, text);
            }
            ACTIONS.moveByOffset(120, 0).perform();
        }
    }

    @SneakyThrows
    private static void login() {
        log.info("正在打开猎聘网站...");
        CHROME_DRIVER.get(homeUrl);
        log.info("猎聘正在登录...");
        if (isCookieValid(cookiePath)) {
            SeleniumUtil.loadCookie(cookiePath);
            CHROME_DRIVER.navigate().refresh();
        }
        WAIT.until(ExpectedConditions.presenceOfElementLocated(By.id("header-logo-box")));
        if (isLoginRequired()) {
            log.info("cookie失效，尝试扫码登录...");
            scanLogin();
            SeleniumUtil.saveCookie(cookiePath);
        } else {
            log.info("cookie有效，准备投递...");
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
