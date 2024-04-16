package boss;

import lombok.Data;
import lombok.SneakyThrows;
import utils.JobUtils;

import java.util.List;

@Data
public class BossConfig {
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
    private String cityCode;

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

    @SneakyThrows
    public static BossConfig init() {
        BossConfig config = JobUtils.getConfig(BossConfig.class);
        // 转换城市编码
        config.setCityCode(BossEnum.CityCode.forValue(config.getCityCode()).getCode());
        // 转换工作类型
        config.setJobType(BossEnum.JobType.forValue(config.getJobType()).getCode());
        // 转换薪资范围
        config.setSalary(BossEnum.Salary.forValue(config.getSalary()).getCode());
        // 转换工作经验要求
        config.getExperience().replaceAll(value -> BossEnum.Experience.forValue(value).getCode());
        // 转换学历要求
        config.getDegree().replaceAll(value -> BossEnum.Degree.forValue(value).getCode());
        // 转换公司规模
        config.getScale().replaceAll(value -> BossEnum.Scale.forValue(value).getCode());
        // 转换公司融资阶段
        config.getStage().replaceAll(value -> BossEnum.Financing.forValue(value).getCode());
        // 转换行业
        config.getIndustry().replaceAll(value -> BossEnum.Industry.forValue(value).getCode());

        return config;
    }

}
