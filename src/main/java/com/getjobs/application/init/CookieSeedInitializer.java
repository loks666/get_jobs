package com.getjobs.application.init;

import com.getjobs.application.entity.CookieEntity;
import com.getjobs.application.service.CookieService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 应用启动时为各平台创建 Cookie 种子记录（若缺失）
 * 目的：确保后续保存操作始终更新同一条记录，避免“无记录无法更新”的情况。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CookieSeedInitializer implements CommandLineRunner {

    private final CookieService cookieService;

    /**
     * 统一平台标识（与控制器/PlaywrightManager 保持一致）
     */
    private static final List<String> PLATFORMS = List.of("boss", "liepin", "51job", "zhilian");

    @Override
    public void run(String... args) {
        for (String platform : PLATFORMS) {
            try {
                CookieEntity existing = cookieService.getCookieByPlatform(platform);
                if (existing == null) {
                    boolean created = cookieService.saveOrUpdateCookie(platform, "", "seed");
                    if (created) {
                        log.info("已为平台 {} 创建 Cookie 种子记录", platform);
                    } else {
                        log.warn("为平台 {} 创建 Cookie 种子记录失败（返回 false）", platform);
                    }
                } else {
                    log.info("平台 {} 已存在 Cookie 记录 (id={}), 跳过种子创建", platform, existing.getId());
                }
            } catch (Exception e) {
                log.warn("为平台 {} 创建 Cookie 种子记录时发生异常: {}", platform, e.getMessage());
            }
        }
    }
}