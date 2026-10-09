package com.getjobs.worker.manager;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.getjobs.application.entity.CookieEntity;
import com.getjobs.application.service.CookieService;
import com.microsoft.playwright.*;
import com.microsoft.playwright.options.Cookie;
import com.microsoft.playwright.options.WaitUntilState;
import com.microsoft.playwright.options.LoadState;
import jakarta.annotation.PreDestroy;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Playwright管理器
 * Spring管理的单例Bean，在应用启动时自动初始化Playwright实例
 * 支持4个求职平台的共享BrowserContext和登录状态监控
 * 所有平台在同一个浏览器窗口的不同标签页中运行
 */
@Slf4j
@Getter
@Component
@Lazy
public class PlaywrightManager {

    // Playwright实例
    private Playwright playwright;

    // 用持久化上下文启动后没有独立的 Browser 对象，context 本身就代表这个浏览器

    // 浏览器是否已经关闭（用户手动关窗口、Chrome 崩溃等）。
    // 不检测的话：字段还都非空，isInitialized() 依旧返回 true，
    // 但任何页面操作都会抛 TargetClosedError，投递任务随即卡死、按钮再点毫无反应。
    private volatile boolean browserClosed = false;

    // 浏览器上下文（所有平台共享，在同一个窗口中打开多个标签页）
    private BrowserContext context;

    // Boss直聘页面
    private Page bossPage;

    // 猎聘页面
    private Page liepinPage;

    // 51job页面（预留）
    private Page job51Page;

    // 智联招聘页面（预留）
    private Page zhilianPage;

    // 登录状态追踪（平台 -> 是否已登录）
    private final Map<String, Boolean> loginStatus = new ConcurrentHashMap<>();

    // 登录状态监听器
    private final List<Consumer<LoginStatusChange>> loginStatusListeners = new CopyOnWriteArrayList<>();

    // 控制是否暂停对bossPage的后台监控，避免与任务执行并发访问同一页面
    private volatile boolean bossMonitoringPaused = false;
    // 控制是否暂停对liepinPage的后台监控
    private volatile boolean liepinMonitoringPaused = false;

    // 控制是否暂停对51jobPage的后台监控
  private volatile boolean job51MonitoringPaused = false;

    // 控制是否暂停对zhilianPage的后台监控
    private volatile boolean zhilianMonitoringPaused = false;

    // 记录智联招聘是否已处理过未登录引导（仅初始化时执行一次）
    private volatile boolean zhilianLoginGuided = false;

    // 默认超时时间（毫秒）
  private static final int DEFAULT_TIMEOUT = 30000;

    // Playwright调试端口
    private static final int CDP_PORT = 7866;

    // 登录状态探针的超时（毫秒）。探针失败只代表"没看到"，绝不能拖住主流程
    private static final double LOCATOR_PROBE_TIMEOUT = 5000;

    // waitForLoadState 的超时（毫秒）。
    // Boss / 智联都是 SPA + WebSocket 长连接（cookie 里就有 ws.zhipin.com），
    // NETWORKIDLE 可能永远等不到。不给超时会直接把线程挂死 —— 而且"卡住"不是异常，
    // 外面包多少层 try/catch 都没用，实测把初始化卡了两分钟以上还在等。
    private static final double LOAD_STATE_TIMEOUT = 10_000;

    // Boss 登录态 Cookie：出现任意一个即视为已登录
    private static final Set<String> BOSS_LOGIN_COOKIES = Set.of("bst", "wt2", "zp_at", "geek_zp_token");

    // 持久化上下文的用户数据目录，登录态直接落在这里
    private static final Path USER_DATA_DIR = Paths.get(System.getProperty("user.dir"), "browser-data");

    // 平台URL常量
    private static final String BOSS_URL = "https://www.zhipin.com";
    // 初始化时打开的落地页。
    // 不要用首页：实测 navigate("https://www.zhipin.com") 要 60 秒才回来，页面标签一直转圈，
    // 期间任何 evaluate / locator 调用都会无限期阻塞（这两个 API 都没有默认超时），
    // 表现就是启动奇慢、投递卡死、管理页面点按钮没反应。
    // 岗位列表页在同一个浏览器里秒开，用它作为落地页。
    private static final String BOSS_ENTRY_URL = "https://www.zhipin.com/web/geek/jobs";
    private static final String LIEPIN_URL = "https://www.liepin.com";
  private static final String JOB51_URL = "https://www.51job.com";
    private static final String ZHILIAN_URL = "https://www.zhaopin.com";
    private static final String BOSS_DOMAIN = "zhipin.com";
    private static final String LIEPIN_DOMAIN = "liepin.com";
    private static final String JOB51_DOMAIN = "51job.com";
    private static final String ZHILIAN_DOMAIN = "zhaopin.com";
    // 降噪：51job Cookie保存日志节流状态
    private volatile long last51CookieLogMs = 0L;
    private volatile int last51CookieLogCount = -1;
    private volatile String last51CookieRemark = "";

    @Autowired
    private CookieService cookieService;

    // ------------------------------------------------------------------
    // Playwright 单线程调度
    //
    // Playwright Java 不是线程安全的：Playwright 对象以及由它创建的所有对象，
    // 都必须在创建它的那个线程上调用。这个项目原本同时从 main、ForkJoinPool、
    // scheduling-1、http-nio 多个线程操作同一个 BrowserContext，会随机爆出
    // "Object doesn't exist: request@/response@"，以及导航被打断的 net::ERR_ABORTED
    // （投递任务因此整个挂掉）。
    //
    // 这里把所有 Playwright 调用收敛到一个专用线程上串行执行。
    // ------------------------------------------------------------------
    private static final String PW_THREAD_NAME = "playwright-thread";

