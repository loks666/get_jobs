package com.getjobs.application.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.getjobs.application.entity.ConfigEntity;
import com.getjobs.application.mapper.ConfigMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 配置服务类
 */
@Service
public class ConfigService {

    private static final Logger log = LoggerFactory.getLogger(ConfigService.class);

    @Autowired
    private ConfigMapper configMapper;

    /**
     * 获取所有配置（以Map形式返回）
     * @return 配置Map，key为config_key，value为config_value
     */
    public Map<String, String> getAllConfigsAsMap() {
        List<ConfigEntity> configs = configMapper.selectList(null);
        Map<String, String> configMap = new HashMap<>();

        for (ConfigEntity config : configs) {
            configMap.put(config.getConfigKey(), config.getConfigValue());
        }

        return configMap;
    }

    /**
     * 获取所有配置
     * @return 配置列表
     */
    public List<ConfigEntity> getAllConfigs() {
        return configMapper.selectList(null);
    }

    /**
     * 根据配置键获取配置
     * @param configKey 配置键
     * @return 配置实体
     */
    public ConfigEntity getConfigByKey(String configKey) {
        LambdaQueryWrapper<ConfigEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ConfigEntity::getConfigKey, configKey);
        return configMapper.selectOne(queryWrapper);
    }

    /**
     * 根据分类获取配置列表
     * @param category 分类
     * @return 配置列表
     */
    public List<ConfigEntity> getConfigsByCategory(String category) {
        LambdaQueryWrapper<ConfigEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ConfigEntity::getCategory, category);
        return configMapper.selectList(queryWrapper);
    }

    /**
     * 批量更新配置
     * @param configMap 配置Map，key为config_key，value为config_value
     * @return 更新的配置数量
     */
    @Transactional
    public int batchUpdateConfigs(Map<String, String> configMap) {
        int updateCount = 0;

        for (Map.Entry<String, String> entry : configMap.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();

            ConfigEntity config = getConfigByKey(key);

            if (config != null) {
                config.setConfigValue(value);
                config.setUpdatedAt(LocalDateTime.now());
                configMapper.updateById(config);
                updateCount++;
                log.info("更新配置: {} = {}", key, value);
            } else {
                log.warn("配置键不存在: {}", key);
            }
        }

        return updateCount;
    }

    /**
     * 更新单个配置
     * @param configKey 配置键
     * @param configValue 配置值
     * @return 是否更新成功
     */
    @Transactional
    public boolean updateConfig(String configKey, String configValue) {
        ConfigEntity config = getConfigByKey(configKey);

        if (config != null) {
            config.setConfigValue(configValue);
            config.setUpdatedAt(LocalDateTime.now());
            int result = configMapper.updateById(config);

            if (result > 0) {
                log.info("更新配置成功: {} = {}", configKey, configValue);
                return true;
            }
        } else {
            log.warn("配置键不存在: {}", configKey);
        }

        return false;
    }

    /**
     * 创建新配置
     * @param config 配置实体
     * @return 是否创建成功
     */
    @Transactional
    public boolean createConfig(ConfigEntity config) {
        config.setCreatedAt(LocalDateTime.now());
        config.setUpdatedAt(LocalDateTime.now());

        int result = configMapper.insert(config);

        if (result > 0) {
            log.info("创建配置成功: {} = {}", config.getConfigKey(), config.getConfigValue());
            return true;
        }

        return false;
    }
}
