package getjobs.modules.dict.infrastructure.provider.dto.liepin;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class LiepinDictResponse {
    private int flag;
    private LiepinDictData data;

    @Data
    public static class LiepinDictData {
        private List<LiepinDictItem> workExperiences;
        private List<LiepinDictItem> yearSalaries;
        private List<LiepinDictItem> jobKinds;
        private List<LiepinDictItem> salaries;
        private List<LiepinDictItem> compScales;
        private List<LiepinIndustryItem> industries;
        private List<LiepinDictItem> famousComps;
        private List<LiepinDictItem> hotCities;
        private List<LiepinDictItem> pubTimes;
        private List<LiepinDictItem> educations;
        private List<LiepinDictItem> financeStages;
        @JsonProperty("compNatures")
        private List<LiepinDictItem> compNatures;
    }

    @Data
    public static class LiepinDictItem {
        private String code;
        private String name;
    }

    @Data
    public static class LiepinIndustryItem {
        private String code;
        private String name;
        private List<LiepinDictItem> children;
    }
}
