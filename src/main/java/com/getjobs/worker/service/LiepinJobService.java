package com.getjobs.worker.service;

import com.getjobs.application.service.ConfigService;
import com.getjobs.application.service.NotifyService;
import com.getjobs.worker.dto.JobProgressMessage;
import com.getjobs.worker.liepin.Liepin;
import com.getjobs.worker.liepin.LiepinConfig;
import com.getjobs.worker.manager.PlaywrightManager;
import com.microsoft.playwright.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 猎聘任务服务
 * 负责猎聘平台的自动投递任务管理
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LiepinJobService implements JobPlatformService {

    private static final String PLATFORM = "liepin";

    private final PlaywrightManager playwrightManager;
    private final ConfigService configService;
    private final ObjectProvider<Liepin> liepinProvider;
    private final NotifyService notifyService;

    // 运行状态标志
    private volatile boolean isRunning = false;

    // 停止请求标志
    private volatile boolean shouldStop = false;

    @Override
    public void executeDelivery(Consumer<JobProgressMessage> progressCallback) {
        if (isRunning) {
            progressCallback.accept(JobProgressMessage.warning(PLATFORM, "任务已在运行中"));
            return;
        }

        try {
            // 浏览器可能已被手动关闭 / 崩溃，先确保可用，必要时重新拉起
            progressCallback.accept(JobProgressMessage.info(PLATFORM, "检查浏览器状态..."));
            playwrightManager.ensureReady();

            Page page = playwrightManager.getLiepinPage();
            if (page == null) {
                progressCallback.accept(JobProgressMessage.error(PLATFORM, "猎聘页面未初始化"));
                notifyService.needsAttention("猎聘", "页面未初始化",
                        "浏览器会话可能已关闭，请重启应用或重新打开猎聘登录页。");
                return;
            }

            if (!playwrightManager.isLoggedIn(PLATFORM)) {
                progressCallback.accept(JobProgressMessage.error(PLATFORM, "请先登录猎聘"));
                notifyService.needsAttention("猎聘", "登录已失效",
                        "投递任务未能启动，请重新扫码登录猎聘。");
                return;
            }

            isRunning = true;
            shouldStop = false;

            // 暂停后台登录监控，避免并发访问冲突
            playwrightManager.pauseLiepinMonitoring();

            // 加载配置（统一通过 ConfigService 从专表读取）
            LiepinConfig config = configService.getLiepinConfig();
            progressCallback.accept(JobProgressMessage.info(PLATFORM, "配置加载成功"));

            progressCallback.accept(JobProgressMessage.info(PLATFORM, "开始投递任务..."));

            notifyService.deliveryStarted("猎聘", config.getKeywords(),
                    java.util.Collections.singletonList(config.getCityCode()));

            // 创建并执行 Bean
            Liepin.ProgressCallback cb = (message, current, total) -> {
                if (current != null && total != null) {
                    progressCallback.accept(JobProgressMessage.progress(PLATFORM, message, current, total));
                } else {
                    progressCallback.accept(JobProgressMessage.info(PLATFORM, message));
                }
            };

            Liepin liepin = liepinProvider.getObject();
            liepin.setPage(page);
            liepin.setConfig(config);
            liepin.setProgressCallback(cb);
            liepin.setShouldStopCallback(this::shouldStop);

            // 必须跑在 Playwright 专用线程上，否则会和后台登录监控并发操作同一条连接
            long startedAt = System.currentTimeMillis();
            int deliveredCount = playwrightManager.callOnPlaywright(liepin::execute);
            long durationMs = System.currentTimeMillis() - startedAt;

            progressCallback.accept(JobProgressMessage.success(PLATFORM,
                String.format("投递任务完成，共发起%d个聊天", deliveredCount)));

            notifyService.deliveryFinished("猎聘", deliveredCount, durationMs);
        } catch (Exception e) {
            log.error("猎聘投递任务执行失败", e);
            String reason = e.getMessage();
            progressCallback.accept(JobProgressMessage.error(PLATFORM, "投递失败: " + reason));
            notifyService.deliveryFailed("猎聘", reason);
        } finally {
            isRunning = false;
            shouldStop = false;
            try {
                playwrightManager.resumeLiepinMonitoring();
            } catch (Exception ignored) {}
        }
    }

    @Override
    public void stopDelivery() {
        if (!isRunning) {
            log.warn("猎聘任务未在运行，无需停止");
            return;
        }
        log.info("收到停止猎聘任务请求");
        shouldStop = true;
    }

    /**
     * 获取任务状态
     */
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

    public boolean shouldStop() {
        return shouldStop;
    }

    
}
