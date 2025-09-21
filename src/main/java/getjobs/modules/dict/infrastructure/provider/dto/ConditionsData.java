package getjobs.modules.dict.infrastructure.provider.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * 条件数据
 */
public record ConditionsData(
                /** 结算方式列表 */
                @JsonProperty("payTypeList") List<ConditionItem> payTypeList,
                /** 工作经验列表 */
                @JsonProperty("experienceList") List<ConditionItem> experienceList,
                /** 薪资区间列表 */
                @JsonProperty("salaryList") List<SalaryItem> salaryList,
                /** 融资阶段列表 */
                @JsonProperty("stageList") List<ConditionItem> stageList,
                /** 公司性质列表 */
                @JsonProperty("companyNatureList") List<ConditionItem> companyNatureList,
                /** 公司规模列表 */
                @JsonProperty("scaleList") List<ConditionItem> scaleList,
                /** 兼职类型列表 */
                @JsonProperty("partTimeList") List<ConditionItem> partTimeList,
                /** 学历要求列表 */
                @JsonProperty("degreeList") List<ConditionItem> degreeList,
                /** 工作类型列表 */
                @JsonProperty("jobTypeList") List<ConditionItem> jobTypeList) {
}
