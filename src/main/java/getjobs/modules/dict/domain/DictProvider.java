package getjobs.modules.dict.domain;

import getjobs.enums.RecruitmentPlatformEnum;
import getjobs.modules.dict.api.DictBundle;
import getjobs.modules.dict.api.DictGroup;

import java.util.Optional;

public interface DictProvider {
    /** 该实现对应的平台 */
    RecruitmentPlatformEnum platform();

    /** 返回该平台“全部”字典集合（已映射为统一模型） */
    DictBundle fetchAll();

    /** 可选：只取指定分组，平台可直连远端做最小化拉取 */
    default Optional<DictGroup> fetchByKey(String key) {
        return fetchAll().group(key);
    }
}