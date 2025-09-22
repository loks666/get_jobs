package getjobs.modules.dict.service;

import getjobs.common.enums.RecruitmentPlatformEnum;
import getjobs.modules.dict.api.DictBundle;
import getjobs.modules.dict.api.DictGroup;
import getjobs.modules.dict.service.registry.DictProviderRegistry;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class DictFacade {

    private final DictProviderRegistry registry;

    public DictFacade(DictProviderRegistry registry) {
        this.registry = registry;
    }

    public DictBundle fetchAll(RecruitmentPlatformEnum platform) {
        return registry.get(platform).fetchAll();
    }

    public Optional<DictGroup> fetchByKey(RecruitmentPlatformEnum platform, String key) {
        return registry.get(platform).fetchByKey(key);
    }
}