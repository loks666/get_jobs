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
     * 初始化Stealth模式（使浏览器更难被检测为自动化工具）
     */
    @Deprecated
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