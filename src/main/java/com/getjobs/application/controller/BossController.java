package com.getjobs.application.controller;

import com.getjobs.worker.service.BossJobService;
import com.getjobs.worker.dto.JobProgressMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Boss直聘控制器
 * 提供Boss自动投递功能的REST API接口
 */
@Slf4j
@RestController
@RequestMapping("/api/boss")
@CrossOrigin(origins = "*")
public class BossController {

    @Autowired
    private BossJobService bossJobService;
    
    /**
     * 启动Boss自动投递任务
     * @return 响应结果
     */
    @PostMapping("/start")
    public ResponseEntity<Map<String, Object>> startBossJob() {
        Map<String, Object> response = new HashMap<>();

        try {
            // 检查是否已有任务在运行
            if (bossJobService.isRunning()) {
                response.put("success", false);
                response.put("message", "Boss任务已在运行中，请等待当前任务完成");
                response.put("status", "running");
                return ResponseEntity.badRequest().body(response);
            }

            // 异步启动新任务
            CompletableFuture.runAsync(() -> {
                bossJobService.executeDelivery(progressMessage -> {
                    log.info("[{}] {}", progressMessage.getPlatform(), progressMessage.getMessage());
                });
            });

            response.put("success", true);
            response.put("message", "Boss任务启动成功");
            response.put("status", "started");

            log.info("通过API启动Boss任务成功");
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("启动Boss任务失败", e);
            response.put("success", false);
            response.put("message", "启动Boss任务失败: " + e.getMessage());
            response.put("error", e.getClass().getSimpleName());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * 停止Boss任务
     * @return 响应结果
     */
    @PostMapping("/stop")
    public ResponseEntity<Map<String, Object>> stopBossJob() {
        Map<String, Object> response = new HashMap<>();

        try {
            if (!bossJobService.isRunning()) {
                response.put("success", false);
                response.put("message", "没有正在运行的Boss任务");
                return ResponseEntity.badRequest().body(response);
            }

            bossJobService.stopDelivery();

            response.put("success", true);
            response.put("message", "Boss任务停止请求已发送");

            log.info("通过API停止Boss任务");
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("停止Boss任务失败", e);
            response.put("success", false);
            response.put("message", "停止Boss任务失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * 获取任务状态（兼容旧API）
     * @param taskId 任务ID（已废弃，不再使用）
     * @return 任务状态信息
     */
    @GetMapping("/status/{taskId}")
    @Deprecated
    public ResponseEntity<Map<String, Object>> getTaskStatus(@PathVariable String taskId) {
        // 重定向到新的状态接口
        return getCurrentStatus();
    }
    
    /**
     * 获取当前运行状态
     * @return 当前状态信息
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getCurrentStatus() {
        Map<String, Object> response = new HashMap<>();

        try {
            Map<String, Object> status = bossJobService.getStatus();
            response.put("success", true);
            response.putAll(status);
            response.put("timestamp", System.currentTimeMillis());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("获取当前状态失败", e);
            response.put("success", false);
            response.put("message", "获取状态失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * 清理已完成的任务记录（已废弃）
     * @return 清理结果
     */
    @DeleteMapping("/cleanup")
    @Deprecated
    public ResponseEntity<Map<String, Object>> cleanupTasks() {
        Map<String, Object> response = new HashMap<>();

        // BossJobService 不再使用任务记录机制，此接口保留用于兼容
        response.put("success", true);
        response.put("message", "任务记录清理功能已废弃，新版本不再需要此操作");

        return ResponseEntity.ok(response);
    }

    /**
     * 健康检查接口
     * @return 服务状态
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("service", "BossController");
        response.put("status", "healthy");
        response.put("timestamp", System.currentTimeMillis());

        return ResponseEntity.ok(response);
    }
}