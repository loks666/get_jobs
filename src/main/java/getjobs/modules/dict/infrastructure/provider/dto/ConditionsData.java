package getjobs.modules.dict.infrastructure.provider.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * 条件数据
 */
public record ConditionsData(
        @JsonProperty("payTypeList") List<ConditionItem> payTypeList,
        @JsonProperty("experienceList") List<ConditionItem> experienceList,
        @JsonProperty("salaryList") List<SalaryItem> salaryList,
        @JsonProperty("stageList") List<ConditionItem> stageList,
        @JsonProperty("scaleList") List<ConditionItem> scaleList,
        @JsonProperty("partTimeList") List<ConditionItem> partTimeList,
        @JsonProperty("degreeList") List<ConditionItem> degreeList,
        @JsonProperty("jobTypeList") List<ConditionItem> jobTypeList) {
}
