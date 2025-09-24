package getjobs.modules.zhilian.web;

import getjobs.modules.zhilian.service.playwright.ZhiLianApiMonitorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 智联招聘监控控制器
 * 提供智联招聘API监控相关的接口
 * 
 * @author getjobs
 * @since v2.1.1
 */
@Slf4j
@RestController
@RequestMapping("/api/zhilian/monitor")
@RequiredArgsConstructor
public class ZhiLianMonitorController {

    private final ZhiLianApiMonitorService monitorService;

    /**
     * 启动智联招聘API监控
     */
    @PostMapping("/start")
    public ResponseEntity<String> startMonitoring() {
        try {
            monitorService.startMonitoring();
            return ResponseEntity.ok("智联招聘API监控服务已启动");
        } catch (Exception e) {
            log.error("启动智联招聘API监控服务失败: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                .body("启动智联招聘API监控服务失败: " + e.getMessage());
        }
    }

    /**
     * 检查监控服务状态
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getMonitoringStatus() {
        try {
            Map<String, Object> stats = monitorService.getMonitoringStats();
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            log.error("获取智联招聘监控状态失败: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "获取监控状态失败: " + e.getMessage()));
        }
    }

    /**
     * 刷新智联招聘session
     */
    @PostMapping("/refresh-session")
    public ResponseEntity<String> refreshSession() {
        try {
            boolean success = monitorService.refreshSession();
            if (success) {
                return ResponseEntity.ok("智联招聘session刷新成功");
            } else {
                return ResponseEntity.internalServerError()
                    .body("智联招聘session刷新失败");
            }
        } catch (Exception e) {
            log.error("刷新智联招聘session失败: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                .body("刷新智联招聘session失败: " + e.getMessage());
        }
    }

    /**
     * 手动解析智联招聘数据
     * 用于测试数据解析功能
     */
    @PostMapping("/parse-data")
    public ResponseEntity<String> parseData(@RequestBody String jsonData) {
        try {
            monitorService.parseAndSaveZhiLianData(jsonData, "手动测试");
            return ResponseEntity.ok("智联招聘数据解析成功");
        } catch (Exception e) {
            log.error("解析智联招聘数据失败: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                .body("解析智联招聘数据失败: " + e.getMessage());
        }
    }
}
