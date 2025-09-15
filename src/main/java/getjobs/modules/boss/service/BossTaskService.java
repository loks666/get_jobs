package getjobs.modules.boss.service;

import getjobs.modules.boss.dto.BossConfigDTO;
import getjobs.enums.RecruitmentPlatformEnum;
import getjobs.modules.boss.dto.JobDTO;
import getjobs.modules.boss.enums.JobStatusEnum;
import getjobs.repository.entity.JobEntity;
import getjobs.repository.JobRepository;
import getjobs.service.JobService;
import getjobs.service.PlaywrightManager;
import getjobs.service.RecruitmentService;
import getjobs.service.RecruitmentServiceFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * Boss任务服务 - 将4个核心操作分离为独立的服务方法
 * 
 * @author loks666
 *         项目链接: <a href=
 *         "https://github.com/loks666/get_jobs">https://github.com/loks666/get_jobs</a>
 */
@Slf4j
@Service
public class BossTaskService {

    private final PlaywrightManager playwrightManager;

    private final RecruitmentServiceFactory serviceFactory;

    private final JobService jobService;

    private final JobRepository jobRepository;

    // 数据目录路径
    private String dataPath;

    // 存储任务执行状态和结果
    private final ConcurrentHashMap<String, TaskStatus> taskStatusMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Date> taskStartTimeMap = new ConcurrentHashMap<>();

    public BossTaskService(PlaywrightManager playwrightManager, RecruitmentServiceFactory serviceFactory, JobService jobService, JobRepository jobRepository) {
        this.playwrightManager = playwrightManager;
        this.serviceFactory = serviceFactory;
        this.jobService = jobService;
        this.jobRepository = jobRepository;
    }

    @PostConstruct
    public void init() {
        try {
            initializeDataFiles();
        } catch (IOException e) {
            log.error("数据文件初始化失败", e);
        }
    }

    /**
     * 1. 登录操作
     * 
     * @param config 配置信息
     * @return 登录结果
     */
    public LoginResult login(BossConfigDTO config) {
        String taskId = generateTaskId("login");
        taskStatusMap.put(taskId, TaskStatus.RUNNING);
        taskStartTimeMap.put(taskId, new Date());

        try {
            log.info("开始执行登录操作，任务ID: {}", taskId);

            // 确保Playwright已初始化
            playwrightManager.ensureInitialized();

            // 获取Boss直聘服务
            RecruitmentService bossService = serviceFactory.getService(RecruitmentPlatformEnum.BOSS_ZHIPIN);

            // 执行登录
            boolean success = bossService.login(config);

            LoginResult result = new LoginResult();
            result.setTaskId(taskId);
            result.setSuccess(success);
            result.setMessage(success ? "登录成功" : "登录失败");
            result.setTimestamp(new Date());

            taskStatusMap.put(taskId, success ? TaskStatus.COMPLETED : TaskStatus.FAILED);

            log.info("登录操作完成，任务ID: {}, 结果: {}", taskId, success ? "成功" : "失败");
            return result;

        } catch (Exception e) {
            log.error("登录操作执行失败，任务ID: {}", taskId, e);
            taskStatusMap.put(taskId, TaskStatus.FAILED);

            LoginResult result = new LoginResult();
            result.setTaskId(taskId);
            result.setSuccess(false);
            result.setMessage("登录异常: " + e.getMessage());
            result.setTimestamp(new Date());
            return result;
        }
    }

