package job51;

import com.microsoft.playwright.*;
import lombok.SneakyThrows;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.JobUtils;
import utils.PlaywrightUtil;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static utils.Bot.sendMessageByTime;
import static utils.JobUtils.formatDuration;

/**
 * @author loks666
 * 项目链接: <a href="https://github.com/loks666/get_jobs">https://github.com/loks666/get_jobs</a>
 * 前程无忧自动投递简历
 */
public class Job51 {
    private static final Logger log = LoggerFactory.getLogger(Job51.class);

    static Integer page = 1;
    static Integer maxPage = 50;
    static String cookiePath = "./src/main/java/job51/cookie.json";
    static String homeUrl = "https://www.51job.com";
    static String loginUrl = "https://login.51job.com/login.php?lang=c&url=https://www.51job.com/&qrlogin=2";
    static String baseUrl = "https://we.51job.com/pc/search?";
    static List<String> resultList = new ArrayList<>();
    static Job51Config config = Job51Config.init();
    static Date startDate;

    public static void main(String[] args) {
        String searchUrl = getSearchUrl();
        PlaywrightUtil.init();
        startDate = new Date();
        Login();
        config.getKeywords().forEach(keyword -> resume(searchUrl + "&keyword=" + keyword));
        printResult();
    }

    private static void printResult() {
        String message = String.format("\n51job投递完成，共投递%d个简历，用时%s", resultList.size(), formatDuration(startDate, new Date()));
        log.info(message);
        sendMessageByTime(message);
        resultList.clear();
        PlaywrightUtil.close();
    }

    private static String getSearchUrl() {
        return baseUrl +
                JobUtils.appendListParam("jobArea", config.getJobArea()) +
                JobUtils.appendListParam("salary", config.getSalary());
    }

    private static void Login() {
        PlaywrightUtil.navigate(homeUrl);
        if (isCookieValid(cookiePath)) {
            PlaywrightUtil.loadCookies(cookiePath);
            PlaywrightUtil.getPageObject().reload();
            PlaywrightUtil.sleep(1);
        }
        if (isLoginRequired()) {
            log.error("cookie失效，尝试扫码登录...");
            scanLogin();
        }
    }

    private static boolean isLoginRequired() {
        try {
            Locator titleElement = PlaywrightUtil.findElement("//p[@class=\"tit\"]");
            String text = titleElement.textContent();
            return text != null && text.contains("登录");
        } catch (Exception e) {
            log.info("cookie有效，已登录...");
            return false;
        }
    }

