package com.getjobs.worker.utils;

import com.getjobs.application.service.ConfigService;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/** 企业微信和飞书独立推送；每次读取已保存配置，修改后立即生效。 */
@Slf4j
@Service
public class Bot {
    private static volatile Bot INSTANCE;
    private final ConfigService configService;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public Bot(ConfigService configService) {
        this.configService = configService;
        INSTANCE = this;
    }

    public void reloadConfig() { /* 保留调用兼容性，配置在发送时读取。 */ }

    public static void sendMessageByTime(String message) {
        if (INSTANCE != null) INSTANCE.sendMessageByTimeInstance(message);
    }

    public void sendMessageByTimeInstance(String message) {
        sendMessageInstance(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + " " + message);
    }

    public static void sendMessage(String message) {
        if (INSTANCE != null) INSTANCE.sendMessageInstance(message);
    }

    public void sendMessageInstance(String message) {
        if (message == null || message.isBlank()) return;
        Map<String, String> config;
        try {
            config = configService.getAllConfigsAsMap();
        } catch (Exception e) {
            log.warn("读取通知配置失败，跳过推送");
            return;
        }
        if (enabled(config.get("BOT_IS_SEND"))) {
            deliver("企业微信", config.get("HOOK_URL"), "", message, false);
        }
        if (enabled(config.get("FEISHU_BOT_IS_SEND"))) {
            deliver("飞书", config.get("FEISHU_HOOK_URL"), config.get("FEISHU_SECRET"), message, true);
        }
    }

    private static boolean enabled(String value) {
        return value != null && ("1".equals(value.trim()) || "true".equalsIgnoreCase(value.trim()));
    }

    private void deliver(String name, String url, String secret, String message, boolean feishu) {
        if (url == null || url.isBlank()) {
            log.warn("{}通知已启用，但未配置 Webhook", name);
            return;
        }
        try {
            // 企业微信 text 限制 2048 字节；留出空间并保证不拆开 Unicode 字符。
            for (String part : splitText(message, 1800)) {
                JSONObject payload = feishu
                        ? feishuPayload(part, secret, Instant.now().getEpochSecond())
                        : new JSONObject().put("msgtype", "text").put("text", new JSONObject().put("content", part));
                HttpRequest request = HttpRequest.newBuilder(URI.create(url.trim()))
                        .timeout(Duration.ofSeconds(15)).header("Content-Type", "application/json; charset=utf-8")
                        .POST(HttpRequest.BodyPublishers.ofString(payload.toString())).build();
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (!successful(response.statusCode(), response.body(), feishu)) {
                    // Webhook URL、签名密钥及服务端原文均不写入日志。
                    log.warn("{}推送失败，HTTP {}，请检查机器人地址、安全设置及频率限制", name, response.statusCode());
                    return;
                }
            }
            log.info("{}消息推送成功", name);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.warn("{}推送失败（{}），请检查网络及机器人配置", name, e.getClass().getSimpleName());
        }
    }

    static JSONObject feishuPayload(String text, String secret, long timestamp) throws Exception {
        JSONObject payload = new JSONObject().put("msg_type", "text")
                .put("content", new JSONObject().put("text", text));
        if (secret != null && !secret.isBlank()) {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec((timestamp + "\n" + secret.trim()).getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            payload.put("timestamp", String.valueOf(timestamp));
            payload.put("sign", Base64.getEncoder().encodeToString(mac.doFinal(new byte[0])));
        }
        return payload;
    }

    static boolean successful(int status, String body, boolean feishu) {
        if (status < 200 || status >= 300) return false;
        try {
            JSONObject result = new JSONObject(body);
            String key = feishu ? (result.has("code") ? "code" : "StatusCode") : "errcode";
            return result.has(key) && result.getInt(key) == 0;
        } catch (Exception e) {
            return false;
        }
    }

    static List<String> splitText(String text, int byteLimit) {
        List<String> chunks = new ArrayList<>();
        StringBuilder chunk = new StringBuilder();
        int bytes = 0;
        for (int offset = 0; offset < text.length();) {
            int codePoint = text.codePointAt(offset);
            String character = new String(Character.toChars(codePoint));
            int length = character.getBytes(StandardCharsets.UTF_8).length;
            if (bytes + length > byteLimit && !chunk.isEmpty()) {
                chunks.add(chunk.toString());
                chunk.setLength(0);
                bytes = 0;
            }
            chunk.append(character);
            bytes += length;
            offset += Character.charCount(codePoint);
        }
        if (!chunk.isEmpty()) chunks.add(chunk.toString());
        return chunks;
    }
}
