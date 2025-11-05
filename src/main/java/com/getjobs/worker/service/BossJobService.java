package com.getjobs.worker.service;

import com.getjobs.application.service.BlacklistService;
import com.getjobs.worker.boss.Boss;
import com.getjobs.worker.boss.BossConfig;
import com.getjobs.worker.dto.JobProgressMessage;
import com.getjobs.worker.manager.PlaywrightManager;
import com.microsoft.playwright.Page;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Boss直聘任务服务
 * 管理Boss平台的投递任务执行和状态
 */
@Service
@RequiredArgsConstructor
public class BossJobService implements JobPlatformService {
    private static final Logger log = LoggerFactory.getLogger(BossJobService.class);
    private static final String PLATFORM = "boss";

    private final PlaywrightManager playwrightManager;
    private final BlacklistService blacklistService;

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

        isRunning = true;
        shouldStop = false;

        try {
            // 获取Boss页面实例
            Page page = playwrightManager.getBossPage();
            if (page == null) {
                progressCallback.accept(JobProgressMessage.error(PLATFORM, "Boss页面未初始化"));
                return;
            }

            // 检查是否已登录
            if (!playwrightManager.isLoggedIn(PLATFORM)) {
                progressCallback.accept(JobProgressMessage.warning(PLATFORM, "请先登录Boss直聘"));
                return;
            }

            // 加载配置
            BossConfig config = BossConfig.init();
            progressCallback.accept(JobProgressMessage.info(PLATFORM, "配置加载成功"));

            // 从数据库加载黑名单
            Set<String> blackCompanies = blacklistService.getBlackCompanies();
            Set<String> blackRecruiters = blacklistService.getBlackRecruiters();
            Set<String> blackJobs = blacklistService.getBlackJobs();
            progressCallback.accept(JobProgressMessage.info(PLATFORM,
                String.format("黑名单加载成功: 公司(%d) 招聘者(%d) 职位(%d)",
                    blackCompanies.size(), blackRecruiters.size(), blackJobs.size())));

            progressCallback.accept(JobProgressMessage.info(PLATFORM, "开始投递任务..."));

            // 创建Boss实例并执行投递
            Boss.ProgressCallback bossCallback = (message, current, total) -> {
                if (current != null && total != null) {
                    progressCallback.accept(JobProgressMessage.progress(PLATFORM, message, current, total));
                } else {
                    progressCallback.accept(JobProgressMessage.info(PLATFORM, message));
                }
            };

            Boss boss = new Boss(page, config, blackCompanies, blackRecruiters, blackJobs,
                                bossCallback, this::shouldStop);

            int deliveredCount = boss.execute();

            // 保存更新后的黑名单到数据库（Boss类可能在运行过程中更新了黑名单）
            // saveBlacklistToDatabase(boss.getBlackCompanies(), boss.getBlackRecruiters(), boss.getBlackJobs());

            progressCallback.accept(JobProgressMessage.success(PLATFORM,
                String.format("投递任务完成，共发起%d个聊天", deliveredCount)));
        } catch (Exception e) {
            log.error("Boss投递任务执行失败", e);
            progressCallback.accept(JobProgressMessage.error(PLATFORM, "投递失败: " + e.getMessage()));
        } finally {
            isRunning = false;
            shouldStop = false;
        }
    }

    /**
     * 保存黑名单到数据库
     */
    private void saveBlacklistToDatabase(Set<String> companies, Set<String> recruiters, Set<String> jobs) {
        // 清空现有数据
        blacklistService.getAllBlacklist().forEach(entity ->
            blacklistService.removeBlacklist(entity.getType(), entity.getValue()));

        // 保存新数据
        blacklistService.addBlacklistBatch("company", companies);
        blacklistService.addBlacklistBatch("recruiter", recruiters);
        blacklistService.addBlacklistBatch("job", jobs);
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
}
