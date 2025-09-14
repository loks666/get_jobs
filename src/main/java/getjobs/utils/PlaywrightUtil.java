package getjobs.utils;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.Cookie;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.SelectOption;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * Playwright工具类，提供浏览器自动化相关的功能
 */
public class PlaywrightUtil {
    private static final Logger log = LoggerFactory.getLogger(PlaywrightUtil.class);

    // Playwright实例
    private static Playwright PLAYWRIGHT;

    // 浏览器实例
    private static Browser BROWSER;

    // 浏览器上下文
    private static BrowserContext CONTEXT;

    // 浏览器页面
    private static Page PAGE;

    // 默认超时时间（毫秒）
    private static final int DEFAULT_TIMEOUT = 30000;

    // 默认等待时间（毫秒）
    private static final int DEFAULT_WAIT_TIME = 10000;

    /**
     * 初始化Playwright及浏览器实例
     */
    public static void init() {
        // 启动Playwright
        PLAYWRIGHT = Playwright.create();

        // 创建浏览器实例
        BROWSER = PLAYWRIGHT.chromium().launch(new BrowserType.LaunchOptions()
                .setHeadless(false) // 非无头模式，可视化调试
                .setSlowMo(50)); // 放慢操作速度，便于调试

        // 创建浏览器上下文
        CONTEXT = BROWSER.newContext(new Browser.NewContextOptions()
                .setViewportSize(1920, 1080)
                .setUserAgent(
                        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/135.0.0.0 Safari/537.36"));

        // 创建页面
        PAGE = CONTEXT.newPage();
        PAGE.setDefaultTimeout(DEFAULT_TIMEOUT);

        // 启用JavaScript捕获控制台日志（用于调试）
        PAGE.onConsoleMessage(message -> {
            if (message.type().equals("error")) {
                log.error("Browser console error: {}", message.text());
            }
        });

        log.info("Playwright和浏览器实例已初始化完成");
    }

    /**
     * 关闭Playwright及浏览器实例
     */
    public static void close() {
        if (PAGE != null)
            PAGE.close();
        if (CONTEXT != null)
            CONTEXT.close();
        if (BROWSER != null)
            BROWSER.close();
        if (PLAYWRIGHT != null)
            PLAYWRIGHT.close();

        log.info("Playwright及浏览器实例已关闭");
    }

    /**
     * 导航到指定URL
     * 
     * @param url 目标URL
     */
    public static void navigate(String url) {
        PAGE.navigate(url);
        log.info("已导航到URL: {}", url);
    }

    /**
     * 等待指定时间（秒）
     * 
     * @param seconds 等待的秒数
     */
    public static void sleep(int seconds) {
        try {
            TimeUnit.SECONDS.sleep(seconds);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Sleep被中断", e);
        }
    }

    /**
     * 等待指定时间（毫秒）
     * 
     * @param millis 等待的毫秒数
     */
    public static void sleepMillis(int millis) {
        try {
            TimeUnit.MILLISECONDS.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Sleep被中断", e);
        }
    }

    /**
     * 查找元素
     * 
     * @param selector 元素选择器
     * @return 元素对象，如果未找到则返回null
     */
    public static Locator findElement(String selector) {
        return PAGE.locator(selector);
    }

    /**
     * 查找元素并等待直到可见
     * 
     * @param selector 元素选择器
     * @param timeout  超时时间（毫秒）
     * @return 元素对象，如果未找到则返回null
     */
    public static Locator waitForElement(String selector, int timeout) {
        Locator locator = PAGE.locator(selector);
        locator.waitFor(new Locator.WaitForOptions().setTimeout(timeout));
        return locator;
    }

    /**
     * 使用默认超时时间等待元素
     * 
     * @param selector 元素选择器
     * @return 元素对象，如果未找到则返回null
     */
    public static Locator waitForElement(String selector) {
        return waitForElement(selector, DEFAULT_WAIT_TIME);
    }

    /**
     * 点击元素
     * 
     * @param selector 元素选择器
     */
    public static void click(String selector) {
        try {
            PAGE.locator(selector).click();
            log.info("已点击元素: {}", selector);
        } catch (PlaywrightException e) {
            log.error("点击元素失败: {}", selector, e);
        }
    }

    /**
     * 填写表单字段
     * 
     * @param selector 元素选择器
     * @param text     要输入的文本
     */
    public static void fill(String selector, String text) {
        try {
            PAGE.locator(selector).fill(text);
            log.info("已在元素{}中输入文本", selector);
        } catch (PlaywrightException e) {
            log.error("填写表单失败: {}", selector, e);
        }
    }

    /**
     * 模拟人类输入文本（逐字输入）
     * 
     * @param selector 元素选择器
     * @param text     要输入的文本
     * @param minDelay 字符间最小延迟（毫秒）
     * @param maxDelay 字符间最大延迟（毫秒）
     */
    public static void typeHumanLike(String selector, String text, int minDelay, int maxDelay) {
        try {
            Locator locator = PAGE.locator(selector);
            locator.click();

            Random random = new Random();
            for (char c : text.toCharArray()) {
                // 计算本次字符输入的延迟时间
                int delay = random.nextInt(maxDelay - minDelay + 1) + minDelay;

                // 输入单个字符
                locator.pressSequentially(String.valueOf(c),
                        new Locator.PressSequentiallyOptions().setDelay(delay));
            }
            log.info("已模拟人类在元素{}中输入文本", selector);
        } catch (PlaywrightException e) {
            log.error("模拟人类输入失败: {}", selector, e);
        }
    }

    /**
     * 获取元素文本
     * 
     * @param selector 元素选择器
     * @return 元素文本内容
     */
    public static String getText(String selector) {
        try {
            return PAGE.locator(selector).textContent();
        } catch (PlaywrightException e) {
            log.error("获取元素文本失败: {}", selector, e);
            return "";
        }
    }

    /**
     * 获取元素属性值
     * 
     * @param selector      元素选择器
     * @param attributeName 属性名
     * @return 属性值
     */
    public static String getAttribute(String selector, String attributeName) {
        try {
            return PAGE.locator(selector).getAttribute(attributeName);
        } catch (PlaywrightException e) {
            log.error("获取元素属性失败: {}[{}]", selector, attributeName, e);
            return "";
        }
    }

    /**
     * 截取页面截图并保存
     * 
     * @param path 保存路径
     */
    public static void screenshot(String path) {
        try {
            PAGE.screenshot(new Page.ScreenshotOptions().setPath(Paths.get(path)));
            log.info("已保存截图到: {}", path);
        } catch (PlaywrightException e) {
            log.error("截图失败", e);
        }
    }

    /**
     * 截取特定元素的截图
     * 
     * @param selector 元素选择器
     * @param path     保存路径
     */
    public static void screenshotElement(String selector, String path) {
        try {
            PAGE.locator(selector).screenshot(new Locator.ScreenshotOptions().setPath(Paths.get(path)));
            log.info("已保存元素截图到: {}", path);
        } catch (PlaywrightException e) {
            log.error("元素截图失败: {}", selector, e);
        }
    }

    /**
     * 保存Cookie到文件
     * 
     * @param path 保存路径
     */
    public static void saveCookies(String path) {
        try {
            List<Cookie> cookies = CONTEXT.cookies();
            JSONArray jsonArray = new JSONArray();

            for (Cookie cookie : cookies) {
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
     * 从文件加载Cookie
     * 
     * @param path Cookie文件路径
     */
    public static void loadCookies(String path) {
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

            CONTEXT.addCookies(cookies);
            log.info("已从文件加载Cookie: {}", path);
        } catch (IOException e) {
            log.error("加载Cookie失败", e);
        }
    }

    /**
     * 执行JavaScript代码
     * 
     * @param script JavaScript代码
     * @return 执行结果
     */
    public static Object evaluate(String script) {
        try {
            return PAGE.evaluate(script);
        } catch (PlaywrightException e) {
            log.error("执行JavaScript失败", e);
            return null;
        }
    }

    /**
     * 模拟随机用户行为
     */
    public static void simulateRandomUserBehavior() {
        Random random = new Random();

        // 随机滚动
        int scrollY = random.nextInt(1001) - 500; // -500到500之间的随机值
        PAGE.evaluate("window.scrollBy(0," + scrollY + ")");

        sleepMillis(random.nextInt(500) + 200); // 200-700ms随机延迟

        // 有5%的概率进行截图
        if (random.nextInt(100) < 5) {
            screenshot(ProjectRootResolver.rootPath + "/screenshots/random_" + System.currentTimeMillis() + ".png");
        }

        log.debug("已模拟随机用户行为");
    }

    /**
     * 等待页面加载完成
     */
    public static void waitForPageLoad() {
        PAGE.waitForLoadState(LoadState.DOMCONTENTLOADED);
        PAGE.waitForLoadState(LoadState.NETWORKIDLE);
    }

    /**
     * 检查元素是否存在
     * 
     * @param selector 元素选择器
     * @return 是否存在
     */
    public static boolean elementExists(String selector) {
        return PAGE.locator(selector).count() > 0;
    }

    /**
     * 检查元素是否可见
     * 
     * @param selector 元素选择器
     * @return 是否可见
     */
    public static boolean elementIsVisible(String selector) {
        try {
            return PAGE.locator(selector).isVisible();
        } catch (PlaywrightException e) {
            return false;
        }
    }

    /**
     * 选择下拉列表选项（通过文本）
     * 
     * @param selector   选择器
     * @param optionText 选项文本
     */
    public static void selectByText(String selector, String optionText) {
        PAGE.locator(selector).selectOption(new SelectOption().setLabel(optionText));
    }

    /**
     * 选择下拉列表选项（通过值）
     * 
     * @param selector 选择器
     * @param value    选项值
     */
    public static void selectByValue(String selector, String value) {
        PAGE.locator(selector).selectOption(new SelectOption().setValue(value));
    }

    /**
     * 获取当前页面标题
     * 
     * @return 页面标题
     */
    public static String getTitle() {
        return PAGE.title();
    }

    /**
     * 获取当前页面URL
     * 
     * @return 页面URL
     */
    public static String getUrl() {
        return PAGE.url();
    }

    /**
     * 初始化Stealth模式（使浏览器更难被检测为自动化工具）
     */
    public static void initStealth() {
        // 创建桌面设备上下文
        BrowserContext context = BROWSER.newContext(new Browser.NewContextOptions()
                .setViewportSize(1920, 1080)
                .setUserAgent(
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/135.0.0.0 Safari/537.36")
                .setJavaScriptEnabled(true)
                .setBypassCSP(true)
                .setExtraHTTPHeaders(Map.of(
                        "sec-ch-ua", "\"Chromium\";v=\"135\", \"Not A(Brand\";v=\"99\"",
                        "sec-ch-ua-mobile", "?0",
                        "sec-ch-ua-platform", "\"Windows\"",
                        "sec-fetch-dest", "document",
                        "sec-fetch-mode", "navigate",
                        "sec-fetch-site", "same-origin",
                        "accept-language", "zh-CN,zh;q=0.9,en;q=0.8")));

        // 更新全局上下文
        CONTEXT = context;
        // 创建页面
        PAGE = CONTEXT.newPage();

        // 执行stealth.min.js（需要事先准备此文件）
        try {
            String stealthJs = new String(
                    Files.readAllBytes(Paths.get(ProjectRootResolver.rootPath + "/src/main/resources/stealth.min.js")));
            PAGE.addInitScript(stealthJs);
            log.info("已启用Stealth模式");
        } catch (IOException e) {
            log.error("启用Stealth模式失败，无法加载stealth.min.js", e);
        }
    }

    /**
     * 获取Page对象
     * 
     * @return Page对象
     */
    public static Page getPageObject() {
        return PAGE;
    }

    /**
     * 获取BrowserContext对象
     * 
     * @return BrowserContext对象
     */
    public static BrowserContext getContext() {
        return CONTEXT;
    }

    /**
     * 设置自定义Cookie
     * 
     * @param name     Cookie名称
     * @param value    Cookie值
     * @param domain   Cookie域
     * @param path     Cookie路径
     * @param expires  过期时间（可选）
     * @param secure   是否安全（可选）
     * @param httpOnly 是否仅HTTP（可选）
     */
    public static void setCookie(String name, String value, String domain, String path,
            Double expires, Boolean secure, Boolean httpOnly) {
        com.microsoft.playwright.options.Cookie cookie = new com.microsoft.playwright.options.Cookie(name, value);
        cookie.domain = domain;
        cookie.path = path;

        if (expires != null) {
            cookie.expires = expires;
        }

        if (secure != null) {
            cookie.secure = secure;
        }

        if (httpOnly != null) {
            cookie.httpOnly = httpOnly;
        }

        List<com.microsoft.playwright.options.Cookie> cookies = new ArrayList<>();
        cookies.add(cookie);

        CONTEXT.addCookies(cookies);
        log.info("已设置Cookie: {}", name);
    }

    /**
     * 简化的设置Cookie方法
     * 
     * @param name   Cookie名称
     * @param value  Cookie值
     * @param domain Cookie域
     * @param path   Cookie路径
     */
    public static void setCookie(String name, String value, String domain, String path) {
        setCookie(name, value, domain, path, null, null, null);
    }
}