package getjobs.modules.dict.infrastructure.provider.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 智联招聘 API 响应基础结构
 */
public record ZhipinResponse<T>(
        @JsonProperty("code") int code,
        @JsonProperty("message") String message,
        @JsonProperty("zpData") T zpData) {
}
