package com.getjobs.worker.utils;

import com.getjobs.application.service.ConfigService;
import com.sun.net.httpserver.HttpServer;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BotTest {
    @Test void signsFeishuWithKnownVectorAndEscapesText() throws Exception {
        String text = "你好\n\"岗位\"\\路径 😀";
        JSONObject payload = Bot.feishuPayload(text, "example-secret", 1599360473);
        assertEquals("Gqzo3d51m9P8CAgrn86JBZIipyhJhGOpHQbUT4HViD0=", payload.getString("sign"));
        assertEquals("1599360473", payload.getString("timestamp"));
        assertEquals(text, new JSONObject(payload.toString()).getJSONObject("content").getString("text"));
        assertFalse(Bot.feishuPayload("hello", "", 1).has("sign"));
    }

    @Test void validatesBusinessErrorsAndMalformedResponses() {
        assertTrue(Bot.successful(200, "{\"code\":0}", true));
        assertTrue(Bot.successful(200, "{\"StatusCode\":0}", true));
        assertTrue(Bot.successful(200, "{\"errcode\":0}", false));
        assertFalse(Bot.successful(200, "{\"code\":19021}", true));
        assertFalse(Bot.successful(200, "{\"errcode\":93000}", false));
        assertFalse(Bot.successful(500, "{\"code\":0}", true));
        assertFalse(Bot.successful(200, "{}", true));
        assertFalse(Bot.successful(200, "not json", true));
    }

    @Test void chunksPreserveUnicodeAndStayWithinByteLimit() {
        String text = "中文😀\n\"".repeat(800);
        var chunks = Bot.splitText(text, 1800);
        assertEquals(text, String.join("", chunks));
        for (String chunk : chunks) {
            assertTrue(chunk.getBytes(StandardCharsets.UTF_8).length <= 1800);
            assertFalse(Character.isHighSurrogate(chunk.charAt(chunk.length() - 1)));
        }
    }

    @Test void channelsAreIndependentAndSavedChangesApplyWithoutRestart() throws Exception {
        var requests = new CopyOnWriteArrayList<JSONObject>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/feishu", exchange -> {
            requests.add(new JSONObject(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
            byte[] body = "{\"code\":0}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            var config = mock(ConfigService.class);
            Map<String, String> values = new HashMap<>(Map.of("BOT_IS_SEND", "1", "HOOK_URL", ":invalid",
                    "FEISHU_BOT_IS_SEND", "true", "FEISHU_HOOK_URL", "http://127.0.0.1:" + server.getAddress().getPort() + "/feishu"));
            when(config.getAllConfigsAsMap()).thenAnswer(i -> new HashMap<>(values));
            Bot bot = new Bot(config);
            bot.sendMessageInstance("测试通知\n\"引号\"");
            assertEquals(1, requests.size());
            assertEquals("测试通知\n\"引号\"", requests.getFirst().getJSONObject("content").getString("text"));
            values.put("FEISHU_BOT_IS_SEND", "false");
            bot.sendMessageInstance("不应发送");
            assertEquals(1, requests.size());
            values.put("FEISHU_BOT_IS_SEND", "1");
            values.put("FEISHU_SECRET", "new-secret");
            bot.sendMessageInstance("已更新");
            assertEquals(2, requests.size());
            assertTrue(requests.getLast().has("sign"));
        } finally { server.stop(0); }
    }
}
