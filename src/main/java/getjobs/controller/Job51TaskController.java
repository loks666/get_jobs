package getjobs.controller;

import getjobs.common.dto.ConfigDTO;
import getjobs.modules.job51.service.Job51TaskService;
import getjobs.modules.job51.service.Job51TaskService.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 51job任务控制器 - 提供4个独立的API接口
 * 
 * @author loks666
 *         项目链接: <a href=
 *         "https://github.com/loks666/get_jobs">https://github.com/loks666/get_jobs</a>
 */
@Slf4j
@RestController
@RequestMapping("/api/job51/task")
public class Job51TaskController {

    private final Job51TaskService job51TaskService;

    public Job51TaskController(Job51TaskService job51TaskService) {
        this.job51TaskService = job51TaskService;
    }

    /**
     * 1. 登录接口
     * POST /api/job51/task/login
     * 
     * @param config 51job配置信息
     * @return 登录结果
     */
    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody ConfigDTO config) {
        log.info("接收到51job登录请求");

        try {
            LoginResult result = job51TaskService.login(config);

            Map<String, Object> response = new HashMap<>();
            response.put("success", result.isSuccess());
            response.put("taskId", result.getTaskId());
            response.put("message", result.getMessage());
            response.put("timestamp", result.getTimestamp());

            if (result.isSuccess()) {
                log.info("51job登录成功，任务ID: {}", result.getTaskId());
                return ResponseEntity.ok(response);
            } else {
                log.warn("51job登录失败，任务ID: {}, 原因: {}", result.getTaskId(), result.getMessage());
                return ResponseEntity.badRequest().body(response);
            }

        } catch (Exception e) {
            log.error("51job登录接口执行异常", e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "登录接口执行异常: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * 2. 采集岗位接口
     * POST /api/job51/task/collect
     * 
     * @param config 51job配置信息
     * @return 采集结果
     */
    @PostMapping("/collect")
    public ResponseEntity<Map<String, Object>> collectJobs(@RequestBody ConfigDTO config) {
        log.info("接收到51job岗位采集请求");

        try {
            CollectResult result = job51TaskService.collectJobs(config);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("taskId", result.getTaskId());
            response.put("message", result.getMessage());
            response.put("jobCount", result.getJobCount());
            response.put("timestamp", result.getTimestamp());

            // 如果需要返回详细岗位信息，可以添加以下行
            // response.put("jobs", result.getJobs());

            log.info("51job岗位采集完成，任务ID: {}, 采集到 {} 个岗位", result.getTaskId(), result.getJobCount());
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("51job岗位采集接口执行异常", e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "采集接口执行异常: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * 3. 过滤岗位接口
     * POST /api/job51/task/filter
     * 
     * @param request 过滤请求
     * @return 过滤结果
     */
    @PostMapping("/filter")
    public ResponseEntity<Map<String, Object>> filterJobs(@RequestBody FilterRequest request) {
        log.info("接收到51job岗位过滤请求");

        try {
            FilterResult result = job51TaskService.filterJobs(request.getConfig());

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", result.getMessage());
            response.put("originalCount", result.getOriginalCount());
            response.put("filteredCount", result.getFilteredCount());
            response.put("timestamp", result.getTimestamp());

            // 如果需要返回详细岗位信息，可以添加以下行
            // response.put("jobs", result.getJobs());

            log.info("51job岗位过滤完成，原始 {} 个，过滤后 {} 个",
                    result.getOriginalCount(), result.getFilteredCount());
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("51job岗位过滤接口执行异常", e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "过滤接口执行异常: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * 4. 投递岗位接口
     * POST /api/job51/task/deliver
     * 
     * @param request 投递请求（包含配置和是否实际投递）
     * @return 投递结果
     */
    @PostMapping("/deliver")
    public ResponseEntity<Map<String, Object>> deliverJobs(@RequestBody DeliveryRequest request) {
        log.info("接收到51job岗位投递请求，实际投递: {}",
                request.isEnableActualDelivery());

        try {
            DeliveryResult result = job51TaskService.deliverJobs(
                    request.getConfig(),
                    request.isEnableActualDelivery());

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("taskId", result.getTaskId());
            response.put("message", result.getMessage());
            response.put("totalCount", result.getTotalCount());
            response.put("deliveredCount", result.getDeliveredCount());
            response.put("actualDelivery", result.isActualDelivery());
            response.put("timestamp", result.getTimestamp());

            // 如果有岗位详情，添加到响应中
            if (result.getJobDetails() != null && !result.getJobDetails().isEmpty()) {
                response.put("jobDetails", result.getJobDetails());
            }

            log.info("51job岗位投递完成，任务ID: {}, 处理 {} 个岗位",
                    result.getTaskId(), result.getDeliveredCount());
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("51job岗位投递接口执行异常", e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "投递接口执行异常: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * 查询任务状态接口
     * GET /api/job51/task/status/{taskId}
     * 
     * @param taskId 任务ID
     * @return 任务状态
     */
    @GetMapping("/status/{taskId}")
    public ResponseEntity<Map<String, Object>> getTaskStatus(@PathVariable String taskId) {
        try {
            Job51TaskService.TaskStatus status = job51TaskService.getTaskStatus(taskId);

            Map<String, Object> response = new HashMap<>();
            if (status != null) {
                response.put("success", true);
                response.put("taskId", taskId);
                response.put("status", status.name());
            } else {
                response.put("success", false);
                response.put("message", "任务不存在");
            }

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("查询任务状态异常，任务ID: {}", taskId, e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "查询状态异常: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * 清理任务数据接口
     * DELETE /api/job51/task/{taskId}
     * 
     * @param taskId 任务ID
     * @return 清理结果
     */
    @DeleteMapping("/{taskId}")
    public ResponseEntity<Map<String, Object>> clearTask(@PathVariable String taskId) {
        try {
            job51TaskService.clearTaskData(taskId);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "任务数据清理成功");

            log.info("任务数据清理成功，任务ID: {}", taskId);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("清理任务数据异常，任务ID: {}", taskId, e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "清理异常: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    // 请求DTO类
    public static class FilterRequest {
        private String collectTaskId;
        private ConfigDTO config;

        public String getCollectTaskId() {
            return collectTaskId;
        }

        public void setCollectTaskId(String collectTaskId) {
            this.collectTaskId = collectTaskId;
        }

        public ConfigDTO getConfig() {
            return config;
        }

        public void setConfig(ConfigDTO config) {
            this.config = config;
        }
    }

    public static class DeliveryRequest {
        private ConfigDTO config;
        private boolean enableActualDelivery = false; // 默认为模拟投递

        public ConfigDTO getConfig() {
            return config;
        }

        public void setConfig(ConfigDTO config) {
            this.config = config;
        }

        public boolean isEnableActualDelivery() {
            return enableActualDelivery;
        }

        public void setEnableActualDelivery(boolean enableActualDelivery) {
            this.enableActualDelivery = enableActualDelivery;
        }
    }
}
