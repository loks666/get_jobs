package com.getjobs.worker.manager;

import com.microsoft.playwright.*;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Playwright管理器
 * Spring管理的单例Bean，在应用启动时自动初始化Playwright实例
 * 浏览器窗口可手动拖拽调整大小，页面自适应
 */
@Data
@Component
public class PlaywrightManager {
    private static final Logger log = LoggerFactory.getLogger(PlaywrightManager.class);

    /**
     * -- GETTER --
     *  获取Playwright实例
     */
    // Playwright实例
    private Playwright playwright;

    /**
     * -- GETTER --
     *  获取浏览器实例
     */
    // 浏览器实例
    private Browser browser;

    /**
     * -- GETTER --
     *  获取浏览器上下文
     */
    // 浏览器上下文
    private BrowserContext context;

    /**
     * -- GETTER --
     *  获取页面
     */
    // 浏览器页面
    private Page page;

    // 默认超时时间（毫秒）
    private static final int DEFAULT_TIMEOUT = 30000;

    // Playwright调试端口
    private static final int CDP_PORT = 7866;

    /**
     * 初始化Playwright实例
     * 在Spring容器启动后自动执行
     */
    @PostConstruct
    public void init() {
        log.info("开始初始化Playwright管理器...");

        try {
            // 启动Playwright
            playwright = Playwright.create();
            log.info("Playwright实例创建成功");

            // 创建浏览器实例，使用固定CDP端口7866，最大化启动
            browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                    .setHeadless(false) // 非无头模式，可视化调试
                    .setSlowMo(50) // 放慢操作速度，便于调试
                    .setArgs(List.of(
                            "--remote-debugging-port=" + CDP_PORT, // 使用固定CDP端口
                            "--start-maximized" // 最大化启动窗口
                    )));
            log.info("浏览器实例创建成功（CDP端口: {}）", CDP_PORT);

            // 创建浏览器上下文，不设置viewport让窗口可自由拖拽调整
            context = browser.newContext(new Browser.NewContextOptions()
                    .setViewportSize(null) // 不设置固定视口，允许窗口自适应
                    .setUserAgent(
                            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/135.0.0.0 Safari/537.36"));
            log.info("浏览器上下文创建成功（视口: 自适应，可手动调整窗口大小）");

            // 创建页面
            page = context.newPage();
            page.setDefaultTimeout(DEFAULT_TIMEOUT);
            log.info("浏览器页面创建成功");

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
            if (page != null) {
                page.close();
                log.info("页面已关闭");
            }
            if (context != null) {
                context.close();
                log.info("上下文已关闭");
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
     * 检查Playwright是否已初始化
     */
    public boolean isInitialized() {
        return playwright != null && browser != null && page != null;
    }

    /**
     * 获取CDP端口号
     */
    public int getCdpPort() {
        return CDP_PORT;
    }
}
