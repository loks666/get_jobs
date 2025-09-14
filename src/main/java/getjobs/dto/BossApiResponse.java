package getjobs.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * BOSS直聘API响应数据结构
 * 
 * @author getjobs
 * @since v2.0.1
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class BossApiResponse implements Serializable {

    /**
     * 响应码
     */
    private Integer code;

    /**
     * 响应消息
     */
    private String message;

    /**
     * 职位数据
     */
    private ZpData zpData;

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Data
    public static class ZpData implements Serializable {

        /**
         * 是否有更多数据
         */
        private Boolean hasMore;

        /**
         * 职位列表
         */
        private List<Map<String, Object>> jobList;

        /**
         * 类型
         */
        private Integer type;
    }
}
