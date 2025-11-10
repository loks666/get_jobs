package com.getjobs.application.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.getjobs.application.entity.CookieEntity;
import com.getjobs.application.entity.Job51ConfigEntity;
import com.getjobs.application.entity.Job51OptionEntity;
import com.getjobs.application.service.CookieService;
import com.getjobs.application.service.Job51Service;
import com.getjobs.worker.manager.PlaywrightManager;
// Boss 控制器已独立，移除 Boss 依赖
import com.getjobs.worker.service.Job51JobService;
import com.getjobs.worker.dto.JobProgressMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.catalina.connector.ClientAbortException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * JobController：51job 平台控制器（含配置、任务、分析、SSE、登录状态）
 * 路由保持原样：/api/51job/... 和 /api/jobs/login-status/...
 */
@Slf4j
@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class JobController {
    private static final ObjectMapper objectMapper = new ObjectMapper();

    // Services / Managers
    private final Job51Service job51Service;
    private final Job51JobService job51JobService;
    private final PlaywrightManager playwrightManager;
    private final CookieService cookieService;

    // SSE emitter lists
    private final List<SseEmitter> job51ProgressEmitters = new CopyOnWriteArrayList<>();
    private final List<SseEmitter> loginStatusEmitters = new CopyOnWriteArrayList<>();

    // ==================== 51job 投递进度 SSE ====================

    /** SSE - 51job投递任务进度推送 */
    @GetMapping(value = "/51job/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamJob51Progress() {
        SseEmitter emitter = new SseEmitter(0L); // 0 = 永不超时
        job51ProgressEmitters.add(emitter);

        emitter.onCompletion(() -> {
            log.info("51job进度SSE连接已完成");
            job51ProgressEmitters.remove(emitter);
        });

        emitter.onTimeout(() -> {
            log.info("51job进度SSE连接超时");
            job51ProgressEmitters.remove(emitter);
        });

        emitter.onError(e -> {
            log.error("51job进度SSE连接错误", e);
            job51ProgressEmitters.remove(emitter);
        });

        try {
            emitter.send(SseEmitter.event()
                    .name("connected")
                    .data(Map.of("message", "已连接到51job投递进度推送")));
        } catch (IOException e) {
            log.error("发送SSE连接消息失败", e);
        }

        return emitter;
    }

    /** SSE - 登录状态变化推送 */
    @GetMapping(value = "/jobs/login-status/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamLoginStatus() {
        SseEmitter emitter = new SseEmitter(0L);
        loginStatusEmitters.add(emitter);

        playwrightManager.addLoginStatusListener(this::sendLoginStatusChange);

        emitter.onCompletion(() -> {
            log.info("登录状态SSE连接已完成");
            loginStatusEmitters.remove(emitter);
        });

        emitter.onTimeout(() -> {
            log.info("登录状态SSE连接超时");
            loginStatusEmitters.remove(emitter);
        });

        emitter.onError(e -> {
            log.error("登录状态SSE连接错误", e);
            loginStatusEmitters.remove(emitter);
        });

        try {
            boolean bossLoggedIn = playwrightManager.isLoggedIn("boss");
            boolean liepinLoggedIn = playwrightManager.isLoggedIn("liepin");
            boolean job51LoggedIn = playwrightManager.isLoggedIn("51job");
            boolean zhilianLoggedIn = playwrightManager.isLoggedIn("zhilian");

            emitter.send(SseEmitter.event()
                    .name("connected")
                    .data(Map.of(
                            "message", "已连接到登录状态推送",
                            "bossLoggedIn", bossLoggedIn,
                            "liepinLoggedIn", liepinLoggedIn,
                            "job51LoggedIn", job51LoggedIn,
                            "zhilianLoggedIn", zhilianLoggedIn
                    )));
        } catch (IOException e) {
            log.error("发送SSE连接消息失败", e);
        }

        return emitter;
    }

    private void sendJob51Progress(JobProgressMessage message) {
        List<SseEmitter> deadEmitters = new CopyOnWriteArrayList<>();
        for (SseEmitter emitter : job51ProgressEmitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("progress")
                        .data(objectMapper.writeValueAsString(message)));
            } catch (Exception e) {
                if (e instanceof AsyncRequestNotUsableException ||
                        e instanceof ClientAbortException ||
                        (e.getCause() instanceof ClientAbortException) ||
                        (e instanceof IOException && String.valueOf(e.getMessage()).contains("中止了一个已建立的连接"))) {
                    log.debug("51job进度 SSE 客户端已断开，移除连接: {}", e.getMessage());
                    try { emitter.complete(); } catch (Exception ignored) {}
                } else {
                    log.error("发送51job进度消息失败", e);
                }
                deadEmitters.add(emitter);
            }
        }
        job51ProgressEmitters.removeAll(deadEmitters);
    }

    private void sendLoginStatusChange(PlaywrightManager.LoginStatusChange change) {
        List<SseEmitter> deadEmitters = new CopyOnWriteArrayList<>();
        for (SseEmitter emitter : loginStatusEmitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("login-status")
                        .data(objectMapper.writeValueAsString(Map.of(
                                "platform", change.platform(),
                                "isLoggedIn", change.isLoggedIn(),
                                "timestamp", change.timestamp()
                        ))));
            } catch (Exception e) {
                if (e instanceof AsyncRequestNotUsableException ||
                        e instanceof ClientAbortException ||
                        (e.getCause() instanceof ClientAbortException) ||
                        (e instanceof IOException && String.valueOf(e.getMessage()).contains("中止了一个已建立的连接"))) {
                    log.debug("登录状态 SSE 客户端已断开，移除连接: {}", e.getMessage());
                    try { emitter.complete(); } catch (Exception ignored) {}
                } else {
                    log.error("发送登录状态消息失败", e);
                }
                deadEmitters.add(emitter);
            }
        }
        loginStatusEmitters.removeAll(deadEmitters);
    }

    /** 心跳 - 登录状态 SSE */
    @Scheduled(fixedRate = 30000)
    public void heartbeatLoginStatus() {
        if (loginStatusEmitters.isEmpty()) return;
        List<SseEmitter> deadEmitters = new CopyOnWriteArrayList<>();
        for (SseEmitter emitter : loginStatusEmitters) {
            try {
                emitter.send(SseEmitter.event().name("ping").data("keep-alive"));
            } catch (Exception e) {
                if (e instanceof AsyncRequestNotUsableException ||
                        e instanceof ClientAbortException ||
                        (e.getCause() instanceof ClientAbortException) ||
                        (e instanceof IOException && String.valueOf(e.getMessage()).contains("中止了一个已建立的连接"))) {
                    log.debug("登录状态 SSE 客户端已断开（心跳），移除连接: {}", e.getMessage());
                    try { emitter.complete(); } catch (Exception ignored) {}
                } else {
                    log.error("发送登录状态心跳失败", e);
                }
                deadEmitters.add(emitter);
            }
        }
        loginStatusEmitters.removeAll(deadEmitters);
    }

    /** 心跳 - Boss进度 SSE */
    @Scheduled(fixedRate = 30000)
    public void heartbeatJob51Progress() {
        if (job51ProgressEmitters.isEmpty()) return;
        List<SseEmitter> deadEmitters = new CopyOnWriteArrayList<>();
        for (SseEmitter emitter : job51ProgressEmitters) {
            try {
                emitter.send(SseEmitter.event().name("ping").data("keep-alive"));
            } catch (Exception e) {
                if (e instanceof AsyncRequestNotUsableException ||
                        e instanceof ClientAbortException ||
                        (e.getCause() instanceof ClientAbortException) ||
                        (e instanceof IOException && String.valueOf(e.getMessage()).contains("中止了一个已建立的连接"))) {
                    log.debug("51job进度 SSE 客户端已断开（心跳），移除连接: {}", e.getMessage());
                    try { emitter.complete(); } catch (Exception ignored) {}
                } else {
                    log.error("发送51job进度心跳失败", e);
                }
                deadEmitters.add(emitter);
            }
        }
        job51ProgressEmitters.removeAll(deadEmitters);
    }

    // ==================== 51job 配置 / 登录 / Cookie / 任务 ====================

    /** 获取 51job 配置与可选项 */
    @GetMapping("/51job/config")
    public Map<String, Object> getAllJob51Config() {
        Map<String, Object> result = new HashMap<>();

        Job51ConfigEntity config = job51Service.getFirstConfig();
        if (config == null) config = new Job51ConfigEntity();

        Map<String, List<Map<String, String>>> options = new HashMap<>();
        options.put("jobArea", buildOptionsFromDb("jobArea"));
        options.put("salary", buildOptionsFromDb("salary"));

        result.put("config", config);
        result.put("options", options);
        return result;
    }

    /** 更新 51job 配置 */
    @PutMapping("/51job/config")
    public Job51ConfigEntity updateConfig(@RequestBody Job51ConfigEntity config) {
        if (config == null) return job51Service.getFirstConfig();

        if (config.getKeywords() != null) {
            List<String> list = job51Service.parseListString(config.getKeywords());
            String normalized = toBracketListString(list);
            config.setKeywords(normalized);
        }

        if (config.getJobArea() != null) {
            List<String> raw = job51Service.parseListString(config.getJobArea());
            List<String> names = toNames("jobArea", raw);
            config.setJobArea(toBracketListString(names));
        }
        if (config.getSalary() != null) {
            List<String> raw = job51Service.parseListString(config.getSalary());
            List<String> names = toNames("salary", raw);
            config.setSalary(toBracketListString(names));
        }

        return job51Service.updateConfig(config);
    }

    /** 返回 jobArea 选项列表 */
    @GetMapping("/51job/config/options/jobArea")
    public List<Map<String, String>> getJobAreaOptions() { return buildOptionsFromDb("jobArea"); }

    /** 返回 salary 选项列表 */
    @GetMapping("/51job/config/options/salary")
    public List<Map<String, String>> getSalaryOptions() { return buildOptionsFromDb("salary"); }

    /** 触发51job登录流程 */
    @PostMapping("/51job/login")
    public ResponseEntity<Map<String, Object>> triggerLogin() {
        Map<String, Object> response = new HashMap<>();
        try {
            playwrightManager.trigger51jobLogin();
            response.put("success", true);
            response.put("message", "已打开51job登录页并尝试点击扫码登录，请扫码完成登录");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("触发51job登录失败", e);
            response.put("success", false);
            response.put("message", "触发登录失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /** 检查51job登录状态 */
    @GetMapping("/51job/login-status")
    public ResponseEntity<Map<String, Object>> checkLoginStatus51() {
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

    /** 退出51job登录 */
    @PostMapping("/51job/logout")
    public ResponseEntity<Map<String, Object>> logout51job() {
        Map<String, Object> response = new HashMap<>();
        try {
            playwrightManager.setLoginStatus("51job", false);
            cookieService.clearCookieByPlatform("51job", "manual logout");
            try { playwrightManager.clear51jobCookies(); } catch (Exception e) { log.warn("清理51job上下文Cookie异常: {}", e.getMessage()); }
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

    /** 读取数据库中的 51job Cookie 记录 */
    @GetMapping("/51job/cookie")
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

    /** 主动保存51job Cookie到数据库 */
    @PostMapping("/51job/save-cookie")
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

    /** 启动51job自动投递任务 */
    @PostMapping("/51job/start")
    public ResponseEntity<Map<String, Object>> start51jobJob() {
        Map<String, Object> response = new HashMap<>();
        try {
            if (!playwrightManager.isLoggedIn("51job")) {
                response.put("success", false);
                response.put("message", "请先登录51job");
                response.put("status", "not_logged_in");
                return ResponseEntity.badRequest().body(response);
            }
            if (job51JobService.isRunning()) {
                response.put("success", false);
                response.put("message", "51job任务已在运行中，请等待当前任务完成");
                response.put("status", "running");
                return ResponseEntity.badRequest().body(response);
            }
            CompletableFuture.runAsync(() -> job51JobService.executeDelivery(pm -> {
                // 推送到 SSE 并保留日志输出
                sendJob51Progress(pm);
                log.info("[{}] {}", pm.getPlatform(), pm.getMessage());
            }));
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

    /** 停止51job任务 */
    @PostMapping("/51job/stop")
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

    /** 获取当前51job运行状态 */
    @GetMapping("/51job/status")
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

    /** 51job健康检查 */
    @GetMapping("/51job/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("service", "Job51Controller");
        response.put("status", "healthy");
        response.put("timestamp", System.currentTimeMillis());
        return ResponseEntity.ok(response);
    }

    // ==================== 51job Analytics ====================

    /** 投递分析统计与图表（支持筛选条件） */
    @GetMapping("/51job/stats")
    public Job51Service.StatsResponse getStats(
            @RequestParam(value = "statuses", required = false) String statuses,
            @RequestParam(value = "location", required = false) String location,
            @RequestParam(value = "experience", required = false) String experience,
            @RequestParam(value = "degree", required = false) String degree,
            @RequestParam(value = "minK", required = false) Double minK,
            @RequestParam(value = "maxK", required = false) Double maxK,
            @RequestParam(value = "keyword", required = false) String keyword
    ) {
        List<String> statusList = null;
        if (statuses != null && !statuses.trim().isEmpty()) {
            statusList = List.of(statuses.split(",")).stream()
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());
        }
        return job51Service.getJob51Stats(statusList, location, experience, degree, minK, maxK, keyword);
    }

    /** 岗位列表（分页 + 筛选） */
    @GetMapping("/51job/list")
    public Job51Service.PagedResult51 list(
            @RequestParam(value = "statuses", required = false) String statuses,
            @RequestParam(value = "location", required = false) String location,
            @RequestParam(value = "experience", required = false) String experience,
            @RequestParam(value = "degree", required = false) String degree,
            @RequestParam(value = "minK", required = false) Double minK,
            @RequestParam(value = "maxK", required = false) Double maxK,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "page", required = false, defaultValue = "1") Integer page,
            @RequestParam(value = "size", required = false, defaultValue = "20") Integer size
    ) {
        List<String> statusList = null;
        if (statuses != null && !statuses.trim().isEmpty()) {
            statusList = List.of(statuses.split(",")).stream()
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());
        }
        return job51Service.listJob51(statusList, location, experience, degree, minK, maxK, keyword, page, size);
    }

    /** 刷新 job51_data，返回总数 */
    @GetMapping("/51job/reload")
    public Map<String, Object> reload() { return job51Service.reloadJob51Data(); }

    // ==================== 辅助方法 ====================

    private String toBracketListString(List<String> list) {
        if (list == null || list.isEmpty()) return "[]";
        String joined = list.stream()
                .filter(s -> s != null && !s.trim().isEmpty())
                .map(s -> s.replace("\"", "\\\""))
                .map(s -> "\"" + s + "\"")
                .collect(java.util.stream.Collectors.joining(", "));
        return "[" + joined + "]";
    }

    private List<String> toNames(String type, List<String> inputs) {
        List<Job51OptionEntity> options = job51Service.getOptionsByType(type);
        Map<String, String> codeToName = new HashMap<>();
        Map<String, String> nameToName = new HashMap<>();
        for (Job51OptionEntity o : options) {
            if (o.getCode() != null) codeToName.put(o.getCode(), o.getName());
            if (o.getName() != null) nameToName.put(o.getName(), o.getName());
        }
        List<String> names = new ArrayList<>();
        for (String s : inputs) {
            if (s == null) continue;
            String t = s.trim();
            if (t.isEmpty()) continue;
            String name = codeToName.getOrDefault(t, nameToName.getOrDefault(t, t));
            names.add(name);
        }
        return names;
    }

    private List<Map<String, String>> buildOptionsFromDb(String type) {
        List<Job51OptionEntity> rows = job51Service.getOptionsByType(type);
        List<Map<String, String>> list = new ArrayList<>();
        for (Job51OptionEntity row : rows) {
            Map<String, String> item = new HashMap<>();
            item.put("name", row.getName());
            item.put("code", row.getCode());
            list.add(item);
        }
        return list;
    }
}