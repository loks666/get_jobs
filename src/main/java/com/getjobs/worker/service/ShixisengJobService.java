package com.getjobs.worker.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.getjobs.application.entity.ShixisengConfigEntity;
import com.getjobs.application.service.ShixisengService;
import com.getjobs.worker.dto.JobProgressMessage;
import com.getjobs.worker.manager.PlaywrightManager;
import com.getjobs.worker.shixiseng.Shixiseng;
import com.getjobs.worker.shixiseng.ShixisengConfig;
import com.microsoft.playwright.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShixisengJobService implements JobPlatformService {

    private static final String PLATFORM = "shixiseng";

    private final PlaywrightManager playwrightManager;
    private final ShixisengService shixisengService;
    private final ObjectProvider<Shixiseng> shixisengProvider;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private volatile boolean isRunning = false;
    private volatile boolean shouldStop = false;

    @Override
    public void executeDelivery(Consumer<JobProgressMessage> progressCallback) {
        if (isRunning) {
            progressCallback.accept(JobProgressMessage.warning(PLATFORM, "任务已在运行中"));
            return;
        }

        try {
            progressCallback.accept(JobProgressMessage.info(PLATFORM, "检查浏览器状态..."));
            playwrightManager.ensureReady();

            Page page = playwrightManager.getShixisengPage();
            if (page == null) {
                progressCallback.accept(JobProgressMessage.error(PLATFORM, "实习僧页面未初始化"));
                return;
            }

            if (!playwrightManager.isLoggedIn(PLATFORM)) {
                progressCallback.accept(JobProgressMessage.error(PLATFORM, "请先登录实习僧"));
                return;
            }

            isRunning = true;
            shouldStop = false;
            playwrightManager.pauseShixisengMonitoring();

            ShixisengConfig config = loadConfig();
            progressCallback.accept(JobProgressMessage.info(PLATFORM, "配置加载成功，开始实习僧投递..."));

            Shixiseng worker = shixisengProvider.getObject();
            worker.setPage(page);
            worker.setConfig(config);
            worker.setProgressCallback((msg, cur, tot) -> {
                if (cur != null && tot != null) {
                    progressCallback.accept(JobProgressMessage.progress(PLATFORM, msg, cur, tot));
                } else {
                    progressCallback.accept(JobProgressMessage.info(PLATFORM, msg));
                }
            });
            worker.setShouldStopCallback(this::shouldStop);

            int deliveredCount = playwrightManager.callOnPlaywright(worker::execute);
            progressCallback.accept(JobProgressMessage.success(PLATFORM,
                    String.format("实习僧投递任务完成，共投递 %d 个岗位", deliveredCount)));
        } catch (Exception e) {
            log.error("实习僧投递任务执行失败", e);
            progressCallback.accept(JobProgressMessage.error(PLATFORM, "投递失败: " + e.getMessage()));
        } finally {
            isRunning = false;
            shouldStop = false;
            try {
                playwrightManager.resumeShixisengMonitoring();
            } catch (Exception ignored) {}
        }
    }

    private ShixisengConfig loadConfig() {
        ShixisengConfigEntity entity = shixisengService.getFirstConfig();
        ShixisengConfig cfg = new ShixisengConfig();
        if (entity == null) {
            cfg.setKeywords(List.of("产品实习生"));
            cfg.setCity("广州");
            cfg.setEnableAi(true);
            return cfg;
        }
        cfg.setCity(entity.getCity() != null ? entity.getCity() : "广州");
        cfg.setEnableAi(entity.getEnableAi() == null || entity.getEnableAi() == 1);
        List<String> kwList = Collections.emptyList();
        if (entity.getKeywords() != null && !entity.getKeywords().isBlank()) {
            String raw = entity.getKeywords().trim();
            if (raw.startsWith("[")) {
                try {
                    kwList = objectMapper.readValue(raw, new TypeReference<>() {});
                } catch (Exception ignored) {}
            } else {
                kwList = List.of(raw.replace("，", ",").split(","));
            }
        }
        cfg.setKeywords(kwList);
        return cfg;
    }

    @Override
    public void stopDelivery() {
        if (!isRunning) {
            log.warn("实习僧任务未在运行，无需停止");
            return;
        }
        log.info("收到停止实习僧任务请求");
        shouldStop = true;
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

    public boolean shouldStop() {
        return shouldStop;
    }
}
