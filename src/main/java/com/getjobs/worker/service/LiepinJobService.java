package com.getjobs.worker.service;

import com.getjobs.application.entity.LiepinConfigEntity;
import com.getjobs.application.service.ConfigService;
import com.getjobs.application.service.LiepinDataService;
import com.getjobs.worker.dto.JobProgressMessage;
import com.getjobs.worker.liepin.Liepin;
import com.getjobs.worker.liepin.LiepinConfig;
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
    private final ConfigService configService;
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

            // 加载配置（统一从 config 表读取）
            LiepinConfig config = buildLiepinConfigFromConfigService();
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

    private LiepinConfig buildLiepinConfigFromConfigService() {
        LiepinConfig config = new LiepinConfig();

        // 关键词解析：支持逗号、中文逗号、或 [a,b] 格式
        String rawKeywords = configService.getConfigValue("liepin.keywords");
        List<String> keywords = new ArrayList<>();
        if (rawKeywords != null && !rawKeywords.isBlank()) {
            String raw = rawKeywords.trim().replace('，', ',');
            if (raw.startsWith("[") && raw.endsWith("]")) {
                raw = raw.substring(1, raw.length() - 1);
            }
            for (String s : raw.split(",")) {
                String t = s.trim();
                if (!t.isEmpty()) keywords.add(t);
            }
        }
        config.setKeywords(keywords);

        // 城市编码：从中文名映射到代码（必需映射成功）；为空视为不限
        String cityNameOrCode = configService.getConfigValue("liepin.city");
        String cityCode = "";
        if (cityNameOrCode != null && !cityNameOrCode.isBlank()) {
            // 优先按中文名映射；若失败，尝试作为代码验证存在
            String codeByName = liepinDataService.getCodeByTypeAndName("city", cityNameOrCode.trim());
            if (codeByName == null || codeByName.isEmpty()) {
                // 若提供的是代码，保持原值；否则报错
                String maybeName = liepinDataService.getNameByTypeAndCode("city", cityNameOrCode.trim());
                if (maybeName == null || maybeName.isEmpty() || maybeName.equals(cityNameOrCode.trim())) {
                    // 无法确认映射
                    throw new IllegalArgumentException("未在数据库中找到城市编码: " + cityNameOrCode);
                } else {
                    cityCode = cityNameOrCode.trim();
                }
            } else {
                cityCode = codeByName;
            }
        }
        config.setCityCode(cityCode);

        // 薪资代码：支持直接提供代码或中文名映射
        String salaryValue = configService.getConfigValue("liepin.salaryCode");
        if (salaryValue == null || salaryValue.isBlank()) {
            config.setSalary("");
        } else {
            String codeByName = liepinDataService.getCodeByTypeAndName("salary", salaryValue.trim());
            config.setSalary((codeByName != null && !codeByName.isEmpty()) ? codeByName : salaryValue.trim());
        }

        return config;
    }
}
