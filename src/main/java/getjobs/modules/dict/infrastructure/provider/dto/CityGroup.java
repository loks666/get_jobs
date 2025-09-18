package getjobs.modules.dict.infrastructure.provider.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * 城市分组
 */
public record CityGroup(
        @JsonProperty("firstChar") String firstChar,
        @JsonProperty("cityList") List<City> cityList) {
}
