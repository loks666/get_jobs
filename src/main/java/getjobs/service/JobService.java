package getjobs.service;

import getjobs.modules.boss.dto.JobDTO;
import getjobs.repository.entity.JobEntity;
import getjobs.repository.JobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 职位服务类
 * 
 * @author getjobs
 * @since v2.0.1
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JobService {

    private final JobRepository jobRepository;

    /**
     * 批量保存职位信息到数据库
     * 
     * @param jobDTOs  职位DTO列表
     * @param platform 数据来源平台
     * @return 保存的职位数量
     */
    @Transactional
    public int saveJobs(List<JobDTO> jobDTOs, String platform) {
        if (jobDTOs == null || jobDTOs.isEmpty()) {
            log.warn("没有职位数据需要保存");
            return 0;
        }

        try {
            // 转换为实体对象
            List<JobEntity> jobEntities = jobDTOs.stream()
                    .map(dto -> convertToEntity(dto, platform))
                    .collect(Collectors.toList());

            // 批量保存
            jobRepository.saveAll(jobEntities);

            log.info("成功保存 {} 个职位到数据库，平台: {}", jobEntities.size(), platform);
            return jobEntities.size();

        } catch (Exception e) {
            log.error("保存职位数据到数据库失败", e);
            throw new RuntimeException("保存职位数据失败: " + e.getMessage(), e);
        }
    }

    /**
     * 查询所有职位实体并转换为JobDTO列表
     * 
     * @param platform 平台名称（可选，为null时查询所有平台）
     * @return JobDTO列表
     */
    public List<JobDTO> findAllJobsAsDTO(String platform) {
        try {
            List<JobEntity> jobEntities;
            if (platform != null && !platform.trim().isEmpty()) {
                jobEntities = jobRepository.findAll().stream()
                        .filter(job -> platform.equalsIgnoreCase(job.getPlatform()))
                        .collect(Collectors.toList());
            } else {
                jobEntities = jobRepository.findAll();
            }

            return jobEntities.stream()
                    .map(this::convertToDTO)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("查询职位数据失败", e);
            throw new RuntimeException("查询职位数据失败: " + e.getMessage(), e);
        }
    }

    /**
     * 将JobEntity转换为JobDTO
     * 
     * @param entity JobEntity对象
     * @return JobDTO对象
     */
    public JobDTO convertToDTO(JobEntity entity) {
        JobDTO dto = new JobDTO();

        // 基础职位信息映射
        dto.setHref(entity.getJobUrl());
        dto.setJobName(entity.getJobTitle());
        dto.setJobArea(entity.getWorkCity());
        dto.setJobInfo(entity.getJobDescription());
        dto.setSalary(entity.getSalaryDesc());
        dto.setSalaryDesc(entity.getSalaryDesc());
        dto.setJobExperience(entity.getJobExperience());
        dto.setJobDegree(entity.getJobDegree());

        // 处理JSON格式的字段
        if (entity.getJobLabels() != null && !entity.getJobLabels().trim().isEmpty()) {
            dto.setJobLabels(List.of(entity.getJobLabels().split(",")));
        }
        if (entity.getSkills() != null && !entity.getSkills().trim().isEmpty()) {
            dto.setSkills(List.of(entity.getSkills().split(",")));
        }
        if (entity.getWelfareList() != null && !entity.getWelfareList().trim().isEmpty()) {
            dto.setWelfareList(List.of(entity.getWelfareList().split(",")));
        }

        dto.setJobDescription(entity.getJobDescription());
        dto.setJobRequirements(entity.getJobRequirements());

        // 公司信息映射
        dto.setCompanyName(entity.getCompanyName());
        dto.setCompanyIndustry(entity.getCompanyIndustry());
        dto.setCompanyStage(entity.getCompanyStage());
        dto.setCompanyScale(entity.getCompanyScale());
        dto.setCompanyLogo(entity.getCompanyLogo());
        dto.setCompanyTag(entity.getCompanyTag());

        // 工作地点信息映射
        dto.setWorkCity(entity.getWorkCity());
        dto.setWorkArea(entity.getWorkArea());
        dto.setBusinessDistrict(entity.getBusinessDistrict());
        dto.setLongitude(entity.getLongitude());
        dto.setLatitude(entity.getLatitude());

        // HR信息映射
        dto.setRecruiter(entity.getHrName());
        dto.setHrName(entity.getHrName());
        dto.setHrTitle(entity.getHrTitle());
        dto.setHrAvatar(entity.getHrAvatar());
        dto.setHrOnline(entity.getHrOnline());
        dto.setHrCertLevel(entity.getHrCertLevel());
        dto.setHrActiveTime(entity.getBossActiveTimeDesc());

        // 系统信息映射
        dto.setPlatform(entity.getPlatform());
        dto.setEncryptJobId(entity.getEncryptJobId());
        dto.setEncryptHrId(entity.getEncryptHrId());
        dto.setEncryptCompanyId(entity.getEncryptCompanyId());
        dto.setSecurityId(entity.getSecurityId());
        dto.setStatus(entity.getStatus());
        dto.setFilterReason(entity.getFilterReason());
        dto.setIsFavorite(entity.getIsFavorite());
        dto.setIsOptimal(entity.getIsOptimal());
        dto.setIsProxyJob(entity.getIsProxyJob());
        dto.setProxyType(entity.getProxyType());
        dto.setIsGoldHunter(entity.getIsGoldHunter());
        dto.setIsContacted(entity.getIsContacted());
        dto.setIsShielded(entity.getIsShielded());
        dto.setJobValidStatus(entity.getJobValidStatus());
        dto.setIconWord(entity.getIconWord());
        dto.setLeastMonthDesc(entity.getLeastMonthDesc());
        dto.setDaysPerWeekDesc(entity.getDaysPerWeekDesc());
        dto.setShowTopPosition(entity.getShowTopPosition());
        dto.setIsOutland(entity.getIsOutland());
        dto.setAnonymousStatus(entity.getAnonymousStatus());
        dto.setItemId(entity.getItemId());
        dto.setExpectId(entity.getExpectId());
        dto.setCityCode(entity.getCityCode());
        dto.setIndustryCode(entity.getIndustryCode());
        dto.setJobType(entity.getJobType());
        dto.setAtsDirectPost(entity.getAtsDirectPost());
        dto.setSearchId(entity.getSearchId());

        return dto;
    }

    /**
     * 将JobDTO转换为JobEntity
     * 
     * @param dto      JobDTO对象
     * @param platform 数据来源平台
     * @return JobEntity对象
     */
    private JobEntity convertToEntity(JobDTO dto, String platform) {
        JobEntity entity = new JobEntity();

        // 基本信息映射
        entity.setJobTitle(dto.getJobName());
        entity.setCompanyName(dto.getCompanyName());
        entity.setSalaryDesc(dto.getSalary());
        entity.setWorkCity(dto.getJobArea());
        entity.setJobDescription(dto.getJobInfo());
        entity.setJobUrl(dto.getHref());
        entity.setPlatform(platform);
        entity.setHrName(dto.getRecruiter());
        entity.setCompanyTag(dto.getCompanyTag());
        entity.setJobLabels(String.join(",", dto.getJobLabels()));

        // 设置默认值
        entity.setStatus(0); // 待处理
        entity.setIsFavorite(false); // 默认不收藏

        return entity;
    }

    /**
     * 批量更新职位状态和过滤原因
     * 
     * @param encryptJobIds       加密职位ID列表
     * @param status       新状态
     * @param filterReason 过滤原因
     * @return 更新的职位数量
     */
    @Transactional
    public int updateJobStatus(List<String> encryptJobIds, Integer status, String filterReason) {
        if (encryptJobIds == null || encryptJobIds.isEmpty()) {
            log.warn("没有职位ID需要更新状态");
            return 0;
        }

        try {
            List<JobEntity> jobEntities = jobRepository.findAllByEncryptJobIdIn(encryptJobIds);
            for (JobEntity entity : jobEntities) {
                entity.setStatus(status);
                entity.setFilterReason(filterReason);
            }

            jobRepository.saveAll(jobEntities);
            log.info("成功更新 {} 个职位的状态为 {}，过滤原因: {}", jobEntities.size(), status, filterReason);
            return jobEntities.size();

        } catch (Exception e) {
            log.error("批量更新职位状态失败", e);
            throw new RuntimeException("批量更新职位状态失败: " + e.getMessage(), e);
        }
    }

    /**
     * 根据平台查询所有职位实体
     * 
     * @param platform 平台名称
     * @return 职位实体列表
     */
    public List<JobEntity> findAllJobEntitiesByPlatform(String platform) {
        try {
            if (platform != null && !platform.trim().isEmpty()) {
                // 使用数据库级别的查询，避免在内存中过滤大量数据
                return jobRepository.findByPlatform(platform);
            } else {
                return jobRepository.findAll();
            }
        } catch (Exception e) {
            log.error("查询职位实体失败", e);
            throw new RuntimeException("查询职位实体失败: " + e.getMessage(), e);
        }
    }
}
