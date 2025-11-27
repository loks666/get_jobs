package job51;

import com.microsoft.playwright.*;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.json.JSONObject;
import utils.JobUtils;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * @author loks666
 * 项目链接: <a href="https://github.com/loks666/get_jobs">https://github.com/loks666/get_jobs</a>
 * 前程无忧自动投递简历 - Playwright版本
 */
@Slf4j
public class Job51 {
    static {
        System.setProperty("log.name", "job51");
    }

    static Integer page = 1;
    static Integer maxPage = 50;
    static String cookiePath = "./src/main/java/job51/cookie.json";  // Cookie文件路径
    static String homeUrl = "https://www.51job.com";
    static String loginUrl = "https://login.51job.com/login.php?lang=c&url=https://www.51job.com/&qrlogin=2";
    static String baseUrl = "https://we.51job.com/pc/search?";
    static List<String> resultList = new ArrayList<>();
    static Job51Config config = Job51Config.init();
    static Date startDate;
    static boolean reachedDailyLimit = false;

    // Playwright 相关
    static Playwright playwright;
    static Browser browser;
    static BrowserContext context;
    static Page browserPage;

    static {
        try {
            // 检查cookiePath文件是否存在，不存在则创建
            java.io.File cookieFile = new java.io.File(cookiePath);
            if (!cookieFile.exists()) {
                // 确保父目录存在
                if (!cookieFile.getParentFile().exists()) {
                    cookieFile.getParentFile().mkdirs();
                }
                // 创建空cookie文件
                Files.write(Paths.get(cookiePath), "[]".getBytes());
                log.info("创建cookie文件: {}", cookiePath);
            }
        } catch (IOException e) {
            log.error("创建cookie文件时发生异常: {}", e.getMessage());
        }
    }

    public static void main(String[] args) {
        String searchUrl = getSearchUrl();
        initPlaywright();
        startDate = new Date();
        
        // 登录检测
        login();
        
        // 遍历所有关键词进行投递
        config.getKeywords().forEach(keyword -> resume(searchUrl + "&keyword=" + keyword));
        printResult();
    }

    private static void printResult() {
//        String message = String.format("\n51job投递完成，共投递%d个简历，用时%s", resultList.size(), formatDuration(startDate, new Date()));
//        log.info(message);
//        sendMessageByTime(message);
        resultList.clear();
        
        // 关闭浏览器
        if (browserPage != null) browserPage.close();
        if (browser != null) browser.close();
        if (playwright != null) playwright.close();
    }

    private static String getSearchUrl() {
        return baseUrl +
                JobUtils.appendListParam("jobArea", config.getJobArea()) +
                JobUtils.appendListParam("salary", config.getSalary());
    }

