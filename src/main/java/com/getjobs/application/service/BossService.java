package com.getjobs.application.service;

import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Boss直聘服务类
 * 提供启动Boss自动投递功能的服务接口
 */
@Service
public class BossService {
    
    private static final Logger log = LoggerFactory.getLogger(BossService.class);
    
    // 用于跟踪Boss任务的执行状态
    private final ConcurrentHashMap<String, TaskStatus> taskStatusMap = new ConcurrentHashMap<>();
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    
    /**
     * 任务状态枚举
     */
    @Getter
    public enum TaskStatus {
        RUNNING("运行中"),
        COMPLETED("已完成"),
        FAILED("执行失败"),
        STOPPED("已停止");
        
        private final String description;
        
        TaskStatus(String description) {
            this.description = description;
        }

    }
    
    /**
     * 启动Boss自动投递任务
     * @return 任务ID
     */
    public String startBossJob() {
        if (isRunning.get()) {
            throw new RuntimeException("Boss任务已在运行中，请等待当前任务完成");
        }
        
        String taskId = "boss_task_" + System.currentTimeMillis();
        taskStatusMap.put(taskId, TaskStatus.RUNNING);
        isRunning.set(true);
        
        log.info("开始启动Boss自动投递任务，任务ID: {}", taskId);
        
        // 异步执行Boss任务
        executeBossJobAsync(taskId);
        
        return taskId;
    }
    
    /**
     * 异步执行Boss任务
     * @param taskId 任务ID
     */
    @Async
    public void executeBossJobAsync(String taskId) {
        try {
            log.info("Boss任务开始执行，任务ID: {}", taskId);
            
            // 调用Boss的main方法逻辑
            executeBossMainLogic();
            
            taskStatusMap.put(taskId, TaskStatus.COMPLETED);
            log.info("Boss任务执行完成，任务ID: {}", taskId);
            
        } catch (Exception e) {
            log.error("Boss任务执行失败，任务ID: {}, 错误信息: {}", taskId, e.getMessage(), e);
            taskStatusMap.put(taskId, TaskStatus.FAILED);
        } finally {
            isRunning.set(false);
        }
    }
    
    /**
     * 执行Boss的核心逻辑
     * 将Boss类的逻辑封装为可调用的方法
     *
     * @deprecated 已被 BossJobService 替代，请使用 worker.service.BossJobService
     */
    @Deprecated
    private void executeBossMainLogic() {
        throw new UnsupportedOperationException(
            "Boss.main() 已被重构为非静态类。请使用 com.getjobs.worker.service.BossJobService 代替。");
    }
    
    /**
     * 获取任务状态
     * @param taskId 任务ID
     * @return 任务状态
     */
    public TaskStatus getTaskStatus(String taskId) {
        return taskStatusMap.getOrDefault(taskId, null);
    }
    
    /**
     * 检查是否有任务正在运行
     * @return true表示有任务在运行
     */
    public boolean isTaskRunning() {
        return isRunning.get();
    }
    
    /**
     * 获取当前运行状态描述
     * @return 状态描述
     */
    public String getCurrentStatus() {
        if (isRunning.get()) {
            return "Boss任务正在运行中...";
        } else {
            return "Boss任务未运行";
        }
    }
    
    /**
     * 清理已完成的任务记录
     */
    public void cleanupCompletedTasks() {
        taskStatusMap.entrySet().removeIf(entry -> 
            entry.getValue() == TaskStatus.COMPLETED || 
            entry.getValue() == TaskStatus.FAILED ||
            entry.getValue() == TaskStatus.STOPPED
        );
        log.info("已清理完成的任务记录");
    }
}