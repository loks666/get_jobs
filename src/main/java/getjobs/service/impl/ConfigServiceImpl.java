package getjobs.service.impl;

import getjobs.repository.entity.ConfigEntity;
import getjobs.repository.ConfigRepository;
import getjobs.service.ConfigService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ConfigServiceImpl implements ConfigService {

    private final ConfigRepository configRepository;

    public ConfigServiceImpl(ConfigRepository configRepository) {
        this.configRepository = configRepository;
    }

    @Override
    @Transactional
    public ConfigEntity save(ConfigEntity entity) {
        // 仅保留一条配置记录
        List<ConfigEntity> all = configRepository.findAll();
        if (!all.isEmpty()) {
            ConfigEntity existed = all.get(0);
            entity.setId(existed.getId());
        }
        return configRepository.save(entity);
    }

    @Override
    public ConfigEntity load() {
        return configRepository.findAll().stream().findFirst().orElse(null);
    }
}
