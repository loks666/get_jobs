package boss;

import lombok.Data;
import lombok.SneakyThrows;
import utils.JobUtils;

import java.util.List;
import java.util.stream.Collectors;

/**
 * @author loks666
 * 项目链接: <a href="https://github.com/loks666/get_jobs">https://github.com/loks666/get_jobs</a>
 */
@Data
public class MobileBossConfig {
    /**
     * 用于打招呼的语句
     */
    private String sayHi;

    /**
     * 开发者模式
     */
    private Boolean debugger;

    /**
     * 搜索关键词列表
     */
    private List<String> keywords;

    /**
     * 城市编码
     */
    private List<String> cityCode;

    /**
     * 行业列表
     */
    private List<String> industry;

    /**
     * 工作经验要求
     */
    private List<String> experience;

    /**
     * 工作类型
     */
    private String jobType;

    /**
     * 薪资范围
     */
    private String salary;

    /**
     * 学历要求列表
     */
    private List<String> degree;

    /**
     * 公司规模列表
     */
    private List<String> scale;

    /**
     * 公司融资阶段列表
     */
    private List<String> stage;

    /**
     * 是否开放AI检测
     */
    private Boolean enableAI;

    /**
     * 是否过滤不活跃hr
     */
    private Boolean filterDeadHR;

    /**
     * 是否发送图片简历
     */
    private Boolean sendImgResume;

    /**
     * 目标薪资
     */
    private List<Integer> expectedSalary;

    /**
     * 等待时间
     */
    private String waitTime;

    private List<String> deadStatus;

    private Integer nextIntervalMinutes;

    @SneakyThrows
    public static MobileBossConfig init() {
        MobileBossConfig config = JobUtils.getConfig(MobileBossConfig.class);

        // 转换薪资范围
        config.setSalary(MobileBossEnum.Salary.forValue(config.getSalary()).getCode());
        // 转换城市编码
        config.setCityCode(config.getCityCode().stream().map(value -> MobileBossEnum.CityCode.forValue(value).getCode()).collect(Collectors.toList()));
        // 转换工作经验要求
        config.setExperience(config.getExperience().stream().map(value -> MobileBossEnum.Experience.forValue(value).getCode()).collect(Collectors.toList()));
        // 转换学历要求
        config.setDegree(config.getDegree().stream().map(value -> MobileBossEnum.Degree.forValue(value).getCode()).collect(Collectors.toList()));
        // 转换公司规模
        config.setScale(config.getScale().stream().map(value -> MobileBossEnum.Scale.forValue(value).getCode()).collect(Collectors.toList()));

        return config;
    }

}
