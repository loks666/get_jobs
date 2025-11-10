package com.getjobs.worker.utils;

import com.getjobs.application.service.ConfigService;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.fluent.Request;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * @author loks666
 * 项目链接: <a href="https://github.com/loks666/get_jobs">https://github.com/loks666/get_jobs</a>
 */
@Slf4j
@Service
public class Bot {

    private static volatile Bot INSTANCE;

    private final ConfigService configService;
    private String hookUrl;
    private boolean isSend;

    @Autowired
    public Bot(ConfigService configService) {
        this.configService = configService;
        INSTANCE = this;
        reloadConfig();
    }

    /**
     * 从数据库配置表加载所需配置
     */
    public void reloadConfig() {
        try {
            this.hookUrl = configService.getConfigValue("HOOK_URL");
            String sendFlag = configService.getConfigValue("BOT_IS_SEND");
            this.isSend = ("true".equalsIgnoreCase(sendFlag) || "1".equals(sendFlag));

            if (this.hookUrl == null || this.hookUrl.isBlank()) {
                log.warn("HOOK_URL 未配置，Bot 将不发送消息。");
                this.isSend = false;
            }
        } catch (Exception e) {
            log.error("加载Bot配置失败: {}", e.getMessage());
            this.isSend = false;
        }
    }

    public static void sendMessageByTime(String message) {
        Bot inst = INSTANCE;
        if (inst == null) {
            log.error("Bot 尚未初始化为 Spring Bean，忽略发送。");
            return;
        }
        inst.sendMessageByTimeInstance(message);
    }

    public void sendMessageByTimeInstance(String message) {
        if (!isSend) {
            return;
        }
        String currentTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        String formattedMessage = String.format("%s %s", currentTime, message);
        sendMessageInstance(formattedMessage);
    }

    public static void sendMessage(String message) {
        Bot inst = INSTANCE;
        if (inst == null) {
            log.warn("Bot 尚未初始化为 Spring Bean，忽略发送。");
            return;
        }
        inst.sendMessageInstance(message);
    }

    public void sendMessageInstance(String message) {
        if (!isSend) {
            return;
        }
        if (hookUrl == null || hookUrl.isBlank()) {
            log.warn("HOOK_URL 未设置，无法推送消息。");
            return;
        }
        try {
            String response = Request.post(hookUrl)
                    .bodyString("{\"msgtype\": \"text\", \"text\": {\"content\": \"" + message + "\"}}",
                            org.apache.hc.core5.http.ContentType.APPLICATION_JSON)
                    .execute()
                    .returnContent()
                    .asString();
            log.info("消息推送成功: {}", response);
        } catch (Exception e) {
            log.error("消息推送失败: {}", e.getMessage());
        }
    }

    public static void main(String[] args) {
        // 本地测试请确保 Spring 容器已初始化并注入 ConfigService。
    }

}
