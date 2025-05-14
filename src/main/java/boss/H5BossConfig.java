package boss;

import lombok.Data;
import lombok.SneakyThrows;
import utils.JobUtils;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author loks666
 * 项目链接: <a href="https://github.com/loks666/get_jobs">https://github.com/loks666/get_jobs</a>
 */
@Data
public class H5BossConfig {
    /**
     * 用于打招呼的语句
     */
    private String sayHi;


    /**
     * 搜索关键词列表
     */
    private List<String> keywords;

    /**
     * 城市编码
     */
    private List<String> cityCode;

    /**
     * 自定义城市编码映射
     */
    private Map<String, String> customCityCode;


    /**
     * 工作经验要求
     */
    private List<String> experience;


    /**
     * 薪资范围
     */
    private String salary;

    /**
     * 学历要求列表
     */
    private List<String> degree;

    List<String> scale;


    @SneakyThrows
    public static H5BossConfig init() {
        H5BossConfig config = JobUtils.getConfig(H5BossConfig.class);

        // 转换薪资范围
        config.setSalary(H5BossEnum.Salary.forValue(config.getSalary()).getCode());

        // 处理城市编码
        List<String> convertedCityCodes = config.getCityCode().stream()
                .map(city -> {
                    // 优先从自定义映射中获取
                    if (config.getCustomCityCode() != null && config.getCustomCityCode().containsKey(city)) {
                        return config.getCustomCityCode().get(city);
                    }
                    // 否则从枚举中获取
                    return H5BossEnum.CityCode.forValue(city).getCode();
                })
                .collect(Collectors.toList());
        config.setCityCode(convertedCityCodes);

        // 转换工作经验要求
        config.setExperience(config.getExperience().stream().map(value -> H5BossEnum.Experience.forValue(value).getCode()).collect(Collectors.toList()));
        // 转换学历要求
        config.setDegree(config.getDegree().stream().map(value -> H5BossEnum.Degree.forValue(value).getCode()).collect(Collectors.toList()));
        // 转换公司规模
        config.setScale(config.getScale().stream().map(value -> H5BossEnum.Scale.forValue(value).getCode()).collect(Collectors.toList()));

        return config;
    }

}
