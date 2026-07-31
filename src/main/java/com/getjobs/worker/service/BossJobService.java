package com.getjobs.worker.service;

import com.getjobs.application.service.ConfigService;
import com.getjobs.worker.boss.Boss;
import com.getjobs.worker.boss.BossAuthenticationExpiredException;
import com.getjobs.worker.boss.BossConfig;
import com.getjobs.worker.boss.BossDailyDeliveryLimitReachedException;
import com.getjobs.worker.dto.JobProgressMessage;
import com.getjobs.worker.manager.PlaywrightManager;
import com.getjobs.worker.utils.DeliveryLimit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
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

    // 任务运行状态
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    // 停止标志
    private volatile boolean shouldStop = false;

    @Override
    public void executeDelivery(Consumer<JobProgressMessage> progressCallback) {
        if (!isRunning.compareAndSet(false, true)) {
            progressCallback.accept(JobProgressMessage.warning(PLATFORM, "任务已在运行中"));
            return;
        }

        try {
            if (!playwrightManager.hasPage(PLATFORM)) {
                progressCallback.accept(JobProgressMessage.error(PLATFORM, "Boss页面未初始化"));
                return;
            }

            // 检查是否已登录
            if (!playwrightManager.isLoggedIn(PLATFORM)) {
                progressCallback.accept(JobProgressMessage.error(PLATFORM, "请先登录Boss直聘"));
                return;
            }

            // 通过校验后再标记运行
            shouldStop = false;

            // 暂停后台登录监控，避免与投递流程并发访问同一Page
            playwrightManager.pauseBossMonitoring();

            // 加载配置（统一从 boss_config 专表读取）
            BossConfig config = configService.getBossConfig();
            progressCallback.accept(JobProgressMessage.info(PLATFORM, "配置加载成功"));

            progressCallback.accept(JobProgressMessage.info(PLATFORM, "开始投递任务..."));

            // 创建Boss实例并执行投递
            Boss.ProgressCallback bossCallback = (message, current, total) -> {
                if (current != null && total != null) {
                    progressCallback.accept(JobProgressMessage.progress(PLATFORM, message, current, total));
                } else {
                    progressCallback.accept(JobProgressMessage.info(PLATFORM, message));
                }
            };

            int deliveredCount = playwrightManager.withPage(PLATFORM, page -> {
                Boss boss = bossProvider.getObject();
                boss.setPage(page);
                boss.setConfig(config);
                boss.setProgressCallback(bossCallback);
                boss.setShouldStopCallback(this::shouldStop);
                boss.prepare();
                return boss.execute();
            });

            progressCallback.accept(JobProgressMessage.success(PLATFORM,
                String.format("投递任务完成，共发起%d个聊天", deliveredCount)));
        } catch (BossDailyDeliveryLimitReachedException e) {
            log.warn("[boss] 今日已达到投递上限，任务已自动停止");
            progressCallback.accept(JobProgressMessage.warning(
                    PLATFORM,
                    "今日已达投递上限，程序已自动停止投递。",
                    "BOSS_DAILY_DELIVERY_LIMIT_REACHED"));
        } catch (BossAuthenticationExpiredException e) {
            log.warn("[boss] 检测到 Cookie 已过期，投递任务已停止，请重新获取 Cookie 后登录");
            try {
                playwrightManager.handleBossAuthenticationExpired();
            } catch (Exception cleanupError) {
                log.warn("[boss] 清理过期 Cookie 失败: {}", cleanupError.getMessage());
            }
            progressCallback.accept(JobProgressMessage.error(
                    PLATFORM,
                    "检测到 Boss 登录已失效，投递已停止。请重新获取 Cookie 并重新登录后再继续投递。",
                    "BOSS_COOKIE_EXPIRED"));
        } catch (Exception e) {
            log.error("Boss投递任务执行失败", e);
            progressCallback.accept(JobProgressMessage.error(PLATFORM, "投递失败: " + e.getMessage()));
        } finally {
            isRunning.set(false);
            shouldStop = false;
            // 恢复后台登录监控
            try {
                playwrightManager.resumeBossMonitoring();
            } catch (Exception ignored) {}
        }
    }

    @Override
    public void stopDelivery() {
        if (isRunning.get()) {
            log.info("收到停止Boss投递任务的请求");
            shouldStop = true;
        }
    }

    @Override
    public Map<String, Object> getStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("platform", PLATFORM);
        status.put("isRunning", isRunning.get());
        status.put("isLoggedIn", playwrightManager.isLoggedIn(PLATFORM));
        status.put("maxDeliveryAttempts", DeliveryLimit.configuredMax());
        return status;
    }

    @Override
    public String getPlatformName() {
        return PLATFORM;
    }

    @Override
    public boolean isRunning() {
        return isRunning.get();
    }

    /**
     * 检查是否应该停止
     * 供Boss.java调用
     */
    public boolean shouldStop() {
        return shouldStop;
    }

    
}
