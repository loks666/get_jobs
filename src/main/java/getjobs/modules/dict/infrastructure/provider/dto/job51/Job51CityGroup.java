package getjobs.modules.dict.infrastructure.provider.dto.job51;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * 51job城市分组
 */
public record Job51CityGroup(
        @JsonProperty("title") String title,
        @JsonProperty("type") String type,
        @JsonProperty("items") List<Job51City> items,
        @JsonProperty("eItems") List<Job51City> eItems) {
}
