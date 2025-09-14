package getjobs.service;

import getjobs.entity.ConfigEntity;

public interface ConfigService {

    ConfigEntity save(ConfigEntity entity);

    ConfigEntity load();
}
