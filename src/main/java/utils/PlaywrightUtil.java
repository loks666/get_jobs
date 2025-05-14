package utils;

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

    /**
     * 设备类型枚举
     */
    public enum DeviceType {
        DESKTOP, // 桌面设备
        MOBILE // 移动设备
    }

    // 默认设备类型
    private static DeviceType defaultDeviceType = DeviceType.DESKTOP;

    // Playwright实例
    private static Playwright PLAYWRIGHT;

    // 浏览器实例
    private static Browser BROWSER;

    // 桌面浏览器上下文
    private static BrowserContext DESKTOP_CONTEXT;

    // 移动设备浏览器上下文
    private static BrowserContext MOBILE_CONTEXT;

    // 桌面浏览器页面
    private static Page DESKTOP_PAGE;

    // 移动设备浏览器页面
    private static Page MOBILE_PAGE;

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

        // 创建桌面浏览器上下文
        DESKTOP_CONTEXT = BROWSER.newContext(new Browser.NewContextOptions()
                .setViewportSize(1920, 1080)
                .setUserAgent(
                        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/135.0.0.0 Safari/537.36"));

        // 创建移动设备浏览器上下文
        MOBILE_CONTEXT = BROWSER.newContext(new Browser.NewContextOptions()
                .setViewportSize(375, 812)
                .setDeviceScaleFactor(3.0)
                .setIsMobile(true)
                .setHasTouch(true)
                .setUserAgent(
                        "Mozilla/5.0 (iPhone; CPU iPhone OS 13_2_3 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/13.0.3 Mobile/15E148 Safari/604.1"));

        // 创建桌面页面
        DESKTOP_PAGE = DESKTOP_CONTEXT.newPage();
        DESKTOP_PAGE.setDefaultTimeout(DEFAULT_TIMEOUT);

        // 创建移动设备页面
        MOBILE_PAGE = MOBILE_CONTEXT.newPage();
        MOBILE_PAGE.setDefaultTimeout(DEFAULT_TIMEOUT);

        // 启用JavaScript捕获控制台日志（用于调试）
        DESKTOP_PAGE.onConsoleMessage(message -> {
            if (message.type().equals("error")) {
                log.error("Browser console error: {}", message.text());
            }
        });

        MOBILE_PAGE.onConsoleMessage(message -> {
            if (message.type().equals("error")) {
                log.error("Mobile browser console error: {}", message.text());
            }
        });

        log.info("Playwright和浏览器实例已初始化完成");
    }

    /**
     * 设置默认设备类型
     * 
     * @param deviceType 设备类型
     */
    public static void setDefaultDeviceType(DeviceType deviceType) {
        defaultDeviceType = deviceType;
        log.info("已设置默认设备类型为: {}", deviceType);
    }

    /**
     * 获取当前页面（基于当前设备类型）
     * 
     * @param deviceType 设备类型
     * @return 对应的Page对象
     */
    private static Page getPage(DeviceType deviceType) {
        return deviceType == DeviceType.DESKTOP ? DESKTOP_PAGE : MOBILE_PAGE;
    }

    /**
     * 获取当前上下文（基于当前设备类型）
     * 
     * @param deviceType 设备类型
     * @return 对应的BrowserContext对象
     */
    private static BrowserContext getContext(DeviceType deviceType) {
        return deviceType == DeviceType.DESKTOP ? DESKTOP_CONTEXT : MOBILE_CONTEXT;
    }

    /**
     * 关闭Playwright及浏览器实例
     */
    public static void close() {
        if (DESKTOP_PAGE != null)
            DESKTOP_PAGE.close();
        if (MOBILE_PAGE != null)
            MOBILE_PAGE.close();
        if (DESKTOP_CONTEXT != null)
            DESKTOP_CONTEXT.close();
        if (MOBILE_CONTEXT != null)
            MOBILE_CONTEXT.close();
        if (BROWSER != null)
            BROWSER.close();
        if (PLAYWRIGHT != null)
            PLAYWRIGHT.close();

        log.info("Playwright及浏览器实例已关闭");
    }

    /**
     * 导航到指定URL
     * 
     * @param url        目标URL
     * @param deviceType 设备类型
     */
    public static void navigate(String url, DeviceType deviceType) {
        getPage(deviceType).navigate(url);
        log.info("已导航到URL: {} (设备类型: {})", url, deviceType);
    }

    /**
     * 使用默认设备类型导航到指定URL
     * 
     * @param url 目标URL
     */
    public static void navigate(String url) {
        navigate(url, defaultDeviceType);
    }

    /**
     * 移动设备导航到指定URL (兼容旧代码)
     * 
     * @param url 目标URL
     */
    public static void mobileNavigate(String url) {
        navigate(url, DeviceType.MOBILE);
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
     * @param selector   元素选择器
     * @param deviceType 设备类型
     * @return 元素对象，如果未找到则返回null
     */
    public static Locator findElement(String selector, DeviceType deviceType) {
        return getPage(deviceType).locator(selector);
    }

    /**
     * 使用默认设备类型查找元素
     * 
     * @param selector 元素选择器
     * @return 元素对象，如果未找到则返回null
     */
    public static Locator findElement(String selector) {
        return findElement(selector, defaultDeviceType);
    }

    /**
     * 查找元素并等待直到可见
     * 
     * @param selector   元素选择器
     * @param timeout    超时时间（毫秒）
     * @param deviceType 设备类型
     * @return 元素对象，如果未找到则返回null
     */
    public static Locator waitForElement(String selector, int timeout, DeviceType deviceType) {
        Locator locator = getPage(deviceType).locator(selector);
        locator.waitFor(new Locator.WaitForOptions().setTimeout(timeout));
        return locator;
    }

    /**
     * 使用默认设备类型查找元素并等待直到可见
     * 
     * @param selector 元素选择器
     * @param timeout  超时时间（毫秒）
     * @return 元素对象，如果未找到则返回null
     */
    public static Locator waitForElement(String selector, int timeout) {
        return waitForElement(selector, timeout, defaultDeviceType);
    }

    /**
     * 使用默认超时时间和默认设备类型等待元素
     * 
     * @param selector 元素选择器
     * @return 元素对象，如果未找到则返回null
     */
    public static Locator waitForElement(String selector) {
        return waitForElement(selector, DEFAULT_WAIT_TIME, defaultDeviceType);
    }

    /**
     * 点击元素
     * 
     * @param selector   元素选择器
     * @param deviceType 设备类型
     */
    public static void click(String selector, DeviceType deviceType) {
        try {
            getPage(deviceType).locator(selector).click();
            log.info("已点击元素: {} (设备类型: {})", selector, deviceType);
        } catch (PlaywrightException e) {
            log.error("点击元素失败: {} (设备类型: {})", selector, deviceType, e);
        }
    }

    /**
     * 使用默认设备类型点击元素
     * 
     * @param selector 元素选择器
     */
    public static void click(String selector) {
        click(selector, defaultDeviceType);
    }

    /**
     * 填写表单字段
     * 
     * @param selector   元素选择器
     * @param text       要输入的文本
     * @param deviceType 设备类型
     */
    public static void fill(String selector, String text, DeviceType deviceType) {
        try {
            getPage(deviceType).locator(selector).fill(text);
            log.info("已在元素{}中输入文本 (设备类型: {})", selector, deviceType);
        } catch (PlaywrightException e) {
            log.error("填写表单失败: {} (设备类型: {})", selector, deviceType, e);
        }
    }

    /**
     * 使用默认设备类型填写表单字段
     * 
     * @param selector 元素选择器
     * @param text     要输入的文本
     */
    public static void fill(String selector, String text) {
        fill(selector, text, defaultDeviceType);
    }

    /**
     * 模拟人类输入文本（逐字输入）
     * 
     * @param selector   元素选择器
     * @param text       要输入的文本
     * @param minDelay   字符间最小延迟（毫秒）
     * @param maxDelay   字符间最大延迟（毫秒）
     * @param deviceType 设备类型
     */
    public static void typeHumanLike(String selector, String text, int minDelay, int maxDelay, DeviceType deviceType) {
        try {
            Locator locator = getPage(deviceType).locator(selector);
            locator.click();

            Random random = new Random();
            for (char c : text.toCharArray()) {
                // 计算本次字符输入的延迟时间
                int delay = random.nextInt(maxDelay - minDelay + 1) + minDelay;

                // 输入单个字符
                locator.pressSequentially(String.valueOf(c),
                        new Locator.PressSequentiallyOptions().setDelay(delay));
            }
            log.info("已模拟人类在元素{}中输入文本 (设备类型: {})", selector, deviceType);
        } catch (PlaywrightException e) {
            log.error("模拟人类输入失败: {} (设备类型: {})", selector, deviceType, e);
        }
    }

    /**
     * 使用默认设备类型模拟人类输入文本
     * 
     * @param selector 元素选择器
     * @param text     要输入的文本
     * @param minDelay 字符间最小延迟（毫秒）
     * @param maxDelay 字符间最大延迟（毫秒）
     */
    public static void typeHumanLike(String selector, String text, int minDelay, int maxDelay) {
        typeHumanLike(selector, text, minDelay, maxDelay, defaultDeviceType);
    }

    /**
     * 获取元素文本
     * 
     * @param selector   元素选择器
     * @param deviceType 设备类型
     * @return 元素文本内容
     */
    public static String getText(String selector, DeviceType deviceType) {
        try {
            return getPage(deviceType).locator(selector).textContent();
        } catch (PlaywrightException e) {
            log.error("获取元素文本失败: {} (设备类型: {})", selector, deviceType, e);
            return "";
        }
    }

    /**
     * 使用默认设备类型获取元素文本
     * 
     * @param selector 元素选择器
     * @return 元素文本内容
     */
    public static String getText(String selector) {
        return getText(selector, defaultDeviceType);
    }

    /**
     * 获取元素属性值
     * 
     * @param selector      元素选择器
     * @param attributeName 属性名
     * @param deviceType    设备类型
     * @return 属性值
     */
    public static String getAttribute(String selector, String attributeName, DeviceType deviceType) {
        try {
            return getPage(deviceType).locator(selector).getAttribute(attributeName);
        } catch (PlaywrightException e) {
            log.error("获取元素属性失败: {}[{}] (设备类型: {})", selector, attributeName, deviceType, e);
            return "";
        }
    }

    /**
     * 使用默认设备类型获取元素属性值
     * 
     * @param selector      元素选择器
     * @param attributeName 属性名
     * @return 属性值
     */
    public static String getAttribute(String selector, String attributeName) {
        return getAttribute(selector, attributeName, defaultDeviceType);
    }

    /**
     * 截取页面截图并保存
     * 
     * @param path       保存路径
     * @param deviceType 设备类型
     */
    public static void screenshot(String path, DeviceType deviceType) {
        try {
            getPage(deviceType).screenshot(new Page.ScreenshotOptions().setPath(Paths.get(path)));
            log.info("已保存截图到: {} (设备类型: {})", path, deviceType);
        } catch (PlaywrightException e) {
            log.error("截图失败 (设备类型: {})", deviceType, e);
        }
    }

    /**
     * 使用默认设备类型截取页面截图并保存
     * 
     * @param path 保存路径
     */
    public static void screenshot(String path) {
        screenshot(path, defaultDeviceType);
    }

    /**
     * 截取特定元素的截图
     * 
     * @param selector   元素选择器
     * @param path       保存路径
     * @param deviceType 设备类型
     */
    public static void screenshotElement(String selector, String path, DeviceType deviceType) {
        try {
            getPage(deviceType).locator(selector).screenshot(new Locator.ScreenshotOptions().setPath(Paths.get(path)));
            log.info("已保存元素截图到: {} (设备类型: {})", path, deviceType);
        } catch (PlaywrightException e) {
            log.error("元素截图失败: {} (设备类型: {})", selector, deviceType, e);
        }
    }

    /**
     * 使用默认设备类型截取特定元素的截图
     * 
     * @param selector 元素选择器
     * @param path     保存路径
     */
    public static void screenshotElement(String selector, String path) {
        screenshotElement(selector, path, defaultDeviceType);
    }

    /**
     * 保存Cookie到文件
     * 
     * @param path       保存路径
     * @param deviceType 设备类型
     */
    public static void saveCookies(String path, DeviceType deviceType) {
        try {
            List<Cookie> cookies = getContext(deviceType).cookies();
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
                log.info("Cookie已保存到文件: {} (设备类型: {})", path, deviceType);
            }
        } catch (IOException e) {
            log.error("保存Cookie失败 (设备类型: {})", deviceType, e);
        }
    }

    /**
     * 使用默认设备类型保存Cookie到文件
     * 
     * @param path 保存路径
     */
    public static void saveCookies(String path) {
        saveCookies(path, defaultDeviceType);
    }

    /**
     * 从文件加载Cookie
     * 
     * @param path       Cookie文件路径
     * @param deviceType 设备类型
     */
    public static void loadCookies(String path, DeviceType deviceType) {
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

            getContext(deviceType).addCookies(cookies);
            log.info("已从文件加载Cookie: {} (设备类型: {})", path, deviceType);
        } catch (IOException e) {
            log.error("加载Cookie失败 (设备类型: {})", deviceType, e);
        }
    }

    /**
     * 使用默认设备类型从文件加载Cookie
     * 
     * @param path Cookie文件路径
     */
    public static void loadCookies(String path) {
        loadCookies(path, defaultDeviceType);
        loadCookies(path, DeviceType.MOBILE);
    }

    /**
     * 执行JavaScript代码
     * 
     * @param script     JavaScript代码
     * @param deviceType 设备类型
     * @return 执行结果
     */
    public static Object evaluate(String script, DeviceType deviceType) {
        try {
            return getPage(deviceType).evaluate(script);
        } catch (PlaywrightException e) {
            log.error("执行JavaScript失败 (设备类型: {})", deviceType, e);
            return null;
        }
    }

    /**
     * 使用默认设备类型执行JavaScript代码
     * 
     * @param script JavaScript代码
     * @return 执行结果
     */
    public static Object evaluate(String script) {
        return evaluate(script, defaultDeviceType);
    }

    /**
     * 模拟随机用户行为
     * 
     * @param deviceType 设备类型
     */
    public static void simulateRandomUserBehavior(DeviceType deviceType) {
        Random random = new Random();

        // 随机滚动
        int scrollY = random.nextInt(1001) - 500; // -500到500之间的随机值
        getPage(deviceType).evaluate("window.scrollBy(0," + scrollY + ")");

        sleepMillis(random.nextInt(500) + 200); // 200-700ms随机延迟

        // 有5%的概率进行截图
        if (random.nextInt(100) < 5) {
            screenshot(ProjectRootResolver.rootPath + "/screenshots/random_" + System.currentTimeMillis() + ".png",
                    deviceType);
        }

        log.debug("已模拟随机用户行为 (设备类型: {})", deviceType);
    }

    /**
     * 使用默认设备类型模拟随机用户行为
     */
    public static void simulateRandomUserBehavior() {
        simulateRandomUserBehavior(defaultDeviceType);
    }

    /**
     * 等待页面加载完成
     * 
     * @param deviceType 设备类型
     */
    public static void waitForPageLoad(DeviceType deviceType) {
        getPage(deviceType).waitForLoadState(LoadState.DOMCONTENTLOADED);
        getPage(deviceType).waitForLoadState(LoadState.NETWORKIDLE);
    }

    /**
     * 使用默认设备类型等待页面加载完成
     */
    public static void waitForPageLoad() {
        waitForPageLoad(defaultDeviceType);
    }

    /**
     * 检查元素是否存在
     * 
     * @param selector   元素选择器
     * @param deviceType 设备类型
     * @return 是否存在
     */
    public static boolean elementExists(String selector, DeviceType deviceType) {
        return getPage(deviceType).locator(selector).count() > 0;
    }

    /**
     * 使用默认设备类型检查元素是否存在
     * 
     * @param selector 元素选择器
     * @return 是否存在
     */
    public static boolean elementExists(String selector) {
        return elementExists(selector, defaultDeviceType);
    }

    /**
     * 检查元素是否可见
     * 
     * @param selector   元素选择器
     * @param deviceType 设备类型
     * @return 是否可见
     */
    public static boolean elementIsVisible(String selector, DeviceType deviceType) {
        try {
            return getPage(deviceType).locator(selector).isVisible();
        } catch (PlaywrightException e) {
            return false;
        }
    }

    /**
     * 使用默认设备类型检查元素是否可见
     * 
     * @param selector 元素选择器
     * @return 是否可见
     */
    public static boolean elementIsVisible(String selector) {
        return elementIsVisible(selector, defaultDeviceType);
    }

    /**
     * 选择下拉列表选项（通过文本）
     * 
     * @param selector   选择器
     * @param optionText 选项文本
     * @param deviceType 设备类型
     */
    public static void selectByText(String selector, String optionText, DeviceType deviceType) {
        getPage(deviceType).locator(selector).selectOption(new SelectOption().setLabel(optionText));
    }

    /**
     * 使用默认设备类型选择下拉列表选项（通过文本）
     * 
     * @param selector   选择器
     * @param optionText 选项文本
     */
    public static void selectByText(String selector, String optionText) {
        selectByText(selector, optionText, defaultDeviceType);
    }

    /**
     * 选择下拉列表选项（通过值）
     * 
     * @param selector   选择器
     * @param value      选项值
     * @param deviceType 设备类型
     */
    public static void selectByValue(String selector, String value, DeviceType deviceType) {
        getPage(deviceType).locator(selector).selectOption(new SelectOption().setValue(value));
    }

    /**
     * 使用默认设备类型选择下拉列表选项（通过值）
     * 
     * @param selector 选择器
     * @param value    选项值
     */
    public static void selectByValue(String selector, String value) {
        selectByValue(selector, value, defaultDeviceType);
    }

    /**
     * 获取当前页面标题
     * 
     * @param deviceType 设备类型
     * @return 页面标题
     */
    public static String getTitle(DeviceType deviceType) {
        return getPage(deviceType).title();
    }

    /**
     * 使用默认设备类型获取当前页面标题
     * 
     * @return 页面标题
     */
    public static String getTitle() {
        return getTitle(defaultDeviceType);
    }

    /**
     * 获取当前页面URL
     * 
     * @param deviceType 设备类型
     * @return 页面URL
     */
    public static String getUrl(DeviceType deviceType) {
        return getPage(deviceType).url();
    }

    /**
     * 使用默认设备类型获取当前页面URL
     * 
     * @return 页面URL
     */
    public static String getUrl() {
        return getUrl(defaultDeviceType);
    }

    /**
     * 初始化Stealth模式（使浏览器更难被检测为自动化工具）
     * 
     * @param deviceType 设备类型
     */
    public static void initStealth(DeviceType deviceType) {
        BrowserContext context;

        // 根据设备类型创建相应的上下文
        if (deviceType == DeviceType.DESKTOP) {
            // 桌面设备上下文
            context = BROWSER.newContext(new Browser.NewContextOptions()
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
            DESKTOP_CONTEXT = context;
            // 创建页面
            DESKTOP_PAGE = DESKTOP_CONTEXT.newPage();
        } else {
            // 移动设备上下文
            context = BROWSER.newContext(new Browser.NewContextOptions()
                    .setViewportSize(375, 812)
                    .setDeviceScaleFactor(3.0)
                    .setIsMobile(true)
                    .setHasTouch(true)
                    .setUserAgent(
                            "Mozilla/5.0 (iPhone; CPU iPhone OS 13_2_3 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/13.0.3 Mobile/15E148 Safari/604.1")
                    .setJavaScriptEnabled(true)
                    .setBypassCSP(true)
                    .setExtraHTTPHeaders(Map.of(
                            "sec-ch-ua", "\"Chromium\";v=\"135\", \"Not A(Brand\";v=\"99\"",
                            "sec-ch-ua-mobile", "?1",
                            "sec-ch-ua-platform", "\"iOS\"",
                            "sec-fetch-dest", "document",
                            "sec-fetch-mode", "navigate",
                            "sec-fetch-site", "same-origin",
                            "accept-language", "zh-CN,zh;q=0.9,en;q=0.8")));

            // 更新全局上下文
            MOBILE_CONTEXT = context;
            // 创建页面
            MOBILE_PAGE = MOBILE_CONTEXT.newPage();
        }

        // 获取当前页面
        Page page = getPage(deviceType);

        // 执行stealth.min.js（需要事先准备此文件）
        try {
            String stealthJs = new String(
                    Files.readAllBytes(Paths.get(ProjectRootResolver.rootPath + "/src/main/resources/stealth.min.js")));
            page.addInitScript(stealthJs);
            log.info("已启用Stealth模式 (设备类型: {})", deviceType);
        } catch (IOException e) {
            log.error("启用Stealth模式失败，无法加载stealth.min.js (设备类型: {})", deviceType, e);
        }
    }

    /**
     * 使用默认设备类型初始化Stealth模式
     */
    public static void initStealth() {
        initStealth(defaultDeviceType);
    }

    /**
     * 获取当前设备类型的Page对象
     * 
     * @param deviceType 设备类型
     * @return 对应的Page对象
     */
    public static Page getPageObject(DeviceType deviceType) {
        return deviceType == DeviceType.DESKTOP ? DESKTOP_PAGE : MOBILE_PAGE;
    }

    /**
     * 使用默认设备类型获取Page对象
     * 
     * @return 对应的Page对象
     */
    public static Page getPageObject() {
        return getPageObject(defaultDeviceType);
    }

    /**
     * 设置自定义Cookie
     * 
     * @param name       Cookie名称
     * @param value      Cookie值
     * @param domain     Cookie域
     * @param path       Cookie路径
     * @param expires    过期时间（可选）
     * @param secure     是否安全（可选）
     * @param httpOnly   是否仅HTTP（可选）
     * @param deviceType 设备类型
     */
    public static void setCookie(String name, String value, String domain, String path,
            Double expires, Boolean secure, Boolean httpOnly, DeviceType deviceType) {
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

        getContext(deviceType).addCookies(cookies);
        log.info("已设置Cookie: {} (设备类型: {})", name, deviceType);
    }

    /**
     * 使用默认设备类型设置自定义Cookie
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
        setCookie(name, value, domain, path, expires, secure, httpOnly, defaultDeviceType);
    }

    /**
     * 简化的设置Cookie方法
     * 
     * @param name       Cookie名称
     * @param value      Cookie值
     * @param domain     Cookie域
     * @param path       Cookie路径
     * @param deviceType 设备类型
     */
    public static void setCookie(String name, String value, String domain, String path, DeviceType deviceType) {
        setCookie(name, value, domain, path, null, null, null, deviceType);
    }

    /**
     * 使用默认设备类型的简化设置Cookie方法
     * 
     * @param name   Cookie名称
     * @param value  Cookie值
     * @param domain Cookie域
     * @param path   Cookie路径
     */
    public static void setCookie(String name, String value, String domain, String path) {
        setCookie(name, value, domain, path, null, null, null, defaultDeviceType);
    }
}