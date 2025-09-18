package getjobs.modules.dict.infrastructure.provider.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 薪资项
 */
public record SalaryItem(
        @JsonProperty("code") int code,
        @JsonProperty("name") String name,
        @JsonProperty("lowSalary") int lowSalary,
        @JsonProperty("highSalary") int highSalary) {
}
