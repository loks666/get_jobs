package com.getjobs.application.controller;

import com.getjobs.application.service.ConfigService;
import com.getjobs.application.service.NotifyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 企业微信推送相关接口。
 */
@Slf4j
@RestController
@RequestMapping("/api/notify")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class NotifyController {

    private final NotifyService notifyService;
    private final ConfigService configService;

    /**
     * 发送一条测试消息，用于验证 Webhook 是否配置正确。
     */
    @PostMapping("/test")
    public ResponseEntity<Map<String, Object>> sendTest() {
        Map<String, Object> response = new HashMap<>();
        try {
            notifyService.sendTestMessage(null);
            response.put("success", true);
            response.put("message", "已发送测试消息，请查看企业微信群。");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("发送测试消息失败", e);
            response.put("success", false);
            response.put("message", "发送失败：" + e.getMessage());
            return ResponseEntity.ok(response);
        }
    }

    /**
     * 查看当前推送配置状态（是否配置了 Webhook、开关是否打开）。
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        Map<String, Object> response = new HashMap<>();
        String hookUrl = configService.getConfigValue("HOOK_URL");
        String sendFlag = configService.getConfigValue("BOT_IS_SEND");
        boolean enabled = hookUrl != null && !hookUrl.isBlank()
                && ("1".equals(sendFlag) || "true".equalsIgnoreCase(sendFlag));

        response.put("success", true);
        response.put("configured", hookUrl != null && !hookUrl.isBlank());
        response.put("enabled", enabled);
        return ResponseEntity.ok(response);
    }
}
