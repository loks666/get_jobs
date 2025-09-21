package getjobs.modules.dict.infrastructure.provider.dto.boss;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * 城市分组数据
 */
public record CityGroupData(
        @JsonProperty("cityGroup") List<CityGroup> cityGroup,
        @JsonProperty("hotCityList") List<City> hotCityList,
        @JsonProperty("locationCity") City locationCity) {
}
