package com.getjobs.application.controller;

import com.getjobs.application.entity.CookieEntity;
import com.getjobs.application.service.CookieService;
import com.getjobs.worker.manager.PlaywrightManager;
import com.getjobs.worker.service.LiepinJobService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 猎聘控制器
 * 提供猎聘自动投递功能的REST API接口
 */
@Slf4j
@RestController
@RequestMapping("/api/liepin")
@CrossOrigin(origins = "*")
public class LiepinController {

    @Autowired
    private LiepinJobService liepinJobService;

    @Autowired
    private PlaywrightManager playwrightManager;

    @Autowired
    private CookieService cookieService;

    /**
     * 检查登录状态
     * @return 登录状态信息
     */
    @GetMapping("/login-status")
    public ResponseEntity<Map<String, Object>> checkLoginStatus() {
        Map<String, Object> response = new HashMap<>();

        try {
            boolean isLoggedIn = playwrightManager.isLoggedIn("liepin");
            response.put("success", true);
            response.put("isLoggedIn", isLoggedIn);
            response.put("message", isLoggedIn ? "已登录" : "未登录");

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("检查猎聘登录状态失败", e);
            response.put("success", false);
            response.put("message", "检查登录状态失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * 启动猎聘自动投递任务
     * @return 响应结果
     */
    @PostMapping("/start")
    public ResponseEntity<Map<String, Object>> startLiepinJob() {
        Map<String, Object> response = new HashMap<>();

        try {
            // 未登录则不允许启动
            if (!playwrightManager.isLoggedIn("liepin")) {
                response.put("success", false);
                response.put("message", "请先登录猎聘");
                response.put("status", "not_logged_in");
                return ResponseEntity.badRequest().body(response);
            }

            // 检查是否已有任务在运行
            if (liepinJobService.isRunning()) {
                response.put("success", false);
                response.put("message", "猎聘任务已在运行中，请等待当前任务完成");
                response.put("status", "running");
                return ResponseEntity.badRequest().body(response);
            }

            // 异步启动新任务
            CompletableFuture.runAsync(() -> {
                liepinJobService.executeDelivery(progressMessage -> {
                    log.info("[{}] {}", progressMessage.getPlatform(), progressMessage.getMessage());
                });
            });

            response.put("success", true);
            response.put("message", "猎聘任务启动成功");
            response.put("status", "started");

            log.info("通过API启动猎聘任务成功");
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("启动猎聘任务失败", e);
            response.put("success", false);
            response.put("message", "启动猎聘任务失败: " + e.getMessage());
            response.put("error", e.getClass().getSimpleName());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * 停止猎聘任务
     * @return 响应结果
     */
    @PostMapping("/stop")
    public ResponseEntity<Map<String, Object>> stopLiepinJob() {
        Map<String, Object> response = new HashMap<>();

        try {
            if (!liepinJobService.isRunning()) {
                response.put("success", false);
                response.put("message", "没有正在运行的猎聘任务");
                return ResponseEntity.badRequest().body(response);
            }

            liepinJobService.stopDelivery();

            response.put("success", true);
            response.put("message", "猎聘任务停止请求已发送");

            log.info("通过API停止猎聘任务");
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("停止猎聘任务失败", e);
            response.put("success", false);
            response.put("message", "停止猎聘任务失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * 获取当前运行状态
     * @return 当前状态信息
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getCurrentStatus() {
        Map<String, Object> response = new HashMap<>();

        try {
            Map<String, Object> status = liepinJobService.getStatus();
            response.put("success", true);
            response.putAll(status);
            response.put("timestamp", System.currentTimeMillis());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("获取猎聘当前状态失败", e);
            response.put("success", false);
            response.put("message", "获取状态失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * 健康检查接口
     * @return 服务状态
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("service", "LiepinController");
        response.put("status", "healthy");
        response.put("timestamp", System.currentTimeMillis());

        return ResponseEntity.ok(response);
    }

    /**
     * 调试接口：读取数据库中的猎聘 Cookie 记录
     */
    @GetMapping("/cookie")
    public ResponseEntity<Map<String, Object>> getLiepinCookieRecord() {
        Map<String, Object> response = new HashMap<>();
        try {
            CookieEntity cookie = cookieService.getCookieByPlatform("liepin");
            Map<String, Object> data = new HashMap<>();
            if (cookie != null) {
                data.put("id", cookie.getId());
                data.put("platform", cookie.getPlatform());
                data.put("cookie_value", cookie.getCookieValue());
                data.put("remark", cookie.getRemark());
                data.put("created_at", cookie.getCreatedAt());
                data.put("updated_at", cookie.getUpdatedAt());
            } else {
                data.put("platform", "liepin");
                data.put("cookie_value", null);
                data.put("message", "未找到猎聘Cookie记录");
            }
            response.put("success", true);
            response.put("data", data);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "读取Cookie记录失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * 退出登录：清空数据库Cookie并清理运行中的上下文Cookie
     */
    @PostMapping("/logout")
    public ResponseEntity<Map<String, Object>> logoutLiepin() {
        Map<String, Object> response = new HashMap<>();
        try {
            // 更新登录状态为未登录并触发SSE通知
            playwrightManager.setLoginStatus("liepin", false);

            // 清空数据库中猎聘平台的所有 Cookie 值
            cookieService.clearCookieByPlatform("liepin", "manual logout");

            // 清理运行中的上下文Cookie
            try {
                playwrightManager.clearLiepinCookies();
            } catch (Exception e) {
                log.warn("清理猎聘上下文Cookie时发生异常，但不影响退出流程: {}", e.getMessage());
            }

            response.put("success", true);
            response.put("message", "猎聘已退出登录，数据库Cookie和上下文Cookie均已清理");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("猎聘退出登录失败", e);
            response.put("success", false);
            response.put("message", "退出登录失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * 调试接口：主动保存当前上下文中的猎聘 Cookie 到数据库
     */
    @PostMapping("/save-cookie")
    public ResponseEntity<Map<String, Object>> saveLiepinCookie() {
        Map<String, Object> response = new HashMap<>();
        try {
            playwrightManager.saveLiepinCookiesToDb("manual save");
            response.put("success", true);
            response.put("message", "已主动保存猎聘Cookie到数据库");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "保存猎聘Cookie失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }
}
