package com.getjobs.application.controller;

import com.getjobs.application.entity.CookieEntity;
import com.getjobs.application.security.LocalRequestGuard;
import com.getjobs.application.security.SafeCookieRecord;
import com.getjobs.application.service.CookieService;
import com.getjobs.worker.manager.PlaywrightManager;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 统一的 Cookie 读/写控制器
 * 提供：
 * - GET /api/cookie?platform=... 读取指定平台的 Cookie 记录
 * - POST /api/cookie/save?platform=...&remark=... 保存当前上下文 Cookie 到数据库
 */
@Slf4j
@RestController
@RequestMapping("/api/cookie")
@RequiredArgsConstructor
public class CookieController {

    private final CookieService cookieService;
    private final PlaywrightManager playwrightManager;

    private static final Set<String> ALLOWED_PLATFORMS = Set.of("boss", "liepin", "51job", "zhilian");

    @GetMapping
    public ResponseEntity<Map<String, Object>> getCookie(@RequestParam("platform") String platform, HttpServletRequest request) {
        Map<String, Object> response = new HashMap<>();
        try {
            LocalRequestGuard.assertLoopback(request);
            if (!ALLOWED_PLATFORMS.contains(platform)) {
                response.put("success", false);
                response.put("message", "不支持的平台: " + platform);
                return ResponseEntity.badRequest().body(response);
            }

            CookieEntity cookie = cookieService.getCookieByPlatform(platform);
            response.put("success", true);
            response.put("data", SafeCookieRecord.fromEntity(cookie, platform, "未找到Cookie记录"));
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("读取Cookie记录失败", e);
            response.put("success", false);
            response.put("message", "读取Cookie记录失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    @PostMapping("/save")
    public ResponseEntity<Map<String, Object>> saveCookie(
            @RequestParam("platform") String platform,
            @RequestParam(value = "remark", defaultValue = "manual save") String remark,
            HttpServletRequest request
    ) {
        Map<String, Object> response = new HashMap<>();
        try {
            LocalRequestGuard.assertLoopback(request);
            if (!ALLOWED_PLATFORMS.contains(platform)) {
                response.put("success", false);
                response.put("message", "不支持的平台: " + platform);
                return ResponseEntity.badRequest().body(response);
            }

            playwrightManager.saveCookiesToDb(platform, remark);
            response.put("success", true);
            response.put("message", String.format("已主动保存 %s Cookie 到数据库", platform));
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        } catch (Exception e) {
            log.error("保存Cookie失败", e);
            response.put("success", false);
            response.put("message", "保存Cookie失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }
}
