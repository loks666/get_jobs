package ai;

import lombok.Data;
import utils.JobUtils;

@Data
public class AiConfig {

    /**
     * 介绍语
     */
    private String introduce;

    /**
     * 提示词
     */
    private String prompt;

    public AiConfig() {
    }

    public AiConfig(String introduce, String prompt) {
        this.introduce = introduce;
        this.prompt = prompt;
    }

    public static AiConfig init() {
        AiConfig config = JobUtils.getConfig(AiConfig.class);
        return new AiConfig(config.introduce, config.prompt);
    }

}
