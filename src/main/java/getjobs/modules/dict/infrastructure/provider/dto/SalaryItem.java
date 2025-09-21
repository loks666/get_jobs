package getjobs.modules.dict.infrastructure.provider.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 薪资项
 */
public record SalaryItem(
                @JsonProperty("code") String code,
                @JsonProperty("name") String name,
                @JsonProperty("lowSalary") Integer lowSalary,
                @JsonProperty("highSalary") Integer highSalary) {
}
