package com.getjobs.worker.manager;

import com.microsoft.playwright.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.awt.*;

/**
 * Playwright管理器
 * Spring管理的单例Bean，在应用启动时自动初始化Playwright实例
 * 支持自适应屏幕大小
 */
@Component
public class PlaywrightManager {
    private static final Logger log = LoggerFactory.getLogger(PlaywrightManager.class);

    // Playwright实例
    private Playwright playwright;

    // 浏览器实例
    private Browser browser;

    // 桌面浏览器上下文
    private BrowserContext desktopContext;

    // 移动设备浏览器上下文
    private BrowserContext mobileContext;

    // 桌面浏览器页面
    private Page desktopPage;

    // 移动设备浏览器页面
    private Page mobilePage;

    // 默认超时时间（毫秒）
    private static final int DEFAULT_TIMEOUT = 30000;

    // 屏幕尺寸
    private int screenWidth;
    private int screenHeight;

    /**
     * 获取屏幕尺寸
     */
    private void detectScreenSize() {
        try {
            GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
            GraphicsDevice gd = ge.getDefaultScreenDevice();
            DisplayMode mode = gd.getDisplayMode();

            screenWidth = mode.getWidth();
            screenHeight = mode.getHeight();

            log.info("检测到屏幕尺寸: {}x{}", screenWidth, screenHeight);
        } catch (HeadlessException e) {
            // 无头环境，使用默认值
            screenWidth = 1920;
            screenHeight = 1080;
            log.warn("无法检测屏幕尺寸（无头环境），使用默认值: {}x{}", screenWidth, screenHeight);
        } catch (Exception e) {
            // 其他异常，使用默认值
            screenWidth = 1920;
            screenHeight = 1080;
            log.warn("检测屏幕尺寸失败，使用默认值: {}x{}", screenWidth, screenHeight, e);
        }
    }

    /**
     * 初始化Playwright实例
     * 在Spring容器启动后自动执行
     */
    @PostConstruct
    public void init() {
        log.info("开始初始化Playwright管理器...");

        // 检测屏幕尺寸
        detectScreenSize();

        try {
            // 启动Playwright
            playwright = Playwright.create();
            log.info("Playwright实例创建成功");

            // 创建浏览器实例
            browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                    .setHeadless(false) // 非无头模式，可视化调试
                    .setSlowMo(50)); // 放慢操作速度，便于调试
            log.info("浏览器实例创建成功");

            // 创建桌面浏览器上下文（使用自适应屏幕大小）
            desktopContext = browser.newContext(new Browser.NewContextOptions()
                    .setViewportSize(screenWidth, screenHeight)
                    .setUserAgent(
                            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/135.0.0.0 Safari/537.36"));
            log.info("桌面浏览器上下文创建成功（视口大小: {}x{}）", screenWidth, screenHeight);

            // 创建移动设备浏览器上下文
            mobileContext = browser.newContext(new Browser.NewContextOptions()
                    .setViewportSize(375, 812)
                    .setDeviceScaleFactor(3.0)
                    .setIsMobile(true)
                    .setHasTouch(true)
                    .setUserAgent(
                            "Mozilla/5.0 (iPhone; CPU iPhone OS 13_2_3 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/13.0.3 Mobile/15E148 Safari/604.1"));
            log.info("移动设备浏览器上下文创建成功");

            // 创建桌面页面
            desktopPage = desktopContext.newPage();
            desktopPage.setDefaultTimeout(DEFAULT_TIMEOUT);
            log.info("桌面浏览器页面创建成功");

            log.info("Playwright管理器初始化完成！");
        } catch (Exception e) {
            log.error("Playwright管理器初始化失败", e);
            throw new RuntimeException("Playwright初始化失败", e);
        }
    }

    /**
     * 关闭Playwright实例
     * 在Spring容器销毁前自动执行
     */
    @PreDestroy
    public void destroy() {
        log.info("开始关闭Playwright管理器...");

        try {
            if (desktopPage != null) {
                desktopPage.close();
                log.info("桌面页面已关闭");
            }
            if (mobilePage != null) {
                mobilePage.close();
                log.info("移动页面已关闭");
            }
            if (desktopContext != null) {
                desktopContext.close();
                log.info("桌面上下文已关闭");
            }
            if (mobileContext != null) {
                mobileContext.close();
                log.info("移动上下文已关闭");
            }
            if (browser != null) {
                browser.close();
                log.info("浏览器已关闭");
            }
            if (playwright != null) {
                playwright.close();
                log.info("Playwright实例已关闭");
            }

            log.info("Playwright管理器关闭完成！");
        } catch (Exception e) {
            log.error("关闭Playwright管理器时发生错误", e);
        }
    }

    /**
     * 获取桌面页面
     */
    public Page getDesktopPage() {
        return desktopPage;
    }

    /**
     * 获取移动页面（懒加载）
     */
    public Page getMobilePage() {
        if (mobilePage == null) {
            mobilePage = mobileContext.newPage();
            mobilePage.setDefaultTimeout(DEFAULT_TIMEOUT);
            log.info("移动浏览器页面创建成功");
        }
        return mobilePage;
    }

    /**
     * 获取桌面上下文
     */
    public BrowserContext getDesktopContext() {
        return desktopContext;
    }

    /**
     * 获取移动上下文
     */
    public BrowserContext getMobileContext() {
        return mobileContext;
    }

    /**
     * 获取浏览器实例
     */
    public Browser getBrowser() {
        return browser;
    }

    /**
     * 获取Playwright实例
     */
    public Playwright getPlaywright() {
        return playwright;
    }

    /**
     * 获取检测到的屏幕宽度
     */
    public int getScreenWidth() {
        return screenWidth;
    }

    /**
     * 获取检测到的屏幕高度
     */
    public int getScreenHeight() {
        return screenHeight;
    }

    /**
     * 检查Playwright是否已初始化
     */
    public boolean isInitialized() {
        return playwright != null && browser != null && desktopPage != null;
    }
}
