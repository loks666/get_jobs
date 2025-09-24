package getjobs.modules.dict.infrastructure.provider.dto.zhilian;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 智联招聘字典项
 */
public record ZhilianDictItem(
        @JsonProperty("code") String code,
        @JsonProperty("parentCode") String parentCode,
        @JsonProperty("name") String name,
        @JsonProperty("en_name") String enName,
        @JsonProperty("deleted") Boolean deleted,
        @JsonProperty("sublist") List<ZhilianDictItem> sublist,
        @JsonProperty("longitude") Double longitude,
        @JsonProperty("latitude") Double latitude
) {
}
