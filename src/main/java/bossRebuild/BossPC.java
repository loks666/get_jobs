package bossRebuild;

import bossRebuild.config.BossConfig;
import bossRebuild.constants.Constants;
import bossRebuild.constants.Elements;
import bossRebuild.service.DataService;
import bossRebuild.service.FilterService;
import bossRebuild.service.JobService; // 确保导入正确的 JobService
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.Job;
import utils.JobUtils;
import utils.SeleniumUtil;

import java.io.IOException;
import java.time.Duration;
import java.util.*;

import static utils.Bot.sendMessageByTime;
import static utils.Constant.*;

public class BossPC {
    private static final Logger log = LoggerFactory.getLogger(BossPC.class);
    private static Set<String> blackCompanies = new HashSet<>();
    private static Set<String> blackRecruiters = new HashSet<>();
    private static Set<String> blackJobs = new HashSet<>();
    private static List<Job> resultList = new ArrayList<>();
    private static Date startDate;
    private static BossConfig config = BossConfig.init();

    public static void main(String[] args) {
        DataService dataService = new DataService();
        dataService.loadData(Constants.DATA_PATH, blackCompanies, blackRecruiters, blackJobs);

        SeleniumUtil.initDriver();
        startDate = new Date();
        login();

        WebDriverWait wait = new WebDriverWait(CHROME_DRIVER, 40);
        FilterService filterService = new FilterService(config, blackCompanies, blackRecruiters, blackJobs);
        JobService jobService = new JobService(wait, config, resultList, filterService); // 确保包路径正确

        try {
            for (String s : config.getCityCode()) {
                jobService.postJobByCity(s);
            }
        } catch (Exception e) {
            log.error("投递过程中发生异常：{}", e.getMessage(), e);
        } finally {
            printResult(dataService);
        }
    }

    private static void printResult(DataService dataService) {
        String message = String.format("\nBoss投递完成，共发起%d个聊天，用时%s", resultList.size(), JobUtils.formatDuration(startDate, new Date()));
        log.info(message);
        sendMessageByTime(message);
        dataService.saveData(Constants.DATA_PATH, blackCompanies, blackRecruiters, blackJobs);
        resultList.clear();
        try {
            CHROME_DRIVER.close();
            CHROME_DRIVER.quit();
        } catch (Exception e) {
            log.error("关闭浏览器时发生异常：{}", e.getMessage(), e);
        }
    }

    private static void login() {
        log.info("打开Boss直聘网站中...");
        CHROME_DRIVER.get(Constants.HOME_URL);
        SeleniumUtil.sleep(2); // 等待页面加载

        // 检查是否需要登录
        if (SeleniumUtil.isCookieValid(Constants.COOKIE_PATH)) {
            log.info("尝试加载cookie...");
            SeleniumUtil.loadCookie(Constants.COOKIE_PATH);
            CHROME_DRIVER.navigate().refresh();
            SeleniumUtil.sleep(2);
            if (!isLoginRequired()) {
                log.info("cookie有效，已登录...");
                return;
            }
        }

        log.error("cookie失效或未登录，尝试扫码登录...");
        scanLogin();
    }

    private static boolean isLoginRequired() {
        try {
            // 检查登录按钮是否存在
            WebElement loginButton = CHROME_DRIVER.findElement(By.xpath(Elements.LOGIN_BUTTON_CSS));
            String text = loginButton.getText();
            if (text != null && text.contains("登录")) {
                log.info("检测到登录按钮，需登录...");
                return true;
            }
        } catch (Exception e) {
            log.debug("未找到登录按钮，检查是否已登录...");
        }

        try {
            // 检查是否出现403错误
            WebElement login403 = CHROME_DRIVER.findElement(By.xpath(Elements.LOGIN_403_XPATH));
            login403.click();
            log.info("检测到403错误，跳转到登录页面...");
            return true;
        } catch (Exception e) {
            log.debug("未出现403访问异常...");
        }

        try {
            // 检查导航栏用户信息，确认是否已登录
            WebElement navFigure = CHROME_DRIVER.findElement(By.xpath(Elements.NAV_FIGURE_XPATH));
            String text = navFigure.getText();
            if (text != null && !text.contains("登录")) {
                log.info("导航栏用户信息存在，已登录...");
                return false;
            }
        } catch (Exception e) {
            log.debug("未找到导航栏用户信息，可能需要登录...");
        }

        return true; // 默认需要登录
    }

    private static void scanLogin() {
        CHROME_DRIVER.get(Constants.HOME_URL + "/web/user/?ka=header-login");
        SeleniumUtil.sleep(3);

        try {
            WebElement navFigure = CHROME_DRIVER.findElement(By.xpath(Elements.NAV_FIGURE_XPATH));
            String text = navFigure.getText();
            if (!Objects.equals(text, "登录")) {
                log.info("已经登录，直接开始投递...");
                return;
            }
        } catch (Exception ignored) {
            log.debug("未找到导航栏用户信息，继续尝试扫码登录...");
        }

        log.info("等待用户扫码登录...");
        WebElement app = WAIT.until(ExpectedConditions.presenceOfElementLocated(By.xpath(Elements.EWM_SWITCH_XPATH)));

        boolean login = false;
        long startTime = System.currentTimeMillis();
        final long TIMEOUT = 10 * 60 * 1000; // 10分钟超时
        Scanner scanner = new Scanner(System.in);

        while (!login) {
            long elapsed = System.currentTimeMillis() - startTime;
            if (elapsed >= TIMEOUT) {
                log.error("超过10分钟未完成登录，程序退出...");
                CHROME_DRIVER.quit();
                System.exit(1);
            }

            try {
                app.click();
                WAIT.until(ExpectedConditions.presenceOfElementLocated(By.xpath(Elements.HEADER_LOGIN_XPATH)));
                WAIT.until(ExpectedConditions.presenceOfElementLocated(By.xpath(Elements.LOGIN_SUCCESS_XPATH)));
                login = true;
                log.info("登录成功！保存cookie...");
                SeleniumUtil.saveCookie(Constants.COOKIE_PATH);
            } catch (Exception e) {
                log.error("登录失败，等待用户操作或 2 秒后重试... 错误信息：{}", e.getMessage());
                boolean userInput = waitForUserInputOrTimeout(scanner);
                if (userInput) {
                    log.info("检测到用户输入，继续尝试登录...");
                }
                SeleniumUtil.sleep(2);
            }
        }
    }

    private static boolean waitForUserInputOrTimeout(Scanner scanner) {
        long end = System.currentTimeMillis() + 2000;
        while (System.currentTimeMillis() < end) {
            try {
                if (System.in.available() > 0) {
                    scanner.nextLine();
                    return true;
                }
            } catch (IOException e) {
                log.debug("检查用户输入时发生异常：{}", e.getMessage());
            }
            SeleniumUtil.sleep(1);
        }
        return false;
    }
}