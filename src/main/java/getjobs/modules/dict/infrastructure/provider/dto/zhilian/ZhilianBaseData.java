package getjobs.modules.dict.infrastructure.provider.dto.zhilian;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 智联招聘基础数据响应
 */
public record ZhilianBaseData(
        @JsonProperty("companyType") List<ZhilianDictItem> companyType,
        @JsonProperty("salaryType") List<ZhilianDictItem> salaryType,
        @JsonProperty("subway") List<ZhilianSubway> subway,
        @JsonProperty("cityList") List<ZhilianDictItem> cityList,
        @JsonProperty("position") List<ZhilianDictItem> position
) {
}
