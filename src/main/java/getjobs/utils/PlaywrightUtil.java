package getjobs.utils;

import com.microsoft.playwright.*;
import lombok.extern.slf4j.Slf4j;

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
@Slf4j
public class PlaywrightUtil {

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
    
    // 随机User-Agent列表
    private static final String[] USER_AGENTS = {
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/135.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/135.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.36"
    };

    /**
     * 创建HTTP头部信息
     * 
     * @return HTTP头部Map
     */
    private static Map<String, String> createHeaders() {
        Map<String, String> headers = new java.util.HashMap<>();
        headers.put("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7");
        headers.put("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");
        headers.put("Accept-Encoding", "gzip, deflate, br");
        headers.put("Cache-Control", "no-cache");
        headers.put("Pragma", "no-cache");
        headers.put("sec-ch-ua", "\"Chromium\";v=\"135\", \"Not A(Brand\";v=\"99\"");
        headers.put("sec-ch-ua-mobile", "?0");
        headers.put("sec-ch-ua-platform", "\"Windows\"");
        headers.put("sec-fetch-dest", "document");
        headers.put("sec-fetch-mode", "navigate");
        headers.put("sec-fetch-site", "none");
        headers.put("sec-fetch-user", "?1");
        headers.put("upgrade-insecure-requests", "1");
        return headers;
    }

    /**
     * 获取随机User-Agent
     * @return 随机User-Agent字符串
     */
    public static String getRandomUserAgent() {
        Random random = new Random();
        return USER_AGENTS[random.nextInt(USER_AGENTS.length)];
    }

