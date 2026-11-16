package com.getjobs.application.controller;

import com.getjobs.application.entity.ShixisengConfigEntity;
import com.getjobs.application.service.CookieService;
import com.getjobs.application.service.ShixisengService;
import com.getjobs.worker.manager.PlaywrightManager;
import com.getjobs.worker.service.ShixisengJobService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/shixiseng")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class ShixisengController {

    private final ShixisengJobService shixisengJobService;
    private final PlaywrightManager playwrightManager;
    private final CookieService cookieService;
    private final ShixisengService shixisengService;

    @GetMapping("/login-status")
    public ResponseEntity<Map<String, Object>> checkLoginStatus() {
        boolean isLoggedIn = playwrightManager.isLoggedIn("shixiseng");
        return ResponseEntity.ok(Map.of(
                "success", true,
                "isLoggedIn", isLoggedIn,
                "message", isLoggedIn ? "已登录" : "未登录"
        ));
    }

    @PostMapping("/start")
    public ResponseEntity<Map<String, Object>> startJob() {
        Map<String, Object> response = new HashMap<>();
        if (!playwrightManager.isLoggedIn("shixiseng")) {
            response.put("success", false);
            response.put("message", "请先登录实习僧");
            response.put("status", "not_logged_in");
            return ResponseEntity.badRequest().body(response);
        }
        if (shixisengJobService.isRunning()) {
            response.put("success", false);
            response.put("message", "实习僧任务已在运行中，请等待当前任务完成");
            response.put("status", "running");
            return ResponseEntity.badRequest().body(response);
        }
        CompletableFuture.runAsync(() ->
                shixisengJobService.executeDelivery(msg -> log.info("[{}] {}", msg.getPlatform(), msg.getMessage()))
        );
        response.put("success", true);
        response.put("message", "实习僧任务启动成功");
        response.put("status", "started");
        log.info("通过API启动实习僧任务成功");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/stop")
    public ResponseEntity<Map<String, Object>> stopJob() {
        if (!shixisengJobService.isRunning()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "没有正在运行的实习僧任务"));
        }
        shixisengJobService.stopDelivery();
        return ResponseEntity.ok(Map.of("success", true, "message", "实习僧任务停止请求已发送"));
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        Map<String, Object> response = new HashMap<>(shixisengJobService.getStatus());
        response.put("success", true);
        response.put("timestamp", System.currentTimeMillis());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, Object>> logout() {
        try {
            playwrightManager.clearShixisengCookies();
            cookieService.saveOrUpdateCookie("shixiseng", "", "logout");
            playwrightManager.setLoginStatus("shixiseng", false);
            return ResponseEntity.ok(Map.of("success", true, "message", "已退出实习僧登录"));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @GetMapping("/config")
    public Map<String, Object> getConfig() {
        ShixisengConfigEntity config = shixisengService.getFirstConfig();
        if (config == null) config = new ShixisengConfigEntity();
        return Map.of("config", config);
    }

    @PutMapping("/config")
    public ShixisengConfigEntity updateConfig(@RequestBody ShixisengConfigEntity config) {
        return shixisengService.saveOrUpdateConfig(config);
    }

    @GetMapping("/stats")
    public ShixisengService.StatsResponse getStats(
            @RequestParam(value = "statuses", required = false) String statuses,
            @RequestParam(value = "location", required = false) String location,
            @RequestParam(value = "degree", required = false) String degree,
            @RequestParam(value = "keyword", required = false) String keyword
    ) {
        List<String> statusList = (statuses != null && !statuses.isBlank())
                ? Arrays.stream(statuses.split(",")).map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toList())
                : null;
        return shixisengService.getShixisengStats(statusList, location, degree, keyword);
    }

    @GetMapping("/list")
    public ShixisengService.PagedResult list(
            @RequestParam(value = "statuses", required = false) String statuses,
            @RequestParam(value = "location", required = false) String location,
            @RequestParam(value = "degree", required = false) String degree,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size
    ) {
        List<String> statusList = (statuses != null && !statuses.isBlank())
                ? Arrays.stream(statuses.split(",")).map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toList())
                : null;
        return shixisengService.listShixisengJobs(statusList, location, degree, keyword, page, size);
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of("success", true, "service", "ShixisengController", "status", "healthy");
    }
}
