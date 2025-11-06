package com.getjobs.worker.manager;

import com.microsoft.playwright.*;
import com.getjobs.application.entity.CookieEntity;
import com.getjobs.application.service.CookieService;
import com.microsoft.playwright.options.Cookie;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
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
@Slf4j
@Getter
@Component
public class PlaywrightManager {

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

    @Autowired
    private CookieService cookieService;

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

        // 尝试从数据库加载Boss平台Cookie到上下文
        try {
            CookieEntity cookieEntity = cookieService.getCookieByPlatform("boss");
            if (cookieEntity != null && cookieEntity.getCookieValue() != null && !cookieEntity.getCookieValue().isBlank()) {
                String cookieStr = cookieEntity.getCookieValue();
                List<Cookie> cookies = parseCookiesFromString(cookieStr);

                if (!cookies.isEmpty()) {
                    bossContext.addCookies(cookies);
                    log.info("已从数据库加载Boss Cookie并注入浏览器上下文，共 {} 条", cookies.size());
                } else {
                    log.warn("解析Cookie失败，未能加载任何Cookie");
                }
            } else {
                log.info("数据库未找到Boss Cookie或值为空，跳过Cookie注入");
            }
        } catch (Exception e) {
            log.warn("从数据库加载Boss Cookie失败: {}", e.getMessage());
        }

        // 导航到Boss直聘首页
        try {
            bossPage.navigate(BOSS_URL);
            log.info("Boss直聘页面已导航到: {}", BOSS_URL);

            // 等待页面加载
            Thread.sleep(2000);

            // 检查是否需要登录
            if (!checkIfLoggedIn()) {
                log.info("检测到未登录，导航到登录页面...");
                bossPage.navigate(BOSS_URL + "/web/user/?ka=header-login");
                Thread.sleep(1000);

                // 尝试切换到二维码登录（点击“APP扫码登录”按钮）
                try {
                    // 新版登录页按钮：<div class="btn-sign-switch ewm-switch"><div class="switch-tip">APP扫码登录</div></div>
                    Locator qrSwitch = bossPage.locator(".btn-sign-switch.ewm-switch");
                    if (qrSwitch.count() > 0) {
                        qrSwitch.click();
                        log.info("已点击二维码登录切换按钮（.btn-sign-switch.ewm-switch）");
                    } else {
                        // 兜底：按文本匹配内部提示
                        Locator tip = bossPage.getByText("APP扫码登录");
                        if (tip.count() > 0) {
                            tip.click();
                            log.info("已点击包含文本的二维码登录切换提示（APP扫码登录）");
                        } else {
                            // 兼容旧版选择器
                            Locator legacy = bossPage.locator("li.sign-switch-tip");
                            if (legacy.count() > 0) {
                                legacy.click();
                                log.info("已通过旧版选择器切换二维码登录（li.sign-switch-tip）");
                            } else {
                                log.info("未找到二维码登录切换按钮，保持当前登录页");
                            }
                        }
                    }
                } catch (Exception e) {
                    log.debug("切换二维码登录失败: {}", e.getMessage());
                }
            } else {
                log.info("Boss直聘已登录");
            }
        } catch (Exception e) {
            log.warn("Boss直聘页面导航失败: {}", e.getMessage());
        }

        // 初始化登录状态为未登录
        loginStatus.put("boss", checkIfLoggedIn());

        // 设置登录状态监控
        setupLoginMonitoring(bossPage);

        log.info("Boss直聘平台初始化完成");
    }

    /**
     * 检查Boss是否已登录
     */
    private boolean checkIfLoggedIn() {
        try {
            return bossPage.locator("li.nav-figure span.label-text").count() > 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 设置登录状态监控
     *
     * @param page 页面实例
     */
    private void setupLoginMonitoring(Page page) {
        // 监听页面导航事件，检测URL变化
        page.onFrameNavigated(frame -> {
            if (frame == page.mainFrame()) {
                checkLoginStatus(page, "boss");
            }
        });

        log.info("{}平台登录状态监控已启用", "boss");
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
            if (platform.equals("boss")) {
                isLoggedIn = page.locator("li.nav-figure span.label-text").count() > 0;
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

        // 登录成功时保存 Cookie 到数据库（仅 boss 平台）
        if ("boss".equals(platform)) {
            saveBossCookiesToDatabase("login success");
        }

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
     * 统一的Boss Cookie保存方法（使用JSON序列化）
     *
     * @param remark 备注信息
     */
    private void saveBossCookiesToDatabase(String remark) {
        try {
            List<com.microsoft.playwright.options.Cookie> cookies = bossContext.cookies();
            // 使用ObjectMapper序列化为JSON字符串
            String cookieJson = new ObjectMapper().writeValueAsString(cookies);
            boolean result = cookieService.saveOrUpdateCookie("boss", cookieJson, remark);
            if (result) {
                log.info("Boss Cookie已保存到数据库，共 {} 条，remark={}", cookies.size(), remark);
            }
        } catch (Exception e) {
            log.warn("保存Boss Cookie失败: {}", e.getMessage());
        }
    }

    /**
     * 主动保存 Boss Cookie 到数据库（用于调试/验证）
     */
    public void saveBossCookiesToDb(String remark) {
        saveBossCookiesToDatabase(remark);
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
     * 从JSON字符串解析Cookie列表
     *
     * @param cookieJson Cookie的JSON字符串
     * @return Cookie列表
     */
    private List<Cookie> parseCookiesFromString(String cookieJson) {
        List<Cookie> cookies = new ArrayList<>();

        try {
            ObjectMapper objectMapper = new ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode jsonArray = objectMapper.readTree(cookieJson);

            for (com.fasterxml.jackson.databind.JsonNode node : jsonArray) {
                // 创建Cookie对象（name和value是必需的）
                Cookie cookie = new Cookie(
                    node.get("name").asText(),
                    node.get("value").asText()
                );

                // 设置可选字段
                if (node.has("domain") && !node.get("domain").isNull()) {
                    cookie.domain = node.get("domain").asText();
                }
                if (node.has("path") && !node.get("path").isNull()) {
                    cookie.path = node.get("path").asText();
                }
                if (node.has("expires") && !node.get("expires").isNull()) {
                    cookie.expires = node.get("expires").asDouble();
                }
                if (node.has("httpOnly") && !node.get("httpOnly").isNull()) {
                    cookie.httpOnly = node.get("httpOnly").asBoolean();
                }
                if (node.has("secure") && !node.get("secure").isNull()) {
                    cookie.secure = node.get("secure").asBoolean();
                }
                if (node.has("sameSite") && !node.get("sameSite").isNull()) {
                    String sameSite = node.get("sameSite").asText();
                    if (sameSite != null && !sameSite.isEmpty()) {
                        cookie.sameSite = com.microsoft.playwright.options.SameSiteAttribute.valueOf(
                            sameSite.toUpperCase()
                        );
                    }
                }

                cookies.add(cookie);
            }

            log.debug("成功解析Cookie，共 {} 条", cookies.size());
        } catch (Exception e) {
            log.error("解析Cookie JSON失败: {}", e.getMessage(), e);
        }

        return cookies;
    }

    /**
     * LoginStatusChange - 登录状态变化DTO
     */
    public record LoginStatusChange(String platform, boolean isLoggedIn, long timestamp) {
    }
}
