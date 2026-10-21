package com.getjobs.worker.service;

import com.getjobs.application.service.ConfigService;
import com.getjobs.application.service.NotifyService;
import com.getjobs.worker.dto.JobProgressMessage;
import com.getjobs.worker.manager.PlaywrightManager;
import com.getjobs.worker.zhilian.ZhiLian;
import com.getjobs.worker.zhilian.ZhilianConfig;
import com.microsoft.playwright.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 智联招聘任务服务
 * 管理智联招聘平台的投递任务执行和状态
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ZhilianJobService implements JobPlatformService {
    private static final String PLATFORM = "zhilian";

    private final PlaywrightManager playwrightManager;
    private final ObjectProvider<ZhiLian> zhilianProvider;
    private final ConfigService configService;
    private final NotifyService notifyService;

    // 任务运行状态
    private volatile boolean isRunning = false;
    // 停止标志
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

            // 获取智联招聘页面实例
            Page page = playwrightManager.getZhilianPage();
            if (page == null) {
                progressCallback.accept(JobProgressMessage.error(PLATFORM, "智联招聘页面未初始化"));
                notifyService.needsAttention("智联招聘", "页面未初始化",
                        "浏览器会话可能已关闭，请重启应用或重新打开智联登录页。");
                return;
            }

            // 检查是否已登录
            if (!playwrightManager.isLoggedIn(PLATFORM)) {
                progressCallback.accept(JobProgressMessage.error(PLATFORM, "请先登录智联招聘"));
                notifyService.needsAttention("智联招聘", "登录已失效",
                        "投递任务未能启动，请重新扫码登录智联招聘。");
                return;
            }

            // 通过校验后再标记运行
            isRunning = true;
            shouldStop = false;

            // 暂停后台登录监控，避免与投递流程并发访问同一Page
            playwrightManager.pauseZhilianMonitoring();

            // 加载配置（统一从 zhilian_config 专表读取）
            ZhilianConfig config = configService.getZhilianConfig();
            progressCallback.accept(JobProgressMessage.info(PLATFORM, "配置加载成功"));

            progressCallback.accept(JobProgressMessage.info(PLATFORM, "开始投递任务..."));

            notifyService.deliveryStarted("智联招聘", config.getKeywords(), config.getCityCode());

            // 创建ZhiLian实例并执行投递
            ZhiLian.ProgressCallback zhilianCallback = (message, current, total) -> {
                if (current != null && total != null) {
                    progressCallback.accept(JobProgressMessage.progress(PLATFORM, message, current, total));
                } else {
                    progressCallback.accept(JobProgressMessage.info(PLATFORM, message));
                }
            };

            ZhiLian zhilian = zhilianProvider.getObject();
            zhilian.setPage(page);
            zhilian.setConfig(config);
            zhilian.setProgressCallback(zhilianCallback);
            zhilian.setShouldStopCallback(this::shouldStop);
            zhilian.prepare();

            // 必须跑在 Playwright 专用线程上，否则会和后台登录监控并发操作同一条连接
            long startedAt = System.currentTimeMillis();
            int deliveredCount = playwrightManager.callOnPlaywright(zhilian::execute);
            long durationMs = System.currentTimeMillis() - startedAt;

            progressCallback.accept(JobProgressMessage.success(PLATFORM,
                String.format("投递任务完成，共投递%d个职位", deliveredCount)));

            notifyService.deliveryFinished("智联招聘", deliveredCount, durationMs);
        } catch (Exception e) {
            log.error("智联招聘投递任务执行失败", e);
            String reason = e.getMessage();
            progressCallback.accept(JobProgressMessage.error(PLATFORM, "投递失败: " + reason));
            notifyService.deliveryFailed("智联招聘", reason);
        } finally {
            isRunning = false;
            shouldStop = false;
            // 恢复后台登录监控
            try {
                playwrightManager.resumeZhilianMonitoring();
            } catch (Exception ignored) {}
        }
    }

    @Override
    public void stopDelivery() {
        if (isRunning) {
            log.info("收到停止智联招聘投递任务的请求");
            shouldStop = true;
        }
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
     */
    public boolean shouldStop() {
        return shouldStop;
    }

    
}
