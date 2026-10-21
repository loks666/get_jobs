package com.getjobs.worker.service;

import com.getjobs.application.service.ConfigService;
import com.getjobs.application.service.NotifyService;
import com.getjobs.worker.boss.Boss;
import com.getjobs.worker.boss.BossConfig;
import com.getjobs.worker.dto.JobProgressMessage;
import com.getjobs.worker.manager.PlaywrightManager;
import com.microsoft.playwright.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Boss直聘任务服务
 * 管理Boss平台的投递任务执行和状态
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BossJobService implements JobPlatformService {
    private static final String PLATFORM = "boss";

    private final PlaywrightManager playwrightManager;
    private final ConfigService configService;
    private final ObjectProvider<Boss> bossProvider;
    private final NotifyService notifyService;

    // 任务运行状态
    private volatile boolean isRunning = false;
    // 停止标志
    private volatile boolean shouldStop = false;
    // 本次任务的开始时间（毫秒），用于判断任务是不是卡死了
    private volatile long runningSince = 0L;

    @Override
    public void executeDelivery(Consumer<JobProgressMessage> progressCallback) {
        if (isRunning) {
            progressCallback.accept(JobProgressMessage.warning(PLATFORM, "任务已在运行中"));
            return;
        }

        try {
            // 浏览器可能已经被关掉（手动关窗口 / Chrome 崩溃），这时各字段仍非空，
            // 但每个操作都会抛 TargetClosedError。先确保浏览器可用，必要时重新拉起。
            progressCallback.accept(JobProgressMessage.info(PLATFORM, "检查浏览器状态..."));
            playwrightManager.ensureReady();

            // 获取Boss页面实例
            Page page = playwrightManager.getBossPage();
            if (page == null) {
                progressCallback.accept(JobProgressMessage.error(PLATFORM, "Boss页面未初始化"));
                notifyService.needsAttention("Boss直聘", "Boss页面未初始化",
                        "浏览器会话可能已关闭，请重启应用或在页面上重新打开 Boss 登录页。");
                return;
            }

            // 检查是否已登录
            if (!playwrightManager.isLoggedIn(PLATFORM)) {
                progressCallback.accept(JobProgressMessage.error(PLATFORM, "请先登录Boss直聘"));
                notifyService.needsAttention("Boss直聘", "登录已失效",
                        "投递任务未能启动，请重新扫码登录 Boss直聘。");
                return;
            }

            // 通过校验后再标记运行
            isRunning = true;
            shouldStop = false;
            runningSince = System.currentTimeMillis();

            // 暂停后台登录监控，避免与投递流程并发访问同一Page
            playwrightManager.pauseBossMonitoring();

            // 加载配置（统一从 boss_config 专表读取）
            BossConfig config = configService.getBossConfig();
            progressCallback.accept(JobProgressMessage.info(PLATFORM, "配置加载成功"));

            progressCallback.accept(JobProgressMessage.info(PLATFORM, "开始投递任务..."));

            notifyService.deliveryStarted("Boss直聘", config.getKeywords(), config.getCityCode());

            // 创建Boss实例并执行投递
            Boss.ProgressCallback bossCallback = (message, current, total) -> {
                if (current != null && total != null) {
                    progressCallback.accept(JobProgressMessage.progress(PLATFORM, message, current, total));
                } else {
                    progressCallback.accept(JobProgressMessage.info(PLATFORM, message));
                }
            };

            Boss boss = bossProvider.getObject();
            boss.setPage(page);
            boss.setConfig(config);
            boss.setProgressCallback(bossCallback);
            boss.setShouldStopCallback(this::shouldStop);
            boss.prepare();

            // 整个投递流程必须跑在 Playwright 专用线程上。
            // 原来跑在 ForkJoinPool 上，会和 scheduling-1 的登录监控同时操作同一条
            // Playwright 连接，导航被打断报 net::ERR_ABORTED，整个任务直接挂掉。
            long startedAt = System.currentTimeMillis();
            int deliveredCount = playwrightManager.callOnPlaywright(boss::execute);
            long durationMs = System.currentTimeMillis() - startedAt;

            progressCallback.accept(JobProgressMessage.success(PLATFORM,
                String.format("投递任务完成，共发起%d个聊天", deliveredCount)));

            notifyService.deliveryFinished("Boss直聘", deliveredCount, durationMs);
        } catch (Exception e) {
            log.error("Boss投递任务执行失败", e);
            String reason = e.getMessage();
            progressCallback.accept(JobProgressMessage.error(PLATFORM, "投递失败: " + reason));
            notifyService.deliveryFailed("Boss直聘", reason);
        } finally {
            isRunning = false;
            shouldStop = false;
            runningSince = 0L;
            // 恢复后台登录监控
            try {
                playwrightManager.resumeBossMonitoring();
            } catch (Exception ignored) {}
        }
    }

    @Override
    public void stopDelivery() {
        if (!isRunning) {
            return;
        }
        log.info("收到停止Boss投递任务的请求");
        shouldStop = true;

        // shouldStop 只在投递流程的检查点生效；如果任务卡在某个 Playwright 调用里，
        // 它永远看不到这个标志，isRunning 就会一直是 true，把"开始投递"永久堵死。
        // 这里给它一段时间自行退出，超时就强制把状态复位，至少让界面能重新发起任务。
        new Thread(() -> {
            long deadline = System.currentTimeMillis() + FORCE_RESET_GRACE_MS;
            while (isRunning && System.currentTimeMillis() < deadline) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
            if (isRunning) {
                log.warn("Boss投递任务在 {} 秒内未响应停止指令，强制复位运行状态（后台可能仍有残留操作）",
                        FORCE_RESET_GRACE_MS / 1000);
                isRunning = false;
                runningSince = 0L;
                try {
                    playwrightManager.resumeBossMonitoring();
                } catch (Exception ignored) {}
            }
        }, "boss-stop-watchdog").start();
    }

    /** 停止指令发出后，等待任务自行退出的宽限时间 */
    private static final long FORCE_RESET_GRACE_MS = 15_000L;

    /** 任务已经跑了多久（毫秒）；没在跑返回 0 */
    public long runningForMillis() {
        long since = runningSince;
        return since == 0L ? 0L : System.currentTimeMillis() - since;
    }

    @Override
    public Map<String, Object> getStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("platform", PLATFORM);
        status.put("isRunning", isRunning);
        status.put("isLoggedIn", playwrightManager.isLoggedIn(PLATFORM));
        return status;
    }

    @Override
    public String getPlatformName() {
        return PLATFORM;
    }

    @Override
    public boolean isRunning() {
        return isRunning;
    }

    /**
     * 检查是否应该停止
     * 供Boss.java调用
     */
    public boolean shouldStop() {
        return shouldStop;
    }

    
}
