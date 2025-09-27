package getjobs.modules.liepin.service;

import getjobs.common.dto.ConfigDTO;
import getjobs.common.enums.RecruitmentPlatformEnum;
import getjobs.modules.boss.dto.JobDTO;
import getjobs.common.enums.JobStatusEnum;
import getjobs.repository.entity.JobEntity;
import getjobs.repository.JobRepository;
import getjobs.service.JobService;
import getjobs.service.PlaywrightManager;
import getjobs.service.RecruitmentService;
import getjobs.service.RecruitmentServiceFactory;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 猎聘任务服务
 *
 * @author getjobs
 */
@Slf4j
@Service
public class LiepinTaskService {

    private final PlaywrightManager playwrightManager;

    private final RecruitmentServiceFactory serviceFactory;

    private final JobService jobService;

    private final JobRepository jobRepository;

    private String dataPath;

    private final ConcurrentHashMap<String, TaskStatus> taskStatusMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Date> taskStartTimeMap = new ConcurrentHashMap<>();

    public LiepinTaskService(PlaywrightManager playwrightManager, RecruitmentServiceFactory serviceFactory,
            JobService jobService, JobRepository jobRepository) {
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

    public LoginResult login(ConfigDTO config) {
        String taskId = generateTaskId("login");
        taskStatusMap.put(taskId, TaskStatus.RUNNING);
        taskStartTimeMap.put(taskId, new Date());

        try {
            log.info("开始执行猎聘登录操作，任务ID: {}", taskId);
            playwrightManager.ensureInitialized();
            RecruitmentService liepinService = serviceFactory.getService(RecruitmentPlatformEnum.LIEPIN);
            boolean success = liepinService.login(config);

            LoginResult result = new LoginResult();
            result.setTaskId(taskId);
            result.setSuccess(success);
            result.setMessage(success ? "登录成功" : "登录失败");
            result.setTimestamp(new Date());

            taskStatusMap.put(taskId, success ? TaskStatus.COMPLETED : TaskStatus.FAILED);
            log.info("猎聘登录操作完成，任务ID: {}, 结果: {}", taskId, success ? "成功" : "失败");
            return result;

        } catch (Exception e) {
            log.error("猎聘登录操作执行失败，任务ID: {}", taskId, e);
            taskStatusMap.put(taskId, TaskStatus.FAILED);

            LoginResult result = new LoginResult();
            result.setTaskId(taskId);
            result.setSuccess(false);
            result.setMessage("登录异常: " + e.getMessage());
            result.setTimestamp(new Date());
            return result;
        }
    }

    public CollectResult collectJobs(ConfigDTO config) {
        String taskId = generateTaskId("collect");
        taskStatusMap.put(taskId, TaskStatus.RUNNING);
        taskStartTimeMap.put(taskId, new Date());

        try {
            log.info("开始执行猎聘岗位采集操作，任务ID: {}", taskId);
            playwrightManager.ensureInitialized();
            RecruitmentService liepinService = serviceFactory.getService(RecruitmentPlatformEnum.LIEPIN);
            List<JobDTO> allJobDTOS = liepinService.collectJobs(config);

            int savedCount = 0;
            if (!allJobDTOS.isEmpty()) {
                try {
                    savedCount = jobService.saveJobs(allJobDTOS, RecruitmentPlatformEnum.LIEPIN.name());
                    log.info("成功保存 {} 个岗位到数据库", savedCount);
                } catch (Exception e) {
                    log.error("保存岗位到数据库失败", e);
                }
            }

            CollectResult result = new CollectResult();
            result.setTaskId(taskId);
            result.setJobCount(allJobDTOS.size());
            result.setJobs(allJobDTOS);
            result.setMessage(String.format("成功采集到 %d 个岗位，保存到数据库 %d 个", allJobDTOS.size(), savedCount));
            result.setTimestamp(new Date());

            taskStatusMap.put(taskId, TaskStatus.COMPLETED);
            log.info("猎聘岗位采集操作完成，任务ID: {}, 采集到 {} 个岗位，保存到数据库 {} 个", taskId, allJobDTOS.size(), savedCount);
            return result;

        } catch (Exception e) {
            log.error("猎聘岗位采集操作执行失败，任务ID: {}", taskId, e);
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

    public FilterResult filterJobs(ConfigDTO config) {
        try {
            log.info("开始执行猎聘岗位过滤操作");
            RecruitmentService liepinService = serviceFactory.getService(RecruitmentPlatformEnum.LIEPIN);
            List<JobEntity> allJobEntities = jobService.findAllJobEntitiesByPlatform(RecruitmentPlatformEnum.LIEPIN.getPlatformCode());
            if (allJobEntities == null || allJobEntities.isEmpty()) {
                throw new IllegalArgumentException("数据库中未找到职位数据或职位数据为空");
            }

            List<JobDTO> jobDTOS = allJobEntities.stream().map(jobService::convertToDTO).collect(Collectors.toList());
            List<JobDTO> filteredJobDTOS = liepinService.filterJobs(jobDTOS, config);

            List<String> filteredJobIds = jobDTOS.stream()
                .filter(j -> !filteredJobDTOS.contains(j))
                .map(JobDTO::getEncryptJobId)
                .collect(Collectors.toList());

            if (!filteredJobIds.isEmpty()) {
                jobService.updateJobStatus(filteredJobIds, JobStatusEnum.FILTERED.getCode(), "被过滤");
            }

            FilterResult result = new FilterResult();
            result.setOriginalCount(allJobEntities.size());
            result.setFilteredCount(filteredJobDTOS.size());
            result.setJobs(filteredJobDTOS);
            result.setMessage(String.format("原始岗位 %d 个，过滤后剩余 %d 个，已过滤 %d 个", allJobEntities.size(), filteredJobDTOS.size(), filteredJobIds.size()));
            result.setTimestamp(new Date());

            log.info("猎聘岗位过滤操作完成，原始 {} 个，过滤后 {} 个，已过滤 {} 个", allJobEntities.size(), filteredJobDTOS.size(), filteredJobIds.size());
            return result;

        } catch (Exception e) {
            log.error("猎聘岗位过滤操作执行失败", e);
            FilterResult result = new FilterResult();
            result.setOriginalCount(0);
            result.setFilteredCount(0);
            result.setJobs(new ArrayList<>());
            result.setMessage("过滤异常: " + e.getMessage());
            result.setTimestamp(new Date());
            return result;
        }
    }

    public DeliveryResult deliverJobs(ConfigDTO config, boolean enableActualDelivery) {
        String taskId = generateTaskId("deliver");
        taskStatusMap.put(taskId, TaskStatus.RUNNING);
        taskStartTimeMap.put(taskId, new Date());

        try {
            log.info("开始执行猎聘岗位投递操作，任务ID: {}, 实际投递: {}", taskId, enableActualDelivery);
            List<JobEntity> jobEntities = jobRepository.findByStatusAndPlatform(JobStatusEnum.PENDING.getCode(), RecruitmentPlatformEnum.LIEPIN.getPlatformCode());
            if (jobEntities == null || jobEntities.isEmpty()) {
                throw new IllegalArgumentException("未找到可投递的猎聘岗位记录");
            }

            List<JobDTO> filteredJobDTOS = jobEntities.stream().map(jobService::convertToDTO).collect(Collectors.toList());
            int deliveredCount = 0;

            if (enableActualDelivery) {
                RecruitmentService liepinService = serviceFactory.getService(RecruitmentPlatformEnum.LIEPIN);
                deliveredCount = liepinService.deliverJobs(filteredJobDTOS, config);
                liepinService.saveData(dataPath);
                log.info("实际投递完成，成功投递 {} 个岗位", deliveredCount);
            } else {
                deliveredCount = filteredJobDTOS.size();
                log.info("模拟投递完成，可投递岗位 {} 个", deliveredCount);
            }

            DeliveryResult result = new DeliveryResult();
            result.setTaskId(taskId);
            result.setTotalCount(filteredJobDTOS.size());
            result.setDeliveredCount(deliveredCount);
            result.setActualDelivery(enableActualDelivery);
            result.setMessage(String.format("%s完成，处理 %d 个岗位", enableActualDelivery ? "实际投递" : "模拟投递", deliveredCount));
            result.setTimestamp(new Date());

            if (filteredJobDTOS.size() <= 10) {
                result.setJobDetails(buildJobDetails(filteredJobDTOS));
            }

            taskStatusMap.put(taskId, TaskStatus.COMPLETED);
            log.info("猎聘岗位投递操作完成，任务ID: {}, 处理 {} 个岗位", taskId, deliveredCount);
            return result;

        } catch (Exception e) {
            log.error("猎聘岗位投递操作执行失败，任务ID: {}", taskId, e);
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

    public TaskStatus getTaskStatus(String taskId) {
        return taskStatusMap.get(taskId);
    }

    public void clearTaskData(String taskId) {
        taskStatusMap.remove(taskId);
        taskStartTimeMap.remove(taskId);
    }

    private String generateTaskId(String operation) {
        return operation + "_" + System.currentTimeMillis();
    }

    private List<String> buildJobDetails(List<JobDTO> jobDTOS) {
        return jobDTOS.stream()
                .map(job -> String.format("%s - %s | %s | %s", job.getCompanyName(), job.getJobName(), job.getSalary(), job.getJobArea()))
                .collect(Collectors.toList());
    }

    private void initializeDataFiles() throws IOException {
        String userHome = System.getProperty("user.home");
        dataPath = userHome + File.separator + "getjobs";
        File getJobsDir = new File(dataPath);
        if (!getJobsDir.exists()) {
            getJobsDir.mkdirs();
        }
        File dataDir = new File(dataPath, "data");
        if (!dataDir.exists()) {
            dataDir.mkdirs();
        }
        dataPath = dataDir.getAbsolutePath();
    }

    public enum TaskStatus {
        RUNNING, COMPLETED, FAILED
    }

    public static class LoginResult {
        private String taskId;
        private boolean success;
        private String message;
        private Date timestamp;
        public String getTaskId() { return taskId; }
        public void setTaskId(String taskId) { this.taskId = taskId; }
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public Date getTimestamp() { return timestamp; }
        public void setTimestamp(Date timestamp) { this.timestamp = timestamp; }
    }

    public static class CollectResult {
        private String taskId;
        private int jobCount;
        private List<JobDTO> jobs;
        private String message;
        private Date timestamp;
        public String getTaskId() { return taskId; }
        public void setTaskId(String taskId) { this.taskId = taskId; }
        public int getJobCount() { return jobCount; }
        public void setJobCount(int jobCount) { this.jobCount = jobCount; }
        public List<JobDTO> getJobs() { return jobs; }
        public void setJobs(List<JobDTO> jobs) { this.jobs = jobs; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public Date getTimestamp() { return timestamp; }
        public void setTimestamp(Date timestamp) { this.timestamp = timestamp; }
    }

    public static class FilterResult {
        private String taskId;
        private int originalCount;
        private int filteredCount;
        private List<JobDTO> jobs;
        private String message;
        private Date timestamp;
        public String getTaskId() { return taskId; }
        public void setTaskId(String taskId) { this.taskId = taskId; }
        public int getOriginalCount() { return originalCount; }
        public void setOriginalCount(int originalCount) { this.originalCount = originalCount; }
        public int getFilteredCount() { return filteredCount; }
        public void setFilteredCount(int filteredCount) { this.filteredCount = filteredCount; }
        public List<JobDTO> getJobs() { return jobs; }
        public void setJobs(List<JobDTO> jobs) { this.jobs = jobs; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public Date getTimestamp() { return timestamp; }
        public void setTimestamp(Date timestamp) { this.timestamp = timestamp; }
    }

    public static class DeliveryResult {
        private String taskId;
        private int totalCount;
        private int deliveredCount;
        private boolean actualDelivery;
        private String message;
        private Date timestamp;
        private List<String> jobDetails;
        public String getTaskId() { return taskId; }
        public void setTaskId(String taskId) { this.taskId = taskId; }
        public int getTotalCount() { return totalCount; }
        public void setTotalCount(int totalCount) { this.totalCount = totalCount; }
        public int getDeliveredCount() { return deliveredCount; }
        public void setDeliveredCount(int deliveredCount) { this.deliveredCount = deliveredCount; }
        public boolean isActualDelivery() { return actualDelivery; }
        public void setActualDelivery(boolean actualDelivery) { this.actualDelivery = actualDelivery; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public Date getTimestamp() { return timestamp; }
        public void setTimestamp(Date timestamp) { this.timestamp = timestamp; }
        public List<String> getJobDetails() { return jobDetails; }
        public void setJobDetails(List<String> jobDetails) { this.jobDetails = jobDetails; }
    }
}
