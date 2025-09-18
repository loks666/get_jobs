package getjobs.modules.dict.infrastructure.provider.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 条件项
 */
public record ConditionItem(
        @JsonProperty("code") int code,
        @JsonProperty("name") String name) {
}