    /**
     * 2. 采集操作
     * 
     * @param config 配置信息
     * @return 采集结果
     */
    public CollectResult collectJobs(BossConfigDTO config) {
        String taskId = generateTaskId("collect");
        taskStatusMap.put(taskId, TaskStatus.RUNNING);
        taskStartTimeMap.put(taskId, new Date());

        try {
            log.info("开始执行岗位采集操作，任务ID: {}", taskId);

            // 确保Playwright已初始化
            playwrightManager.ensureInitialized();

            // 获取Boss直聘服务
            RecruitmentService bossService = serviceFactory.getService(RecruitmentPlatformEnum.BOSS_ZHIPIN);

            // 采集岗位
            List<JobDTO> allJobDTOS = new ArrayList<>();

            // 采集搜索岗位
            List<JobDTO> searchJobDTOS = bossService.collectJobs(config);
            allJobDTOS.addAll(searchJobDTOS);

            // 采集推荐岗位（如果配置开启）
            if (config.getRecommendJobs()) {
                List<JobDTO> recommendJobDTOS = bossService.collectRecommendJobs(config);
                allJobDTOS.addAll(recommendJobDTOS);
            }

            // 保存到数据库
            int savedCount = 0;
            if (!allJobDTOS.isEmpty()) {
                try {
                    savedCount = jobService.saveJobs(allJobDTOS, RecruitmentPlatformEnum.BOSS_ZHIPIN.name());
                    log.info("成功保存 {} 个岗位到数据库", savedCount);
                } catch (Exception e) {
                    log.error("保存岗位到数据库失败", e);
                    // 即使数据库保存失败，也不影响采集结果的返回
                }
            }

            CollectResult result = new CollectResult();
            result.setTaskId(taskId);
            result.setJobCount(allJobDTOS.size());
            result.setJobs(allJobDTOS);
            result.setMessage(String.format("成功采集到 %d 个岗位，保存到数据库 %d 个", allJobDTOS.size(), savedCount));
            result.setTimestamp(new Date());

            taskStatusMap.put(taskId, TaskStatus.COMPLETED);

            log.info("岗位采集操作完成，任务ID: {}, 采集到 {} 个岗位，保存到数据库 {} 个", taskId, allJobDTOS.size(), savedCount);
            return result;

        } catch (Exception e) {
            log.error("岗位采集操作执行失败，任务ID: {}", taskId, e);
            taskStatusMap.put(taskId, TaskStatus.FAILED);

            CollectResult result = new CollectResult();
            result.setTaskId(taskId);
            result.setJobCount(0);
            result.setJobs(new ArrayList<>());
            result.setMessage("采集异常: " + e.getMessage());
            result.setTimestamp(new Date());
            return result;
        }
    }

    /**
     * 3. 过滤操作
     * 
     * @param config 配置信息
     * @return 过滤结果
     */
    public FilterResult filterJobs(BossConfigDTO config) {
        try {
            log.info("开始执行岗位过滤操作");

            // 直接从数据库查询所有职位实体
            List<JobEntity> allJobEntities = jobService.findAllJobEntitiesByPlatform("BOSS直聘");
            if (allJobEntities == null || allJobEntities.isEmpty()) {
                throw new IllegalArgumentException("数据库中未找到职位数据或职位数据为空");
            }

            // 执行过滤逻辑，获取过滤原因
            List<JobDTO> filteredJobDTOS = new ArrayList<>();
            List<Long> filteredJobIds = new ArrayList<>();
            List<String> filterReasons = new ArrayList<>();

            for (JobEntity entity : allJobEntities) {
                JobDTO job = jobService.convertToDTO(entity);
                String filterReason = getFilterReason(job, config);
                if (filterReason == null) {
                    // 通过过滤
                    filteredJobDTOS.add(job);
                } else {
                    // 被过滤，记录原因
                    filteredJobIds.add(entity.getId());
                    filterReasons.add(filterReason);
                }
            }

            // 批量更新被过滤的职位状态
            if (!filteredJobIds.isEmpty()) {
                // 按过滤原因分组更新
                Map<String, List<Long>> reasonGroups = new HashMap<>();
                for (int i = 0; i < filteredJobIds.size(); i++) {
                    String reason = filterReasons.get(i);
                    Long jobId = filteredJobIds.get(i);
                    reasonGroups.computeIfAbsent(reason, k -> new ArrayList<>()).add(jobId);
                }

                for (Map.Entry<String, List<Long>> entry : reasonGroups.entrySet()) {
                    jobService.updateJobStatus(entry.getValue(), JobStatusEnum.FILTERED.getCode(), entry.getKey()); // 3表示已过滤状态
                }
            }

            FilterResult result = new FilterResult();
            result.setTaskId(null); // 不再使用任务ID
            result.setOriginalCount(allJobEntities.size());
            result.setFilteredCount(filteredJobDTOS.size());
            result.setJobs(filteredJobDTOS);
            result.setMessage(String.format("原始岗位 %d 个，过滤后剩余 %d 个，已过滤 %d 个",
                    allJobEntities.size(), filteredJobDTOS.size(), filteredJobIds.size()));
            result.setTimestamp(new Date());

            log.info("岗位过滤操作完成，原始 {} 个，过滤后 {} 个，已过滤 {} 个",
                    allJobEntities.size(), filteredJobDTOS.size(), filteredJobIds.size());
            return result;

        } catch (Exception e) {
            log.error("岗位过滤操作执行失败", e);

            FilterResult result = new FilterResult();
            result.setTaskId(null);
            result.setOriginalCount(0);
            result.setFilteredCount(0);
            result.setJobs(new ArrayList<>());
            result.setMessage("过滤异常: " + e.getMessage());
            result.setTimestamp(new Date());
            return result;
        }
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

        return null; // 通过所有过滤条件
    }

