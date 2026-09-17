package com.getjobs.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.getjobs.application.service.chatgpt.CodexRpcClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;
import java.nio.file.Path;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ChatGptServiceTest {
    @TempDir Path directory;
    private final ObjectMapper json = new ObjectMapper();
    private ChatGptService service;
    private CodexRpcClient rpc;

    @BeforeEach void setup() {
        service = new ChatGptService("codex", directory.toString());
        rpc = mock(CodexRpcClient.class);
        when(rpc.isAlive()).thenReturn(true);
        ReflectionTestUtils.setField(service, "client", rpc);
    }

    private void authenticated() throws Exception {
        when(rpc.request(eq("account/read"), any(), any())).thenReturn(json.readTree("{\"account\":{\"type\":\"chatgpt\",\"email\":\"test@example.com\",\"planType\":\"plus\"}}"));
    }

    @Test void rejectsUnsignedAccountBeforeStartingGeneration() throws Exception {
        when(rpc.request(eq("account/read"), any(), any())).thenReturn(json.readTree("{\"account\":null}"));
        assertTrue(assertThrows(IllegalStateException.class, () -> service.generate("hello", "")).getMessage().contains("登录 ChatGPT"));
        verify(rpc, never()).request(eq("thread/start"), any(), any());
    }

    @Test void loginStatusAndLogoutExposeNoTokens() throws Exception {
        when(rpc.request(eq("account/read"), any(), any())).thenReturn(json.readTree("{\"account\":null}"));
        when(rpc.request(eq("account/login/start"), any(), any())).thenReturn(json.readTree("{\"loginId\":\"login-1\",\"authUrl\":\"https://auth.openai.com/authorize?state=test\",\"accessToken\":\"never-return-this\"}"));
        assertEquals(Map.of("authUrl", "https://auth.openai.com/authorize?state=test"), service.login());
        assertEquals(true, service.status().get("pending"));
        service.onNotification(json.readTree("{\"method\":\"account/login/completed\",\"params\":{\"loginId\":\"login-1\",\"success\":true}}"));
        authenticated();
        assertEquals(true, service.status().get("loggedIn"));
        assertFalse(service.status().containsKey("accessToken"));
        service.logout();
        verify(rpc).request(eq("account/logout"), any(), any());
    }

    @Test void handlesNotificationsBeforeTurnStartResponseAndReturnsOnlyFinalText() throws Exception {
        authenticated();
        when(rpc.request(eq("thread/start"), any(), any())).thenAnswer(invocation -> {
            Map<?, ?> params = invocation.getArgument(1);
            assertEquals(true, params.get("ephemeral"));
            assertEquals("read-only", params.get("sandbox"));
            assertEquals("test-model", params.get("model"));
            return json.readTree("{\"thread\":{\"id\":\"thread-1\"}}");
        });
        when(rpc.request(eq("turn/start"), any(), any())).thenAnswer(invocation -> {
            service.onNotification(json.readTree("{\"method\":\"item/completed\",\"params\":{\"threadId\":\"thread-1\",\"item\":{\"type\":\"agentMessage\",\"phase\":\"commentary\",\"text\":\"working\"}}}"));
            service.onNotification(json.readTree("{\"method\":\"item/completed\",\"params\":{\"threadId\":\"thread-1\",\"item\":{\"type\":\"agentMessage\",\"phase\":\"final_answer\",\"text\":\"你好，期待沟通。\"}}}"));
            service.onNotification(json.readTree("{\"method\":\"turn/completed\",\"params\":{\"threadId\":\"thread-1\",\"turn\":{\"status\":\"completed\"}}}"));
            return json.readTree("{}");
        });
        assertEquals("你好，期待沟通。", service.generate("岗位描述", "test-model"));
        verify(rpc).request(eq("thread/unsubscribe"), eq(Map.of("threadId", "thread-1")), any());
    }

    @Test void surfacesTurnFailureAndDisconnectInsteadOfReturningEmptyText() throws Exception {
        authenticated();
        when(rpc.request(eq("thread/start"), any(), any())).thenReturn(json.readTree("{\"thread\":{\"id\":\"thread-1\"}}"));
        when(rpc.request(eq("turn/start"), any(), any())).thenAnswer(invocation -> {
            service.onNotification(json.readTree("{\"method\":\"turn/completed\",\"params\":{\"threadId\":\"thread-1\",\"turn\":{\"status\":\"failed\"}}}"));
            return json.readTree("{}");
        });
        assertThrows(IllegalStateException.class, () -> service.generate("hello", ""));
        when(rpc.request(eq("turn/start"), any(), any())).thenAnswer(invocation -> {
            service.onNotification(json.readTree("{\"method\":\"transport/closed\"}"));
            return json.readTree("{}");
        });
        assertThrows(IllegalStateException.class, () -> service.generate("hello", ""));
    }
}
