package getjobs.controller;

import getjobs.service.DataBackupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 数据备份和恢复控制器
 * 提供H2内存数据库的数据备份到用户目录和恢复功能
 * 
 * @author getjobs
 * @since v2.0.1
 */
@Slf4j
@RestController
@RequestMapping("/api/backup")
@RequiredArgsConstructor
public class DataBackupController {

    private final DataBackupService dataBackupService;

    /**
     * 备份当前H2内存数据库中的所有数据到用户目录
     * 
     * @return 备份结果
     */
    @PostMapping("/export")
    public ResponseEntity<Map<String, Object>> exportData() {
        Map<String, Object> response = new HashMap<>();

        try {
            String backupPath = dataBackupService.exportData();
            response.put("success", true);
            response.put("message", "数据备份成功");
            response.put("backupPath", backupPath);
            response.put("timestamp", System.currentTimeMillis());

            log.info("数据备份成功，备份路径: {}", backupPath);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("数据备份失败", e);
            response.put("success", false);
            response.put("message", "数据备份失败: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * 从用户目录恢复数据到H2内存数据库
     * 
     * @return 恢复结果
     */
    @PostMapping("/import")
    public ResponseEntity<Map<String, Object>> importData() {
        Map<String, Object> response = new HashMap<>();

        try {
            boolean restored = dataBackupService.importData();
            if (restored) {
                response.put("success", true);
                response.put("message", "数据恢复成功");
                response.put("timestamp", System.currentTimeMillis());
                log.info("数据恢复成功");
            } else {
                response.put("success", false);
                response.put("message", "未找到备份文件或备份文件为空");
                log.warn("数据恢复失败：未找到备份文件");
            }

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("数据恢复失败", e);
            response.put("success", false);
            response.put("message", "数据恢复失败: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * 获取备份文件信息
     * 
     * @return 备份文件信息
     */
    @GetMapping("/info")
    public ResponseEntity<Map<String, Object>> getBackupInfo() {
        Map<String, Object> response = new HashMap<>();

        try {
            Map<String, Object> backupInfo = dataBackupService.getBackupInfo();
            response.put("success", true);
            response.put("data", backupInfo);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("获取备份信息失败", e);
            response.put("success", false);
            response.put("message", "获取备份信息失败: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * 删除备份文件
     * 
     * @return 删除结果
     */
    @DeleteMapping("/clean")
    public ResponseEntity<Map<String, Object>> cleanBackup() {
        Map<String, Object> response = new HashMap<>();

        try {
            boolean cleaned = dataBackupService.cleanBackup();
            response.put("success", cleaned);
            response.put("message", cleaned ? "备份文件删除成功" : "备份文件不存在");

            log.info("备份文件清理结果: {}", cleaned);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("删除备份文件失败", e);
            response.put("success", false);
            response.put("message", "删除备份文件失败: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }
}
