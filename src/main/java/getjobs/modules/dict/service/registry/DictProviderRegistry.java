package getjobs.modules.dict.service.registry;

import getjobs.common.enums.RecruitmentPlatformEnum;
import getjobs.modules.dict.domain.DictProvider;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class DictProviderRegistry {

    private final Map<RecruitmentPlatformEnum, DictProvider> providerMap;

    public DictProviderRegistry(List<DictProvider> providers) {
        this.providerMap = providers.stream()
                .collect(Collectors.toUnmodifiableMap(DictProvider::platform, p -> p));
    }

    public DictProvider get(RecruitmentPlatformEnum platform) {
        var p = providerMap.get(platform);
        if (p == null) throw new IllegalArgumentException("No DictProvider for platform: " + platform);
        return p;
    }
}