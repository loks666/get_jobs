package getjobs.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import getjobs.repository.entity.ConfigEntity;
import getjobs.repository.entity.JobEntity;
import getjobs.repository.ConfigRepository;
import getjobs.repository.JobRepository;
import getjobs.service.DataBackupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据备份服务实现类
 * 
 * @author getjobs
 * @since v2.0.1
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataBackupServiceImpl implements DataBackupService {

    private final ConfigRepository configRepository;
    private final JobRepository jobRepository;
    private final ObjectMapper objectMapper;

    private static final String BACKUP_DIR_NAME = "getjobs";
    private static final String BACKUP_FILE_NAME = "data_backup.json";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    /**
     * 获取用户目录下的备份目录路径
     */
    private Path getBackupDirPath() {
        String userHome = System.getProperty("user.home");
        return Paths.get(userHome, BACKUP_DIR_NAME);
    }

    /**
     * 获取备份文件路径
     */
    private Path getBackupFilePath() {
        return getBackupDirPath().resolve(BACKUP_FILE_NAME);
    }

    @Override
    public String exportData() throws Exception {
        // 确保备份目录存在
        Path backupDir = getBackupDirPath();
        if (!Files.exists(backupDir)) {
            Files.createDirectories(backupDir);
            log.info("创建备份目录: {}", backupDir);
        }

        // 收集所有数据
        Map<String, Object> backupData = new HashMap<>();

        // 导出配置数据
        List<ConfigEntity> configs = configRepository.findAll();
        backupData.put("configs", configs);

        // 导出职位数据
        List<JobEntity> jobs = jobRepository.findAll();
        backupData.put("jobs", jobs);

        // 添加元数据
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("exportTime", LocalDateTime.now().format(DATE_FORMATTER));
        metadata.put("configCount", configs.size());
        metadata.put("jobCount", jobs.size());
        metadata.put("version", "1.0");
        backupData.put("metadata", metadata);

        // 写入备份文件（覆盖写入）
        Path backupFilePath = getBackupFilePath();
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(backupFilePath.toFile(), backupData);

        log.info("数据备份完成 - 配置: {} 条, 职位: {} 条, 备份文件: {}",
                configs.size(), jobs.size(), backupFilePath);

        return backupFilePath.toString();
    }

    @Override
    @Transactional
    public boolean importData() throws Exception {
        Path backupFilePath = getBackupFilePath();

        // 检查备份文件是否存在
        if (!Files.exists(backupFilePath)) {
            log.warn("备份文件不存在: {}", backupFilePath);
            return false;
        }

        // 读取备份文件
        Map<String, Object> backupData = objectMapper.readValue(backupFilePath.toFile(),
                new TypeReference<Map<String, Object>>() {
                });

        // 检查备份文件是否为空
        if (backupData.isEmpty()) {
            log.warn("备份文件为空: {}", backupFilePath);
            return false;
        }

        // 清空现有数据（可选，根据需求决定）
        // configRepository.deleteAll();
        // jobRepository.deleteAll();

        // 恢复配置数据
        if (backupData.containsKey("configs")) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> configMaps = (List<Map<String, Object>>) backupData.get("configs");
            for (Map<String, Object> configMap : configMaps) {
                ConfigEntity config = objectMapper.convertValue(configMap, ConfigEntity.class);
                configRepository.save(config);
            }
            log.info("恢复配置数据: {} 条", configMaps.size());
        }

        // 恢复职位数据
        if (backupData.containsKey("jobs")) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> jobMaps = (List<Map<String, Object>>) backupData.get("jobs");
            for (Map<String, Object> jobMap : jobMaps) {
                JobEntity job = objectMapper.convertValue(jobMap, JobEntity.class);
                jobRepository.save(job);
            }
            log.info("恢复职位数据: {} 条", jobMaps.size());
        }

        // 输出备份元数据信息
        if (backupData.containsKey("metadata")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> metadata = (Map<String, Object>) backupData.get("metadata");
            log.info("数据恢复完成 - 备份时间: {}, 版本: {}",
                    metadata.get("exportTime"), metadata.get("version"));
        }

        return true;
    }

    @Override
    public Map<String, Object> getBackupInfo() throws Exception {
        Map<String, Object> info = new HashMap<>();
        Path backupFilePath = getBackupFilePath();

        if (Files.exists(backupFilePath)) {
            File backupFile = backupFilePath.toFile();
            info.put("exists", true);
            info.put("filePath", backupFilePath.toString());
            info.put("fileSize", backupFile.length());
            info.put("lastModified", backupFile.lastModified());

            // 尝试读取备份文件中的元数据
            try {
                Map<String, Object> backupData = objectMapper.readValue(backupFile,
                        new TypeReference<Map<String, Object>>() {
                        });
                if (backupData.containsKey("metadata")) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> metadata = (Map<String, Object>) backupData.get("metadata");
                    info.put("exportTime", metadata.get("exportTime"));
                    info.put("configCount", metadata.get("configCount"));
                    info.put("jobCount", metadata.get("jobCount"));
                    info.put("version", metadata.get("version"));
                }
            } catch (Exception e) {
                log.warn("读取备份文件元数据失败", e);
            }
        } else {
            info.put("exists", false);
            info.put("filePath", backupFilePath.toString());
        }

        return info;
    }

    @Override
    public boolean cleanBackup() throws Exception {
        Path backupFilePath = getBackupFilePath();

        if (Files.exists(backupFilePath)) {
            Files.delete(backupFilePath);
            log.info("备份文件已删除: {}", backupFilePath);
            return true;
        } else {
            log.info("备份文件不存在，无需删除: {}", backupFilePath);
            return false;
        }
    }
}
