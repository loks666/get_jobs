package getjobs.modules.dict.infrastructure.provider.dto.job51;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * 映射application.yml中json51.dict-json的响应结构
 */
@Data
public class DictJsonResponse {

    @JsonProperty("status")
    private String status;

    @JsonProperty("message")
    private String message;

    @JsonProperty("resultbody")
    private ResultBody resultBody;

    @Data
    public static class ResultBody {

        @JsonProperty("d_industry")
        private List<DictItem> industry;

        @JsonProperty("d_area")
        private List<DictItem> area;

        @JsonProperty("d_search_cottype")
        private List<DictItem> companyType;

        @JsonProperty("d_search_workyear")
        private List<DictItem> workYear;

        @JsonProperty("d_search_providesalary")
        private List<DictItem> salary;

        @JsonProperty("d_search_companysize")
        private List<DictItem> companySize;

        @JsonProperty("d_search_degreefrom")
        private List<DictItem> degree;

        @JsonProperty("d_search_jobterm")
        private List<DictItem> jobTerm;

        @JsonProperty("d_search_issuedate")
        private List<DictItem> issueDate;

        @JsonProperty("d_search_postchannel")
        private List<DictItem> postChannel;
    }

    @Data
    public static class DictItem {

        @JsonProperty("id")
        private String id;

        @JsonProperty("value")
        private String value;

        @JsonProperty("sub")
        private List<DictItem> sub;

        @JsonProperty("trace")
        private List<String> trace;
    }
}