    /**
     * 检查岗位是否在黑名单中
     */
    private boolean isJobInBlacklist(JobDTO jobDTO) {
        List<String> blackJobs = Arrays.asList("销售", "客服", "推广"); // 可以从配置中读取
        return blackJobs.stream().anyMatch(blackJob -> jobDTO.getJobName().contains(blackJob));
    }

    /**
     * 检查公司是否在黑名单中
     */
    private boolean isCompanyInBlacklist(JobDTO jobDTO) {
        List<String> blackCompanies = Arrays.asList("外包", "派遣"); // 可以从配置中读取
        return blackCompanies.stream().anyMatch(blackCompany -> jobDTO.getCompanyName().contains(blackCompany));
    }

    /**
     * 检查招聘者是否在黑名单中
     */
    private boolean isRecruiterInBlacklist(JobDTO jobDTO) {
        List<String> blackRecruiters = Arrays.asList(); // 可以从配置中读取
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
            // 简化的薪资检查逻辑
            if (salary.contains("面议") || salary.contains("不限")) {
                return false;
            }

            // 提取薪资数字进行简单比较
            String numbers = salary.replaceAll("[^0-9]", "");
            if (numbers.length() >= 2) {
                int salaryValue = Integer.parseInt(numbers.substring(0, Math.min(4, numbers.length())));
                int minExpected = expectedSalary.get(0);
                return salaryValue < minExpected;
            }

            return false;
        } catch (Exception e) {
            log.debug("薪资解析失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 4. 投递操作
     * 
     * @param config               配置信息
     * @param enableActualDelivery 是否启用实际投递
     * @return 投递结果
     */
    public DeliveryResult deliverJobs(BossConfigDTO config, boolean enableActualDelivery) {
        String taskId = generateTaskId("deliver");
        taskStatusMap.put(taskId, TaskStatus.RUNNING);
        taskStartTimeMap.put(taskId, new Date());

        try {
            log.info("开始执行岗位投递操作，任务ID: {}, 实际投递: {}",
                    taskId, enableActualDelivery);

            // 从数据库获取未过滤的岗位记录（status != 3）
            List<JobEntity> jobEntities = jobRepository.findByStatusNot(3);
            if (jobEntities == null || jobEntities.isEmpty()) {
                throw new IllegalArgumentException("未找到可投递的岗位记录，数据库中所有岗位都已被过滤");
            }

            // 转换为JobDTO
            List<JobDTO> filteredJobDTOS = jobEntities.stream()
                    .map(jobService::convertToDTO)
                    .collect(Collectors.toList());

            int deliveredCount = 0;

            if (enableActualDelivery) {
                // 获取Boss直聘服务
                RecruitmentService bossService = serviceFactory.getService(RecruitmentPlatformEnum.BOSS_ZHIPIN);

                // 执行实际投递
                deliveredCount = bossService.deliverJobs(filteredJobDTOS, config);

                // 保存数据
                bossService.saveData(dataPath);

                log.info("实际投递完成，成功投递 {} 个岗位", deliveredCount);
            } else {
                // 仅模拟投递
                deliveredCount = filteredJobDTOS.size();
                log.info("模拟投递完成，可投递岗位 {} 个", deliveredCount);
            }

            DeliveryResult result = new DeliveryResult();
            result.setTaskId(taskId);
            result.setTotalCount(filteredJobDTOS.size());
            result.setDeliveredCount(deliveredCount);
            result.setActualDelivery(enableActualDelivery);
            result.setMessage(String.format("%s完成，处理 %d 个岗位",
                    enableActualDelivery ? "实际投递" : "模拟投递", deliveredCount));
            result.setTimestamp(new Date());

            // 显示岗位详情
            if (filteredJobDTOS.size() <= 10) {
                result.setJobDetails(buildJobDetails(filteredJobDTOS));
            }

            taskStatusMap.put(taskId, TaskStatus.COMPLETED);

            log.info("岗位投递操作完成，任务ID: {}, 处理 {} 个岗位", taskId, deliveredCount);
            return result;

        } catch (Exception e) {
            log.error("岗位投递操作执行失败，任务ID: {}", taskId, e);
            taskStatusMap.put(taskId, TaskStatus.FAILED);

            DeliveryResult result = new DeliveryResult();
            result.setTaskId(taskId);
            result.setTotalCount(0);
            result.setDeliveredCount(0);
            result.setActualDelivery(enableActualDelivery);
            result.setMessage("投递异常: " + e.getMessage());
            result.setTimestamp(new Date());
            return result;
        }
    }

    /**
     * 获取任务状态
     * 
     * @param taskId 任务ID
     * @return 任务状态
     */
    public TaskStatus getTaskStatus(String taskId) {
        return taskStatusMap.get(taskId);
    }


    /**
     * 清理任务数据
     * 
     * @param taskId 任务ID
     */
    public void clearTaskData(String taskId) {
        taskStatusMap.remove(taskId);
        taskStartTimeMap.remove(taskId);
    }

    private String generateTaskId(String operation) {
        return operation + "_" + System.currentTimeMillis();
    }

    private List<String> buildJobDetails(List<JobDTO> jobDTOS) {
        return jobDTOS.stream()
                .map(job -> String.format("%s - %s | %s | %s",
                        job.getCompanyName(),
                        job.getJobName(),
                        job.getSalary() != null ? job.getSalary() : "薪资未知",
                        job.getJobArea() != null ? job.getJobArea() : "地区未知"))
                .collect(Collectors.toList());
    }

    private void initializeDataFiles() throws IOException {
        // 初始化工作目录为用户home目录下的getjobs目录
        String userHome = System.getProperty("user.home");
        dataPath = userHome + File.separator + "getjobs";

        log.info("初始化工作目录: {}", dataPath);

        // 检查getjobs目录是否存在，不存在则创建
        File getJobsDir = new File(dataPath);
        if (!getJobsDir.exists()) {
            boolean created = getJobsDir.mkdirs();
            if (created) {
                log.info("成功创建getjobs目录: {}", dataPath);
            } else {
                log.error("创建getjobs目录失败: {}", dataPath);
                throw new IOException("无法创建getjobs目录: " + dataPath);
            }
        } else {
            log.info("getjobs目录已存在: {}", dataPath);
        }

        // 检查并创建data子目录
        File dataDir = new File(dataPath, "data");
        if (!dataDir.exists()) {
            boolean created = dataDir.mkdirs();
            if (created) {
                log.info("成功创建data子目录: {}", dataDir.getAbsolutePath());
            } else {
                log.error("创建data子目录失败: {}", dataDir.getAbsolutePath());
                throw new IOException("无法创建data子目录: " + dataDir.getAbsolutePath());
            }
        } else {
            log.info("data子目录已存在: {}", dataDir.getAbsolutePath());
        }

        // 更新dataPath为实际的data目录路径
        dataPath = dataDir.getAbsolutePath();
        log.info("数据文件目录设置为: {}", dataPath);
    }

    // 内部类定义
    public enum TaskStatus {
        RUNNING, COMPLETED, FAILED
    }

    public static class LoginResult {
        private String taskId;
        private boolean success;
        private String message;
        private Date timestamp;

        // getters and setters
        public String getTaskId() {
            return taskId;
        }

        public void setTaskId(String taskId) {
            this.taskId = taskId;
        }

        public boolean isSuccess() {
            return success;
        }

        public void setSuccess(boolean success) {
            this.success = success;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public Date getTimestamp() {
            return timestamp;
        }

        public void setTimestamp(Date timestamp) {
            this.timestamp = timestamp;
        }
    }

    public static class CollectResult {
        private String taskId;
        private int jobCount;
        private List<JobDTO> jobDTOS;
        private String message;
        private Date timestamp;

        // getters and setters
        public String getTaskId() {
            return taskId;
        }

        public void setTaskId(String taskId) {
            this.taskId = taskId;
        }

        public int getJobCount() {
            return jobCount;
        }

        public void setJobCount(int jobCount) {
            this.jobCount = jobCount;
        }

        public List<JobDTO> getJobs() {
            return jobDTOS;
        }

        public void setJobs(List<JobDTO> jobDTOS) {
            this.jobDTOS = jobDTOS;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public Date getTimestamp() {
            return timestamp;
        }

        public void setTimestamp(Date timestamp) {
            this.timestamp = timestamp;
        }
    }

    public static class FilterResult {
        private String taskId;
        private int originalCount;
        private int filteredCount;
        private List<JobDTO> jobDTOS;
        private String message;
        private Date timestamp;

        // getters and setters
        public String getTaskId() {
            return taskId;
        }

        public void setTaskId(String taskId) {
            this.taskId = taskId;
        }

        public int getOriginalCount() {
            return originalCount;
        }

        public void setOriginalCount(int originalCount) {
            this.originalCount = originalCount;
        }

        public int getFilteredCount() {
            return filteredCount;
        }

        public void setFilteredCount(int filteredCount) {
            this.filteredCount = filteredCount;
        }

        public List<JobDTO> getJobs() {
            return jobDTOS;
        }

        public void setJobs(List<JobDTO> jobDTOS) {
            this.jobDTOS = jobDTOS;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public Date getTimestamp() {
            return timestamp;
        }

        public void setTimestamp(Date timestamp) {
            this.timestamp = timestamp;
        }
    }

    public static class DeliveryResult {
        private String taskId;
        private int totalCount;
        private int deliveredCount;
        private boolean actualDelivery;
        private String message;
        private Date timestamp;
        private List<String> jobDetails;

        // getters and setters
        public String getTaskId() {
            return taskId;
        }

        public void setTaskId(String taskId) {
            this.taskId = taskId;
        }

        public int getTotalCount() {
            return totalCount;
        }

        public void setTotalCount(int totalCount) {
            this.totalCount = totalCount;
        }

        public int getDeliveredCount() {
            return deliveredCount;
        }

        public void setDeliveredCount(int deliveredCount) {
            this.deliveredCount = deliveredCount;
        }

        public boolean isActualDelivery() {
            return actualDelivery;
        }

        public void setActualDelivery(boolean actualDelivery) {
            this.actualDelivery = actualDelivery;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public Date getTimestamp() {
            return timestamp;
        }

        public void setTimestamp(Date timestamp) {
            this.timestamp = timestamp;
        }

        public List<String> getJobDetails() {
            return jobDetails;
        }

        public void setJobDetails(List<String> jobDetails) {
            this.jobDetails = jobDetails;
        }
    }
}
