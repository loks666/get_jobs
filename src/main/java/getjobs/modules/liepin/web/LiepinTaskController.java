package getjobs.modules.liepin.web;

import getjobs.common.dto.ConfigDTO;
import getjobs.modules.liepin.service.LiepinTaskService;
import getjobs.modules.liepin.service.LiepinTaskService.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/liepin/task")
public class LiepinTaskController {

    private final LiepinTaskService liepinTaskService;

    public LiepinTaskController(LiepinTaskService liepinTaskService) {
        this.liepinTaskService = liepinTaskService;
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody ConfigDTO config) {
        log.info("接收到猎聘登录请求");
        try {
            LoginResult result = liepinTaskService.login(config);
            Map<String, Object> response = new HashMap<>();
            response.put("success", result.isSuccess());
            response.put("taskId", result.getTaskId());
            response.put("message", result.getMessage());
            response.put("timestamp", result.getTimestamp());
            if (result.isSuccess()) {
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.badRequest().body(response);
            }
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "登录接口执行异常: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @PostMapping("/collect")
    public ResponseEntity<Map<String, Object>> collectJobs(@RequestBody ConfigDTO config) {
        log.info("接收到猎聘岗位采集请求");
        try {
            CollectResult result = liepinTaskService.collectJobs(config);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("taskId", result.getTaskId());
            response.put("message", result.getMessage());
            response.put("jobCount", result.getJobCount());
            response.put("timestamp", result.getTimestamp());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "采集接口执行异常: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @PostMapping("/filter")
    public ResponseEntity<Map<String, Object>> filterJobs(@RequestBody FilterRequest request) {
        log.info("接收到猎聘岗位过滤请求");
        try {
            FilterResult result = liepinTaskService.filterJobs(request.getConfig());
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", result.getMessage());
            response.put("originalCount", result.getOriginalCount());
            response.put("filteredCount", result.getFilteredCount());
            response.put("timestamp", result.getTimestamp());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "过滤接口执行异常: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @PostMapping("/deliver")
    public ResponseEntity<Map<String, Object>> deliverJobs(@RequestBody DeliveryRequest request) {
        log.info("接收到猎聘岗位投递请求，实际投递: {}", request.isEnableActualDelivery());
        try {
            DeliveryResult result = liepinTaskService.deliverJobs(request.getConfig(), request.isEnableActualDelivery());
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("taskId", result.getTaskId());
            response.put("message", result.getMessage());
            response.put("totalCount", result.getTotalCount());
            response.put("deliveredCount", result.getDeliveredCount());
            response.put("actualDelivery", result.isActualDelivery());
            response.put("timestamp", result.getTimestamp());
            if (result.getJobDetails() != null && !result.getJobDetails().isEmpty()) {
                response.put("jobDetails", result.getJobDetails());
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "投递接口执行异常: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @GetMapping("/status/{taskId}")
    public ResponseEntity<Map<String, Object>> getTaskStatus(@PathVariable String taskId) {
        try {
            LiepinTaskService.TaskStatus status = liepinTaskService.getTaskStatus(taskId);
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
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "查询状态异常: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    public static class FilterRequest {
        private ConfigDTO config;
        public ConfigDTO getConfig() { return config; }
        public void setConfig(ConfigDTO config) { this.config = config; }
    }

    public static class DeliveryRequest {
        private ConfigDTO config;
        private boolean enableActualDelivery = false;
        public ConfigDTO getConfig() { return config; }
        public void setConfig(ConfigDTO config) { this.config = config; }
        public boolean isEnableActualDelivery() { return enableActualDelivery; }
        public void setEnableActualDelivery(boolean enableActualDelivery) { this.enableActualDelivery = enableActualDelivery; }
    }
}
