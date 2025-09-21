package getjobs.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Job51字典配置类，用于读取application.yml中的dict-json配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "json51")
public class Job51DictConfig {

    /**
     * dict-json配置内容
     */
    private String dictJson;
}
