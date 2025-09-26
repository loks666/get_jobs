package getjobs.modules.liepin.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * 猎聘API响应数据结构
 *
 * @author getjobs
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class LiePinApiResponse {

    @JsonProperty("code")
    private Integer code;

    @JsonProperty("data")
    private LiePinData data;

    /**
     * 主要数据包装类
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class LiePinData {
        @JsonProperty("count")
        private Integer count;

        @JsonProperty("list")
        private List<LiePinJobItem> list;
    }

    /**
     * 职位信息项
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class LiePinJobItem {
        @JsonProperty("jobId")
        private String jobId;

        @JsonProperty("jobTitle")
        private String jobTitle;

        @JsonProperty("salary")
        private String salary;

        @JsonProperty("city")
        private String city;

        @JsonProperty("education")
        private String education;

        @JsonProperty("experience")
        private String experience;

        @JsonProperty("publishTime")
        private String publishTime;

        @JsonProperty("positionUrl")
        private String positionUrl;

        // 公司信息
        @JsonProperty("companyId")
        private String companyId;

        @JsonProperty("companyName")
        private String companyName;

        @JsonProperty("companyLogo")
        private String companyLogo;

        @JsonProperty("companyScale")
        private String companyScale;

        @JsonProperty("industry")
        private String industry;

        // HR信息
        @JsonProperty("hrName")
        private String hrName;

        @JsonProperty("hrTitle")
        private String hrTitle;

    }
}
