package getjobs.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import getjobs.modules.boss.dto.JobDTO;
import getjobs.repository.entity.JobEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * BOSS直聘职位数据转换工具类
 * 
 * @author getjobs
 * @since v2.0.1
 */
@Slf4j
@Component
public class BossJobDataConverter {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 将BOSS直聘API响应中的职位数据转换为JobEntity
     * 
     * @param jobData BOSS直聘API响应中的单个职位数据
     * @return JobEntity 职位实体
     */
    public JobEntity convertToJobEntity(Map<String, Object> jobData) {
        try {
            JobEntity jobEntity = new JobEntity();

            // 基础职位信息
            jobEntity.setJobTitle(getStringValue(jobData, "jobName"));
            jobEntity.setSalaryDesc(getStringValue(jobData, "salaryDesc"));
            jobEntity.setJobExperience(getStringValue(jobData, "jobExperience"));
            jobEntity.setJobDegree(getStringValue(jobData, "jobDegree"));
            jobEntity.setJobLabels(convertListToJson(getListValue(jobData, "jobLabels")));
            jobEntity.setSkills(convertListToJson(getListValue(jobData, "skills")));

            // 公司信息
            jobEntity.setCompanyName(getStringValue(jobData, "brandName"));
            jobEntity.setCompanyIndustry(getStringValue(jobData, "brandIndustry"));
            jobEntity.setCompanyStage(getStringValue(jobData, "brandStageName"));
            jobEntity.setCompanyScale(getStringValue(jobData, "brandScaleName"));
            jobEntity.setCompanyLogo(getStringValue(jobData, "brandLogo"));

            // 工作地点信息
            jobEntity.setWorkCity(getStringValue(jobData, "cityName"));
            jobEntity.setWorkArea(getStringValue(jobData, "areaDistrict"));
            jobEntity.setBusinessDistrict(getStringValue(jobData, "businessDistrict"));

            // GPS坐标
            Map<String, Object> gps = getMapValue(jobData, "gps");
            if (gps != null) {
                jobEntity.setLongitude(
                        getBigDecimal(gps, "longitude"));
                jobEntity.setLatitude(
                        getBigDecimal(gps, "latitude"));
            }

            // HR信息
            jobEntity.setHrName(getStringValue(jobData, "bossName"));
            jobEntity.setHrTitle(getStringValue(jobData, "bossTitle"));
            jobEntity.setHrAvatar(getStringValue(jobData, "bossAvatar"));
            jobEntity.setHrOnline(getBooleanValue(jobData, "bossOnline"));
            jobEntity.setHrCertLevel(getIntegerValue(jobData, "bossCert"));

            // 系统信息
            jobEntity.setPlatform("BOSS直聘");
            jobEntity.setEncryptJobId(getStringValue(jobData, "encryptJobId"));
            jobEntity.setEncryptHrId(getStringValue(jobData, "encryptBossId"));
            jobEntity.setEncryptCompanyId(getStringValue(jobData, "encryptBrandId"));
            jobEntity.setSecurityId(getStringValue(jobData, "securityId"));

            // 状态信息
            jobEntity.setIsOptimal(getBooleanValue(jobData, "optimal"));
            jobEntity.setIsProxyJob(getBooleanValue(jobData, "proxyJob"));
            jobEntity.setProxyType(getIntegerValue(jobData, "proxyType"));
            jobEntity.setIsGoldHunter(getBooleanValue(jobData, "goldHunter"));
            jobEntity.setIsContacted(getBooleanValue(jobData, "contact"));
            jobEntity.setIsShielded(getIntegerValue(jobData, "isShield") == 1);
            jobEntity.setJobValidStatus(getIntegerValue(jobData, "jobValidStatus"));

            // 其他信息
            jobEntity.setWelfareList(convertListToJson(getListValue(jobData, "welfareList")));
            jobEntity.setIconFlagList(convertIntegerListToJson(getIntegerListValue(jobData, "iconFlagList")));
            jobEntity.setBeforeNameIcons(convertListToJson(getListValue(jobData, "beforeNameIcons")));
            jobEntity.setAfterNameIcons(convertListToJson(getListValue(jobData, "afterNameIcons")));
            jobEntity.setIconWord(getStringValue(jobData, "iconWord"));
            jobEntity.setLeastMonthDesc(getStringValue(jobData, "leastMonthDesc"));
            jobEntity.setDaysPerWeekDesc(getStringValue(jobData, "daysPerWeekDesc"));
            jobEntity.setShowTopPosition(getBooleanValue(jobData, "showTopPosition"));
            jobEntity.setIsOutland(getIntegerValue(jobData, "outland") == 1);
            jobEntity.setAnonymousStatus(getIntegerValue(jobData, "anonymous"));
            jobEntity.setItemId(getIntegerValue(jobData, "itemId"));
            jobEntity.setExpectId(getLongValue(jobData, "expectId"));
            jobEntity.setCityCode(getLongValue(jobData, "city"));
            jobEntity.setIndustryCode(getLongValue(jobData, "industry"));
            jobEntity.setJobType(getIntegerValue(jobData, "jobType"));
            jobEntity.setAtsDirectPost(getBooleanValue(jobData, "atsDirectPost"));
            jobEntity.setSearchId(getStringValue(jobData, "lid"));

            // 构造职位链接
            String encryptJobId = getStringValue(jobData, "encryptJobId");
            if (encryptJobId != null) {
                jobEntity.setJobUrl("https://www.zhipin.com/job_detail/" + encryptJobId + ".html");
            }

            return jobEntity;

        } catch (Exception e) {
            log.error("转换BOSS直聘职位数据失败: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 将JobEntity转换为JobDTO
     * 
     * @param jobEntity 职位实体
     * @return JobDTO 职位数据传输对象
     */
    public JobDTO convertToJobDTO(JobEntity jobEntity) {
        try {
            JobDTO jobDTO = new JobDTO();

            // 基础职位信息
            jobDTO.setJobName(jobEntity.getJobTitle());
            jobDTO.setSalary(jobEntity.getSalaryDesc());
            jobDTO.setSalaryDesc(jobEntity.getSalaryDesc());
            jobDTO.setJobExperience(jobEntity.getJobExperience());
            jobDTO.setJobDegree(jobEntity.getJobDegree());
            jobDTO.setJobLabels(convertJsonToList(jobEntity.getJobLabels()));
            jobDTO.setSkills(convertJsonToList(jobEntity.getSkills()));
            jobDTO.setJobDescription(jobEntity.getJobDescription());
            jobDTO.setJobRequirements(jobEntity.getJobRequirements());
            jobDTO.setHref(jobEntity.getJobUrl());

            // 公司信息
            jobDTO.setCompanyName(jobEntity.getCompanyName());
            jobDTO.setCompanyIndustry(jobEntity.getCompanyIndustry());
            jobDTO.setCompanyStage(jobEntity.getCompanyStage());
            jobDTO.setCompanyScale(jobEntity.getCompanyScale());
            jobDTO.setCompanyLogo(jobEntity.getCompanyLogo());
            jobDTO.setCompanyTag(jobEntity.getCompanyTag());

            // 工作地点信息
            jobDTO.setJobArea(jobEntity.getWorkCity());
            jobDTO.setWorkCity(jobEntity.getWorkCity());
            jobDTO.setWorkArea(jobEntity.getWorkArea());
            jobDTO.setBusinessDistrict(jobEntity.getBusinessDistrict());
            jobDTO.setLongitude(jobEntity.getLongitude());
            jobDTO.setLatitude(jobEntity.getLatitude());

            // HR信息
            jobDTO.setRecruiter(jobEntity.getHrName());
            jobDTO.setHrName(jobEntity.getHrName());
            jobDTO.setHrTitle(jobEntity.getHrTitle());
            jobDTO.setHrAvatar(jobEntity.getHrAvatar());
            jobDTO.setHrOnline(jobEntity.getHrOnline());
            jobDTO.setHrCertLevel(jobEntity.getHrCertLevel());
            jobDTO.setHrActiveTime(jobEntity.getHrActiveTime());

            // 系统信息
            jobDTO.setPlatform(jobEntity.getPlatform());
            jobDTO.setEncryptJobId(jobEntity.getEncryptJobId());
            jobDTO.setEncryptHrId(jobEntity.getEncryptHrId());
            jobDTO.setEncryptCompanyId(jobEntity.getEncryptCompanyId());
            jobDTO.setSecurityId(jobEntity.getSecurityId());
            jobDTO.setStatus(jobEntity.getStatus());
            jobDTO.setIsFavorite(jobEntity.getIsFavorite());
            jobDTO.setIsOptimal(jobEntity.getIsOptimal());
            jobDTO.setIsProxyJob(jobEntity.getIsProxyJob());
            jobDTO.setProxyType(jobEntity.getProxyType());
            jobDTO.setIsGoldHunter(jobEntity.getIsGoldHunter());
            jobDTO.setIsContacted(jobEntity.getIsContacted());
            jobDTO.setIsShielded(jobEntity.getIsShielded());
            jobDTO.setJobValidStatus(jobEntity.getJobValidStatus());

            // 其他信息
            jobDTO.setWelfareList(convertJsonToList(jobEntity.getWelfareList()));
            jobDTO.setIconFlagList(convertJsonToIntegerList(jobEntity.getIconFlagList()));
            jobDTO.setBeforeNameIcons(convertJsonToList(jobEntity.getBeforeNameIcons()));
            jobDTO.setAfterNameIcons(convertJsonToList(jobEntity.getAfterNameIcons()));
            jobDTO.setIconWord(jobEntity.getIconWord());
            jobDTO.setLeastMonthDesc(jobEntity.getLeastMonthDesc());
            jobDTO.setDaysPerWeekDesc(jobEntity.getDaysPerWeekDesc());
            jobDTO.setShowTopPosition(jobEntity.getShowTopPosition());
            jobDTO.setIsOutland(jobEntity.getIsOutland());
            jobDTO.setAnonymousStatus(jobEntity.getAnonymousStatus());
            jobDTO.setItemId(jobEntity.getItemId());
            jobDTO.setExpectId(jobEntity.getExpectId());
            jobDTO.setCityCode(jobEntity.getCityCode());
            jobDTO.setIndustryCode(jobEntity.getIndustryCode());
            jobDTO.setJobType(jobEntity.getJobType());
            jobDTO.setAtsDirectPost(jobEntity.getAtsDirectPost());
            jobDTO.setSearchId(jobEntity.getSearchId());

            return jobDTO;

        } catch (Exception e) {
            log.error("转换JobEntity到JobDTO失败: {}", e.getMessage(), e);
            return null;
        }
    }

    // ==================== 辅助方法 ====================

    private String getStringValue(Map<String, Object> data, String key) {
        Object value = data.get(key);
        return value != null ? value.toString() : null;
    }

    private Integer getIntegerValue(Map<String, Object> data, String key) {
        Object value = data.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return null;
    }

    private Long getLongValue(Map<String, Object> data, String key) {
        Object value = data.get(key);
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return null;
    }

    private Double getDoubleValue(Map<String, Object> data, String key) {
        Object value = data.get(key);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return null;
    }

    private BigDecimal getBigDecimal(Map<String, Object> data, String key) {
        Object value = data.get(key);
        if (value instanceof Number) {
            return BigDecimal.valueOf(((Number) value).doubleValue());
        }
        return null;
    }

    private Boolean getBooleanValue(Map<String, Object> data, String key) {
        Object value = data.get(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        } else if (value instanceof Number) {
            return ((Number) value).intValue() == 1;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private List<String> getListValue(Map<String, Object> data, String key) {
        Object value = data.get(key);
        if (value instanceof List) {
            return (List<String>) value;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private List<Integer> getIntegerListValue(Map<String, Object> data, String key) {
        Object value = data.get(key);
        if (value instanceof List) {
            return (List<Integer>) value;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getMapValue(Map<String, Object> data, String key) {
        Object value = data.get(key);
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        return null;
    }

    private String convertListToJson(List<String> list) {
        if (list == null || list.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(list);
        } catch (JsonProcessingException e) {
            log.error("转换List到JSON失败: {}", e.getMessage());
            return null;
        }
    }

    private String convertIntegerListToJson(List<Integer> list) {
        if (list == null || list.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(list);
        } catch (JsonProcessingException e) {
            log.error("转换IntegerList到JSON失败: {}", e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> convertJsonToList(String json) {
        if (json == null || json.trim().isEmpty()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, List.class);
        } catch (JsonProcessingException e) {
            log.error("转换JSON到List失败: {}", e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private List<Integer> convertJsonToIntegerList(String json) {
        if (json == null || json.trim().isEmpty()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, List.class);
        } catch (JsonProcessingException e) {
            log.error("转换JSON到IntegerList失败: {}", e.getMessage());
            return null;
        }
    }
}
