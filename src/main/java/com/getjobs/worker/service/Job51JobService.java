package com.getjobs.worker.service;

import com.getjobs.worker.dto.JobProgressMessage;
import com.getjobs.worker.job51.Job51;
import com.getjobs.worker.job51.Job51Config;
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
 * 51job任务服务
 * 管理51job平台的投递任务执行和状态
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class Job51JobService implements JobPlatformService {
    private static final String PLATFORM = "51job";

    private final PlaywrightManager playwrightManager;
    private final ObjectProvider<Job51> job51Provider;

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
            // 获取51job页面实例
            Page page = playwrightManager.getJob51Page();
            if (page == null) {
                progressCallback.accept(JobProgressMessage.error(PLATFORM, "51job页面未初始化"));
                return;
            }

            // 检查是否已登录
            if (!playwrightManager.isLoggedIn(PLATFORM)) {
                progressCallback.accept(JobProgressMessage.error(PLATFORM, "请先登录51job"));
                return;
            }

            // 通过校验后再标记运行
            isRunning = true;
            shouldStop = false;

            // 暂停后台登录监控，避免与投递流程并发访问同一Page
            playwrightManager.pause51jobMonitoring();

            // 加载配置
            Job51Config config = Job51Config.init();
            progressCallback.accept(JobProgressMessage.info(PLATFORM, "配置加载成功"));

            progressCallback.accept(JobProgressMessage.info(PLATFORM, "开始投递任务..."));

            // 创建Job51实例并执行投递
            Job51.ProgressCallback job51Callback = (message, current, total) -> {
                if (current != null && total != null) {
                    progressCallback.accept(JobProgressMessage.progress(PLATFORM, message, current, total));
                } else {
                    progressCallback.accept(JobProgressMessage.info(PLATFORM, message));
                }
            };

            Job51 job51 = job51Provider.getObject();
            job51.setPage(page);
            job51.setConfig(config);
            job51.setProgressCallback(job51Callback);
            job51.setShouldStopCallback(this::shouldStop);
            job51.prepare();

            int deliveredCount = job51.execute();

            progressCallback.accept(JobProgressMessage.success(PLATFORM,
                String.format("投递任务完成，共投递%d个职位", deliveredCount)));
        } catch (Exception e) {
            log.error("51job投递任务执行失败", e);
            progressCallback.accept(JobProgressMessage.error(PLATFORM, "投递失败: " + e.getMessage()));
        } finally {
            isRunning = false;
            shouldStop = false;
            // 恢复后台登录监控
            try {
                playwrightManager.resume51jobMonitoring();
            } catch (Exception ignored) {}
        }
    }

    @Override
    public void stopDelivery() {
        if (isRunning) {
            log.info("收到停止51job投递任务的请求");
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
