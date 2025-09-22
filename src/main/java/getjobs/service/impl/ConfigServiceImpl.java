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
        // 若提供了平台类型，则按平台类型做 upsert；否则仍回退到单条记录策略
        if (entity.getPlatformType() != null && !entity.getPlatformType().trim().isEmpty()) {
            return configRepository.findFirstByPlatformTypeOrderByIdAsc(entity.getPlatformType().trim())
                    .map(existed -> {
                        entity.setId(existed.getId());
                        return configRepository.save(entity);
                    })
                    .orElseGet(() -> configRepository.save(entity));
        } else {
            List<ConfigEntity> all = configRepository.findAll();
            if (!all.isEmpty()) {
                ConfigEntity existed = all.get(0);
                entity.setId(existed.getId());
            }
            return configRepository.save(entity);
        }
    }

    @Override
    public ConfigEntity load() {
        return configRepository.findAll().stream().findFirst().orElse(null);
    }

    @Override
    public ConfigEntity loadByPlatformType(String platformType) {
        if (platformType == null || platformType.trim().isEmpty()) {
            return load();
        }
        return configRepository.findFirstByPlatformTypeOrderByIdAsc(platformType.trim()).orElse(null);
    }
}
