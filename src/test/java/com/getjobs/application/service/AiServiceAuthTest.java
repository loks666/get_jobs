package com.getjobs.application.service;

import com.getjobs.application.mapper.AiMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AiServiceAuthTest {
    @Test void chatGptModeDoesNotRequireApiKeyOrBaseUrl() {
        ConfigService config = mock(ConfigService.class);
        ChatGptService chatgpt = mock(ChatGptService.class);
        when(config.getConfigValue("AI_AUTH_MODE")).thenReturn("chatgpt");
        when(config.getConfigValue("CHATGPT_MODEL")).thenReturn("selected-model");
        when(chatgpt.generate("hello", "selected-model")).thenReturn("reply");
        var ai = new AiService(config, mock(AiMapper.class), chatgpt);
        assertEquals("reply", ai.sendRequest("hello"));
        verify(config, never()).getAiConfigs();
    }

    @Test void existingConfigurationsStillUseApiKeyAndInvalidModesFail() {
        ConfigService config = mock(ConfigService.class);
        ChatGptService chatgpt = mock(ChatGptService.class);
        when(config.getAiConfigs()).thenThrow(new IllegalStateException("缺少必要配置: API_KEY"));
        var ai = new AiService(config, mock(AiMapper.class), chatgpt);
        assertTrue(assertThrows(IllegalStateException.class, () -> ai.sendRequest("hello")).getMessage().contains("API_KEY"));
        verifyNoInteractions(chatgpt);
        when(config.getConfigValue("AI_AUTH_MODE")).thenReturn("wrong");
        assertTrue(assertThrows(IllegalStateException.class, () -> ai.sendRequest("hello")).getMessage().contains("不支持"));
    }
}
