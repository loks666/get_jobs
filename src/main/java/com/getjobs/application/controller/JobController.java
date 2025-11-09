package com.getjobs.application.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.getjobs.worker.dto.JobProgressMessage;
import com.getjobs.worker.manager.PlaywrightManager;
import com.getjobs.worker.service.BossJobService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 任务投递控制器
 * 提供Boss平台的REST API和SSE实时推送功能
 */
@Slf4j
@RestController
@RequestMapping("/api/jobs")
@RequiredArgsConstructor
public class JobController {
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final BossJobService bossJobService;
    private final PlaywrightManager playwrightManager;

    // Boss任务进度SSE连接列表
    private final List<SseEmitter> bossProgressEmitters = new CopyOnWriteArrayList<>();

    // 登录状态变化SSE连接列表
    private final List<SseEmitter> loginStatusEmitters = new CopyOnWriteArrayList<>();

    /**
     * SSE - Boss投递任务进度推送
     */
    @GetMapping(value = "/boss/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamBossProgress() {
        SseEmitter emitter = new SseEmitter(0L); // 0 = 永不超时
        bossProgressEmitters.add(emitter);

        emitter.onCompletion(() -> {
            log.info("Boss进度SSE连接已完成");
            bossProgressEmitters.remove(emitter);
        });

        emitter.onTimeout(() -> {
            log.info("Boss进度SSE连接超时");
            bossProgressEmitters.remove(emitter);
        });

        emitter.onError(e -> {
            log.error("Boss进度SSE连接错误", e);
            bossProgressEmitters.remove(emitter);
        });

        // 发送连接成功消息
        try {
            emitter.send(SseEmitter.event()
                    .name("connected")
                    .data(Map.of("message", "已连接到Boss投递进度推送")));
        } catch (IOException e) {
            log.error("发送SSE连接消息失败", e);
        }

        return emitter;
    }

    /**
     * SSE - 登录状态变化推送
     */
    @GetMapping(value = "/login-status/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamLoginStatus() {
        SseEmitter emitter = new SseEmitter(0L);
        loginStatusEmitters.add(emitter);

        // 注册登录状态监听器
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

        // 发送连接成功消息和当前登录状态（不打印日志）
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

    /**
     * POST - 启动Boss投递任务
     */
    @PostMapping("/boss/execute")
    public ResponseEntity<Map<String, Object>> executeBoss() {
        if (bossJobService.isRunning()) {
            return ResponseEntity.ok(Map.of(
                    "status", "already_running",
                    "message", "Boss投递任务已在运行中"
            ));
        }

        // 异步执行投递任务
        CompletableFuture.runAsync(() -> {
            bossJobService.executeDelivery(this::sendBossProgress);
        });

        return ResponseEntity.ok(Map.of(
                "status", "started",
                "message", "Boss投递任务已启动"
        ));
    }

    /**
     * POST - 停止Boss投递任务
     */
    @PostMapping("/boss/stop")
    public ResponseEntity<Map<String, Object>> stopBoss() {
        bossJobService.stopDelivery();
        return ResponseEntity.ok(Map.of(
                "status", "stopping",
                "message", "正在停止Boss投递任务"
        ));
    }

    /**
     * GET - 获取Boss任务状态
     */
    @GetMapping("/boss/status")
    public ResponseEntity<Map<String, Object>> getBossStatus() {
        return ResponseEntity.ok(bossJobService.getStatus());
    }

    /**
     * 向所有Boss进度连接发送消息
     */
    private void sendBossProgress(JobProgressMessage message) {
        List<SseEmitter> deadEmitters = new CopyOnWriteArrayList<>();

        for (SseEmitter emitter : bossProgressEmitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("progress")
                        .data(objectMapper.writeValueAsString(message)));
            } catch (Exception e) {
                log.error("发送Boss进度消息失败", e);
                deadEmitters.add(emitter);
            }
        }

        // 移除失败的连接
        bossProgressEmitters.removeAll(deadEmitters);
    }

    /**
     * 向所有登录状态连接发送消息
     */
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
                log.error("发送登录状态消息失败", e);
                deadEmitters.add(emitter);
            }
        }

        // 移除失败的连接
        loginStatusEmitters.removeAll(deadEmitters);
    }
}