   /**
     * 初始化 Playwright
     */
    private static void initPlaywright() {
        try {
            playwright = Playwright.create();
            browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                    .setHeadless(false)
                    .setArgs(List.of(
                            "--start-maximized",
                            "--disable-blink-features=AutomationControlled"
                    )));
            
            context = browser.newContext(new Browser.NewContextOptions()
                    .setViewportSize(null)); // 使用浏览器窗口大小
            
            browserPage = context.newPage();
            log.info("Playwright 初始化成功");
        } catch (Exception e) {
            log.error("Playwright 初始化失败", e);
            throw new RuntimeException("Playwright 初始化失败", e);
        }
    }

    /**
     * 登录检测与Cookie加载
     */
    @SneakyThrows
    private static void login() {
        log.info("打开51job网站中...");
        browserPage.navigate(homeUrl);
        sleep(1);

        // 尝试加载Cookie
        if (isCookieValid(cookiePath)) {
            loadCookies(cookiePath);
            browserPage.reload();
            sleep(1);
        }

        // 检查是否需要登录
        if (isLoginRequired()) {
            log.error("cookie失效，尝试扫码登录...");
            scanLogin();
        } else {
            log.info("cookie有效，已登录");
        }
    }

    /**
     * 检查Cookie文件是否有效
     */
    private static boolean isCookieValid(String path) {
        try {
            java.io.File file = new java.io.File(path);
            if (!file.exists()) {
                return false;
            }
            String content = new String(Files.readAllBytes(Paths.get(path)));
            return content != null && !content.trim().equals("[]") && !content.trim().isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 从文件加载Cookie
     */
    private static void loadCookies(String path) {
        try {
            String jsonText = new String(Files.readAllBytes(Paths.get(path)));
            JSONArray jsonArray = new JSONArray(jsonText);

            List<com.microsoft.playwright.options.Cookie> cookies = new ArrayList<>();
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject jsonObject = jsonArray.getJSONObject(i);

                com.microsoft.playwright.options.Cookie cookie = new com.microsoft.playwright.options.Cookie(
                        jsonObject.getString("name"),
                        jsonObject.getString("value"));

                if (!jsonObject.isNull("domain")) {
                    cookie.domain = jsonObject.getString("domain");
                }
                if (!jsonObject.isNull("path")) {
                    cookie.path = jsonObject.getString("path");
                }
                if (!jsonObject.isNull("expires")) {
                    cookie.expires = jsonObject.getDouble("expires");
                }
                if (!jsonObject.isNull("secure")) {
                    cookie.secure = jsonObject.getBoolean("secure");
                }
                if (!jsonObject.isNull("httpOnly")) {
                    cookie.httpOnly = jsonObject.getBoolean("httpOnly");
                }

                cookies.add(cookie);
            }

            context.addCookies(cookies);
            log.info("已从文件加载Cookie: {}", path);
        } catch (IOException e) {
            log.error("加载Cookie失败", e);
        }
    }

    /**
     * 保存Cookie到文件
     */
    private static void saveCookies(String path) {
        try {
            List<com.microsoft.playwright.options.Cookie> cookies = context.cookies();
            JSONArray jsonArray = new JSONArray();

            for (com.microsoft.playwright.options.Cookie cookie : cookies) {
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("name", cookie.name);
                jsonObject.put("value", cookie.value);
                jsonObject.put("domain", cookie.domain);
                jsonObject.put("path", cookie.path);
                if (cookie.expires != null) {
                    jsonObject.put("expires", cookie.expires);
                }
                jsonObject.put("secure", cookie.secure);
                jsonObject.put("httpOnly", cookie.httpOnly);
                jsonArray.put(jsonObject);
            }

            try (FileWriter file = new FileWriter(path)) {
                file.write(jsonArray.toString(4));
                log.info("Cookie已保存到文件: {}", path);
            }
        } catch (IOException e) {
            log.error("保存Cookie失败", e);
        }
    }

    /**
     * 检查是否需要登录
     */
    private static boolean isLoginRequired() {
        try {
            Locator loginElement = browserPage.locator("//a[contains(@class, 'uname')]");
            if (loginElement.count() > 0) {
                String text = loginElement.textContent();
                return text != null && text.contains("登录");
            }
            return false;
        } catch (Exception e) {
            log.info("cookie有效，已登录...");
            return false;
        }
    }

    /**
     * 扫码登录
     */
    @SneakyThrows
    private static void scanLogin() {
        log.info("等待扫码登录...");
        browserPage.navigate(loginUrl);
        
        // 等待登录成功的标志：出现"在线简历"链接
        try {
            browserPage.waitForSelector("//a[contains(text(), '在线简历')]", 
                new Page.WaitForSelectorOptions().setTimeout(5 * 60 * 1000)); // 等待5分钟
            log.info("登录成功！");
            saveCookies(cookiePath);
        } catch (Exception e) {
            log.error("登录超时或失败", e);
            throw new RuntimeException("登录超时或失败", e);
        }
    }

    @SneakyThrows
    private static void resume(String url) {
        browserPage.navigate(url);
        sleep(2);

        // 点击排序选项（选择第一个排序方式）
        try {
            Locator sortOptions = browserPage.locator("div.ss");
            if (sortOptions.count() > 0) {
                sortOptions.first().click();
                sleep(1);
            }
        } catch (Exception e) { /* 静默 */ }

        // 由于51更新，每投递一页之前，停止10秒
        sleep(10);

        // 遍历页面投递
        for (int j = page; j <= maxPage; j++) {
            log.info("第 {} 页", j);
            
            // 跳转到指定页码
            if (j > 1 && !jumpToPage(j)) {
                break;
            }

            sleep(2);

            // 检查是否出现访问验证
            if (checkAccessVerification()) {
                log.error("出现访问验证了！程序退出...");
                printResult();
                return;
            }

            // 投递当前页面的所有职位
            deliverCurrentPage();
            if (reachedDailyLimit) break;

            sleep(3);
        }
    }

    /**
     * 投递当前页面的所有职位
     */
    private static void deliverCurrentPage() {
        try {
            sleep(1);

            // 查找所有职位的checkbox
            Locator checkboxes = browserPage.locator("div.ick");
            if (checkboxes.count() == 0) {
                return;
            }

            // 查找职位名称和公司名称
            Locator titles = browserPage.locator("[class*='jname text-cut']");
            Locator companies = browserPage.locator("[class*='cname text-cut']");

            int jobCount = checkboxes.count();

            // 选中所有职位
            for (int i = 0; i < jobCount; i++) {
                try {
                    Locator checkbox = checkboxes.nth(i);
                    // 使用JavaScript点击，避免元素被遮挡
                    checkbox.evaluate("el => el.click()");

                    String title = i < titles.count() ? titles.nth(i).textContent() : "未知职位";
                    String company = i < companies.count() ? companies.nth(i).textContent() : "未知公司";
                    String jobInfo = company + " | " + title;
                    resultList.add(jobInfo);
                    log.info("选中: {}", jobInfo);
                } catch (Exception e) { /* 静默 */ }
            }

            sleep(1);

            // 滚动到页面顶部
            browserPage.evaluate("window.scrollTo(0, 0)");
            sleep(1);

            // 点击批量投递按钮
            clickBatchDeliverButton();

            sleep(3);

            // 处理投递成功弹窗
            handleDeliverySuccessDialog();

            // 处理单独投递申请弹窗
            handleSeparateDeliveryDialog();

        } catch (Exception e) {
            log.error("投递当前页面失败", e);
        }
    }

    /**
     * 点击批量投递按钮
     */
    private static void clickBatchDeliverButton() {
        int retryCount = 0;
        boolean success = false;

        while (!success && retryCount < 5) {
            try {
                // 查找批量投递按钮
                Locator parent = browserPage.locator("div.tabs_in");
                Locator buttons = parent.locator("button.p_but");

                if (buttons.count() > 1) {
                    sleep(1);
                    buttons.nth(1).click();
                    
                    // 🚨 点击后立即检测"日投递上限"提示（短暂出现，需快速多次检测）
                    for (int i = 0; i < 10; i++) {
                        try { Thread.sleep(200); } catch (InterruptedException ignored) {} // 每200ms检测一次
                        if (detectDailyLimitToast()) {
                            reachedDailyLimit = true;
                            log.warn("点击投递按钮后，检测到 51job 日投递上限提示，停止投递");
                            return;
                        }
                    }
                    
                    success = true;
                } else {
                    break;
                }
            } catch (Exception e) {
                retryCount++;
                sleep(1);
            }
        }
    }

    /**
     * 处理投递成功弹窗
     */
    private static void handleDeliverySuccessDialog() {
        try {
            sleep(2);

            Locator successContent = browserPage.locator("//div[@class='successContent']");
            if (successContent.count() > 0) {
                String text = successContent.textContent();
                if (text != null && text.contains("快来扫码下载")) {
                    log.info("检测到下载App弹窗，关闭中...");
                    // 关闭弹窗
                    Locator closeButton = browserPage.locator("[class*='van-icon van-icon-cross van-popup__close-icon van-popup__close-icon--top-right']");
                    if (closeButton.count() > 0) {
                        closeButton.click();
                        log.info("成功关闭下载App弹窗");
                    }
                }
            }

            // 兼容提示弹框：投递成功N个，未投递M个
            Locator elDialogBody = browserPage.locator(".el-dialog__body");
            if (elDialogBody.count() > 0) {
                String dialogText = elDialogBody.first().innerText();
                if (dialogText != null && dialogText.contains("投递成功")) {
                    Integer successNum = null;
                    Integer failNum = null;
                    try {
                        java.util.regex.Matcher m1 = java.util.regex.Pattern.compile("投递成功\\D*(\\d+)").matcher(dialogText);
                        if (m1.find()) successNum = Integer.parseInt(m1.group(1));
                        java.util.regex.Matcher m2 = java.util.regex.Pattern.compile("未投递\\D*(\\d+)").matcher(dialogText);
                        if (m2.find()) failNum = Integer.parseInt(m2.group(1));
                    } catch (Exception ignored) {}
                    log.info("[51job] 投递结果：成功 {} 个，未投递 {} 个", successNum, failNum);

                    // 优先点击"确定/关闭"按钮
                    try {
                        Locator okBtn = browserPage.locator(".el-dialog__footer button:has-text('确定'), .el-message-box__btns button:has-text('确定')");
                        if (okBtn.count() > 0) {
                            okBtn.first().click();
                        } else {
                            // 点击关闭按钮
                            Locator headerBtn = browserPage.locator("button.el-dialog__headerbtn, .el-dialog__header button.el-dialog__headerbtn, button[aria-label='Close']");
                            if (headerBtn.count() > 0 && headerBtn.first().isVisible()) {
                                headerBtn.first().click(new Locator.ClickOptions().setForce(true).setTimeout(2000));
                            } else {
                                // 按 ESC
                                browserPage.keyboard().press("Escape");
                            }
                        }
                        sleep(1);
                    } catch (Exception ignored) {}
                }
            }

            // 弹窗处理后再次检测是否出现"日投递上限"提示
            try {
                if (detectDailyLimitToast()) {
                    reachedDailyLimit = true;
                    log.warn("处理成功弹窗后，检测到 51job 日投递上限提示，停止当前页");
                }
            } catch (Exception ignored) {}
        } catch (Exception e) {
            log.debug("未找到投递成功弹窗或处理失败: {}", e.getMessage());
        }
    }

    /**
     * 处理单独投递申请弹窗
     */
    private static void handleSeparateDeliveryDialog() {
        try {
            Locator dialogContent = browserPage.locator("//div[@class='el-dialog__body']/span");
            if (dialogContent.count() > 0) {
                String text = dialogContent.textContent();
                if (text != null && text.contains("需要到企业招聘平台单独申请")) {
                    log.info("检测到单独投递申请弹窗，关闭中...");
                    // 关闭弹窗
                    Locator closeButton = browserPage.locator("#app > div > div.post > div > div > div.j_result > div > div:nth-child(2) > div > div:nth-child(2) > div:nth-child(2) > div > div.el-dialog__header > button > i");
                    if (closeButton.count() > 0) {
                        closeButton.click();
                        log.info("成功关闭单独投递申请弹窗");
                    }
                }
            }
        } catch (Exception e) {
            log.debug("未找到单独投递申请弹窗或处理失败: {}", e.getMessage());
        }
    }

    /**
     * 跳转到指定页码
     */
    private static boolean jumpToPage(int pageNum) {
        for (int retry = 0; retry < 3; retry++) {
            try {
                Locator pageInput = browserPage.locator("#jump_page");
                if (pageInput.count() == 0) {
                    log.warn("未找到页码输入框");
                    return false;
                }

                sleep(1);
                pageInput.click();
                pageInput.fill("");
                pageInput.fill(String.valueOf(pageNum));

                // 点击跳转按钮
                Locator jumpButton = browserPage.locator("#app > div > div.post > div > div > div.j_result > div > div:nth-child(2) > div > div.bottom-page > div > div > span.jumpPage");
                if (jumpButton.count() > 0) {
                    jumpButton.click();
                }

                // 滚动到页面顶部
                browserPage.evaluate("window.scrollTo(0, 0)");
                sleep(2);

                log.info("成功跳转到第{}页", pageNum);
                return true;
            } catch (Exception e) {
                log.warn("跳转到第{}页失败，重试第{}次: {}", pageNum, retry + 1, e.getMessage());
                sleep(1);

                // 检查是否出现异常，如果出现则刷新页面
                if (checkAccessVerification()) {
                    return false;
                }
                browserPage.reload();
                sleep(2);
            }
        }
        return false;
    }

    /**
     * 检测 51job 页面是否出现"日投递上限"提示的浮框（短暂存在，需及时检查）
     */
    private static boolean detectDailyLimitToast() {
        try {
            String[] kws = new String[]{
                    "今日投递太多", "您今日投递太多", "休息一下明天再来", "达到上限", "次数过多"
            };
            for (String kw : kws) {
                Locator textToast = browserPage.locator("text=" + kw);
                if (textToast.count() > 0 && textToast.first().isVisible()) {
                    return true;
                }
            }
            
            Locator msg = browserPage.locator(".el-message, .el-message--info, .toast, .message, div[role='alert'], .el-notification__content");
            if (msg.count() > 0) {
                java.util.List<String> texts = new java.util.ArrayList<>();
                try { texts = msg.allInnerTexts(); } catch (Exception ignored) {}
                for (String t : texts) {
                    if (t == null) continue;
                    String tt = t.replace('\n', ' ').trim();
                    for (String kw : kws) {
                        if (tt.contains(kw)) return true;
                    }
                }
            }
            
            Object foundObj = browserPage.evaluate("() => { const kws = ['今日投递太多','您今日投递太多','休息一下明天再来','达到上限','次数过多']; const bodyText = document.body ? (document.body.innerText || '') : ''; return kws.some(k=>bodyText.includes(k)); }");
            if (foundObj instanceof Boolean) {
                return (Boolean) foundObj;
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 检查是否出现访问验证
     */
    private static boolean checkAccessVerification() {
        try {
            Locator verify = browserPage.locator("//p[@class='waf-nc-title']");
            if (verify.count() > 0) {
                String text = verify.textContent();
                return text != null && text.contains("验证");
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 睡眠指定秒数
     */
    private static void sleep(int seconds) {
        try {
            Thread.sleep(seconds * 1000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
