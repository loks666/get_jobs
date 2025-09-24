package getjobs.modules.zhilian.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * 智联招聘API响应数据结构
 * 对应接口：https://fe-api.zhaopin.com/c/i/search/positions
 * 
 * @author getjobs
 * @since v2.1.1
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ZhiLianApiResponse {

    @JsonProperty("code")
    private Integer code;

    @JsonProperty("data")
    private ZhiLianData data;

    /**
     * 主要数据包装类
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ZhiLianData {
        @JsonProperty("count")
        private Integer count;

        @JsonProperty("list")
        private List<ZhiLianJobItem> list;

        @JsonProperty("isEndPage")
        private Integer isEndPage;
    }

    /**
     * 职位信息项
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ZhiLianJobItem {
        @JsonProperty("jobId")
        private Long jobId;

        @JsonProperty("name")
        private String name;

        @JsonProperty("salary60")
        private String salary60;

        @JsonProperty("salaryReal")
        private String salaryReal;

        @JsonProperty("workCity")
        private String workCity;

        @JsonProperty("cityDistrict")
        private String cityDistrict;

        @JsonProperty("streetName")
        private String streetName;

        @JsonProperty("education")
        private String education;

        @JsonProperty("workingExp")
        private String workingExp;

        @JsonProperty("workType")
        private String workType;

        @JsonProperty("jobSummary")
        private String jobSummary;

        @JsonProperty("publishTime")
        private String publishTime;

        @JsonProperty("firstPublishTime")
        private String firstPublishTime;

        @JsonProperty("positionUrl")
        private String positionUrl;

        @JsonProperty("number")
        private String number;

        @JsonProperty("recruitNumber")
        private Integer recruitNumber;

        // 公司信息
        @JsonProperty("companyId")
        private Long companyId;

        @JsonProperty("companyName")
        private String companyName;

        @JsonProperty("companyLogo")
        private String companyLogo;

        @JsonProperty("companySize")
        private String companySize;

        @JsonProperty("companyUrl")
        private String companyUrl;

        @JsonProperty("industryName")
        private String industryName;

        @JsonProperty("property")
        private String property;

        @JsonProperty("propertyName")
        private String propertyName;

        // 技能标签
        @JsonProperty("jobSkillTags")
        private List<SkillTag> jobSkillTags;

        @JsonProperty("skillLabel")
        private List<SkillLabel> skillLabel;

        @JsonProperty("showSkillTags")
        private List<ShowSkillTag> showSkillTags;

        // 福利标签
        @JsonProperty("welfareTagList")
        private List<String> welfareTagList;

        @JsonProperty("jobKnowledgeWelfareFeatures")
        private List<String> jobKnowledgeWelfareFeatures;

        // HR信息
        @JsonProperty("staffCard")
        private StaffCard staffCard;

        // 匹配信息
        @JsonProperty("matchInfo")
        private MatchInfo matchInfo;

        @JsonProperty("jobHitReason")
        private String jobHitReason;

        // 其他信息
        @JsonProperty("subJobTypeLevelName")
        private String subJobTypeLevelName;

        @JsonProperty("financingStage")
        private FinancingStage financingStage;
    }

    /**
     * 技能标签
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SkillTag {
        @JsonProperty("id")
        private Long id;

        @JsonProperty("name")
        private String name;

        @JsonProperty("standard")
        private Boolean standard;
    }

    /**
     * 技能标签（简化版）
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SkillLabel {
        @JsonProperty("state")
        private Integer state;

        @JsonProperty("value")
        private String value;
    }

    /**
     * 显示技能标签
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ShowSkillTag {
        @JsonProperty("tag")
        private String tag;

        @JsonProperty("highlightBackGroundColor")
        private String highlightBackGroundColor;

        @JsonProperty("highlightWordColor")
        private String highlightWordColor;
    }

    /**
     * HR信息
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class StaffCard {
        @JsonProperty("id")
        private Long id;

        @JsonProperty("staffName")
        private String staffName;

        @JsonProperty("hrJob")
        private String hrJob;

        @JsonProperty("avatar")
        private String avatar;

        @JsonProperty("hrOnlineState")
        private String hrOnlineState;

        @JsonProperty("hrStateInfo")
        private String hrStateInfo;

        @JsonProperty("lastOnlineTime")
        private Long lastOnlineTime;
    }

    /**
     * 匹配信息
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MatchInfo {
        @JsonProperty("matched")
        private Integer matched;

        @JsonProperty("icon")
        private String icon;

        @JsonProperty("tagState")
        private Integer tagState;
    }

    /**
     * 融资阶段
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class FinancingStage {
        @JsonProperty("code")
        private Integer code;

        @JsonProperty("name")
        private String name;
    }
}
