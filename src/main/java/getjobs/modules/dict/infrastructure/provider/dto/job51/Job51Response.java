package getjobs.modules.dict.infrastructure.provider.dto.job51;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * 51job API响应
 */
public record Job51Response(
        @JsonProperty("items") List<Job51CityGroup> items) {
}
