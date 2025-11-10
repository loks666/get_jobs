package com.getjobs.worker.service;

import com.getjobs.application.service.BossDataService;
import com.getjobs.application.service.ConfigService;
import com.getjobs.worker.boss.Boss;
import com.getjobs.worker.boss.BossConfig;
import com.getjobs.worker.dto.JobProgressMessage;
import com.getjobs.worker.manager.PlaywrightManager;
import com.microsoft.playwright.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Boss直聘任务服务
 * 管理Boss平台的投递任务执行和状态
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BossJobService implements JobPlatformService {
    private static final String PLATFORM = "boss";

    private final PlaywrightManager playwrightManager;
    private final BossDataService bossDataService;
    private final ConfigService configService;
    private final ObjectProvider<Boss> bossProvider;

    // 任务运行状态
    private volatile boolean isRunning = false;
    // 停止标志
    private volatile boolean shouldStop = false;

    @Override
    public void executeDelivery(Consumer<JobProgressMessage> progressCallback) {
        if (isRunning) {
            progressCallback.accept(JobProgressMessage.warning(PLATFORM, "任务已在运行中"));
            return;
        }

        try {
            // 获取Boss页面实例
            Page page = playwrightManager.getBossPage();
            if (page == null) {
                progressCallback.accept(JobProgressMessage.error(PLATFORM, "Boss页面未初始化"));
                return;
            }

            // 检查是否已登录
            if (!playwrightManager.isLoggedIn(PLATFORM)) {
                progressCallback.accept(JobProgressMessage.error(PLATFORM, "请先登录Boss直聘"));
                return;
            }

            // 通过校验后再标记运行
            isRunning = true;
            shouldStop = false;

            // 暂停后台登录监控，避免与投递流程并发访问同一Page
            playwrightManager.pauseBossMonitoring();

            // 加载配置（统一从 config 表读取）
            BossConfig config = buildBossConfigFromConfigService();
            progressCallback.accept(JobProgressMessage.info(PLATFORM, "配置加载成功"));

            progressCallback.accept(JobProgressMessage.info(PLATFORM, "开始投递任务..."));

            // 创建Boss实例并执行投递
            Boss.ProgressCallback bossCallback = (message, current, total) -> {
                if (current != null && total != null) {
                    progressCallback.accept(JobProgressMessage.progress(PLATFORM, message, current, total));
                } else {
                    progressCallback.accept(JobProgressMessage.info(PLATFORM, message));
                }
            };

            Boss boss = bossProvider.getObject();
            boss.setPage(page);
            boss.setConfig(config);
            boss.setProgressCallback(bossCallback);
            boss.setShouldStopCallback(this::shouldStop);
            boss.prepare();

            int deliveredCount = boss.execute();

            progressCallback.accept(JobProgressMessage.success(PLATFORM,
                String.format("投递任务完成，共发起%d个聊天", deliveredCount)));
        } catch (Exception e) {
            log.error("Boss投递任务执行失败", e);
            progressCallback.accept(JobProgressMessage.error(PLATFORM, "投递失败: " + e.getMessage()));
        } finally {
            isRunning = false;
            shouldStop = false;
            // 恢复后台登录监控
            try {
                playwrightManager.resumeBossMonitoring();
            } catch (Exception ignored) {}
        }
    }

    @Override
    public void stopDelivery() {
        if (isRunning) {
            log.info("收到停止Boss投递任务的请求");
            shouldStop = true;
        }
    }

    @Override
    public Map<String, Object> getStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("platform", PLATFORM);
        status.put("isRunning", isRunning);
        status.put("isLoggedIn", playwrightManager.isLoggedIn(PLATFORM));
        return status;
    }

    @Override
    public String getPlatformName() {
        return PLATFORM;
    }

    @Override
    public boolean isRunning() {
        return isRunning;
    }

    /**
     * 检查是否应该停止
     * 供Boss.java调用
     */
    public boolean shouldStop() {
        return shouldStop;
    }

    private BossConfig buildBossConfigFromConfigService() {
        BossConfig config = new BossConfig();

        // 文本与布尔/数值
        String sayHi = configService.getConfigValue("boss.sayHi");
        config.setSayHi(sayHi);

        String debugger = configService.getConfigValue("boss.debugger");
        config.setDebugger(debugger != null && ("1".equals(debugger.trim()) || "true".equalsIgnoreCase(debugger.trim())));

        String enableAI = configService.getConfigValue("boss.enableAI");
        config.setEnableAI(enableAI != null && ("1".equals(enableAI.trim()) || "true".equalsIgnoreCase(enableAI.trim())));

        String filterDeadHR = configService.getConfigValue("boss.filterDeadHR");
        config.setFilterDeadHR(filterDeadHR != null && ("1".equals(filterDeadHR.trim()) || "true".equalsIgnoreCase(filterDeadHR.trim())));

        String sendImgResume = configService.getConfigValue("boss.sendImgResume");
        config.setSendImgResume(sendImgResume != null && ("1".equals(sendImgResume.trim()) || "true".equalsIgnoreCase(sendImgResume.trim())));

        String waitTime = configService.getConfigValue("boss.waitTime");
        config.setWaitTime((waitTime != null && !waitTime.isBlank()) ? waitTime.trim() : null);

        // 关键词
        String rawKeywords = configService.getConfigValue("boss.keywords");
        config.setKeywords(bossDataService.parseListString(rawKeywords));

        // 城市/行业/经验/学历/规模/阶段 -> 统一为代码列表
        config.setCityCode(bossDataService.toCodes("city", bossDataService.parseListString(configService.getConfigValue("boss.city"))));
        config.setIndustry(bossDataService.toCodes("industry", bossDataService.parseListString(configService.getConfigValue("boss.industry"))));
        config.setExperience(bossDataService.toCodes("experience", bossDataService.parseListString(configService.getConfigValue("boss.experience"))));
        config.setDegree(bossDataService.toCodes("degree", bossDataService.parseListString(configService.getConfigValue("boss.degree"))));
        config.setScale(bossDataService.toCodes("scale", bossDataService.parseListString(configService.getConfigValue("boss.scale"))));
        config.setStage(bossDataService.toCodes("stage", bossDataService.parseListString(configService.getConfigValue("boss.stage"))));

        // 职位类型：取首个代码，空则不限
        java.util.List<String> jobTypeCodes = bossDataService.toCodes("jobType",
                bossDataService.parseListString(configService.getConfigValue("boss.jobType")));
        String jobTypeCode = jobTypeCodes.isEmpty() ? com.getjobs.worker.utils.Constant.UNLIMITED_CODE : jobTypeCodes.get(0);
        config.setJobType(jobTypeCode);

        // 薪资代码列表
        config.setSalary(bossDataService.toCodes("salary", bossDataService.parseListString(configService.getConfigValue("boss.salary"))));

        // 期望薪资（min,max）
        String expectedMin = configService.getConfigValue("boss.expectedSalaryMin");
        String expectedMax = configService.getConfigValue("boss.expectedSalaryMax");
        if ((expectedMin != null && !expectedMin.isBlank()) || (expectedMax != null && !expectedMax.isBlank())) {
            int min = 0;
            int max = 0;
            try { if (expectedMin != null && !expectedMin.isBlank()) min = Integer.parseInt(expectedMin.trim()); } catch (Exception ignored) {}
            try { if (expectedMax != null && !expectedMax.isBlank()) max = Integer.parseInt(expectedMax.trim()); } catch (Exception ignored) {}
            config.setExpectedSalary(java.util.Arrays.asList(min, max));
        }

        // HR不在线状态
        config.setDeadStatus(bossDataService.parseListString(configService.getConfigValue("boss.deadStatus")));

        return config;
    }
}
