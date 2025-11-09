package com.getjobs.application.controller;

import com.getjobs.application.entity.CookieEntity;
import com.getjobs.application.service.CookieService;
import com.getjobs.worker.manager.PlaywrightManager;
import com.getjobs.worker.service.Job51JobService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 51job控制器
 * 提供51job平台Cookie管理和登录状态控制的REST API接口
 */
@Slf4j
@RestController
@RequestMapping("/api/51job")
@CrossOrigin(origins = "*")
public class Job51Controller {

    @Autowired
    private PlaywrightManager playwrightManager;

    @Autowired
    private CookieService cookieService;

    @Autowired
    private Job51JobService job51JobService;

    /**
     * 检查登录状态
     * @return 登录状态信息
     */
    @GetMapping("/login-status")
    public ResponseEntity<Map<String, Object>> checkLoginStatus() {
        Map<String, Object> response = new HashMap<>();

        try {
            boolean isLoggedIn = playwrightManager.isLoggedIn("51job");
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
        response.put("service", "Job51Controller");
        response.put("status", "healthy");
        response.put("timestamp", System.currentTimeMillis());

        return ResponseEntity.ok(response);
    }

    /**
     * 调试接口：读取数据库中的 51job Cookie 记录
     */
    @GetMapping("/cookie")
    public ResponseEntity<Map<String, Object>> get51jobCookieRecord() {
        Map<String, Object> response = new HashMap<>();
        try {
            CookieEntity cookie = cookieService.getCookieByPlatform("51job");
            Map<String, Object> data = new HashMap<>();
            if (cookie != null) {
                data.put("id", cookie.getId());
                data.put("platform", cookie.getPlatform());
                data.put("cookie_value", cookie.getCookieValue());
                data.put("remark", cookie.getRemark());
                data.put("created_at", cookie.getCreatedAt());
                data.put("updated_at", cookie.getUpdatedAt());
            } else {
                data.put("platform", "51job");
                data.put("cookie_value", null);
                data.put("message", "未找到51job Cookie记录");
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
    public ResponseEntity<Map<String, Object>> logout51job() {
        Map<String, Object> response = new HashMap<>();
        try {
            // 更新登录状态为未登录并触发SSE通知
            playwrightManager.setLoginStatus("51job", false);

            // 清空数据库中 51job 平台的所有 Cookie 值
            cookieService.clearCookieByPlatform("51job", "manual logout");

            // 清理运行中的上下文Cookie
            try {
                playwrightManager.clear51jobCookies();
            } catch (Exception e) {
                log.warn("清理51job上下文Cookie时发生异常，但不影响退出流程: {}", e.getMessage());
            }

            response.put("success", true);
            response.put("message", "51job已退出登录，数据库Cookie和上下文Cookie均已清理");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("退出登录失败", e);
            response.put("success", false);
            response.put("message", "退出登录失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * 调试接口：主动保存当前上下文中的 51job Cookie 到数据库
     */
    @PostMapping("/save-cookie")
    public ResponseEntity<Map<String, Object>> save51jobCookie() {
        Map<String, Object> response = new HashMap<>();
        try {
            playwrightManager.save51jobCookiesToDb("manual save");
            response.put("success", true);
            response.put("message", "已主动保存51job Cookie到数据库");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "保存51job Cookie失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * 启动51job自动投递任务
     * @return 响应结果
     */
    @PostMapping("/start")
    public ResponseEntity<Map<String, Object>> start51jobJob() {
        Map<String, Object> response = new HashMap<>();

        try {
            // 未登录则不允许启动
            if (!playwrightManager.isLoggedIn("51job")) {
                response.put("success", false);
                response.put("message", "请先登录51job");
                response.put("status", "not_logged_in");
                return ResponseEntity.badRequest().body(response);
            }

            // 检查是否已有任务在运行
            if (job51JobService.isRunning()) {
                response.put("success", false);
                response.put("message", "51job任务已在运行中，请等待当前任务完成");
                response.put("status", "running");
                return ResponseEntity.badRequest().body(response);
            }

            // 异步启动新任务
            CompletableFuture.runAsync(() -> {
                job51JobService.executeDelivery(progressMessage -> {
                    log.info("[{}] {}", progressMessage.getPlatform(), progressMessage.getMessage());
                });
            });

            response.put("success", true);
            response.put("message", "51job任务启动成功");
            response.put("status", "started");

            log.info("通过API启动51job任务成功");
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("启动51job任务失败", e);
            response.put("success", false);
            response.put("message", "启动51job任务失败: " + e.getMessage());
            response.put("error", e.getClass().getSimpleName());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * 停止51job任务
     * @return 响应结果
     */
    @PostMapping("/stop")
    public ResponseEntity<Map<String, Object>> stop51jobJob() {
        Map<String, Object> response = new HashMap<>();

        try {
            if (!job51JobService.isRunning()) {
                response.put("success", false);
                response.put("message", "没有正在运行的51job任务");
                return ResponseEntity.badRequest().body(response);
            }

            job51JobService.stopDelivery();

            response.put("success", true);
            response.put("message", "51job任务停止请求已发送");

            log.info("通过API停止51job任务");
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("停止51job任务失败", e);
            response.put("success", false);
            response.put("message", "停止51job任务失败: " + e.getMessage());
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
            Map<String, Object> status = job51JobService.getStatus();
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
