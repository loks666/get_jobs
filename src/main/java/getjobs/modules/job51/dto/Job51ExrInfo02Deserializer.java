package getjobs.modules.job51.dto;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;

/**
 * Job51ExrInfo02自定义反序列化器
 * 用于处理exrInfo02字段可能是JSON字符串或对象的情况
 * 
 * @author getjobs
 * @since v2.1.1
 */
@Slf4j
public class Job51ExrInfo02Deserializer extends JsonDeserializer<Job51ApiResponse.Job51ExrInfo02> {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Job51ApiResponse.Job51ExrInfo02 deserialize(JsonParser p, DeserializationContext ctxt) 
            throws IOException, JsonProcessingException {
        
        try {
            // 检查当前token类型
            switch (p.getCurrentToken()) {
                case START_OBJECT:
                    // 如果是对象，直接解析
                    return objectMapper.readValue(p, Job51ApiResponse.Job51ExrInfo02.class);
                    
                case VALUE_STRING:
                    // 如果是字符串，先获取字符串值，然后解析为对象
                    String jsonString = p.getValueAsString();
                    if (jsonString != null && !jsonString.trim().isEmpty()) {
                        try {
                            return objectMapper.readValue(jsonString, Job51ApiResponse.Job51ExrInfo02.class);
                        } catch (Exception e) {
                            log.warn("无法解析exrInfo02 JSON字符串: {}", jsonString, e);
                            return null;
                        }
                    }
                    break;
                    
                case VALUE_NULL:
                    return null;
                    
                default:
                    log.warn("exrInfo02字段的token类型不支持: {}", p.getCurrentToken());
                    break;
            }
            
            return null;
        } catch (Exception e) {
            log.error("反序列化Job51ExrInfo02时发生错误", e);
            return null;
        }
    }
}
