package getjobs.service;

import getjobs.repository.entity.ConfigEntity;

public interface ConfigService {

    ConfigEntity save(ConfigEntity entity);

    ConfigEntity load();

    /**
     * 按平台类型加载配置
     */
    ConfigEntity loadByPlatformType(String platformType);
}
