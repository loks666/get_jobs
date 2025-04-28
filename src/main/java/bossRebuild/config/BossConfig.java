package bossRebuild.config;

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
public class BossConfig {
    private String sayHi;
    private Boolean debugger;
    private List<String> keywords;
    private List<String> cityCode;
    private List<String> industry;
    private List<String> experience;
    private String jobType;
    private String salary;
    private List<String> degree;
    private List<String> scale;
    private List<String> stage;
    private Boolean enableAI;
    private Boolean filterDeadHR;
    private Boolean sendImgResume;
    private List<Integer> expectedSalary;
    private String waitTime;

    @SneakyThrows
    public static BossConfig init() {
        BossConfig config = JobUtils.getConfig(BossConfig.class);

        config.setJobType(BossEnum.JobType.forValue(config.getJobType()).getCode());
        config.setSalary(BossEnum.Salary.forValue(config.getSalary()).getCode());
        config.setCityCode(config.getCityCode().stream().map(value -> BossEnum.CityCode.forValue(value).getCode()).collect(Collectors.toList()));
        config.setExperience(config.getExperience().stream().map(value -> BossEnum.Experience.forValue(value).getCode()).collect(Collectors.toList()));
        config.setDegree(config.getDegree().stream().map(value -> BossEnum.Degree.forValue(value).getCode()).collect(Collectors.toList()));
        config.setScale(config.getScale().stream().map(value -> BossEnum.Scale.forValue(value).getCode()).collect(Collectors.toList()));
        config.setStage(config.getStage().stream().map(value -> BossEnum.Financing.forValue(value).getCode()).collect(Collectors.toList()));
        config.setIndustry(config.getIndustry().stream().map(value -> BossEnum.Industry.forValue(value).getCode()).collect(Collectors.toList()));

        return config;
    }
}