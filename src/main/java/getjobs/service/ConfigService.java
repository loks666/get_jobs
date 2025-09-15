package getjobs.service;

import getjobs.repository.entity.ConfigEntity;

public interface ConfigService {

    ConfigEntity save(ConfigEntity entity);

    ConfigEntity load();
}
