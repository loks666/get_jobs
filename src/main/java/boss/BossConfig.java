package boss;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import utils.JobUtils;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author loks666
 * 项目链接: <a href="https://github.com/loks666/get_jobs">https://github.com/loks666/get_jobs</a>
 */
@Data
@Slf4j
public class BossConfig {
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
     * 目标薪资
     */
    private List<Integer> expectedSalary;

    /**
     * 等待时间
     */
    private String waitTime;

    /**
     * HR未上线状态
     */
    private List<String> deadStatus;
    /**
     * 期望工作区域
     */
    private List<String> jobAdds;

    /**
     * 城市代码映射缓存
     */
    private static Map<String, String> cityCodeMap = new HashMap<>();

    /**
     * 从JSON文件加载城市代码
     */
    @SuppressWarnings("unchecked")
    private static void loadCityCodeFromJson() {
        if (!cityCodeMap.isEmpty()) {
            return;
        }

        try {
            ObjectMapper mapper = new ObjectMapper();
            File jsonFile = new File("src/main/java/boss/city-industry-code.json");

            if (!jsonFile.exists()) {
                log.error("城市代码JSON文件不存在: {}", jsonFile.getAbsolutePath());
                return;
            }

            // 读取JSON文件
            Map<String, Object> data = mapper.readValue(jsonFile, new TypeReference<>() {});
            List<Map<String, Object>> cityList = (List<Map<String, Object>>) data.get("city");

            if (cityList != null) {
                for (Map<String, Object> city : cityList) {
                    String name = (String) city.get("name");
                    Object codeObj = city.get("code");
                    String code = codeObj != null ? codeObj.toString() : "";
                    cityCodeMap.put(name, code);
                }
            }
        } catch (IOException e) {
            log.error("加载城市代码失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 根据城市名称获取城市代码
     * @param cityName 城市名称
     * @return 城市代码，如果未找到返回null
     */
    private static String getCityCodeFromJson(String cityName) {
        return cityCodeMap.get(cityName);
    }

    @SneakyThrows
    public static BossConfig init() {
        BossConfig config = JobUtils.getConfig(BossConfig.class);

        // 加载城市代码JSON数据
        loadCityCodeFromJson();

        // 转换工作类型
        config.setJobType(BossEnum.JobType.forValue(config.getJobType()).getCode());
        // 转换薪资范围
        config.setSalary(BossEnum.Salary.forValue(config.getSalary()).getCode());
        // 转换城市编码
        List<String> convertedCityCodes = config.getCityCode().stream()
                .map(city -> {
                    // 优先从自定义映射中获取
                    if (config.getCustomCityCode() != null && config.getCustomCityCode().containsKey(city)) {
                        return config.getCustomCityCode().get(city);
                    }
                    // 尝试从枚举中获取（不限、全国）
                    BossEnum.CityCode enumCity = BossEnum.CityCode.forValue(city);
                    if (enumCity != BossEnum.CityCode.NULL || "不限".equals(city) || "全国".equals(city)) {
                        return enumCity.getCode();
                    }
                    // 从JSON文件中获取
                    String codeFromJson = getCityCodeFromJson(city);
                    if (codeFromJson != null) {
                        return codeFromJson;
                    }
                    // 如果都找不到，返回"不限"的代码
                    log.warn("未找到城市【{}】的代码，使用默认值", city);
                    return "0";
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

}
