package getjobs.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import getjobs.modules.job51.dto.Job51ApiResponse;
import getjobs.repository.entity.JobEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 51Job数据转换器
 * 负责将51Job API响应数据转换为JobEntity
 * 
 * @author getjobs
 * @since v2.1.1
 */
@Slf4j
@Component
public class Job51DataConverter {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 将51Job API响应中的职位数据转换为JobEntity
     * 
     * @param jobItem 51Job API响应中的单个职位数据
     * @return JobEntity 职位实体
     */
    public JobEntity convertToJobEntity(Job51ApiResponse.Job51JobItem jobItem) {
        try {
            JobEntity jobEntity = new JobEntity();

            // 基础职位信息
            jobEntity.setJobTitle(jobItem.getJobName());
            jobEntity.setSalaryDesc(jobItem.getProvideSalaryString());
            jobEntity.setJobExperience(jobItem.getWorkYearString());
            jobEntity.setJobDegree(jobItem.getDegreeString());
            jobEntity.setJobDescription(jobItem.getJobDescribe());
            jobEntity.setJobUrl(jobItem.getJobHref());

            // 职位标签处理 - 优先使用jobTags，如果为空则使用jobTagsForOrder
            List<String> tags = jobItem.getJobTags();
            if (tags == null || tags.isEmpty()) {
                tags = jobItem.getJobTagsForOrder();
            }
            if (tags != null && !tags.isEmpty()) {
                jobEntity.setJobLabels(convertListToJson(tags));
                jobEntity.setSkills(convertListToJson(tags));
            }

            // 公司信息
            jobEntity.setCompanyName(jobItem.getCompanyName());
            // 优先使用fullCompanyName，如果没有则使用companyName
            if (jobItem.getFullCompanyName() != null && !jobItem.getFullCompanyName().isEmpty()) {
                jobEntity.setCompanyName(jobItem.getFullCompanyName());
            }
            
            // 构建行业信息
            String industry = jobItem.getCompanyIndustryType1Str();
            if (jobItem.getIndustryType2Str() != null && !jobItem.getIndustryType2Str().isEmpty()) {
                industry += "/" + jobItem.getIndustryType2Str();
            }
            jobEntity.setCompanyIndustry(industry);
            
            jobEntity.setCompanyScale(jobItem.getCompanySizeString());
            jobEntity.setCompanyStage(jobItem.getCompanyTypeString());
            jobEntity.setCompanyLogo(jobItem.getCompanyLogo());

            // 工作地点信息
            if (jobItem.getJobAreaLevelDetail() != null) {
                Job51ApiResponse.Job51AreaDetail area = jobItem.getJobAreaLevelDetail();
                jobEntity.setWorkCity(area.getCityString());
                jobEntity.setWorkArea(area.getDistrictString());
                jobEntity.setBusinessDistrict(area.getLandMarkString());
            } else {
                jobEntity.setWorkCity(jobItem.getJobAreaString());
            }

            // GPS坐标
            if (jobItem.getLon() != null && jobItem.getLat() != null) {
                try {
                    jobEntity.setLongitude(new BigDecimal(jobItem.getLon()));
                    jobEntity.setLatitude(new BigDecimal(jobItem.getLat()));
                } catch (NumberFormatException e) {
                    log.warn("GPS坐标格式错误: lon={}, lat={}", jobItem.getLon(), jobItem.getLat());
                }
            }

            // HR信息
            jobEntity.setHrName(jobItem.getHrName());
            jobEntity.setHrTitle(jobItem.getHrPosition());
            jobEntity.setHrAvatar(jobItem.getSmallHrLogoUrl());
            jobEntity.setHrOnline(jobItem.getHrIsOnline());
            jobEntity.setHrActiveTime(jobItem.getHrActiveStatusGreen());

            // 系统信息
            jobEntity.setPlatform("51job");
            jobEntity.setEncryptJobId(jobItem.getJobId());
            jobEntity.setEncryptHrId(jobItem.getHrUid());
            jobEntity.setEncryptCompanyId(jobItem.getEncCoId());
            
            // 设置公司ID
            if (jobItem.getCoId() != null) {
                try {
                    jobEntity.setItemId(Integer.parseInt(jobItem.getCoId()));
                } catch (NumberFormatException e) {
                    log.warn("公司ID格式错误: {}", jobItem.getCoId());
                }
            }

            // 状态信息
            jobEntity.setStatus(0); // 待处理状态
            jobEntity.setIsFavorite(false);
            jobEntity.setIsContacted(jobItem.getIsCommunicate());

            // 福利信息
            if (jobItem.getJobWelfareCodeDataList() != null && !jobItem.getJobWelfareCodeDataList().isEmpty()) {
                List<String> welfareList = jobItem.getJobWelfareCodeDataList().stream()
                    .map(Job51ApiResponse.Job51WelfareData::getChineseTitle)
                    .collect(Collectors.toList());
                jobEntity.setWelfareList(convertListToJson(welfareList));
            }

            // 芝麻标签信息
            if (jobItem.getSesameLabelList() != null && !jobItem.getSesameLabelList().isEmpty()) {
                List<String> labelList = jobItem.getSesameLabelList().stream()
                    .map(Job51ApiResponse.Job51SesameLabel::getLabelName)
                    .collect(Collectors.toList());
                jobEntity.setCompanyTag(String.join(",", labelList));
            }

            // 51job特有字段映射
            jobEntity.setJobType(parseJobType(jobItem.getJobType()));
            // 注：JobEntity中没有直接对应的字段，可以在jobLabels中记录这些信息
            if (Boolean.TRUE.equals(jobItem.getIsRemoteWork())) {
                String currentLabels = jobEntity.getJobLabels();
                if (currentLabels == null) {
                    currentLabels = "[\"远程工作\"]";
                } else {
                    // 添加远程工作标签到现有标签中
                    currentLabels = currentLabels.replace("]", ",\"远程工作\"]");
                }
                jobEntity.setJobLabels(currentLabels);
            }
            if (Boolean.TRUE.equals(jobItem.getIsIntern())) {
                String currentLabels = jobEntity.getJobLabels();
                if (currentLabels == null) {
                    currentLabels = "[\"实习\"]";
                } else {
                    // 添加实习标签到现有标签中
                    currentLabels = currentLabels.replace("]", ",\"实习\"]");
                }
                jobEntity.setJobLabels(currentLabels);
            }

            // 薪资范围解析 - 使用精确的薪资范围数据
            parseSalaryRange(jobItem, jobEntity);
            
            // 更新时间信息
            if (jobItem.getUpdateDateTime() != null) {
                jobEntity.setHrActiveTime(jobItem.getUpdateDateTime());
            }
            
            // 处理职位发布和确认时间
            if (jobItem.getIssueDateString() != null) {
                // 可以在这里设置职位发布时间到相应字段，如果JobEntity有的话
                log.debug("职位发布时间: {}", jobItem.getIssueDateString());
            }

            // 行业和职能编码
            if (jobItem.getIndustryType1() != null) {
                try {
                    jobEntity.setIndustryCode(Long.parseLong(jobItem.getIndustryType1()));
                } catch (NumberFormatException e) {
                    log.warn("行业编码格式错误: {}", jobItem.getIndustryType1());
                }
            }
            
            // 处理工作区域信息
            if (jobItem.getWorkAreaCode() != null) {
                try {
                    jobEntity.setCityCode(Long.parseLong(jobItem.getWorkAreaCode()));
                } catch (NumberFormatException e) {
                    log.warn("区域代码格式错误: {}", jobItem.getWorkAreaCode());
                }
            }
            
            // 处理职位类型和任期信息
            if (jobItem.getTermStr() != null) {
                // 可以将任期信息添加到职位标签中
                String currentLabels = jobEntity.getJobLabels();
                if (currentLabels == null) {
                    currentLabels = "[\"" + jobItem.getTermStr() + "\"]";
                } else {
                    currentLabels = currentLabels.replace("]", ",\"" + jobItem.getTermStr() + "\"]");
                }
                jobEntity.setJobLabels(currentLabels);
            }

            return jobEntity;

        } catch (Exception e) {
            log.error("转换51Job职位数据失败: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 解析薪资范围
     */
    private void parseSalaryRange(Job51ApiResponse.Job51JobItem jobItem, JobEntity jobEntity) {
        // 优先使用provideSalaryString，如果为空则使用jobSalaryMin和jobSalaryMax计算
        if (jobItem.getProvideSalaryString() != null && !jobItem.getProvideSalaryString().trim().isEmpty()) {
            jobEntity.setSalaryDesc(jobItem.getProvideSalaryString());
        } else if (jobItem.getJobSalaryMin() != null && jobItem.getJobSalaryMax() != null) {
            try {
                // 51job的薪资单位通常是元/月
                int minMonthly = Integer.parseInt(jobItem.getJobSalaryMin());
                int maxMonthly = Integer.parseInt(jobItem.getJobSalaryMax());
                
                // 构建薪资描述
                if (minMonthly >= 10000) {
                    double minWan = minMonthly / 10000.0;
                    double maxWan = maxMonthly / 10000.0;
                    if (minWan == (int) minWan && maxWan == (int) maxWan) {
                        jobEntity.setSalaryDesc(String.format("%.0f-%.0f万", minWan, maxWan));
                    } else {
                        jobEntity.setSalaryDesc(String.format("%.1f-%.1f万", minWan, maxWan));
                    }
                } else {
                    jobEntity.setSalaryDesc(String.format("%d-%d元", minMonthly, maxMonthly));
                }
            } catch (NumberFormatException e) {
                log.warn("薪资范围解析失败: min={}, max={}", jobItem.getJobSalaryMin(), jobItem.getJobSalaryMax());
            }
        }
    }

    /**
     * 解析职位类型
     */
    private Integer parseJobType(String jobTypeStr) {
        if (jobTypeStr == null) {
            return 0; // 默认全职
        }
        
        try {
            return Integer.parseInt(jobTypeStr);
        } catch (NumberFormatException e) {
            log.warn("职位类型解析失败: {}", jobTypeStr);
            return 0;
        }
    }

    /**
     * 将字符串列表转换为JSON字符串
     */
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

    /**
     * 检查51Job职位数据是否有效
     * 
     * @param jobItem 职位数据
     * @return 是否有效
     */
    public boolean isValidJobData(Job51ApiResponse.Job51JobItem jobItem) {
        if (jobItem == null) {
            return false;
        }

        // 基础必填字段检查
        if (jobItem.getJobId() == null || jobItem.getJobId().trim().isEmpty()) {
            log.warn("职位ID为空，跳过该职位");
            return false;
        }

        if (jobItem.getJobName() == null || jobItem.getJobName().trim().isEmpty()) {
            log.warn("职位名称为空，跳过该职位: {}", jobItem.getJobId());
            return false;
        }

        // 公司名称检查 - 优先检查fullCompanyName
        String companyName = jobItem.getFullCompanyName();
        if (companyName == null || companyName.trim().isEmpty()) {
            companyName = jobItem.getCompanyName();
        }
        if (companyName == null || companyName.trim().isEmpty()) {
            log.warn("公司名称为空，跳过该职位: {}", jobItem.getJobId());
            return false;
        }

        // 检查是否过期
        if (Boolean.TRUE.equals(jobItem.getIsExpire())) {
            log.debug("职位已过期，跳过该职位: {}", jobItem.getJobId());
            return false;
        }

        // 检查薪资信息 - 至少要有一种薪资描述
        if ((jobItem.getProvideSalaryString() == null || jobItem.getProvideSalaryString().trim().isEmpty()) &&
            (jobItem.getJobSalaryMin() == null || jobItem.getJobSalaryMax() == null)) {
            log.debug("薪资信息缺失，跳过该职位: {}", jobItem.getJobId());
            return false;
        }

        // 检查工作地点信息
        if ((jobItem.getJobAreaString() == null || jobItem.getJobAreaString().trim().isEmpty()) &&
            (jobItem.getJobAreaLevelDetail() == null || 
             jobItem.getJobAreaLevelDetail().getCityString() == null ||
             jobItem.getJobAreaLevelDetail().getCityString().trim().isEmpty())) {
            log.debug("工作地点信息缺失，跳过该职位: {}", jobItem.getJobId());
            return false;
        }

        return true;
    }

    /**
     * 生成职位的唯一标识符
     * 用于去重判断
     * 
     * @param jobItem 职位数据
     * @return 唯一标识符
     */
    public String generateJobUniqueId(Job51ApiResponse.Job51JobItem jobItem) {
        if (jobItem == null || jobItem.getJobId() == null) {
            return null;
        }
        
        // 使用职位ID作为唯一标识
        return "51job_" + jobItem.getJobId();
    }
}
