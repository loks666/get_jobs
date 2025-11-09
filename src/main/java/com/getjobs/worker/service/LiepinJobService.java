package com.getjobs.worker.service;

import com.getjobs.worker.manager.PlaywrightManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 猎聘任务服务
 * 负责猎聘平台的自动投递任务管理
 */
@Slf4j
@Service
public class LiepinJobService {

    @Autowired
    private PlaywrightManager playwrightManager;

    // 运行状态标志
    private final AtomicBoolean running = new AtomicBoolean(false);

    // 停止请求标志
    private final AtomicBoolean stopRequested = new AtomicBoolean(false);

    /**
     * 检查任务是否正在运行
     */
    public boolean isRunning() {
        return running.get();
    }

    /**
     * 执行猎聘投递任务
     */
    public void executeDelivery(java.util.function.Consumer<ProgressMessage> progressCallback) {
        if (running.getAndSet(true)) {
            log.warn("猎聘任务已在运行中，跳过本次执行");
            return;
        }

        stopRequested.set(false);

        try {
            log.info("========================================");
            log.info("  开始执行猎聘投递任务");
            log.info("========================================");

            // 暂停后台登录监控，避免并发操作页面
            playwrightManager.pauseLiepinMonitoring();

            progressCallback.accept(new ProgressMessage("liepin", "猎聘投递任务已启动"));

            // TODO: 这里添加实际的猎聘投递逻辑
            // 可以参考BossJobService的实现

            // 模拟任务执行
            Thread.sleep(2000);

            if (stopRequested.get()) {
                log.info("猎聘任务已被用户停止");
                progressCallback.accept(new ProgressMessage("liepin", "猎聘投递任务已停止"));
            } else {
                log.info("猎聘投递任务执行完成");
                progressCallback.accept(new ProgressMessage("liepin", "猎聘投递任务执行完成"));
            }

        } catch (InterruptedException e) {
            log.warn("猎聘任务被中断");
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error("猎聘任务执行失败", e);
            progressCallback.accept(new ProgressMessage("liepin", "猎聘投递任务执行失败: " + e.getMessage()));
        } finally {
            running.set(false);
            stopRequested.set(false);
            // 恢复后台登录监控
            playwrightManager.resumeLiepinMonitoring();
            log.info("猎聘任务已结束");
        }
    }

    /**
     * 停止猎聘投递任务
     */
    public void stopDelivery() {
        if (!running.get()) {
            log.warn("猎聘任务未在运行，无需停止");
            return;
        }

        log.info("收到停止猎聘任务请求");
        stopRequested.set(true);
    }

    /**
     * 获取任务状态
     */
    public Map<String, Object> getStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("running", running.get());
        status.put("stopRequested", stopRequested.get());
        status.put("platform", "liepin");
        return status;
    }

    /**
     * 进度消息DTO
     */
    public record ProgressMessage(String platform, String message) {
        public String getPlatform() {
            return platform;
        }

        public String getMessage() {
            return message;
        }
    }
}
