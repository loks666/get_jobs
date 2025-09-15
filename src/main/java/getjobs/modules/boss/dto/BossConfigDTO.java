package getjobs.modules.boss.dto;

import getjobs.modules.boss.enums.BossEnum;
import getjobs.repository.entity.ConfigEntity;
import getjobs.repository.ConfigRepository;
import getjobs.utils.SpringContextUtil;
import lombok.Data;
import lombok.SneakyThrows;

import java.util.*;
import java.util.stream.Collectors;

@Data
public class BossConfigDTO {

    // 文本与布尔
    private String sayHi;
    private Boolean debugger;

    // 逗号分隔原始输入（来自表单/配置）
    private String keywords;
    private String cityCode;
    private String industry;

    // 单/多选原始字符串（来自表单/配置）
    private String experience;
    private String jobType;
    private String salary;
    private String degree;
    private String scale;
    private String stage;
    private String expectedPosition;

    // 可选：自定义城市编码映射
    private Map<String, String> customCityCode;

    // 功能开关与AI
    private Boolean enableAIJobMatchDetection;
    private Boolean enableAIGreeting;
    private Boolean filterDeadHR;
    private Boolean sendImgResume;
    private Boolean keyFilter;
    private Boolean recommendJobs;
    private Boolean checkStateOwned;

    // 简历配置
    private String resumeImagePath;
    private String resumeContent;

    // 期望薪资（min/max）
    private Integer minSalary;
    private Integer maxSalary;

    // 系统
    private String waitTime;

    // 其他列表型配置
    private List<String> deadStatus;

    // ------------ 单例加载 ------------
    private static volatile BossConfigDTO instance;

    private BossConfigDTO() {
    }

    public static BossConfigDTO getInstance() {
        if (instance == null) {
            synchronized (BossConfigDTO.class) {
                if (instance == null) {
                    instance = init();
                }
            }
        }
        return instance;
    }

    @SneakyThrows
    private static BossConfigDTO init() {
        // 从数据库查询ConfigEntity
        ConfigRepository configRepository = SpringContextUtil.getBean(ConfigRepository.class);
        Optional<ConfigEntity> configOpt = configRepository.getDefaultConfig();

        if (configOpt.isEmpty()) {
            // 如果数据库中没有配置，返回默认配置
            return new BossConfigDTO();
        }

        // 转换为BossConfigDTO
        return convertFromEntity(configOpt.get());
    }

    @SneakyThrows
    public static synchronized void reload() {
        instance = init();
    }

    /**
     * 将ConfigEntity转换为BossConfigDTO
     */
    private static BossConfigDTO convertFromEntity(ConfigEntity entity) {
        BossConfigDTO dto = new BossConfigDTO();

        // 基础字段映射
        dto.setSayHi(entity.getSayHi());
        dto.setEnableAIJobMatchDetection(entity.getEnableAIJobMatchDetection());
        dto.setEnableAIGreeting(entity.getEnableAIGreeting());
        dto.setFilterDeadHR(entity.getFilterDeadHR());
        dto.setSendImgResume(entity.getSendImgResume());
        dto.setKeyFilter(entity.getKeyFilter());
        dto.setRecommendJobs(entity.getRecommendJobs());
        dto.setCheckStateOwned(entity.getCheckStateOwned());
        dto.setResumeImagePath(entity.getResumeImagePath());
        dto.setResumeContent(entity.getResumeContent());
        dto.setWaitTime(entity.getWaitTime());

        // 列表字段转换为逗号分隔的字符串
        if (entity.getKeywords() != null) {
            dto.setKeywords(String.join(",", entity.getKeywords()));
        }
        if (entity.getCityCode() != null) {
            dto.setCityCode(String.join(",", entity.getCityCode()));
        }
        if (entity.getIndustry() != null) {
            dto.setIndustry(String.join(",", entity.getIndustry()));
        }
        if (entity.getExperience() != null) {
            dto.setExperience(String.join(",", entity.getExperience()));
        }
        if (entity.getDegree() != null) {
            dto.setDegree(String.join(",", entity.getDegree()));
        }
        if (entity.getScale() != null) {
            dto.setScale(String.join(",", entity.getScale()));
        }
        if (entity.getStage() != null) {
            dto.setStage(String.join(",", entity.getStage()));
        }
        if (entity.getDeadStatus() != null) {
            dto.setDeadStatus(entity.getDeadStatus());
        }

        // 期望薪资处理
        if (entity.getExpectedSalary() != null && entity.getExpectedSalary().size() >= 2) {
            dto.setMinSalary(entity.getExpectedSalary().get(0));
            dto.setMaxSalary(entity.getExpectedSalary().get(1));
        }

        // 其他字段
        dto.setCustomCityCode(entity.getCustomCityCode());
        dto.setJobType(entity.getJobType());
        dto.setSalary(entity.getSalary());
        dto.setExpectedPosition(entity.getExpectedPosition());

        return dto;
    }

    // ------------ 包装/转换访问器（供业务方使用）------------

    public List<String> getKeywordsList() {
        return splitToList(keywords);
    }

    public List<String> getCityCodeCodes() {
        List<String> cities = splitToList(cityCode);
        if (cities == null)
            return Collections.emptyList();
        return cities.stream()
                .map(city -> {
                    if (customCityCode != null && customCityCode.containsKey(city)) {
                        return customCityCode.get(city);
                    }
                    return BossEnum.CityCode.forValue(city).getCode();
                })
                .collect(Collectors.toList());
    }

    public List<String> getIndustryCodes() {
        return mapToCodes(splitToList(industry), v -> BossEnum.Industry.forValue(v).getCode());
    }

    public List<String> getExperienceCodes() {
        return mapToCodes(splitToList(experience), v -> BossEnum.Experience.forValue(v).getCode());
    }

    public String getJobTypeCode() {
        return BossEnum.JobType.forValue(jobType).getCode();
    }

    public String getSalaryCode() {
        return BossEnum.Salary.forValue(salary).getCode();
    }

    public List<String> getDegreeCodes() {
        return mapToCodes(splitToList(degree), v -> BossEnum.Degree.forValue(v).getCode());
    }

    public List<String> getScaleCodes() {
        return mapToCodes(splitToList(scale), v -> BossEnum.Scale.forValue(v).getCode());
    }

    public List<String> getStageCodes() {
        return mapToCodes(splitToList(stage), v -> BossEnum.Financing.forValue(v).getCode());
    }

    public List<Integer> getExpectedSalary() {
        List<Integer> list = new ArrayList<>();
        if (minSalary != null)
            list.add(minSalary);
        if (maxSalary != null)
            list.add(maxSalary);
        return list;
    }

    // ------------ 工具方法 ------------
    private List<String> splitToList(String text) {
        if (text == null || text.trim().isEmpty()) {
            return new ArrayList<>();
        }
        // 支持中文/英文逗号、竖线、空格分隔
        String[] arr = text.split("[，,|\\s]+");
        return Arrays.stream(arr)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    private List<String> mapToCodes(List<String> src, java.util.function.Function<String, String> mapper) {
        if (src == null)
            return new ArrayList<>();
        return src.stream().map(mapper).collect(Collectors.toList());
    }
}
