package getjobs.modules.dict.infrastructure.provider.dto.zhilian;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 智联招聘API响应数据包装类
 */
public record ZhilianResponse<T>(
        @JsonProperty("code") int code,
        @JsonProperty("message") String message,
        @JsonProperty("data") T data
) {
}
