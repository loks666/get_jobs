package getjobs.modules.dict.infrastructure.provider.dto.zhilian;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 智联招聘地铁/城市数据
 */
public record ZhilianSubway(
        @JsonProperty("code") String code,
        @JsonProperty("parentCode") String parentCode,
        @JsonProperty("name") String name,
        @JsonProperty("en_name") String enName,
        @JsonProperty("deleted") Boolean deleted,
        @JsonProperty("longitude") Double longitude,
        @JsonProperty("latitude") Double latitude,
        @JsonProperty("sublist") List<ZhilianSubway> sublist
) {
}
