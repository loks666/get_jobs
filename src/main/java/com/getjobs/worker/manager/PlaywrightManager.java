package com.getjobs.worker.manager;

import com.microsoft.playwright.*;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Playwright管理器
 * Spring管理的单例Bean，在应用启动时自动初始化Playwright实例
 * 支持4个求职平台的独立BrowserContext和登录状态监控
 */
@Getter
@Component
public class PlaywrightManager {
    private static final Logger log = LoggerFactory.getLogger(PlaywrightManager.class);

    // Playwright实例
    private Playwright playwright;

    // 浏览器实例（所有平台共享）
    private Browser browser;

    // Boss直聘 - 独立上下文和页面
    private BrowserContext bossContext;
    private Page bossPage;

    // 猎聘 - 独立上下文和页面（预留）
    private BrowserContext liepinContext;
    private Page liepinPage;

    // 51job - 独立上下文和页面（预留）
    private BrowserContext job51Context;
    private Page job51Page;

    // 智联招聘 - 独立上下文和页面（预留）
    private BrowserContext zhilianContext;
    private Page zhilianPage;

    // 登录状态追踪（平台 -> 是否已登录）
    private final Map<String, Boolean> loginStatus = new ConcurrentHashMap<>();

    // 登录状态监听器
    private final List<Consumer<LoginStatusChange>> loginStatusListeners = new CopyOnWriteArrayList<>();

    // 默认超时时间（毫秒）
    private static final int DEFAULT_TIMEOUT = 30000;

    // Playwright调试端口
    private static final int CDP_PORT = 7866;

    // 平台URL常量
    private static final String BOSS_URL = "https://www.zhipin.com";
    private static final String LIEPIN_URL = "https://www.liepin.com";
    private static final String JOB51_URL = "https://www.51job.com";
    private static final String ZHILIAN_URL = "https://www.zhaopin.com";

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

            // 初始化Boss直聘平台
            initBossPlatform();

            log.info("Playwright管理器初始化完成！");
        } catch (Exception e) {
            log.error("Playwright管理器初始化失败", e);
            throw new RuntimeException("Playwright初始化失败", e);
        }
    }

    /**
     * 初始化Boss直聘平台
     */
    private void initBossPlatform() {
        log.info("开始初始化Boss直聘平台...");

        // 创建独立的BrowserContext
        bossContext = browser.newContext(new Browser.NewContextOptions()
                .setViewportSize(null) // 不设置固定视口，允许窗口自适应
                .setUserAgent(
                        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/135.0.0.0 Safari/537.36"));

        // 创建页面
        bossPage = bossContext.newPage();
        bossPage.setDefaultTimeout(DEFAULT_TIMEOUT);

        // 导航到Boss直聘首页
        try {
            bossPage.navigate(BOSS_URL);
            log.info("Boss直聘页面已导航到: {}", BOSS_URL);
        } catch (Exception e) {
            log.warn("Boss直聘页面导航失败: {}", e.getMessage());
        }

        // 初始化登录状态为未登录
        loginStatus.put("boss", false);

        // 设置登录状态监控
        setupLoginMonitoring(bossPage, "boss");

        log.info("Boss直聘平台初始化完成");
    }

    /**
     * 设置登录状态监控
     *
     * @param page     页面实例
     * @param platform 平台名称
     */
    private void setupLoginMonitoring(Page page, String platform) {
        // 监听页面导航事件，检测URL变化
        page.onFrameNavigated(frame -> {
            if (frame == page.mainFrame()) {
                checkLoginStatus(page, platform);
            }
        });

        log.info("{}平台登录状态监控已启用", platform);
    }

    /**
     * 检查登录状态
     *
     * @param page     页面实例
     * @param platform 平台名称
     */
    private void checkLoginStatus(Page page, String platform) {
        try {
            boolean isLoggedIn = false;
            String currentUrl = page.url();

            if (platform.equals("boss")) {// Boss直聘登录判断：URL包含/web/geek/ 或者 存在用户头像元素
                isLoggedIn = currentUrl.contains("/web/geek/") ||
                        page.locator(".user-avatar").count() > 0;
                // 其他平台登录判断逻辑预留
            }

            // 如果登录状态发生变化（从未登录变为已登录）
            Boolean previousStatus = loginStatus.get(platform);
            if (isLoggedIn && (previousStatus == null || !previousStatus)) {
                onLoginSuccess(platform);
            }
        } catch (Exception e) {
            // 忽略检查过程中的异常，避免影响正常流程
            log.debug("检查{}平台登录状态时发生异常: {}", platform, e.getMessage());
        }
    }

    /**
     * 登录成功回调
     *
     * @param platform 平台名称
     */
    private void onLoginSuccess(String platform) {
        log.info("检测到{}平台登录成功", platform);
        loginStatus.put(platform, true);

        // 通知所有监听器
        LoginStatusChange change = new LoginStatusChange(platform, true, System.currentTimeMillis());
        loginStatusListeners.forEach(listener -> {
            try {
                listener.accept(change);
            } catch (Exception e) {
                log.error("通知登录状态监听器时发生错误", e);
            }
        });
    }

    /**
     * 定时检查登录状态（每5秒）
     * 用于捕获通过DOM元素判断登录状态的场景
     */
    @Scheduled(fixedDelay = 5000)
    public void scheduledLoginCheck() {
        if (bossPage != null) {
            checkLoginStatus(bossPage, "boss");
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
            // 关闭Boss直聘
            if (bossPage != null) {
                bossPage.close();
                log.info("Boss直聘页面已关闭");
            }
            if (bossContext != null) {
                bossContext.close();
                log.info("Boss直聘上下文已关闭");
            }

            // 关闭浏览器
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
        return playwright != null && browser != null && bossPage != null;
    }

    /**
     * 获取CDP端口号
     */
    public int getCdpPort() {
        return CDP_PORT;
    }

    /**
     * 注册登录状态监听器
     *
     * @param listener 监听器
     */
    public void addLoginStatusListener(Consumer<LoginStatusChange> listener) {
        loginStatusListeners.add(listener);
    }

    /**
     * 移除登录状态监听器
     *
     * @param listener 监听器
     */
    public void removeLoginStatusListener(Consumer<LoginStatusChange> listener) {
        loginStatusListeners.remove(listener);
    }

    /**
     * 获取平台登录状态
     *
     * @param platform 平台名称
     * @return 是否已登录
     */
    public boolean isLoggedIn(String platform) {
        return loginStatus.getOrDefault(platform, false);
    }

    /**
     * LoginStatusChange - 登录状态变化DTO
     */
    public record LoginStatusChange(String platform, boolean isLoggedIn, long timestamp) {
    }
}
