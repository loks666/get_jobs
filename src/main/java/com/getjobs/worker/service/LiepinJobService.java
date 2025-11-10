package com.getjobs.worker.service;

import com.getjobs.application.entity.LiepinConfigEntity;
import com.getjobs.application.service.LiepinDataService;
import com.getjobs.worker.dto.JobProgressMessage;
import com.getjobs.worker.liepin.Liepin;
import com.getjobs.worker.liepin.LiepinConfig;
import com.getjobs.worker.liepin.LiepinEnum;
import com.getjobs.worker.manager.PlaywrightManager;
import com.microsoft.playwright.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 猎聘任务服务
 * 负责猎聘平台的自动投递任务管理
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LiepinJobService implements JobPlatformService {

    private static final String PLATFORM = "liepin";

    private final PlaywrightManager playwrightManager;
    private final LiepinDataService liepinDataService;
    private final ObjectProvider<Liepin> liepinProvider;

    // 运行状态标志
    private volatile boolean isRunning = false;

    // 停止请求标志
    private volatile boolean shouldStop = false;

    @Override
    public void executeDelivery(Consumer<JobProgressMessage> progressCallback) {
        if (isRunning) {
            progressCallback.accept(JobProgressMessage.warning(PLATFORM, "任务已在运行中"));
            return;
        }

        try {
            Page page = playwrightManager.getLiepinPage();
            if (page == null) {
                progressCallback.accept(JobProgressMessage.error(PLATFORM, "猎聘页面未初始化"));
                return;
            }

            if (!playwrightManager.isLoggedIn(PLATFORM)) {
                progressCallback.accept(JobProgressMessage.error(PLATFORM, "请先登录猎聘"));
                return;
            }

            isRunning = true;
            shouldStop = false;

            // 暂停后台登录监控，避免并发访问冲突
            playwrightManager.pauseLiepinMonitoring();

            // 加载配置
            LiepinConfig config = buildWorkerConfig();
            progressCallback.accept(JobProgressMessage.info(PLATFORM, "配置加载成功"));

            progressCallback.accept(JobProgressMessage.info(PLATFORM, "开始投递任务..."));

            // 创建并执行 Bean
            Liepin.ProgressCallback cb = (message, current, total) -> {
                if (current != null && total != null) {
                    progressCallback.accept(JobProgressMessage.progress(PLATFORM, message, current, total));
                } else {
                    progressCallback.accept(JobProgressMessage.info(PLATFORM, message));
                }
            };

            Liepin liepin = liepinProvider.getObject();
            liepin.setPage(page);
            liepin.setConfig(config);
            liepin.setProgressCallback(cb);
            liepin.setShouldStopCallback(this::shouldStop);
            liepin.prepare();

            int deliveredCount = liepin.execute();

            progressCallback.accept(JobProgressMessage.success(PLATFORM,
                String.format("投递任务完成，共发起%d个聊天", deliveredCount)));
        } catch (Exception e) {
            log.error("猎聘投递任务执行失败", e);
            progressCallback.accept(JobProgressMessage.error(PLATFORM, "投递失败: " + e.getMessage()));
        } finally {
            isRunning = false;
            shouldStop = false;
            try {
                playwrightManager.resumeLiepinMonitoring();
            } catch (Exception ignored) {}
        }
    }

    @Override
    public void stopDelivery() {
        if (!isRunning) {
            log.warn("猎聘任务未在运行，无需停止");
            return;
        }
        log.info("收到停止猎聘任务请求");
        shouldStop = true;
    }

    /**
     * 获取任务状态
     */
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

    public boolean shouldStop() {
        return shouldStop;
    }

    private LiepinConfig buildWorkerConfig() {
        LiepinConfigEntity entity = liepinDataService.getFirstConfig();

        LiepinConfig config = new LiepinConfig();

        // 关键词解析：支持逗号、中文逗号、或 [a,b] 格式
        List<String> keywords = new ArrayList<>();
        if (entity != null && entity.getKeywords() != null) {
            String raw = entity.getKeywords().trim();
            raw = raw.replace('，', ',');
            if (raw.startsWith("[") && raw.endsWith("]")) {
                raw = raw.substring(1, raw.length() - 1);
            }
            for (String s : raw.split(",")) {
                String t = s.trim();
                if (!t.isEmpty()) keywords.add(t);
            }
        }
        config.setKeywords(keywords);

        // 城市编码：优先从选项表映射名称->代码；若失败则尝试枚举；默认不限
        String cityCode = "";
        if (entity != null && entity.getCity() != null && !entity.getCity().isEmpty()) {
            cityCode = liepinDataService.getCodeByTypeAndName("city", entity.getCity());
            if (cityCode == null || cityCode.isEmpty()) {
                cityCode = LiepinEnum.CityCode.forValue(entity.getCity()).getCode();
            }
        }
        if (cityCode == null) cityCode = "";
        config.setCityCode(cityCode);

        // 薪资代码
        String salaryCode = entity != null ? entity.getSalaryCode() : null;
        config.setSalary(salaryCode != null ? salaryCode : "");

        return config;
    }
}
