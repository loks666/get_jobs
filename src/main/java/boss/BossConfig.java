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
public class BossConfig {
    
    /**
     * 单例实例
     */
    private static volatile BossConfig instance;
    
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
     * 自定义城市编码映射
     */
    private Map<String, String> customCityCode;

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
     * 简历图片路径
     */
    private String resumeImagePath;

    /**
     * 简历内容
     */
    private String resumeContent;

    /**
     * 目标薪资
     */
    private List<Integer> expectedSalary;

    /**
     * 等待时间
     */
    private String waitTime;

    private List<String> deadStatus;

    /**
     * 是否使用关键词匹配岗位m名称，岗位名称不包含关键字就过滤
     *
     */
    private Boolean keyFilter;

    private Boolean recommendJobs;

    private Boolean h5Jobs;

    /**
     * 是否判定国企
     */
    private Boolean checkStateOwned;

    /**
     * VIP密钥
     */
    private String vipKey;

    /**
     * 接口域名地址
     */
    private String apiDomain;

    /**
     * 私有构造函数，防止外部实例化
     */
    private BossConfig() {
        // 私有构造函数
    }

    /**
     * 获取单例实例
     * 使用双重检查锁定确保线程安全
     */
    @SneakyThrows
    public static BossConfig getInstance() {
        if (instance == null) {
            synchronized (BossConfig.class) {
                if (instance == null) {
                    instance = init();
                }
            }
        }
        return instance;
    }

    /**
     * 初始化配置（私有方法）
     */
    @SneakyThrows
    private static BossConfig init() {
        BossConfig config = JobUtils.getConfig(BossConfig.class);

        // 转换工作类型
        config.setJobType(BossEnum.JobType.forValue(config.getJobType()).getCode());
        // 转换薪资范围
        config.setSalary(BossEnum.Salary.forValue(config.getSalary()).getCode());
        // 转换城市编码
//        config.setCityCode(config.getCityCode().stream().map(value -> BossEnum.CityCode.forValue(value).getCode()).collect(Collectors.toList()));
        List<String> convertedCityCodes = config.getCityCode().stream()
                .map(city -> {
                    // 优先从自定义映射中获取
                    if (config.getCustomCityCode() != null && config.getCustomCityCode().containsKey(city)) {
                        return config.getCustomCityCode().get(city);
                    }
                    // 否则从枚举中获取
                    return BossEnum.CityCode.forValue(city).getCode();
                })
                .collect(Collectors.toList());
        config.setCityCode(convertedCityCodes);
        // 转换工作经验要求
        config.setExperience(config.getExperience().stream().map(value -> BossEnum.Experience.forValue(value).getCode()).collect(Collectors.toList()));
        // 转换学历要求
        config.setDegree(config.getDegree().stream().map(value -> BossEnum.Degree.forValue(value).getCode()).collect(Collectors.toList()));
        // 转换公司规模
        config.setScale(config.getScale().stream().map(value -> BossEnum.Scale.forValue(value).getCode()).collect(Collectors.toList()));
        // 转换公司融资阶段
        config.setStage(config.getStage().stream().map(value -> BossEnum.Financing.forValue(value).getCode()).collect(Collectors.toList()));
        // 转换行业
        config.setIndustry(config.getIndustry().stream().map(value -> BossEnum.Industry.forValue(value).getCode()).collect(Collectors.toList()));

        return config;
    }

    /**
     * 重新加载配置
     * 当配置文件更新时调用此方法
     */
    @SneakyThrows
    public static synchronized void reload() {
        instance = init();
    }

    /**
     * 从Map对象重新加载配置
     * 用于GUI保存配置后立即生效
     */
    @SneakyThrows
    public static synchronized void reload(Map<String, Object> bossConfigMap) {
        // 确保instance已经初始化
        if (instance == null) {
            instance = init();
        }
        
        // 直接更新现有instance的属性，而不是创建新对象
        instance.setSayHi((String) bossConfigMap.get("sayHi"));
        instance.setDebugger((Boolean) bossConfigMap.getOrDefault("debugger", false));
        instance.setKeywords((List<String>) bossConfigMap.get("keywords"));
        instance.setCityCode((List<String>) bossConfigMap.get("cityCode"));
        instance.setCustomCityCode((Map<String, String>) bossConfigMap.get("customCityCode"));
        instance.setIndustry((List<String>) bossConfigMap.get("industry"));
        instance.setExperience((List<String>) bossConfigMap.get("experience"));
        instance.setJobType((String) bossConfigMap.get("jobType"));
        instance.setSalary((String) bossConfigMap.get("salary"));
        instance.setDegree((List<String>) bossConfigMap.get("degree"));
        instance.setScale((List<String>) bossConfigMap.get("scale"));
        instance.setStage((List<String>) bossConfigMap.get("stage"));
        instance.setEnableAI((Boolean) bossConfigMap.getOrDefault("enableAI", false));
        instance.setFilterDeadHR((Boolean) bossConfigMap.getOrDefault("filterDeadHR", false));
        instance.setSendImgResume((Boolean) bossConfigMap.getOrDefault("sendImgResume", false));
        instance.setResumeImagePath((String) bossConfigMap.getOrDefault("resumeImagePath", ""));
        instance.setResumeContent((String) bossConfigMap.getOrDefault("resumeContent", ""));
        instance.setExpectedSalary((List<Integer>) bossConfigMap.get("expectedSalary"));
        instance.setWaitTime(String.valueOf(bossConfigMap.get("waitTime")));
        instance.setDeadStatus((List<String>) bossConfigMap.get("deadStatus"));
        instance.setKeyFilter((Boolean) bossConfigMap.getOrDefault("keyFilter", false));
        instance.setRecommendJobs((Boolean) bossConfigMap.getOrDefault("recommendJobs", false));
        instance.setH5Jobs((Boolean) bossConfigMap.getOrDefault("h5Jobs", false));
        instance.setCheckStateOwned((Boolean) bossConfigMap.getOrDefault("checkStateOwned", false));
        instance.setVipKey((String) bossConfigMap.getOrDefault("vipKey", ""));
        instance.setApiDomain((String) bossConfigMap.getOrDefault("apiDomain", ""));
        
        // 应用转换逻辑
        instance.setJobType(BossEnum.JobType.forValue(instance.getJobType()).getCode());
        instance.setSalary(BossEnum.Salary.forValue(instance.getSalary()).getCode());
        
        // 转换城市编码
        List<String> convertedCityCodes = instance.getCityCode().stream()
                .map(city -> {
                    if (instance.getCustomCityCode() != null && instance.getCustomCityCode().containsKey(city)) {
                        return instance.getCustomCityCode().get(city);
                    }
                    return BossEnum.CityCode.forValue(city).getCode();
                })
                .collect(Collectors.toList());
        instance.setCityCode(convertedCityCodes);
        
        instance.setExperience(instance.getExperience().stream().map(value -> BossEnum.Experience.forValue(value).getCode()).collect(Collectors.toList()));
        instance.setDegree(instance.getDegree().stream().map(value -> BossEnum.Degree.forValue(value).getCode()).collect(Collectors.toList()));
        instance.setScale(instance.getScale().stream().map(value -> BossEnum.Scale.forValue(value).getCode()).collect(Collectors.toList()));
        instance.setStage(instance.getStage().stream().map(value -> BossEnum.Financing.forValue(value).getCode()).collect(Collectors.toList()));
        instance.setIndustry(instance.getIndustry().stream().map(value -> BossEnum.Industry.forValue(value).getCode()).collect(Collectors.toList()));
    }

}
