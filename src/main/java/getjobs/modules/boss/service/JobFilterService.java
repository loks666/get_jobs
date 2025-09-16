package getjobs.modules.boss.service;

import getjobs.modules.boss.dto.BossConfigDTO;
import getjobs.modules.boss.dto.JobDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class JobFilterService {

    private Set<String> blackCompanies = new HashSet<>();
    private Set<String> blackRecruiters = new HashSet<>();

    private Set<String> blackJobs = new HashSet<>();


    public List<JobDTO> filterJobs(List<JobDTO> jobDTOS, BossConfigDTO config) {
        log.info("开始Boss直聘岗位过滤，原始岗位数量: {}", jobDTOS.size());

        List<JobDTO> filteredJobDTOS = jobDTOS.stream()
                .map(job -> {
                    String filterReason = getFilterReason(job, config);
                    job.setFilterReason(filterReason);
                    return job;
                })
                .collect(Collectors.toList());

        log.info("Boss直聘岗位过滤完成，过滤后岗位数量: {}", filteredJobDTOS.size());
        return filteredJobDTOS;
    }


    /**
     * 获取职位过滤原因
     *
     * @param job    职位信息
     * @param config 配置信息
     * @return 过滤原因，null表示通过过滤
     */
    private String getFilterReason(JobDTO job, BossConfigDTO config) {
        // 检查岗位黑名单
        if (isJobInBlacklist(job)) {
            return "岗位名称包含黑名单关键词";
        }

        // 检查公司黑名单
        if (isCompanyInBlacklist(job)) {
            return "公司名称包含黑名单关键词";
        }

        // 检查招聘者黑名单
        if (isRecruiterInBlacklist(job)) {
            return "招聘者包含黑名单关键词";
        }

        // 检查薪资
        if (!isSalaryExpected(job, config)) {
            return "薪资不符合预期范围";
        }

        // 检测HR活跃状态
        if (config.getDeadStatus() != null && !config.getDeadStatus().isEmpty()) {
            if(config.getDeadStatus().contains(job.getHrActiveTime())){
                return "HR活跃状态已被过滤-"+job.getHrActiveTime();
            }
        }

        return null; // 通过所有过滤条件
    }


    /**
     * 检查岗位是否在黑名单中
     */
    private boolean isJobInBlacklist(JobDTO jobDTO) {
        return blackJobs.stream().anyMatch(blackJob -> jobDTO.getJobName().contains(blackJob));
    }

    /**
     * 检查公司是否在黑名单中
     */
    private boolean isCompanyInBlacklist(JobDTO jobDTO) {
        return blackCompanies.stream().anyMatch(blackCompany -> jobDTO.getCompanyName().contains(blackCompany));
    }

    /**
     * 检查招聘者是否在黑名单中
     */
    private boolean isRecruiterInBlacklist(JobDTO jobDTO) {
        return blackRecruiters.stream()
                .anyMatch(blackRecruiter -> jobDTO.getRecruiter() != null
                        && jobDTO.getRecruiter().contains(blackRecruiter));
    }

    /**
     * 检查薪资是否符合预期
     */
    private boolean isSalaryExpected(JobDTO jobDTO, BossConfigDTO config) {
        if (jobDTO.getSalary() == null || jobDTO.getSalary().isEmpty()) {
            return true; // 没有薪资信息时默认通过
        }

        try {
            List<Integer> expectedSalary = config.getExpectedSalary();
            if (expectedSalary == null || expectedSalary.isEmpty()) {
                return true; // 没有期望薪资时默认通过
            }

            return !isSalaryNotExpected(jobDTO.getSalary(), expectedSalary);
        } catch (Exception e) {
            log.debug("薪资验证失败: {}", e.getMessage());
            return true; // 验证失败时默认通过
        }
    }

    /**
     * 检查薪资是否不符合预期
     */
    private boolean isSalaryNotExpected(String salary, List<Integer> expectedSalary) {
        try {
            // 清理薪资文本
            salary = removeYearBonusText(salary);

            if (!isSalaryInExpectedFormat(salary)) {
                return true;
            }

            salary = cleanSalaryText(salary);
            String jobType = detectJobType(salary);
            salary = removeDayUnitIfNeeded(salary);

            Integer[] jobSalaryRange = parseSalaryRange(salary);
            return isSalaryOutOfRange(jobSalaryRange,
                    getMinimumSalary(expectedSalary),
                    getMaximumSalary(expectedSalary),
                    jobType);
        } catch (Exception e) {
            log.error("薪资解析出错", e);
            return true;
        }
    }

    /**
     * 去掉年终奖信息
     */
    private String removeYearBonusText(String salary) {
        if (salary.contains("薪")) {
            return salary.replaceAll("·\\d+薪", "");
        }
        return salary;
    }

    /**
     * 判断薪资格式是否符合预期
     */
    private boolean isSalaryInExpectedFormat(String salaryText) {
        return salaryText.contains("K") || salaryText.contains("k") || salaryText.contains("元/天");
    }

    /**
     * 清理薪资文本
     */
    private String cleanSalaryText(String salaryText) {
        salaryText = salaryText.replace("K", "").replace("k", "");
        int dotIndex = salaryText.indexOf('·');
        if (dotIndex != -1) {
            salaryText = salaryText.substring(0, dotIndex);
        }
        return salaryText;
    }

    /**
     * 判断是否是按天计薪
     */
    private String detectJobType(String salary) {
        if (salary.contains("元/天")) {
            return "day";
        }
        return "month";
    }

    /**
     * 如果是日薪，则去除"元/天"
     */
    private String removeDayUnitIfNeeded(String salary) {
        if (salary.contains("元/天")) {
            return salary.replaceAll("元/天", "");
        }
        return salary;
    }

    /**
     * 解析薪资范围
     */
    private Integer[] parseSalaryRange(String salaryText) {
        try {
            return Arrays.stream(salaryText.split("-"))
                    .map(s -> s.replaceAll("[^0-9]", ""))
                    .map(Integer::parseInt)
                    .toArray(Integer[]::new);
        } catch (Exception e) {
            log.debug("薪资解析失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 检查薪资是否超出范围
     */
    private boolean isSalaryOutOfRange(Integer[] jobSalary, Integer miniSalary, Integer maxSalary, String jobType) {
        if (jobSalary == null) {
            return true;
        }
        if (miniSalary == null) {
            return false;
        }

        if (Objects.equals("day", jobType)) {
            // 期望薪资转为平均每日的工资
            maxSalary = BigDecimal.valueOf(maxSalary).multiply(BigDecimal.valueOf(1000))
                    .divide(BigDecimal.valueOf(21.75), 0, RoundingMode.HALF_UP).intValue();
            miniSalary = BigDecimal.valueOf(miniSalary).multiply(BigDecimal.valueOf(1000))
                    .divide(BigDecimal.valueOf(21.75), 0, RoundingMode.HALF_UP).intValue();
        }

        // 如果职位薪资下限低于期望的最低薪资，返回不符合
        if (jobSalary[1] < miniSalary) {
            return true;
        }
        // 如果职位薪资上限高于期望的最高薪资，返回不符合
        return maxSalary != null && jobSalary[0] > maxSalary;
    }

    private Integer getMinimumSalary(List<Integer> expectedSalary) {
        return expectedSalary != null && !expectedSalary.isEmpty() ? expectedSalary.get(0) : null;
    }

    private Integer getMaximumSalary(List<Integer> expectedSalary) {
        return expectedSalary != null && expectedSalary.size() > 1 ? expectedSalary.get(1) : null;
    }

}