    private final ExecutorService playwrightExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, PW_THREAD_NAME);
        thread.setDaemon(true);
        return thread;
    });

    // 排队中 + 执行中的任务数，给定时监控做"忙就跳过"判断，避免任务堆积
    private final AtomicInteger playwrightQueueDepth = new AtomicInteger();

    /** 当前是否已经在 Playwright 线程上（嵌套调用要就地执行，否则自己等自己会死锁） */
    private boolean onPlaywrightThread() {
        return PW_THREAD_NAME.equals(Thread.currentThread().getName());
    }

    /**
     * 在 Playwright 专用线程上执行并等待结果。已经在该线程上时就地执行。
     */
    public <T> T callOnPlaywright(Callable<T> task) {
        if (onPlaywrightThread()) {
            try {
                return task.call();
            } catch (Exception e) {
                throw toRuntime(e);
            }
        }
        playwrightQueueDepth.incrementAndGet();
        try {
            return playwrightExecutor.submit(() -> {
                try {
                    return task.call();
                } finally {
                    playwrightQueueDepth.decrementAndGet();
                }
            }).get();
        } catch (ExecutionException e) {
            throw toRuntime(e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("等待Playwright任务被中断", e);
        }
    }

    /**
     * 在 Playwright 专用线程上执行并等待完成。
     */
    public void runOnPlaywright(Runnable task) {
        callOnPlaywright(() -> {
            task.run();
            return null;
        });
    }

    /**
     * 给定时监控用：Playwright 线程正忙（比如正在跑投递）时直接跳过这一轮，
     * 不排队、不阻塞调度线程。
     *
     * @return 是否真的提交了任务
     */
    public boolean tryRunOnPlaywright(Runnable task) {
        if (playwrightQueueDepth.get() > 0) {
            return false;
        }
        playwrightQueueDepth.incrementAndGet();
        playwrightExecutor.execute(() -> {
            try {
                task.run();
            } catch (Exception e) {
                log.debug("Playwright后台任务异常: {}", e.getMessage());
            } finally {
                playwrightQueueDepth.decrementAndGet();
            }
        });
        return true;
    }

    /**
     * 把持久化 profile 里的"上次崩溃退出"标记改回正常。
     * <p>
     * 应用被强制停止（IDEA 的停止按钮、kill、崩溃）后，Chrome 下次启动会弹
     * "要恢复页面吗？"模态框。这个框会挂起所有 CDP 页面操作 —— 表现为 navigate
     * 永远不返回而且连超时都不触发，整个初始化线程焊死。
     * 除了命令行标志，这里再把 Preferences 里的退出状态直接改掉。
     */
    private void sanitizeProfileExitState() {
        Path preferences = USER_DATA_DIR.resolve("Default").resolve("Preferences");
        if (!Files.isRegularFile(preferences)) {
            return;
        }
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(preferences.toFile());
            if (!(root instanceof ObjectNode rootNode)) {
                return;
            }
            JsonNode profile = rootNode.get("profile");
            if (!(profile instanceof ObjectNode profileNode)) {
                return;
            }
            String exitType = profileNode.path("exit_type").asText("");
            boolean exitedCleanly = profileNode.path("exited_cleanly").asBoolean(true);
            if ("Normal".equals(exitType) && exitedCleanly) {
                return;
            }
            profileNode.put("exit_type", "Normal");
            profileNode.put("exited_cleanly", true);
            mapper.writeValue(preferences.toFile(), rootNode);
            log.info("已清除浏览器 profile 的崩溃退出标记（原 exit_type={}），避免弹出\"要恢复页面吗？\"", exitType);
        } catch (Exception e) {
            log.warn("清理 profile 退出状态失败（不影响启动）: {}", e.getMessage());
        }
    }

    /** Playwright 线程上是否有前台任务在跑（投递、初始化等） */
    public boolean isPlaywrightBusy() {
        return playwrightQueueDepth.get() > 0;
    }

    private static RuntimeException toRuntime(Throwable throwable) {
        if (throwable instanceof RuntimeException runtimeException) {
            return runtimeException;
        }
        return new RuntimeException(throwable);
    }

    /**
     * 初始化Playwright实例（延迟初始化）。
     * Playwright 对象必须在专用线程上创建，否则后续所有调用都得跨线程。
     */
    public void init() {
        if (isInitialized()) {
            return;
        }
        runOnPlaywright(this::initInternal);
    }

    private void initInternal() {
        if (isInitialized()) {
            return;
        }
        log.info("========================================");
        log.info("  初始化浏览器自动化引擎");
        log.info("========================================");

        try {
            // 启动Playwright，driver 指向 patchright（见 build.gradle.kts 的 installPatchrightDriver）
            playwright = createPlaywright();
            log.info("✓ Playwright引擎已启动 (driver: {})", describeDriver());

            // 清掉上次的"崩溃退出"标记，双保险（命令行标志偶尔不生效）
            sanitizeProfileExitState();

            // 用持久化上下文启动：Patchright 官方明确要求用 launchPersistentContext，
            // 而不是 launch() + newContext()。登录态直接落盘到 USER_DATA_DIR。
            context = playwright.chromium().launchPersistentContext(USER_DATA_DIR,
                    new BrowserType.LaunchPersistentContextOptions()
                            .setChannel("chrome")   // 用本机真实 Chrome，而不是 Chromium
                            .setHeadless(false)     // 无头必被检测
                            .setSlowMo(50)          // 放慢操作速度，便于调试
                            .setViewportSize(null)  // 不锁视口，用窗口实际大小
                            .setChromiumSandbox(true) // 不加 --no-sandbox：它本身就是自动化特征
                            .setArgs(List.of(
                                    "--start-maximized",
                                    // Patchright 会加 --disable-blink-features=AutomationControlled，
                                    // Chrome 因此弹"不受支持的命令行标记"黄条，用 --test-type 抑制
                                    "--test-type",
                                    // 上次非正常退出（IDEA 停止按钮、崩溃、kill）后，Chrome 会弹
                                    // "要恢复页面吗？"模态框。那个框会挂起所有 CDP 页面操作，
                                    // 表现为 navigate 永远不返回、连超时都不触发，整个初始化焊死。
                                    "--disable-session-crashed-bubble",
                                    "--hide-crash-restore-bubble",
                                    "--no-first-run",
                                    "--no-default-browser-check",
                                    // 强制直连，不走系统代理（代理出口 IP 更容易被风控标记）
                                    "--no-proxy-server",
                                    "--proxy-bypass-list=*",
                                    // 把 navigator.webdriver 压成 false。
                                    // patchright 的 driver 会自动加这个标志，但它的超时机制是坏的
                                    // （navigate/isVisible/waitForLoadState 的 timeout 全部失效，
                                    // 会把线程无限期挂死）；用官方 driver 就得自己加。
                                    "--disable-blink-features=AutomationControlled"
                            )));
            // 刻意不设置 UserAgent：伪造 UA 只改字符串，改不了 navigator.platform 和
            // navigator.userAgentData，反而制造出"UA 说 Mac、platform 说 Win32"这种致命矛盾。
            browserClosed = false;
            // 浏览器一旦关闭（手动关窗口 / Chrome 崩溃）立刻置位，
            // 让 isInitialized() 变 false，后续操作走重新初始化而不是一路 TargetClosedError
            context.onClose(closed -> {
                browserClosed = true;
                log.warn("检测到浏览器已关闭，下次操作会自动重新初始化");
            });
            log.info("✓ Chrome已启动（持久化上下文: {}）", USER_DATA_DIR);
            log.info("✓ BrowserContext已创建（所有平台共享）");

            // 顺序创建所有Page（避免并发创建Page导致的竞态条件）
            log.info("开始创建所有平台的Page...");

            // 持久化上下文自带的那个启动标签页不能复用！
            // 复用它会出现：标签页永远停在加载中（左上角一直转圈），而 page.evaluate /
            // locator 这些调用没有默认超时，于是无限期阻塞 —— 实测卡 160 秒还不返回，
            // 表现就是初始化奇慢、投递任务卡死、整个应用发卡。
            // 同一个浏览器里新开的标签页则完全正常，所以这里一律新建，最后把启动页关掉。
            List<Page> startupPages = new ArrayList<>(context.pages());

            bossPage = context.newPage();
            bossPage.setDefaultTimeout(DEFAULT_TIMEOUT);
            log.info("✓ Boss Page已创建");

            liepinPage = context.newPage();
            liepinPage.setDefaultTimeout(DEFAULT_TIMEOUT);
            log.info("✓ 猎聘 Page已创建");

            job51Page = context.newPage();
            job51Page.setDefaultTimeout(DEFAULT_TIMEOUT);
            log.info("✓ 51job Page已创建");

            zhilianPage = context.newPage();
            zhilianPage.setDefaultTimeout(DEFAULT_TIMEOUT);
            log.info("✓ 智联招聘 Page已创建");

            // 四个业务页都建好了，把持久化上下文自带的启动空白页关掉
            for (Page startup : startupPages) {
                try {
                    startup.close();
                } catch (Exception e) {
                    log.debug("关闭启动空白页失败（忽略）: {}", e.getMessage());
                }
            }
            if (!startupPages.isEmpty()) {
                log.info("✓ 已关闭 {} 个启动空白页", startupPages.size());
            }

            // 各平台初始化必须串行：它们共享同一个 BrowserContext 和同一条 Playwright 连接，
            // 原来用 4 个 ForkJoinPool 线程并发跑，会互相打断导航（net::ERR_ABORTED）
            log.info("开始依次初始化所有平台...");
            setupBossPlatform();
            setupLiepinPlatform();
            setup51jobPlatform();
            setupZhilianPlatform();

            log.info("✓ 浏览器自动化引擎初始化完成（所有平台已依次启动）");
            log.info("========================================");
        } catch (Exception e) {
            log.error("✗ 浏览器自动化引擎初始化失败", e);
            throw new RuntimeException("Playwright初始化失败", e);
        }
    }

    /**
     * 创建 Playwright 实例，并把 driver 指向 patchright。
     * <p>
     * 不能只靠 gradle bootRun 传 -Dplaywright.cli.dir —— 从 IDEA 直接跑 main() 时不走那套配置，
     * 会静默退回官方 driver-bundle，反检测全部失效。所以这里自己找一遍，
     * 保证不管用什么方式启动，跑的都是 patchright。
     */
    private Playwright createPlaywright() {
        if (System.getProperty("playwright.cli.dir") == null) {
            Path driverDir = locatePatchrightDriver();
            if (driverDir != null) {
                System.setProperty("playwright.cli.dir", driverDir.toString());
            } else {
                log.warn("未找到 patchright driver，将退回官方 driver-bundle（反检测能力大幅下降）。"
                        + "请先执行: gradlew installPatchrightDriver");
            }
        }

        Map<String, String> env = new HashMap<>();
        // patchright driver 目录里没有 node，得用本机的
        if (System.getProperty("playwright.cli.dir") != null && System.getenv("PLAYWRIGHT_NODEJS_PATH") == null) {
            String nodePath = locateNodeExecutable();
            if (nodePath != null) {
                env.put("PLAYWRIGHT_NODEJS_PATH", nodePath);
                log.info("使用本机 node: {}", nodePath);
            } else {
                log.warn("PATH 里没找到 node，patchright driver 可能起不来，请先安装 Node.js");
            }
        }

        return env.isEmpty()
                ? Playwright.create()
                : Playwright.create(new Playwright.CreateOptions().setEnv(env));
    }

    /**
     * 找 patchright driver 目录，判定标准是里面有 package/cli.js。
     */
    private Path locatePatchrightDriver() {
        Path projectDir = Paths.get(System.getProperty("user.dir"));
        List<Path> candidates = List.of(
                projectDir.resolve("build").resolve("patchright-driver"),
                projectDir.resolve("patchright-driver"),
                // 从子目录启动时往上找一级
                projectDir.getParent() == null ? projectDir
                        : projectDir.getParent().resolve("build").resolve("patchright-driver"));
        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate.resolve("package").resolve("cli.js"))) {
                return candidate.toAbsolutePath();
            }
        }
        return null;
    }

    /**
     * 从 PATH 里找 node 可执行文件。
     */
    private String locateNodeExecutable() {
        boolean windows = System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("windows");
        String exe = windows ? "node.exe" : "node";
        String path = System.getenv("PATH");
        if (path == null) {
            return null;
        }
        for (String entry : path.split(java.io.File.pathSeparator)) {
            if (entry == null || entry.isBlank()) {
                continue;
            }
            try {
                Path candidate = Paths.get(entry.trim()).resolve(exe);
                if (Files.isRegularFile(candidate)) {
                    return candidate.toAbsolutePath().toString();
                }
            } catch (Exception ignore) {
                // PATH 里可能有非法路径，跳过
            }
        }
        return null;
    }

    /**
     * driver 来源说明，仅用于启动日志，方便确认到底跑的是不是 patchright。
     */
    private String describeDriver() {
        String cliDir = System.getProperty("playwright.cli.dir");
        if (cliDir == null || cliDir.isBlank()) {
            return "官方 driver-bundle（没找到 patchright，反检测能力下降）";
        }
        return "patchright @ " + cliDir;
    }

    /**
     * 设置Boss直聘平台（加载Cookie、导航、监控）
     */
    private void setupBossPlatform() {
        log.info("开始初始化Boss直聘平台...");
        // 尝试从数据库加载Boss平台Cookie到上下文
        try {
            CookieEntity cookieEntity = cookieService.getCookieByPlatform("boss");
            if (cookieEntity != null && cookieEntity.getCookieValue() != null && !cookieEntity.getCookieValue().isBlank()) {
                String cookieStr = cookieEntity.getCookieValue();
                List<Cookie> cookies = filterCookiesByDomain(parseCookiesFromString(cookieStr), BOSS_DOMAIN);

                if (!cookies.isEmpty()) {
                    context.addCookies(cookies);
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

        // 导航到Boss直聘首页（带重试机制）
        int maxRetries = 3;
        boolean navigateSuccess = false;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                bossPage.navigate(BOSS_ENTRY_URL, new Page.NavigateOptions()
                        .setTimeout(60000)
                        .setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
                navigateSuccess = true;
                break;
            } catch (Exception e) {
                // Playwright在并发导航时可能抛出 "Object doesn't exist" 异常，但页面实际已加载
                boolean pageAccessible = false;
                try {
                    String url = bossPage.url();
                    pageAccessible = url != null && url.contains("zhipin.com");
                } catch (Exception ignored) {
                }

                if (pageAccessible) {
                    navigateSuccess = true;
                    break;
                }

                if (attempt < maxRetries) {
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }

        if (!navigateSuccess) {
            log.warn("Boss直聘页面导航失败");
        }

        try {
            // 等待页面网络空闲，确保头部导航渲染完成
            try {
                bossPage.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(LOAD_STATE_TIMEOUT));
            } catch (Exception e) {
                log.debug("等待Boss页面网络空闲失败: {}", e.getMessage());
            }

            // 初始化阶段不主动跳转登录页，仅在导航后设置状态
            // 参考猎聘实现：加载Cookie并导航后，由业务侧决定是否触发后续登录流程
        } catch (Exception e) {
            log.warn("Boss直聘页面导航失败: {}", e.getMessage());
        }
        // 初始化登录状态并通知（如果有SSE连接会立即推送）
        setLoginStatus("boss", checkIfLoggedIn());
        // 设置登录状态监控
        setupLoginMonitoring(bossPage);
    }

    /**
     * 检查Boss是否已登录
     */
    private boolean checkIfLoggedIn() {
        // 最可靠的信号是登录态 Cookie：不受页面渲染、跳转、加载速度影响。
        // DOM 探测（头像、登录入口）只作兜底 —— 页面一慢就会误判成未登录，
        // 而 /api/boss/start 是拿这个结果做准入的，误判会直接导致"请先登录"。
        try {
            for (Cookie cookie : context.cookies(BOSS_URL)) {
                if (BOSS_LOGIN_COOKIES.contains(cookie.name)
                        && cookie.value != null && !cookie.value.isBlank()) {
                    return true;
                }
            }
        } catch (Exception ignored) {}

        // 更稳健的登录判断：优先检测用户头像/昵称是否可见；备用检测登录入口是否可见且包含“登录”文本
        try {
            Locator userLabel = bossPage.locator("li.nav-figure span.label-text").first();
            if (isVisibleQuick(userLabel)) {
                return true;
            }
        } catch (Exception ignored) {}

        try {
            // 有些版本仅展示头像入口，无 label-text
            Locator navFigure = bossPage.locator("li.nav-figure").first();
            if (isVisibleQuick(navFigure)) {
                return true;
            }
        } catch (Exception ignored) {}

        try {
            // 未登录时通常有“登录/注册”入口或按钮容器
            Locator loginAnchor = bossPage.locator("li.nav-sign a, .btns").first();
            if (isVisibleQuick(loginAnchor)) {
                String text = loginAnchor.textContent(new Locator.TextContentOptions().setTimeout(LOCATOR_PROBE_TIMEOUT));
                if (text != null && text.contains("登录")) {
                    return false;
                }
            }
        } catch (Exception ignored) {}

        // 无法明确检测到登录特征时，保守返回未登录
        return false;
    }

    /**
     * 带超时的可见性探测。
     * <p>
     * 不能直接用 Locator.isVisible()：Boss 首页会连续做客户端跳转，
     * 期间 frame 反复重建，这个调用会一直等下去（实测把初始化线程焊死了 2 分钟以上都不返回）。
     * 登录检测只是个探针，等不到就当作"没看到"，绝不能阻塞主流程。
     */
    private boolean isVisibleQuick(Locator locator) {
        try {
            return locator.isVisible(new Locator.IsVisibleOptions().setTimeout(LOCATOR_PROBE_TIMEOUT));
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
                // 事件回调是在别的调用"进行中"被派发的，此时再去调 Playwright 属于重入，
                // 会打断正在进行的导航。所以除了暂停标志，还要避开前台任务运行期。
                if (!bossMonitoringPaused && !isPlaywrightBusy()) {
                    checkLoginStatus(page, "boss");
                }
            }
        });

        log.info("{}平台登录状态监控已启用", "boss");
    }

    /**
     * 设置猎聘平台（加载Cookie、导航、监控）
     */
    private void setupLiepinPlatform() {
        log.info("开始初始化猎聘平台...");

        // 尝试从数据库加载猎聘平台Cookie到上下文
        try {
            CookieEntity cookieEntity = cookieService.getCookieByPlatform("liepin");
            if (cookieEntity != null && cookieEntity.getCookieValue() != null && !cookieEntity.getCookieValue().isBlank()) {
                String cookieStr = cookieEntity.getCookieValue();
                List<Cookie> cookies = filterCookiesByDomain(parseCookiesFromString(cookieStr), LIEPIN_DOMAIN);

                if (!cookies.isEmpty()) {
                    context.addCookies(cookies);
                    log.info("已从数据库加载猎聘 Cookie并注入浏览器上下文，共 {} 条", cookies.size());
                } else {
                    log.warn("解析猎聘Cookie失败，未能加载任何Cookie");
                }
            } else {
                log.info("数据库未找到猎聘Cookie或值为空，跳过Cookie注入");
            }
        } catch (Exception e) {
            log.warn("从数据库加载猎聘Cookie失败: {}", e.getMessage());
        }

        // 导航到猎聘首页（带重试机制）
        int maxRetries = 3;
        boolean navigateSuccess = false;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                liepinPage.navigate(LIEPIN_URL, new Page.NavigateOptions()
                        .setTimeout(60000)
                        .setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
                navigateSuccess = true;
                break;
            } catch (Exception e) {
                // Playwright在并发导航时可能抛出 "Object doesn't exist" 异常，但页面实际已加载
                boolean pageAccessible = false;
                try {
                    String url = liepinPage.url();
                    pageAccessible = url != null && url.contains("liepin.com");
                } catch (Exception ignored) {
                }

                if (pageAccessible) {
                    navigateSuccess = true;
                    break;
                }

                if (attempt < maxRetries) {
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }

        if (!navigateSuccess) {
            log.warn("猎聘页面导航失败");
        }

        // 等待页面网络空闲，确保头部导航渲染完成
        try {
            liepinPage.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(LOAD_STATE_TIMEOUT));
        } catch (Exception e) {
            log.debug("等待猎聘页面网络空闲失败: {}", e.getMessage());
        }

        // 初始化登录状态并通知（如果有SSE连接会立即推送）
        setLoginStatus("liepin", checkIfLiepinLoggedIn());
        // 设置登录状态监控
        setupLiepinLoginMonitoring(liepinPage);
    }

    /**
     * 检查猎聘是否已登录
     * 已登录：能找到用户头像 <img class="header-quick-menu-user-photo" ...>
     * 未登录：能找到 <span id="header-quick-menu-login">登录/注册</span>
     */
    private boolean checkIfLiepinLoggedIn() {
        try {
            // 先检查“登录/注册”入口是否可见，若可见则明确未登录
            try {
                Locator loginEntry = liepinPage.locator(
                    "#header-quick-menu-login, a[href*='login'], a[data-key='login'], button[data-key='login'], text=/登录|注册/").first();
                if (isVisibleQuick(loginEntry)) {
                    log.info("检测到未登录猎聘，保持在登录页或首页等待扫码登录");
                    // 若不在登录页，则导航到登录页并尝试切换二维码
                    String currentUrl = null;
                    try { currentUrl = liepinPage.url(); } catch (Exception ignored) {}
                    try {
                        if (currentUrl == null || !currentUrl.contains("/login")) {
                            liepinPage.navigate("https://www.liepin.com/login");
                            try { Thread.sleep(800); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                        }
                        // 优先点击官方切换二维码的容器
                        Locator qrSwitch = liepinPage.locator(".switch-type-mask-img-box").first();
                        if (isVisibleQuick(qrSwitch)) {
                            qrSwitch.click();
                            log.info("已切换到猎聘二维码登录页面，等待用户扫码...");
                        } else {
                            // 兼容新版页面：图片资源名包含 qrcode-btn，需要点击其父级按钮
                            Locator qrImg = liepinPage.locator("img[src*='qrcode-btn']").first();
                            if (qrImg.count() > 0 && isVisibleQuick(qrImg)) {
                                try {
                                    // 尝试点击父节点或最近的可点击容器
                                    qrImg.click();
                                } catch (Exception ignored) {
                                    try {
                                        Locator parentBtn = qrImg.locator("xpath=ancestor::button[1] | xpath=ancestor::*[contains(@class,'btn')][1]").first();
                                        if (parentBtn.count() > 0 && isVisibleQuick(parentBtn)) {
                                            parentBtn.click();
                                        }
                                    } catch (Exception ignored2) {}
                                }
                                log.info("已通过二维码按钮切换到扫码登录状态");
                            }
                        }
                    } catch (Exception e) {
                        log.debug("猎聘登录页引导/二维码切换失败: {}", e.getMessage());
                    }
                    return false;
                }
            } catch (Exception ignored) {}

            // 再检查已登录特征：用户信息容器或用户头像是否存在（无需强制可见）
            try {
                if (liepinPage.locator("#header-quick-menu-user-info").count() > 0) {
                    log.debug("猎聘登录检测：存在用户信息容器，判定已登录");
                    return true;
                }
            } catch (Exception ignored) {}

            try {
                if (liepinPage.locator("img.header-quick-menu-user-photo, .header-quick-menu-user-photo").count() > 0) {
                    log.debug("猎聘登录检测：存在用户头像元素，判定已登录");
                    return true;
                }
            } catch (Exception ignored) {}

            // 兜底：若不存在登录入口且也未找到明确已登录特征，按已登录处理（避免误判）
            try {
                boolean loginEntryExists = liepinPage.locator("#header-quick-menu-login, a[href*='login']").count() > 0;
                if (!loginEntryExists) {
                    log.info("猎聘登录检测：未发现登录入口，兜底判定为已登录");
                    return true;
                }
            } catch (Exception ignored) {}

            // 默认未登录
            log.debug("猎聘登录检测：未匹配到明确特征，判定未登录");
            return false;
        } catch (Exception e) {
            log.debug("猎聘登录检测异常: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 设置猎聘登录状态监控
     *
     * @param page 页面实例
     */
    private void setupLiepinLoginMonitoring(Page page) {
        // 监听页面导航事件，检测URL变化
        page.onFrameNavigated(frame -> {
            if (frame == page.mainFrame()) {
                // 事件回调里再调 Playwright 属于重入，会打断前台任务正在进行的导航
                if (!liepinMonitoringPaused && !isPlaywrightBusy()) {
                    checkLiepinLoginStatus(page);
                }
            }
        });

        log.info("猎聘平台登录状态监控已启用");
    }

    /**
     * 设置51job平台（加载Cookie、导航、监控）
     */
    private void setup51jobPlatform() {
        log.info("开始初始化51job平台...");

        // 尝试从数据库加载51job平台Cookie到上下文
        try {
            CookieEntity cookieEntity = cookieService.getCookieByPlatform("51job");
            if (cookieEntity != null && cookieEntity.getCookieValue() != null && !cookieEntity.getCookieValue().isBlank()) {
                String cookieStr = cookieEntity.getCookieValue();
                List<Cookie> cookies = filterCookiesByDomain(parseCookiesFromString(cookieStr), JOB51_DOMAIN);

                if (!cookies.isEmpty()) {
                    context.addCookies(cookies);
                    log.info("已从数据库加载51job Cookie并注入浏览器上下文，共 {} 条", cookies.size());
                } else {
                    log.warn("解析51job Cookie失败，未能加载任何Cookie");
                }
            } else {
                log.info("数据库未找到51job Cookie或值为空，跳过Cookie注入");
            }
        } catch (Exception e) {
            log.warn("从数据库加载51job Cookie失败: {}", e.getMessage());
        }

        // 导航到51job首页（带重试机制）
        int maxRetries = 3;
        boolean navigateSuccess = false;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                job51Page.navigate(JOB51_URL, new Page.NavigateOptions()
                        .setTimeout(60000)
                        .setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
                navigateSuccess = true;
                break;
            } catch (Exception e) {
                // Playwright在并发导航时可能抛出 "Object doesn't exist" 异常，但页面实际已加载
                boolean pageAccessible = false;
                try {
                    String url = job51Page.url();
                    pageAccessible = url != null && url.contains("51job.com");
                } catch (Exception ignored) {
                }

                if (pageAccessible) {
                    navigateSuccess = true;
                    break;
                }

                if (attempt < maxRetries) {
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }

        if (!navigateSuccess) {
            log.warn("51job页面导航失败");
        }

        try {
            // 检查是否需要登录
            if (!checkIf51jobLoggedIn()) {
                log.info("检测到未登录51job，尝试自动点击登录入口并等待用户登录");

                try {
                    // 优先使用用户提供的选择器：span.login.loginBtnClick
                    Locator loginEntry = job51Page.locator("span.login.loginBtnClick").first();
                    if (loginEntry != null && isVisibleQuick(loginEntry)) {
                        loginEntry.click(new Locator.ClickOptions().setTimeout(30000));
                        log.info("已点击 51job 首页的 ‘登录/注册’ 入口，等待用户登录...");
                        asyncWaitFor51jobLogin();
                    } else {
                        // 备用选择器：文本匹配
                        Locator altLoginEntry = job51Page.locator("text=/登录\\/注册|登录|注册/").first();
                        if (altLoginEntry != null && isVisibleQuick(altLoginEntry)) {
                            altLoginEntry.click(new Locator.ClickOptions().setTimeout(30000));
                            log.info("已点击 51job 首页的登录入口（文本匹配），等待用户登录...");
                            asyncWaitFor51jobLogin();
                        } else {
                            log.info("未找到 51job 登录入口元素，保持在首页等待用户自行登录");
                            // 启动后台轮询，确保无导航也能检测到登录成功
                            asyncWaitFor51jobLogin();
                        }
                    }
                } catch (Exception clickEx) {
                    log.warn("尝试点击 51job 登录入口时发生异常: {}，保持在首页等待用户登录", clickEx.getMessage());
                    // 启动后台轮询，避免异常导致无法检测登录成功
                    asyncWaitFor51jobLogin();
                }
            } else {
                log.info("51job已登录");
            }
        } catch (Exception e) {
            log.warn("51job页面初始化检查失败: {}", e.getMessage());
        }

        // 初始化登录状态并通知（如果有SSE连接会立即推送）
        setLoginStatus("51job", checkIf51jobLoggedIn());
        // 设置登录状态监控
        setup51jobLoginMonitoring(job51Page);
    }

    /**
     * 检查51job是否已登录
     */
  private boolean checkIf51jobLoggedIn() {
      try {
            // 未登录特征：存在“登录/注册”入口
            Locator loginBtn = job51Page.locator("span.login.loginBtnClick").first();
            if (isVisibleQuick(loginBtn)) {
                String txt = (loginBtn.textContent() == null ? "" : loginBtn.textContent()).trim();
                if (txt.contains("登录")) {
                    return false;
                }
            }
            // 已登录特征（增强）：顶部显示用户名入口或个人中心链接
            // 1) 明确的用户名锚点（类名：uname e_icon at）
            Locator userAnchor = job51Page.locator("a.uname.e_icon.at");
            if (userAnchor.count() > 0 && isVisibleQuick(userAnchor.first())) {
                return true;
            }
            // 2) 个人中心链接（href=/pc/my/myjob）
            Locator myJobLink = job51Page.locator("a[href*='/pc/my/myjob']");
            if (myJobLink.count() > 0 && isVisibleQuick(myJobLink.first())) {
                return true;
            }
            // 3) 其他可能的用户信息容器（旧的兜底选择器）
            return job51Page.locator(".login-info, .user-info, .username").count() > 0;
      } catch (Exception e) {
          return false;
      }
  }

    /**
     * 设置51job登录状态监控
     *
     * @param page 页面实例
     */
    private void setup51jobLoginMonitoring(Page page) {
        // 监听页面导航事件，检测URL变化
        page.onFrameNavigated(frame -> {
            if (frame == page.mainFrame()) {
                if (!job51MonitoringPaused && !isPlaywrightBusy()) {
                    check51jobLoginStatus(page);
                }
            }
        });

        log.info("51job平台登录状态监控已启用");
    }

    /**
     * 检查51job登录状态
     *
     * @param page 页面实例
     */
    private void check51jobLoginStatus(Page page) {
        try {
            boolean isLoggedIn = checkIf51jobLoggedIn();
            // 如果登录状态发生变化（从未登录变为已登录）
            Boolean previousStatus = loginStatus.get("51job");
            if (isLoggedIn && (previousStatus == null || !previousStatus)) {
                on51jobLoginSuccess();
            }
        } catch (Exception e) {
            // 忽略检查过程中的异常，避免影响正常流程
            log.debug("检查51job平台登录状态时发生异常: {}", e.getMessage());
        }
    }

    /**
     * 51job登录成功回调
     */
    private void on51jobLoginSuccess() {
        log.info("51job平台登录成功");

        // 更新登录状态并通知
        setLoginStatus("51job", true);

        // 登录成功时保存 Cookie 到数据库
        save51jobCookiesToDatabase("login success");
    }

    /**
     * 在后台异步等待 51job 登录成功。
     * 说明：不阻塞初始化主流程，独立线程每秒轮询一次登录状态，最长等待5分钟。
     */
    private void asyncWaitFor51jobLogin() {
        Thread waitThread = new Thread(() -> {
            try {
                int maxSeconds = 300; // 最长等待 5 分钟
                final java.util.concurrent.atomic.AtomicBoolean loggedIn = new java.util.concurrent.atomic.AtomicBoolean(false);
                for (int i = 0; i < maxSeconds; i++) {
                    // 这个线程不能直接调 Playwright：它和 playwright-thread 同时读同一条连接，
                    // 会抢走对方的事件，导致投递时报 "Object doesn't exist: response@..."。
                    // 所以检测动作交给专用线程；专用线程正忙（比如在投递）就跳过这一秒。
                    // tryRunOnPlaywright 是异步提交的，结果在下一轮循环开头再看
                    if (loggedIn.get()) {
                        return;
                    }
                    final int elapsed = i;
                    tryRunOnPlaywright(() -> {
                        if (job51MonitoringPaused) {
                            return;
                        }
                        try {
                            if (checkIf51jobLoggedIn()) {
                                // 交由统一回调处理登录成功逻辑（包含状态更新与保存 Cookie）
                                on51jobLoginSuccess();
                                log.info("后台等待检测到 51job 登录成功，用时约 {} 秒", elapsed);
                                loggedIn.set(true);
                            }
                        } catch (Exception ignored) {
                        }
                    });

                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        log.debug("等待 51job 登录线程被中断");
                        return;
                    }
                }
                log.warn("后台等待 51job 登录超时（约5分钟），仍未检测到登录成功");
            } catch (Exception e) {
                log.warn("后台等待 51job 登录过程中发生异常: {}", e.getMessage());
            }
        }, "wait-51job-login-thread");

        waitThread.setDaemon(true);
        waitThread.start();
    }

    /**
     * 保存51job Cookie到数据库
     *
     * @param remark 备注信息
     */
  private void save51jobCookiesToDatabase(String remark) {
      try {
          List<com.microsoft.playwright.options.Cookie> cookies = filterCookiesByDomain(context.cookies(), JOB51_DOMAIN);
          // 使用ObjectMapper序列化为JSON字符串
          String cookieJson = new ObjectMapper().writeValueAsString(cookies);
          boolean result = cookieService.saveOrUpdateCookie("51job", cookieJson, remark);
          if (result) {
                long now = System.currentTimeMillis();
                boolean shouldInfoLog = (now - last51CookieLogMs) > 15000 // 至少间隔15秒
                        || cookies.size() != last51CookieLogCount
                        || (remark != null && !remark.equals(last51CookieRemark));
                if (shouldInfoLog) {
                    log.info("保存51job Cookie成功，共 {} 条，remark={}", cookies.size(), remark);
                    last51CookieLogMs = now;
                    last51CookieLogCount = cookies.size();
                    last51CookieRemark = remark == null ? "" : remark;
                } else {
                    // 近似重复的频繁调用，改为debug降低噪音
                    log.debug("保存51job Cookie成功(节流)，条数={}，remark={}", cookies.size(), remark);
                }
          }
      } catch (Exception e) {
          log.warn("保存51job Cookie失败: {}", e.getMessage());
      }
  }

    /**
     * 主动保存51job Cookie到数据库（用于调试/验证）
     */
    public void save51jobCookiesToDb(String remark) {
        save51jobCookiesToDatabase(remark);
    }

    /**
     * 清理51job上下文中的Cookie
     */
    public void clear51jobCookies() {
        try {
            if (context != null) {
                context.clearCookies();
                log.info("已清理共享上下文中的所有Cookie");
            } else {
                log.warn("共享上下文不存在，无法清理Cookie");
            }
        } catch (Exception e) {
            log.error("清理共享上下文Cookie失败: {}", e.getMessage(), e);
            throw new RuntimeException("清理共享上下文Cookie失败", e);
        }
    }

    /**
     * 暂停51job页面的后台登录监控（避免与业务流程并发操作页面）
     */
    public void pause51jobMonitoring() {
        job51MonitoringPaused = true;
        log.debug("51job登录监控已暂停");
    }

    /**
     * 恢复51job页面的后台登录监控
     */
    public void resume51jobMonitoring() {
        job51MonitoringPaused = false;
        log.debug("51job登录监控已恢复");
    }

    /**
     * 触发 51job 登录流程：打开登录页并点击“微信扫码登录”按钮
     */
    public void trigger51jobLogin() {
        try {
            if (job51Page == null) {
                if (context == null) {
                    throw new IllegalStateException("浏览器上下文尚未初始化");
                }
                job51Page = context.newPage();
            }

            // 如果已登录则直接返回
            if (checkIf51jobLoggedIn()) {
                log.info("检测到已登录51job，跳过登录触发");
                return;
            }

            // 先尝试在首页点击“登录/注册”入口
            try {
                job51Page.navigate(JOB51_URL, new Page.NavigateOptions()
                    .setTimeout(60000)
                    .setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
                Locator loginEntry = job51Page.locator("span.login.loginBtnClick, text=/登录\\/注册|登录|注册/").first();
                if (isVisibleQuick(loginEntry)) {
                    loginEntry.click(new Locator.ClickOptions().setTimeout(DEFAULT_TIMEOUT));
                }
            } catch (Exception e) {
                log.debug("在首页尝试点击登录入口失败: {}", e.getMessage());
            }

            // 跳转到官方登录页
            String loginUrl = "https://login.51job.com/login.php";
            job51Page.navigate(loginUrl, new Page.NavigateOptions()
                .setTimeout(60000)
                .setWaitUntil(WaitUntilState.DOMCONTENTLOADED));

            // 尝试点击“微信扫码登录”按钮
            Locator wechatScanBtn = job51Page.locator(
                "i.passIcon.custom-cursor-on-hover[data-sensor-id='sensor_login_wechatScan'], " +
                "i.passIcon[data-sensor-id='sensor_login_wechatScan'], " +
                "[data-sensor-id='sensor_login_wechatScan']"
            ).first();

            if (isVisibleQuick(wechatScanBtn)) {
                wechatScanBtn.click(new Locator.ClickOptions().setTimeout(DEFAULT_TIMEOUT));
                log.info("已点击51job登录页的微信扫码按钮，等待用户扫码登录...");
            } else {
                log.warn("未找到微信扫码登录按钮，用户可在登录页自行选择扫码方式");
            }

            // 不阻塞等待：监控会自动检测到登录成功并保存Cookie
        } catch (Exception e) {
            log.error("触发51job登录流程失败: {}", e.getMessage(), e);
            throw new RuntimeException("触发51job登录流程失败", e);
        }
    }

    /**
     * 设置智联招聘平台（加载Cookie、导航、监控）
     */
    private void setupZhilianPlatform() {
        log.info("开始初始化智联招聘平台...");

        // 尝试从数据库加载智联招聘平台Cookie到上下文
        try {
            CookieEntity cookieEntity = cookieService.getCookieByPlatform("zhilian");
            if (cookieEntity != null && cookieEntity.getCookieValue() != null && !cookieEntity.getCookieValue().isBlank()) {
                String cookieStr = cookieEntity.getCookieValue();
                List<Cookie> cookies = filterCookiesByDomain(parseCookiesFromString(cookieStr), ZHILIAN_DOMAIN);

                if (!cookies.isEmpty()) {
                    context.addCookies(cookies);
                    log.info("已从数据库加载智联招聘 Cookie并注入浏览器上下文，共 {} 条", cookies.size());
                } else {
                    log.warn("解析智联招聘Cookie失败，未能加载任何Cookie");
                }
            } else {
                log.info("数据库未找到智联招聘Cookie或值为空，跳过Cookie注入");
            }
        } catch (Exception e) {
            log.warn("从数据库加载智联招聘Cookie失败: {}", e.getMessage());
        }

        // 导航到智联招聘首页（带重试机制）
        int maxRetries = 3;
        boolean navigateSuccess = false;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                zhilianPage.navigate(ZHILIAN_URL, new Page.NavigateOptions()
                        .setTimeout(60000)
                        .setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
                navigateSuccess = true;
                break;
            } catch (Exception e) {
                // Playwright在并发导航时可能抛出 "Object doesn't exist" 异常，但页面实际已加载
                boolean pageAccessible = false;
                try {
                    String url = zhilianPage.url();
                    pageAccessible = url != null && url.contains("zhaopin.com");
                } catch (Exception ignored) {
                }

                if (pageAccessible) {
                    navigateSuccess = true;
                    break;
                }

                if (attempt < maxRetries) {
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }

        if (!navigateSuccess) {
            log.warn("智联招聘页面导航失败");
        }

        // 等待页面加载完成
        try {
            zhilianPage.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(LOAD_STATE_TIMEOUT));
        } catch (Exception e) {
            log.debug("等待智联页面网络空闲失败: {}", e.getMessage());
        }

        // 初始化登录状态并通知（如果有SSE连接会立即推送）
        setLoginStatus("zhilian", checkIfZhilianLoggedIn());
        // 设置登录状态监控
        setupZhilianLoginMonitoring(zhilianPage);
    }

    /**
     * 检查智联招聘是否已登录
     * 未登录时只在首次检测时引导用户到登录页
     */
    private boolean checkIfZhilianLoggedIn() {
        try {
            if (zhilianPage == null) {
                return false;
            }

            boolean isLoggedIn = false;
            boolean loginButtonExists = false;

            // 检查是否存在"登录/注册"按钮
            try {
                Locator loginButton = zhilianPage.locator("a.home-header__c-no-login").first();
                int count = loginButton.count();
                if (count > 0) {
                    loginButtonExists = true;
                    // 尝试获取文本进一步确认
                    try {
                        String buttonText = loginButton.textContent();
                        if (buttonText != null && buttonText.contains("登录")) {
                            loginButtonExists = true;
                        }
                    } catch (Exception e) {
                        log.debug("智联招聘：获取登录按钮文本失败: {}", e.getMessage());
                    }
                }
            } catch (Exception e) {
                log.debug("智联招聘：检查登录按钮时异常: {}", e.getMessage());
            }

            // 如果存在登录按钮，说明未登录
            if (loginButtonExists) {
                // 只在首次检测到未登录时执行引导操作
                if (!zhilianLoginGuided) {
                    log.info("检测到未登录智联招聘，重定向到登录页面");
                    zhilianLoginGuided = true;

                    // 重定向到登录页面
                    String currentUrl = null;
                    try {
                        currentUrl = zhilianPage.url();
                    } catch (Exception ignored) {
                    }

                    try {
                        if (currentUrl == null || !currentUrl.contains("passport.zhaopin.com/login")) {
                            boolean loginNavOk = false;
                            try {
                                zhilianPage.navigate(
                                        "https://passport.zhaopin.com/login",
                                        new Page.NavigateOptions()
                                                .setTimeout(60000)
                                                .setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
                                );
                                loginNavOk = true;
                            } catch (Exception navEx) {
                                String urlAfter = null;
                                try {
                                    urlAfter = zhilianPage.url();
                                } catch (Exception ignored2) {}

                                if (urlAfter != null && urlAfter.contains("passport.zhaopin.com")) {
                                    loginNavOk = true;
                                    log.debug("智联招聘：登录页导航异常但已在登录域: {}", navEx.getMessage());
                                } else {
                                    log.warn("智联招聘：导航至登录页失败: {}", navEx.getMessage());
                                }
                            }

                            if (loginNavOk) {
                                try {
                                    zhilianPage.waitForLoadState(LoadState.DOMCONTENTLOADED, new Page.WaitForLoadStateOptions().setTimeout(LOAD_STATE_TIMEOUT));
                                } catch (Exception ignored) {}
                                try {
                                    zhilianPage.waitForSelector(
                                            "div.zppp-panel-normal-bar__img, " +
                                            "div.passport-login, #J_loginWrap, " +
                                            "div[class*='qrcode'], img[src*='qrcode']",
                                            new Page.WaitForSelectorOptions().setTimeout(30000)
                                    );
                                } catch (Exception e) {
                                    log.debug("智联招聘：登录页关键元素等待失败: {}", e.getMessage());
                                }
                            }
                        }

                        // 点击二维码登录按钮
                        Locator qrToggle = zhilianPage.locator("div.zppp-panel-normal-bar__img").first();
                        if (qrToggle.count() > 0 && isVisibleQuick(qrToggle)) {
                            qrToggle.click(new Locator.ClickOptions().setTimeout(DEFAULT_TIMEOUT));
                            log.info("已切换到智联二维码登录页面，等待用户扫码...");
                        } else {
                            log.info("智联招聘登录页面已打开，等待用户扫码...");
                        }
                    } catch (Exception e) {
//                        log.warn("智联招聘：打开二维码登录面板失败: {}", e.getMessage());
                    }
                }
                return false;
            }

            // 检查是否有已登录的特征
            try {
                String url = zhilianPage.url();
                if (url != null && url.contains("i.zhaopin.com")) {
                    log.debug("智联招聘：URL包含i.zhaopin.com，判定为已登录");
                    isLoggedIn = true;
                }
            } catch (Exception ignore) {
            }

            // 如果没有登录按钮，也认为已登录
            if (!loginButtonExists) {
                log.debug("智联招聘：未检测到登录按钮，判定为已登录");
                isLoggedIn = true;
            }

            return isLoggedIn;
        } catch (Exception e) {
            log.warn("智联招聘：检查登录状态异常: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 设置智联招聘登录状态监控
     *
     * @param page 页面实例
     */
    private void setupZhilianLoginMonitoring(Page page) {
        // 监听页面导航事件，检测URL变化
        page.onFrameNavigated(frame -> {
            if (frame == page.mainFrame()) {
                if (!zhilianMonitoringPaused && !isPlaywrightBusy()) {
                    checkZhilianLoginStatus(page);
                }
            }
        });

        log.info("智联招聘平台登录状态监控已启用");
    }

    /**
     * 检查智联招聘登录状态
     *
     * @param page 页面实例
     */
    private void checkZhilianLoginStatus(Page page) {
        try {
            boolean isLoggedIn = checkIfZhilianLoggedIn();
            // 如果登录状态发生变化（从未登录变为已登录）
            Boolean previousStatus = loginStatus.get("zhilian");
            if (isLoggedIn && (previousStatus == null || !previousStatus)) {
                onZhilianLoginSuccess();
            }
        } catch (Exception e) {
            // 忽略检查过程中的异常，避免影响正常流程
            log.debug("检查智联招聘平台登录状态时发生异常: {}", e.getMessage());
        }
    }

    /**
     * 主动触发智联招聘登录：点击二维码入口并等待登录成功跳转
     */
    public void triggerZhilianLogin() {
        try {
            if (zhilianPage == null) {
                throw new IllegalStateException("智联招聘页面未初始化");
            }

            // 导航到智联首页，确保DOM就绪
            zhilianPage.navigate(ZHILIAN_URL, new Page.NavigateOptions()
                    .setTimeout(60000)
                    .setWaitUntil(WaitUntilState.DOMCONTENTLOADED));

            // 如果看到未登录入口，尝试打开二维码登录面板
            Locator noLoginAnchor = zhilianPage.locator("a.home-header__c-no-login").first();
            if (isVisibleQuick(noLoginAnchor)) {
                Locator qrToggle = zhilianPage.locator("div.zppp-panel-normal-bar__img").first();
                if (isVisibleQuick(qrToggle)) {
                    qrToggle.click(new Locator.ClickOptions().setTimeout(DEFAULT_TIMEOUT));
                    log.info("已点击智联二维码登录入口，等待用户扫码...");
                } else {
                    log.warn("未找到二维码登录入口元素：div.zppp-panel-normal-bar__img");
                }
            } else {
                log.info("未检测到未登录入口，可能已登录或在其他页面");
            }

            // 监听登录成功：等待URL跳转到 i.zhaopin.com 或用户信息元素出现
            try {
                zhilianPage.waitForURL("**i.zhaopin.com**", new Page.WaitForURLOptions().setTimeout(120_000));
                onZhilianLoginSuccess();
                return;
            } catch (Exception ignored) {
            }
            try {
                zhilianPage.waitForSelector(".user-info, .user-name, .username-text", new Page.WaitForSelectorOptions().setTimeout(120_000));
                onZhilianLoginSuccess();
            } catch (Exception e) {
                log.warn("等待智联登录成功超时或失败: {}", e.getMessage());
            }
        } catch (Exception e) {
            log.error("触发智联登录流程失败: {}", e.getMessage(), e);
            throw new RuntimeException("触发智联登录流程失败", e);
        }
    }

    /**
     * 智联招聘登录成功回调
     */
    private void onZhilianLoginSuccess() {
        log.info("智联招聘平台登录成功");

        // 更新登录状态并通知
        setLoginStatus("zhilian", true);

        // 登录成功时保存 Cookie 到数据库
        saveZhilianCookiesToDatabase("login success");
    }

    /**
     * 保存智联招聘Cookie到数据库
     *
     * @param remark 备注信息
     */
    private void saveZhilianCookiesToDatabase(String remark) {
        try {
            List<com.microsoft.playwright.options.Cookie> cookies = filterCookiesByDomain(context.cookies(), ZHILIAN_DOMAIN);
            // 使用ObjectMapper序列化为JSON字符串
            String cookieJson = new ObjectMapper().writeValueAsString(cookies);
            boolean result = cookieService.saveOrUpdateCookie("zhilian", cookieJson, remark);
            if (result) {
                log.info("保存智联招聘Cookie成功，共 {} 条，remark={}", cookies.size(), remark);
            }
        } catch (Exception e) {
            log.warn("保存智联招聘Cookie失败: {}", e.getMessage());
        }
    }

    /**
     * 主动保存智联招聘Cookie到数据库（用于调试/验证）
     */
    public void saveZhilianCookiesToDb(String remark) {
        saveZhilianCookiesToDatabase(remark);
    }

    /**
     * 统一按平台保存 Cookie 到数据库
     *
     * @param platform 平台标识（boss/liepin/51job/zhilian）
     * @param remark   备注
     */
    public void saveCookiesToDb(String platform, String remark) {
        switch (platform) {
            case "boss" -> saveBossCookiesToDatabase(remark);
            case "liepin" -> saveLiepinCookiesToDatabase(remark);
            case "51job" -> save51jobCookiesToDatabase(remark);
            case "zhilian" -> saveZhilianCookiesToDatabase(remark);
            default -> throw new IllegalArgumentException("Unsupported platform: " + platform);
        }
    }

    /**
     * 清理智联招聘上下文中的Cookie
     */
    public void clearZhilianCookies() {
        try {
            if (context != null) {
                context.clearCookies();
                log.info("已清理共享上下文中的所有Cookie");
            } else {
                log.warn("共享上下文不存在，无法清理Cookie");
            }
        } catch (Exception e) {
            log.error("清理共享上下文Cookie失败: {}", e.getMessage(), e);
            throw new RuntimeException("清理共享上下文Cookie失败", e);
        }
    }

    /**
     * 暂停智联招聘页面的后台登录监控（避免与业务流程并发操作页面）
     */
    public void pauseZhilianMonitoring() {
        zhilianMonitoringPaused = true;
        log.debug("智联招聘登录监控已暂停");
    }

    /**
     * 恢复智联招聘页面的后台登录监控
     */
    public void resumeZhilianMonitoring() {
        zhilianMonitoringPaused = false;
        log.debug("智联招聘登录监控已恢复");
    }

    /**
     * 检查猎聘登录状态
     *
     * @param page 页面实例
     */
    private void checkLiepinLoginStatus(Page page) {
        try {
            boolean isLoggedIn = checkIfLiepinLoggedIn();
            // 如果登录状态发生变化（从未登录变为已登录）
            Boolean previousStatus = loginStatus.get("liepin");
            if (isLoggedIn && (previousStatus == null || !previousStatus)) {
                onLiepinLoginSuccess();
            }
        } catch (Exception e) {
            // 忽略检查过程中的异常，避免影响正常流程
            log.debug("检查猎聘平台登录状态时发生异常: {}", e.getMessage());
        }
    }

    /**
     * 猎聘登录成功回调
     */
    private void onLiepinLoginSuccess() {
        log.info("猎聘平台登录成功");

        // 更新登录状态并通知
        setLoginStatus("liepin", true);

        // 登录成功时保存 Cookie 到数据库
        saveLiepinCookiesToDatabase("login success");
    }

    /**
     * 保存猎聘Cookie到数据库
     *
     * @param remark 备注信息
     */
    private void saveLiepinCookiesToDatabase(String remark) {
        try {
            List<com.microsoft.playwright.options.Cookie> cookies = filterCookiesByDomain(context.cookies(), LIEPIN_DOMAIN);
            // 使用ObjectMapper序列化为JSON字符串
            String cookieJson = new ObjectMapper().writeValueAsString(cookies);
            boolean result = cookieService.saveOrUpdateCookie("liepin", cookieJson, remark);
            if (result) {
                log.info("保存猎聘Cookie成功，共 {} 条，remark={}", cookies.size(), remark);
            }
        } catch (Exception e) {
            log.warn("保存猎聘Cookie失败: {}", e.getMessage());
        }
    }

    /**
     * 主动保存猎聘Cookie到数据库（用于调试/验证）
     */
    public void saveLiepinCookiesToDb(String remark) {
        saveLiepinCookiesToDatabase(remark);
    }

    /**
     * 清理猎聘上下文中的Cookie
     */
    public void clearLiepinCookies() {
        try {
            if (context != null) {
                context.clearCookies();
                log.info("已清理共享上下文中的所有Cookie");
            } else {
                log.warn("共享上下文不存在，无法清理Cookie");
            }
        } catch (Exception e) {
            log.error("清理共享上下文Cookie失败: {}", e.getMessage(), e);
            throw new RuntimeException("清理共享上下文Cookie失败", e);
        }
    }

    /**
     * 暂停猎聘页面的后台登录监控（避免与业务流程并发操作页面）
     */
    public void pauseLiepinMonitoring() {
        liepinMonitoringPaused = true;
        log.debug("猎聘登录监控已暂停");
    }

    /**
     * 恢复猎聘页面的后台登录监控
     */
    public void resumeLiepinMonitoring() {
        liepinMonitoringPaused = false;
        log.debug("猎聘登录监控已恢复");
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
                // 统一复用更稳健的Boss登录判断逻辑
                isLoggedIn = checkIfLoggedIn();
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
        log.info("{}平台登录成功", platform);

        // 更新登录状态并通知（统一使用setLoginStatus方法）
        setLoginStatus(platform, true);

        // 登录成功时保存 Cookie 到数据库（仅 boss 平台）
        if ("boss".equals(platform)) {
            saveBossCookiesToDatabase("login success");
        }
    }

    /**
     * 统一的Boss Cookie保存方法（使用JSON序列化）
     *
     * @param remark 备注信息
     */
    private void saveBossCookiesToDatabase(String remark) {
        try {
            List<com.microsoft.playwright.options.Cookie> cookies = filterCookiesByDomain(context.cookies(), BOSS_DOMAIN);
            // 使用ObjectMapper序列化为JSON字符串
            String cookieJson = new ObjectMapper().writeValueAsString(cookies);
            boolean result = cookieService.saveOrUpdateCookie("boss", cookieJson, remark);
            if (result) {
                log.info("保存Boss Cookie成功，共 {} 条，remark={}", cookies.size(), remark);
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
     * 清理Boss上下文中的Cookie
     * 用于退出登录时清除浏览器上下文中的所有Cookie
     */
    public void clearBossCookies() {
        try {
            if (context != null) {
                context.clearCookies();
                log.info("已清理共享上下文中的所有Cookie");
            } else {
                log.warn("共享上下文不存在，无法清理Cookie");
            }
        } catch (Exception e) {
            log.error("清理共享上下文Cookie失败: {}", e.getMessage(), e);
            throw new RuntimeException("清理共享上下文Cookie失败", e);
        }
    }

    /**
     * 定时检查登录状态（每3秒）
     * 用于捕获通过DOM元素判断登录状态的场景（无导航也可触发）
     */
    @Scheduled(fixedDelay = 3000)
    public void scheduledLoginCheck() {
        if (playwright == null || context == null) {
            return;
        }
        // 必须跑在 Playwright 专用线程上；线程正忙（例如正在投递）就直接跳过这一轮，
        // 否则会打断投递流程里正在进行的导航
        tryRunOnPlaywright(() -> {
            try {
                if (liepinPage != null && !liepinMonitoringPaused) {
                    checkLiepinLoginStatus(liepinPage);
                }
                // 其他平台如需也可启用（保留，但不强制）
                if (bossPage != null && !bossMonitoringPaused) {
                    checkLoginStatus(bossPage, "boss");
                }
                if (job51Page != null && !job51MonitoringPaused) {
                    check51jobLoginStatus(job51Page);
                }
                if (zhilianPage != null && !zhilianMonitoringPaused) {
                    checkZhilianLoginStatus(zhilianPage);
                }
            } catch (Exception e) {
                log.debug("定时登录检测异常: {}", e.getMessage());
            }
        });
    }

    /**
     * 暂停Boss页面的后台登录监控（避免与业务流程并发操作页面）
     */
    public void pauseBossMonitoring() {
        bossMonitoringPaused = true;
        log.debug("Boss登录监控已暂停");
    }

    /**
     * 恢复Boss页面的后台登录监控
     */
    public void resumeBossMonitoring() {
        bossMonitoringPaused = false;
        log.debug("Boss登录监控已恢复");
    }

    /**
     * 关闭Playwright实例
     * 在Spring容器销毁前自动执行
     */
    @PreDestroy
    public void destroy() {
        log.info("开始关闭Playwright管理器...");

        try {
            // 关闭所有页面
            if (bossPage != null) {
                bossPage.close();
                log.info("Boss直聘页面已关闭");
            }
            if (liepinPage != null) {
                liepinPage.close();
                log.info("猎聘页面已关闭");
            }
            if (job51Page != null) {
                job51Page.close();
                log.info("51job页面已关闭");
            }
            if (zhilianPage != null) {
                zhilianPage.close();
                log.info("智联招聘页面已关闭");
            }

            // 关闭共享的BrowserContext（持久化上下文关闭即等于关浏览器，没有独立的 Browser 对象）
            if (context != null) {
                context.close();
                log.info("共享BrowserContext已关闭，浏览器已退出");
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
        return playwright != null && context != null && bossPage != null && !browserClosed;
    }

    /**
     * 确保浏览器可用：已经关闭就重新拉起来。
     * <p>
     * 浏览器被手动关掉或崩溃之后，各个字段依然非空，界面上看什么都正常，
     * 但每个页面操作都会抛 TargetClosedError —— 投递任务卡死、按钮点了没反应。
     * 发起任务前先过一遍这里。
     */
    public synchronized void ensureReady() {
        if (isInitialized()) {
            return;
        }
        log.warn("浏览器不可用（已关闭或未初始化），正在重新初始化...");
        // 旧对象已经失效，先清干净再重建，避免 init() 里的 isInitialized() 短路
        try {
            if (playwright != null) {
                playwright.close();
            }
        } catch (Exception ignored) {
            // 浏览器已经没了，关闭失败很正常
        }
        playwright = null;
        context = null;
        bossPage = null;
        liepinPage = null;
        job51Page = null;
        zhilianPage = null;
        browserClosed = false;
        loginStatus.clear();
        init();
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
     * 手动设置平台登录状态（会触发SSE通知）
     *
     * @param platform   平台名称
     * @param isLoggedIn 是否已登录
     */
    public void setLoginStatus(String platform, boolean isLoggedIn) {
        Boolean previousStatus = loginStatus.get(platform);

        // 只有状态真正发生变化时才更新和通知
        if (previousStatus == null || previousStatus != isLoggedIn) {
            loginStatus.put(platform, isLoggedIn);

            // Boss平台：在设置未登录状态时，顺带引导到登录页并切换二维码扫码
            if ("boss".equals(platform) && !isLoggedIn) {
                try {
                    if (bossPage != null) {
                        String currentUrl = null;
                        try { currentUrl = bossPage.url(); } catch (Exception ignored) {}

                        // 避免重复导航：若当前已在登录页则不再二次跳转
                        if (currentUrl == null || !currentUrl.contains("/web/user/")) {
                            bossPage.navigate(BOSS_URL + "/web/user/?ka=header-login");
                            try { Thread.sleep(800); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                        }

                        // 尝试切换到二维码登录（点击“APP扫码登录”按钮），优先使用新版选择器
                        try {
                            Locator qrSwitch = bossPage.locator(".btn-sign-switch.ewm-switch").first();
                            if (isVisibleQuick(qrSwitch)) {
                                qrSwitch.click();
                            } else {
                                // 兜底：按文本匹配内部提示
                                Locator tip = bossPage.getByText("APP扫码登录").first();
                                if (isVisibleQuick(tip)) {
                                    tip.click();
                                    log.info("已点击包含文本的二维码登录切换提示（APP扫码登录）");
                                } else {
                                    // 兼容旧版选择器
                                    Locator legacy = bossPage.locator("li.sign-switch-tip").first();
                                    if (isVisibleQuick(legacy)) {
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
                    }
                } catch (Exception e) {
                    log.debug("设置Boss未登录状态时执行登录引导失败: {}", e.getMessage());
                }
            }

            // 通知所有监听器（触发SSE推送）
            LoginStatusChange change = new LoginStatusChange(platform, isLoggedIn, System.currentTimeMillis());
            loginStatusListeners.forEach(listener -> {
                try {
                    listener.accept(change);
                } catch (Exception e) {
                    log.error("通知登录状态监听器失败: platform={}, isLoggedIn={}", platform, isLoggedIn, e);
                }
            });

//            log.info("登录状态已更新: platform={}, isLoggedIn={}", platform, isLoggedIn);
        }
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

    private List<Cookie> filterCookiesByDomain(List<Cookie> cookies, String domainSuffix) {
        if (cookies == null || cookies.isEmpty()) {
            return new ArrayList<>();
        }

        String suffix = domainSuffix == null ? "" : domainSuffix.toLowerCase(Locale.ROOT);
        List<Cookie> filtered = new ArrayList<>();
        for (Cookie cookie : cookies) {
            if (cookie == null || cookie.domain == null || cookie.domain.isBlank()) {
                continue;
            }
            String domain = cookie.domain.toLowerCase(Locale.ROOT);
            if (domain.equals(suffix) || domain.endsWith("." + suffix)) {
                filtered.add(cookie);
            }
        }

        return filtered;
    }

    /**
     * LoginStatusChange - 登录状态变化DTO
     */
    public record LoginStatusChange(String platform, boolean isLoggedIn, long timestamp) {
    }
}
