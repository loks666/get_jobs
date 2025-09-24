package getjobs.modules.job51.web;

import getjobs.modules.job51.service.playwright.Job51ApiMonitorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 51Job监控服务控制器
 * 提供51Job API监控服务的管理接口
 * 
 * @author getjobs
 * @since v2.1.1
 */
@Slf4j
@RestController
@RequestMapping("/api/job51/monitor")
@RequiredArgsConstructor
public class Job51MonitorController {

    private final Job51ApiMonitorService job51ApiMonitorService;

    /**
     * 获取监控服务状态
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getMonitorStatus() {
        try {
            Map<String, Object> stats = job51ApiMonitorService.getMonitoringStats();
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            log.error("获取51Job监控状态失败", e);
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "获取监控状态失败: " + e.getMessage()));
        }
    }

    /**
     * 启动监控服务
     */
    @PostMapping("/start")
    public ResponseEntity<Map<String, Object>> startMonitoring() {
        try {
            job51ApiMonitorService.startMonitoring();
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "51Job监控服务已启动"
            ));
        } catch (Exception e) {
            log.error("启动51Job监控服务失败", e);
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "启动监控服务失败: " + e.getMessage()));
        }
    }

    /**
     * 刷新session
     */
    @PostMapping("/refresh-session")
    public ResponseEntity<Map<String, Object>> refreshSession() {
        try {
            boolean success = job51ApiMonitorService.refreshSession();
            if (success) {
                return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "51Job session刷新成功"
                ));
            } else {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "51Job session刷新失败"));
            }
        } catch (Exception e) {
            log.error("刷新51Job session失败", e);
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "刷新session失败: " + e.getMessage()));
        }
    }


}
