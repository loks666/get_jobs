package getjobs.modules.dict.api;

import getjobs.common.enums.RecruitmentPlatformEnum;

import java.util.List;
import java.util.Optional;

/**
 * 平台字典总包
 *
 * @param platform
 * @param groups
 */
public record DictBundle(RecruitmentPlatformEnum platform, List<DictGroup> groups) {

    // 便捷取法：按key取组
    public Optional<DictGroup> group(String key) {
        return groups.stream().filter(g -> g.key().equalsIgnoreCase(key)).findFirst();
    }
}