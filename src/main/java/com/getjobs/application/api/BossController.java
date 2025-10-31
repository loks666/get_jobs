package com.getjobs.application.api;

import com.getjobs.application.service.BossService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Boss直聘控制器
 * 提供Boss自动投递功能的REST API接口
 */
@RestController
@RequestMapping("/api/boss")
@CrossOrigin(origins = "*")
public class BossController {
    
    private static final Logger log = LoggerFactory.getLogger(BossController.class);
    
    @Autowired
    private BossService bossService;
    
    /**
     * 启动Boss自动投递任务
     * @return 响应结果
     */
    @PostMapping("/start")
    public ResponseEntity<Map<String, Object>> startBossJob() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            // 检查是否已有任务在运行
            if (bossService.isTaskRunning()) {
                response.put("success", false);
                response.put("message", "Boss任务已在运行中，请等待当前任务完成");
                response.put("status", "running");
                return ResponseEntity.badRequest().body(response);
            }
            
            // 启动新任务
            String taskId = bossService.startBossJob();
            
            response.put("success", true);
            response.put("message", "Boss任务启动成功");
            response.put("taskId", taskId);
            response.put("status", "started");
            
            log.info("通过API启动Boss任务成功，任务ID: {}", taskId);
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
     * 获取任务状态
     * @param taskId 任务ID
     * @return 任务状态信息
     */
    @GetMapping("/status/{taskId}")
    public ResponseEntity<Map<String, Object>> getTaskStatus(@PathVariable String taskId) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            BossService.TaskStatus status = bossService.getTaskStatus(taskId);
            
            if (status == null) {
                response.put("success", false);
                response.put("message", "任务不存在");
                return ResponseEntity.notFound().build();
            }
            
            response.put("success", true);
            response.put("taskId", taskId);
            response.put("status", status.name());
            response.put("statusDescription", status.getDescription());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("获取任务状态失败", e);
            response.put("success", false);
            response.put("message", "获取任务状态失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }
    
    /**
     * 获取当前运行状态
     * @return 当前状态信息
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getCurrentStatus() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            boolean isRunning = bossService.isTaskRunning();
            String statusDescription = bossService.getCurrentStatus();
            
            response.put("success", true);
            response.put("isRunning", isRunning);
            response.put("status", statusDescription);
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
     * 清理已完成的任务记录
     * @return 清理结果
     */
    @DeleteMapping("/cleanup")
    public ResponseEntity<Map<String, Object>> cleanupTasks() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            bossService.cleanupCompletedTasks();
            
            response.put("success", true);
            response.put("message", "任务记录清理完成");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("清理任务记录失败", e);
            response.put("success", false);
            response.put("message", "清理失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
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