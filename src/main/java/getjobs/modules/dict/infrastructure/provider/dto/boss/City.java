package getjobs.modules.dict.infrastructure.provider.dto.boss;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 城市信息
 */
public record City(
        @JsonProperty("code") int code,
        @JsonProperty("name") String name,
        @JsonProperty("tip") String tip,
        @JsonProperty("subLevelModelList") Object subLevelModelList,
        @JsonProperty("firstChar") String firstChar,
        @JsonProperty("pinyin") String pinyin,
        @JsonProperty("rank") int rank,
        @JsonProperty("mark") int mark,
        @JsonProperty("positionType") int positionType,
        @JsonProperty("cityType") int cityType,
        @JsonProperty("capital") int capital,
        @JsonProperty("color") String color,
        @JsonProperty("recruitmentType") String recruitmentType,
        @JsonProperty("cityCode") String cityCode,
        @JsonProperty("regionCode") int regionCode,
        @JsonProperty("centerGeo") String centerGeo,
        @JsonProperty("value") Object value) {
}