    /**
     * 初始化Playwright及浏览器实例
     */
    public static void init() {
        // 启动Playwright
        PLAYWRIGHT = Playwright.create();

        List<String> args = List.of(
                "--disable-blink-features=AutomationControlled", // 禁用自动化控制特征
                "--disable-web-security", // 禁用web安全
                "--disable-features=VizDisplayCompositor", // 禁用一些特征
                "--disable-dev-shm-usage", // 禁用/dev/shm使用
                "--no-sandbox", // 禁用沙箱
                "--disable-extensions", // 禁用扩展
                "--disable-plugins", // 禁用插件
                "--disable-default-apps", // 禁用默认应用
                "--disable-background-timer-throttling", // 禁用后台定时器限制
                "--disable-renderer-backgrounding", // 禁用渲染器后台处理
                "--disable-backgrounding-occluded-windows", // 禁用隐藏窗口后台处理
                "--disable-ipc-flooding-protection" // 禁用IPC洪水保护
        );

        // 创建浏览器实例
        BROWSER = PLAYWRIGHT.chromium().launch(new BrowserType.LaunchOptions()
                .setHeadless(false) // 非无头模式，可视化调试
                .setSlowMo(50) // 放慢操作速度，便于调试
                .setArgs(List.of()));

    // 随机选择User-Agent
    String randomUserAgent = getRandomUserAgent();
        
        // 创建浏览器上下文，增强伪装设置
        CONTEXT = BROWSER.newContext(new Browser.NewContextOptions()
                .setUserAgent(randomUserAgent)
                .setJavaScriptEnabled(true)
                .setBypassCSP(true)
                .setPermissions(List.of("geolocation", "notifications")) // 添加一些常见权限
//                .setExtraHTTPHeaders(createHeaders())
                .setLocale("zh-CN")
                .setTimezoneId("Asia/Shanghai"));

        // 创建页面
        PAGE = CONTEXT.newPage();
        PAGE.setDefaultTimeout(DEFAULT_TIMEOUT);

        // 注入反检测JavaScript代码
//        PAGE.addInitScript("""
//            // 移除webdriver属性
//            Object.defineProperty(navigator, 'webdriver', {
//                get: () => undefined,
//            });
//
//            // 重写plugins属性
//            Object.defineProperty(navigator, 'plugins', {
//                get: () => [1, 2, 3, 4, 5],
//            });
//
//            // 重写languages属性
//            Object.defineProperty(navigator, 'languages', {
//                get: () => ['zh-CN', 'zh', 'en'],
//            });
//
//            // 重写permissions查询
//            const originalQuery = window.navigator.permissions.query;
//            window.navigator.permissions.query = (parameters) => (
//                parameters.name === 'notifications' ?
//                    Promise.resolve({ state: Notification.permission }) :
//                    originalQuery(parameters)
//            );
//
//            // 隐藏Chrome automation扩展
//            window.chrome = {
//                runtime: {},
//            };
//
//            // 重写Object.getOwnPropertyDescriptor
//            const getOwnPropertyDescriptor = Object.getOwnPropertyDescriptor;
//            Object.getOwnPropertyDescriptor = function(obj, prop) {
//                if (prop === 'webdriver') {
//                    return undefined;
//                }
//                return getOwnPropertyDescriptor(obj, prop);
//            };
//        """);

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
     * 随机延迟等待（秒）
     *
     * @param minSeconds 最小等待秒数
     * @param maxSeconds 最大等待秒数
     */
    public static void randomSleep(int minSeconds, int maxSeconds) {
        try {
            Random random = new Random();
            int randomSeconds = random.nextInt(maxSeconds - minSeconds + 1) + minSeconds;
            log.info("随机延迟{}秒", randomSeconds);
            TimeUnit.SECONDS.sleep(randomSeconds);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("随机Sleep被中断", e);
        }
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
            
            // 先模拟鼠标移动到元素
            simulateHumanMouseMove(selector);
            
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
     * 模拟人类鼠标移动
     *
     * @param selector 目标元素选择器
     */
    public static void simulateHumanMouseMove(String selector) {
        try {
            Locator locator = PAGE.locator(selector);
            
            // 获取元素边界框
            var boundingBox = locator.boundingBox();
            if (boundingBox != null) {
                Random random = new Random();
                
                // 在元素范围内随机选择一个点
                double targetX = boundingBox.x + random.nextDouble() * boundingBox.width;
                double targetY = boundingBox.y + random.nextDouble() * boundingBox.height;
                
                // 模拟鼠标移动轨迹
                PAGE.mouse().move(targetX, targetY);
                
                // 添加随机短暂停留
                randomSleep(1, 2);
            }
        } catch (PlaywrightException e) {
            log.error("模拟鼠标移动失败: {}", selector, e);
        }
    }

    /**
     * 模拟人类点击行为（包含鼠标移动）
     *
     * @param selector 元素选择器
     */
    public static void clickHumanLike(String selector) {
        try {
            // 先模拟鼠标移动
            simulateHumanMouseMove(selector);
            
            // 随机延迟
            randomSleep(1, 3);
            
            // 点击元素
            PAGE.locator(selector).click();
            log.info("已模拟人类点击元素: {}", selector);
        } catch (PlaywrightException e) {
            log.error("模拟人类点击失败: {}", selector, e);
        }
    }

    /**
     * 模拟人类滚动行为
     *
     * @param scrollAmount 滚动量（像素）
     * @param steps        滚动步数
     */
    public static void simulateHumanScroll(int scrollAmount, int steps) {
        try {
            Random random = new Random();
            int stepSize = scrollAmount / steps;
            
            for (int i = 0; i < steps; i++) {
                // 随机化每步的滚动量
                int currentStep = stepSize + random.nextInt(20) - 10;
                
                PAGE.mouse().wheel(0, currentStep);
                
                // 随机延迟
                int delay = 100 + random.nextInt(200);
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            log.info("已模拟人类滚动行为");
        } catch (PlaywrightException e) {
            log.error("模拟滚动失败", e);
        }
    }

    /**
     * 随机模拟页面浏览行为
     */
    public static void simulateRandomBrowsing() {
        try {
            Random random = new Random();
            
            // 随机滚动
            int scrollDirection = random.nextBoolean() ? 1 : -1;
            int scrollAmount = 200 + random.nextInt(300);
            simulateHumanScroll(scrollAmount * scrollDirection, 3 + random.nextInt(5));
            
            // 随机暂停
            randomSleep(2, 5);
            
            // 随机移动鼠标到页面某处
            int pageWidth = 1920; // 假设页面宽度
            int pageHeight = 1080; // 假设页面高度
            double randomX = random.nextDouble() * pageWidth;
            double randomY = random.nextDouble() * pageHeight;
            PAGE.mouse().move(randomX, randomY);
            
            log.info("已模拟随机浏览行为");
        } catch (PlaywrightException e) {
            log.error("模拟随机浏览失败", e);
        }
    }


    /**
     * 初始化Stealth模式（使浏览器更难被检测为自动化工具）
     */
    @Deprecated
    public static void initStealth() {
        // 创建桌面设备上下文
        BrowserContext context = BROWSER.newContext(new Browser.NewContextOptions()
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
                    Files.readAllBytes(Paths.get( "/src/main/resources/stealth.min.js")));
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
     * 获取Browser对象
     *
     * @return Browser对象
     */
    public static Browser getBrowser() {
        return BROWSER;
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

}
