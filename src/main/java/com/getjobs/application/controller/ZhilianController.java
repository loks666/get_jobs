package com.getjobs.application.controller;

import com.getjobs.application.entity.CookieEntity;
import com.getjobs.application.service.CookieService;
import com.getjobs.worker.manager.PlaywrightManager;
import com.getjobs.worker.service.ZhilianJobService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 智联招聘控制器
 * 提供智联招聘平台Cookie管理和登录状态控制的REST API接口
 */
@Slf4j
@RestController
@RequestMapping("/api/zhilian")
@CrossOrigin(origins = "*")
public class ZhilianController {

    @Autowired
    private PlaywrightManager playwrightManager;

    @Autowired
    private CookieService cookieService;

    @Autowired
    private ZhilianJobService zhilianJobService;

    /**
     * 检查登录状态
     * @return 登录状态信息
     */
    @GetMapping("/login-status")
    public ResponseEntity<Map<String, Object>> checkLoginStatus() {
        Map<String, Object> response = new HashMap<>();

        try {
            boolean isLoggedIn = playwrightManager.isLoggedIn("zhilian");
            response.put("success", true);
            response.put("isLoggedIn", isLoggedIn);
            response.put("message", isLoggedIn ? "已登录" : "未登录");

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("检查登录状态失败", e);
            response.put("success", false);
            response.put("message", "检查登录状态失败: " + e.getMessage());
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
        response.put("service", "ZhilianController");
        response.put("status", "healthy");
        response.put("timestamp", System.currentTimeMillis());

        return ResponseEntity.ok(response);
    }

    /**
     * 调试接口：读取数据库中的 智联招聘 Cookie 记录
     */
    @GetMapping("/cookie")
    public ResponseEntity<Map<String, Object>> getZhilianCookieRecord() {
        Map<String, Object> response = new HashMap<>();
        try {
            CookieEntity cookie = cookieService.getCookieByPlatform("zhilian");
            Map<String, Object> data = new HashMap<>();
            if (cookie != null) {
                data.put("id", cookie.getId());
                data.put("platform", cookie.getPlatform());
                data.put("cookie_value", cookie.getCookieValue());
                data.put("remark", cookie.getRemark());
                data.put("created_at", cookie.getCreatedAt());
                data.put("updated_at", cookie.getUpdatedAt());
            } else {
                data.put("platform", "zhilian");
                data.put("cookie_value", null);
                data.put("message", "未找到智联招聘Cookie记录");
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
    public ResponseEntity<Map<String, Object>> logoutZhilian() {
        Map<String, Object> response = new HashMap<>();
        try {
            // 更新登录状态为未登录并触发SSE通知
            playwrightManager.setLoginStatus("zhilian", false);

            // 清空数据库中 智联招聘 平台的所有 Cookie 值
            cookieService.clearCookieByPlatform("zhilian", "manual logout");

            // 清理运行中的上下文Cookie
            try {
                playwrightManager.clearZhilianCookies();
            } catch (Exception e) {
                log.warn("清理智联招聘上下文Cookie时发生异常，但不影响退出流程: {}", e.getMessage());
            }

            response.put("success", true);
            response.put("message", "智联招聘已退出登录，数据库Cookie和上下文Cookie均已清理");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("退出登录失败", e);
            response.put("success", false);
            response.put("message", "退出登录失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * 调试接口：主动保存当前上下文中的 智联招聘 Cookie 到数据库
     */
    @PostMapping("/save-cookie")
    public ResponseEntity<Map<String, Object>> saveZhilianCookie() {
        Map<String, Object> response = new HashMap<>();
        try {
            playwrightManager.saveZhilianCookiesToDb("manual save");
            response.put("success", true);
            response.put("message", "已主动保存智联招聘Cookie到数据库");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "保存智联招聘Cookie失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * 启动智联招聘自动投递任务
     * @return 响应结果
     */
    @PostMapping("/start")
    public ResponseEntity<Map<String, Object>> startZhilianJob() {
        Map<String, Object> response = new HashMap<>();

        try {
            // 未登录则不允许启动
            if (!playwrightManager.isLoggedIn("zhilian")) {
                response.put("success", false);
                response.put("message", "请先登录智联招聘");
                response.put("status", "not_logged_in");
                return ResponseEntity.badRequest().body(response);
            }

            // 检查是否已有任务在运行
            if (zhilianJobService.isRunning()) {
                response.put("success", false);
                response.put("message", "智联招聘任务已在运行中，请等待当前任务完成");
                response.put("status", "running");
                return ResponseEntity.badRequest().body(response);
            }

            // 异步启动新任务
            CompletableFuture.runAsync(() -> {
                zhilianJobService.executeDelivery(progressMessage -> {
                    log.info("[{}] {}", progressMessage.getPlatform(), progressMessage.getMessage());
                });
            });

            response.put("success", true);
            response.put("message", "智联招聘任务启动成功");
            response.put("status", "started");

            log.info("通过API启动智联招聘任务成功");
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("启动智联招聘任务失败", e);
            response.put("success", false);
            response.put("message", "启动智联招聘任务失败: " + e.getMessage());
            response.put("error", e.getClass().getSimpleName());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * 停止智联招聘任务
     * @return 响应结果
     */
    @PostMapping("/stop")
    public ResponseEntity<Map<String, Object>> stopZhilianJob() {
        Map<String, Object> response = new HashMap<>();

        try {
            if (!zhilianJobService.isRunning()) {
                response.put("success", false);
                response.put("message", "没有正在运行的智联招聘任务");
                return ResponseEntity.badRequest().body(response);
            }

            zhilianJobService.stopDelivery();

            response.put("success", true);
            response.put("message", "智联招聘任务停止请求已发送");

            log.info("通过API停止智联招聘任务");
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("停止智联招聘任务失败", e);
            response.put("success", false);
            response.put("message", "停止智联招聘任务失败: " + e.getMessage());
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
            Map<String, Object> status = zhilianJobService.getStatus();
            response.put("success", true);
            response.putAll(status);
            response.put("timestamp", System.currentTimeMillis());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("获取当前状态失败", e);
            response.put("success", false);
            response.put("message", "获取状态失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }
}
