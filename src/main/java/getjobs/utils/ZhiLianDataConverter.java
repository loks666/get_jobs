package getjobs.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import getjobs.common.enums.RecruitmentPlatformEnum;
import getjobs.modules.zhilian.dto.ZhiLianApiResponse;
import getjobs.repository.entity.JobEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 智联招聘数据转换器
 * 负责将智联招聘API响应数据转换为JobEntity
 * 
 * @author getjobs
 * @since v2.1.1
 */
@Slf4j
@Component
public class ZhiLianDataConverter {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 将智联招聘API响应中的职位数据转换为JobEntity
     * 
     * @param jobItem 智联招聘API响应中的单个职位数据
     * @return JobEntity 职位实体
     */
    public JobEntity convertToJobEntity(ZhiLianApiResponse.ZhiLianJobItem jobItem) {
        try {
            JobEntity jobEntity = new JobEntity();

            // 基础职位信息
            jobEntity.setJobTitle(jobItem.getName());
            jobEntity.setSalaryDesc(jobItem.getSalary60());
            jobEntity.setJobExperience(jobItem.getWorkingExp());
            jobEntity.setJobDegree(jobItem.getEducation());
            jobEntity.setJobDescription(jobItem.getJobSummary());
            jobEntity.setJobUrl(jobItem.getPositionUrl());
            jobEntity.setJobType(parseJobType(jobItem.getWorkType()));

            // 职位标签处理 - 使用技能标签
            List<String> skillTags = null;
            if (jobItem.getSkillLabel() != null && !jobItem.getSkillLabel().isEmpty()) {
                skillTags = jobItem.getSkillLabel().stream()
                    .map(ZhiLianApiResponse.SkillLabel::getValue)
                    .filter(value -> value != null && !value.trim().isEmpty())
                    .collect(Collectors.toList());
            }
            
            // 如果技能标签为空，尝试使用showSkillTags
            if ((skillTags == null || skillTags.isEmpty()) && 
                jobItem.getShowSkillTags() != null && !jobItem.getShowSkillTags().isEmpty()) {
                skillTags = jobItem.getShowSkillTags().stream()
                    .map(ZhiLianApiResponse.ShowSkillTag::getTag)
                    .filter(tag -> tag != null && !tag.trim().isEmpty())
                    .collect(Collectors.toList());
            }

            if (skillTags != null && !skillTags.isEmpty()) {
                jobEntity.setJobLabels(convertListToJson(skillTags));
                jobEntity.setSkills(convertListToJson(skillTags));
            }

            // 公司信息
            jobEntity.setCompanyName(jobItem.getCompanyName());
            jobEntity.setCompanyIndustry(jobItem.getIndustryName());
            jobEntity.setCompanyScale(jobItem.getCompanySize());
            jobEntity.setCompanyStage(jobItem.getPropertyName());
            jobEntity.setCompanyLogo(jobItem.getCompanyLogo());

            // 工作地点信息
            jobEntity.setWorkCity(jobItem.getWorkCity());
            jobEntity.setWorkArea(jobItem.getCityDistrict());
            jobEntity.setBusinessDistrict(jobItem.getStreetName());

            // HR信息
            if (jobItem.getStaffCard() != null) {
                ZhiLianApiResponse.StaffCard staffCard = jobItem.getStaffCard();
                jobEntity.setHrName(staffCard.getStaffName());
                jobEntity.setHrTitle(staffCard.getHrJob());
                jobEntity.setHrAvatar(staffCard.getAvatar());
                if (staffCard.getHrOnlineState() != null && !staffCard.getHrOnlineState().isEmpty()) {
                    jobEntity.setHrOnline(true);
                }
                jobEntity.setHrActiveTime(staffCard.getHrStateInfo());
            }

            // 系统信息
            jobEntity.setPlatform(RecruitmentPlatformEnum.ZHILIAN_ZHAOPIN.getPlatformCode());
            jobEntity.setEncryptJobId(String.valueOf(jobItem.getJobId()));
            jobEntity.setEncryptHrId(jobItem.getStaffCard() != null ? 
                String.valueOf(jobItem.getStaffCard().getId()) : null);
            jobEntity.setEncryptCompanyId(String.valueOf(jobItem.getCompanyId()));
            
            // 设置公司ID
            if (jobItem.getCompanyId() != null) {
                jobEntity.setItemId(jobItem.getCompanyId().intValue());
            }

            // 状态信息
            jobEntity.setStatus(0); // 待处理状态
            jobEntity.setIsFavorite(false);
            jobEntity.setIsContacted(false);

            // 福利信息
            List<String> welfareList = jobItem.getWelfareTagList();
            if (welfareList == null || welfareList.isEmpty()) {
                welfareList = jobItem.getJobKnowledgeWelfareFeatures();
            }
            if (welfareList != null && !welfareList.isEmpty()) {
                jobEntity.setWelfareList(convertListToJson(welfareList));
            }

            // 公司特性标签
            if (jobItem.getProperty() != null) {
                jobEntity.setCompanyTag(jobItem.getProperty());
            }

            // 招聘人数 - 存储在备注字段或作为标签
            if (jobItem.getRecruitNumber() != null) {
                String currentLabels = jobEntity.getJobLabels();
                String recruitLabel = "招聘" + jobItem.getRecruitNumber() + "人";
                if (currentLabels == null || currentLabels.isEmpty()) {
                    jobEntity.setJobLabels("[\"" + recruitLabel + "\"]");
                } else {
                    // 添加招聘人数标签到现有标签中
                    currentLabels = currentLabels.replace("]", ",\"" + recruitLabel + "\"]");
                    jobEntity.setJobLabels(currentLabels);
                }
            }

            // 职位分类 - 存储在jobPositionName字段
            if (jobItem.getSubJobTypeLevelName() != null) {
                jobEntity.setJobPositionName(jobItem.getSubJobTypeLevelName());
            }

            // 薪资范围解析
            parseSalaryRange(jobItem, jobEntity);

            // 时间信息 - 存储在hrActiveTime字段
            if (jobItem.getPublishTime() != null) {
                jobEntity.setHrActiveTime("发布时间: " + jobItem.getPublishTime());
            }

            // 融资阶段
            if (jobItem.getFinancingStage() != null && 
                jobItem.getFinancingStage().getName() != null) {
                jobEntity.setCompanyStage(jobItem.getFinancingStage().getName());
            }

            // 匹配信息 - 存储在现有字段中
            if (jobItem.getMatchInfo() != null && jobItem.getMatchInfo().getMatched() != null) {
                // 可以存储在某个整型字段或作为标签
                if (jobItem.getMatchInfo().getMatched() == 1) {
                    String currentLabels = jobEntity.getJobLabels();
                    String matchLabel = "推荐匹配";
                    if (currentLabels == null || currentLabels.isEmpty()) {
                        jobEntity.setJobLabels("[\"" + matchLabel + "\"]");
                    } else {
                        currentLabels = currentLabels.replace("]", ",\"" + matchLabel + "\"]");
                        jobEntity.setJobLabels(currentLabels);
                    }
                }
            }

            // 职位编号 - 存储在searchId字段
            if (jobItem.getNumber() != null) {
                jobEntity.setSearchId(jobItem.getNumber());
            }

            return jobEntity;

        } catch (Exception e) {
            log.error("转换智联招聘职位数据失败: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 解析薪资范围
     */
    private void parseSalaryRange(ZhiLianApiResponse.ZhiLianJobItem jobItem, JobEntity jobEntity) {
        // 优先使用salary60
        if (jobItem.getSalary60() != null && !jobItem.getSalary60().trim().isEmpty()) {
            jobEntity.setSalaryDesc(jobItem.getSalary60());
        } else if (jobItem.getSalaryReal() != null && !jobItem.getSalaryReal().trim().isEmpty()) {
            // 解析salaryReal格式，例如 "18001-27000"
            String salaryReal = jobItem.getSalaryReal();
            if (salaryReal.contains("-")) {
                try {
                    String[] parts = salaryReal.split("-");
                    if (parts.length == 2) {
                        int minSalary = Integer.parseInt(parts[0]);
                        int maxSalary = Integer.parseInt(parts[1]);
                        
                        // 转换为万元格式
                        if (minSalary >= 10000) {
                            double minWan = minSalary / 10000.0;
                            double maxWan = maxSalary / 10000.0;
                            if (minWan == (int) minWan && maxWan == (int) maxWan) {
                                jobEntity.setSalaryDesc(String.format("%.0f-%.0f万", minWan, maxWan));
                            } else {
                                jobEntity.setSalaryDesc(String.format("%.1f-%.1f万", minWan, maxWan));
                            }
                        } else {
                            jobEntity.setSalaryDesc(String.format("%d-%d元", minSalary, maxSalary));
                        }
                    }
                } catch (NumberFormatException e) {
                    log.warn("薪资范围解析失败: {}", salaryReal);
                    jobEntity.setSalaryDesc(salaryReal);
                }
            } else {
                jobEntity.setSalaryDesc(salaryReal);
            }
        }
    }

    /**
     * 解析职位类型
     */
    private Integer parseJobType(String workType) {
        if (workType == null) {
            return 0; // 默认全职
        }
        
        switch (workType) {
            case "全职":
                return 0;
            case "兼职":
                return 1;
            case "实习":
                return 2;
            case "劳务":
                return 3;
            default:
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
     * 检查智联招聘职位数据是否有效
     * 
     * @param jobItem 职位数据
     * @return 是否有效
     */
    public boolean isValidJobData(ZhiLianApiResponse.ZhiLianJobItem jobItem) {
        if (jobItem == null) {
            return false;
        }

        // 基础必填字段检查
        if (jobItem.getJobId() == null) {
            log.warn("职位ID为空，跳过该职位");
            return false;
        }

        if (jobItem.getName() == null || jobItem.getName().trim().isEmpty()) {
            log.warn("职位名称为空，跳过该职位: {}", jobItem.getJobId());
            return false;
        }

        if (jobItem.getCompanyName() == null || jobItem.getCompanyName().trim().isEmpty()) {
            log.warn("公司名称为空，跳过该职位: {}", jobItem.getJobId());
            return false;
        }

        // 检查薪资信息
        if ((jobItem.getSalary60() == null || jobItem.getSalary60().trim().isEmpty()) &&
            (jobItem.getSalaryReal() == null || jobItem.getSalaryReal().trim().isEmpty())) {
            log.debug("薪资信息缺失，跳过该职位: {}", jobItem.getJobId());
            return false;
        }

        // 检查工作地点信息
        if (jobItem.getWorkCity() == null || jobItem.getWorkCity().trim().isEmpty()) {
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
    public String generateJobUniqueId(ZhiLianApiResponse.ZhiLianJobItem jobItem) {
        if (jobItem == null || jobItem.getJobId() == null) {
            return null;
        }
        
        // 使用职位ID作为唯一标识
        return "zhilian_" + jobItem.getJobId();
    }
}
