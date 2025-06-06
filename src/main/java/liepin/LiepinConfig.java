package liepin;

import lombok.Data;
import lombok.SneakyThrows;
import utils.JobUtils;

import java.util.List;

/**
 * @author loks666
 * 项目链接: <a href="https://github.com/loks666/get_jobs">https://github.com/loks666/get_jobs</a>
 */
@Data
public class LiepinConfig {
    /**
     * 搜索关键词列表
     */
    private List<String> keywords;

    /**
     * 城市编码
     */
    private String cityCode;

    /**
     * 薪资范围
     */
    private String salary;

    /**
     * 排除公司名，逗号分割，模糊匹配
     */
    private String excludeCompany;

    /**
     * 包含工作名
     */
    private String containsJobName;

    /**
     * 排除工作名
     */
    private String excludeJobName;

    /**
     * 发布时间
     */
    private String pubTime;


    @SneakyThrows
    public static LiepinConfig init() {
        LiepinConfig config = JobUtils.getConfig(LiepinConfig.class);
        // 转换城市编码
        config.setCityCode(LiepinEnum.CityCode.forValue(config.getCityCode()).getCode());
        return config;
    }

}
