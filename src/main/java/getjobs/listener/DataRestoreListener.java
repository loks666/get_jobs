package getjobs.listener;

import getjobs.service.DataBackupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 应用启动数据恢复监听器
 * 在Spring Boot应用启动完成后自动恢复备份数据
 * 
 * @author getjobs
 * @since v2.0.1
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataRestoreListener {

    private final DataBackupService dataBackupService;

    /**
     * 监听应用启动完成事件，自动恢复备份数据
     */
    @EventListener(ApplicationReadyEvent.class)
    @Order(1000) // 确保在其他组件初始化完成后执行
    public void onApplicationReady() {
        try {
            log.info("应用启动完成，开始检查并恢复备份数据...");

            // 检查备份文件是否存在
            var backupInfo = dataBackupService.getBackupInfo();
            boolean backupExists = (Boolean) backupInfo.get("exists");

            if (backupExists) {
                log.info("发现备份文件，开始恢复数据...");
                log.info("备份文件路径: {}", backupInfo.get("filePath"));
                log.info("备份文件大小: {} bytes", backupInfo.get("fileSize"));

                if (backupInfo.containsKey("exportTime")) {
                    log.info("备份时间: {}", backupInfo.get("exportTime"));
                }
                if (backupInfo.containsKey("configCount")) {
                    log.info("备份配置数量: {}", backupInfo.get("configCount"));
                }
                if (backupInfo.containsKey("jobCount")) {
                    log.info("备份职位数量: {}", backupInfo.get("jobCount"));
                }

                // 执行数据恢复
                boolean restored = dataBackupService.importData();

                if (restored) {
                    log.info("数据恢复成功！");
                } else {
                    log.warn("数据恢复失败或备份文件为空");
                }
            } else {
                log.info("未发现备份文件，使用空数据库启动");
            }

        } catch (Exception e) {
            log.error("启动时数据恢复过程中发生错误", e);
            // 不抛出异常，避免影响应用启动
        }
    }
}
