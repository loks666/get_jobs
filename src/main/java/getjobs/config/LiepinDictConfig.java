package getjobs.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Liepin字典配置类，用于读取application.yml中的dict-json配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "liepin")
public class LiepinDictConfig {

    /**
     * dict-json配置内容
     */
    private String dictJson;
}