    @SneakyThrows
    private static void resume(String url) {
        PlaywrightUtil.navigate(url);
        PlaywrightUtil.sleep(1);

        // 再次判断是否登录
        Locator login = PlaywrightUtil.waitForElement("//a[contains(@class, 'uname')]");
        if (login != null && isNotNullOrEmpty(login.textContent()) && login.textContent().contains("登录")) {
            login.click();
            PlaywrightUtil.waitForElement("//i[contains(@class, 'passIcon')]").click();
            log.info("请扫码登录...");
            PlaywrightUtil.waitForElement("//div[contains(@class, 'joblist')]");
            PlaywrightUtil.saveCookies(cookiePath);
        }

        //由于51更新，每投递一页之前，停止10秒
        PlaywrightUtil.sleep(10);

        int i = 0;
        try {
            PlaywrightUtil.findElement(".ss").nth(i).click();
        } catch (Exception e) {
            findAnomaly();
        }
        for (int j = page; j <= maxPage; j++) {
            while (true) {
                try {
                    Locator mytxt = PlaywrightUtil.waitForElement("#jump_page");
                    PlaywrightUtil.sleep(5);
                    mytxt.click();
                    mytxt.clear();
                    mytxt.fill(String.valueOf(j));
                    PlaywrightUtil.waitForElement("#app > div > div.post > div > div > div.j_result > div > div:nth-child(2) > div > div.bottom-page > div > div > span.jumpPage").click();
                    PlaywrightUtil.getPageObject().keyboard().press("Control+Home");
                    log.info("第 {} 页", j);
                    break;
                } catch (Exception e) {
                    log.error("mytxt.clear()可能异常...");
                    PlaywrightUtil.sleep(1);
                    findAnomaly();
                    PlaywrightUtil.getPageObject().reload();
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
        PlaywrightUtil.sleep(1);
        // 选择所有岗位，批量投递
        Locator checkboxes = PlaywrightUtil.findElement("div.ick");
        int checkboxCount = checkboxes.count();
        if (checkboxCount == 0) {
            return;
        }
        Locator titles = PlaywrightUtil.findElement("[class*='jname text-cut']");
        Locator companies = PlaywrightUtil.findElement("[class*='cname text-cut']");
        
        for (int i = 0; i < checkboxCount; i++) {
            Locator checkbox = checkboxes.nth(i);
            checkbox.click();
            String title = titles.nth(i).textContent();
            String company = companies.nth(i).textContent();
            resultList.add(company + " | " + title);
            log.info("选中:{} | {} 职位", company, title);
        }
        PlaywrightUtil.sleep(1);
        PlaywrightUtil.getPageObject().keyboard().press("Control+Home");
        
        boolean success = false;
        while (!success) {
            try {
                // 查询按钮是否存在
                Locator parent = PlaywrightUtil.findElement("div.tabs_in");
                Locator buttons = parent.locator("button.p_but");
                // 如果按钮存在，则点击
                if (buttons.count() > 1) {
                    PlaywrightUtil.sleep(1);
                    buttons.nth(1).click();
                    success = true;
                }
            } catch (Exception e) {
                log.error("失败，1s后重试..");
                PlaywrightUtil.sleep(1);
            }
        }

        try {
            PlaywrightUtil.sleep(3);
            Locator successContent = PlaywrightUtil.findElement("//div[@class='successContent']");
            String text = successContent.textContent();
            if (text.contains("快来扫码下载~")) {
                //关闭弹窗
                PlaywrightUtil.findElement("[class*='van-icon van-icon-cross van-popup__close-icon van-popup__close-icon--top-right']").click();
            }
        } catch (Exception ignored) {
            log.info("未找到投递成功弹窗！可能为单独投递申请弹窗！");
        }
        
        String particularly = null;
        try {
            particularly = PlaywrightUtil.findElement("//div[@class='el-dialog__body']/span").textContent();
        } catch (Exception ignored) {
        }
        if (particularly != null && particularly.contains("需要到企业招聘平台单独申请")) {
            //关闭弹窗
            PlaywrightUtil.findElement("#app > div > div.post > div > div > div.j_result > div > div:nth-child(2) > div > div:nth-child(2) > div:nth-child(2) > div > div.el-dialog__header > button > i").click();
            log.info("关闭单独投递申请弹窗成功！");
        }
    }

    private static void findAnomaly() {
        try {
            Locator verify = PlaywrightUtil.findElement("//p[@class='waf-nc-title']");
            String verifyText = verify.textContent();
            if (verifyText.contains("验证")) {
                //关闭弹窗
                log.error("出现访问验证了！程序退出...");
                printResult();
                PlaywrightUtil.close();
            }
        } catch (Exception ignored) {
            log.info("未出现访问验证，继续运行...");
        }
    }

    private static void scanLogin() {
        log.info("等待扫码登陆..");
        PlaywrightUtil.navigate(loginUrl);
        PlaywrightUtil.waitForElement("#hasresume");
        PlaywrightUtil.saveCookies(cookiePath);
    }

    private static boolean isCookieValid(String cookiePath) {
        try {
            String cookieContent = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(cookiePath)));
            return cookieContent != null && !cookieContent.equals("[]") && cookieContent.contains("name");
        } catch (Exception e) {
            log.error("读取cookie文件失败: {}", e.getMessage());
            return false;
        }
    }

}
