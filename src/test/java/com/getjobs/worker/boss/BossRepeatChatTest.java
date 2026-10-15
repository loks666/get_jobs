package com.getjobs.worker.boss;

import com.getjobs.application.service.BossService;
import com.getjobs.worker.utils.Job;
import com.getjobs.worker.utils.PlaywrightUtil;
import com.microsoft.playwright.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BossRepeatChatTest {
    @ParameterizedTest
    @CsvSource({"立即沟通,false,true", "立即沟通,true,true", "继续沟通,false,false", "继续沟通,true,true", "立即沟通,,true", "继续沟通,,false", "已关闭,true,false"})
    void sendsOnlyWhenButtonAndSwitchAllow(String text, Boolean enabled, boolean expected) throws Exception {
        BossService service = mock(BossService.class);
        Boss boss = new Boss(service, null, null);
        BossConfig config = new BossConfig();
        config.setAllowRepeatChat(enabled);
        config.setSayHi("您好，希望进一步沟通。");
        boss.setConfig(config);
        Page listing = mock(Page.class);
        Page detail = mock(Page.class);
        BrowserContext context = mock(BrowserContext.class);
        Locator more = mock(Locator.class);
        Locator button = mock(Locator.class);
        Locator input = mock(Locator.class);
        Locator send = mock(Locator.class);
        when(listing.locator(anyString())).thenReturn(more);
        when(more.count()).thenReturn(1);
        when(more.first()).thenReturn(more);
        when(more.getAttribute("href")).thenReturn("/job_detail/test.html");
        when(listing.context()).thenReturn(context);
        when(context.newPage()).thenReturn(detail);
        when(detail.locator("a.btn-startchat, a.op-btn-chat")).thenReturn(button);
        when(button.count()).thenReturn(1);
        when(button.first()).thenReturn(button);
        when(button.textContent()).thenReturn(text);
        when(detail.locator("div#chat-input.chat-input[contenteditable='true'], textarea.input-area")).thenReturn(input);
        when(input.count()).thenReturn(1);
        when(input.first()).thenReturn(input);
        when(input.isVisible()).thenReturn(true);
        when(input.evaluate(anyString())).thenReturn("textarea");
        when(detail.locator("div.send-message, button[type='send'].btn-send, button.btn-send")).thenReturn(send);
        when(send.count()).thenReturn(1);
        when(send.first()).thenReturn(send);
        boss.setPage(listing);
        var submission = Boss.class.getDeclaredMethod("resumeSubmission", String.class, Job.class);
        submission.setAccessible(true);
        try (var ignored = mockStatic(PlaywrightUtil.class)) {
            assertEquals(expected, submission.invoke(boss, "Java", new Job()));
        }
        verify(button, times(expected ? 1 : 0)).click();
        verify(input, times(expected ? 1 : 0)).fill(config.getSayHi());
        verify(send, times(expected ? 1 : 0)).click();
        verify(detail).close();
    }
}
