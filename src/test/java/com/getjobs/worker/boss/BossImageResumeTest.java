package com.getjobs.worker.boss;

import com.getjobs.application.service.AiService;
import com.getjobs.application.service.BossService;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BossImageResumeTest {

    @Test
    void uploadsThroughHiddenFileInputWithoutWaitingForVisibility() throws Exception {
        Page page = mock(Page.class);
        Locator container = mock(Locator.class);
        Locator input = mock(Locator.class);
        when(page.url()).thenReturn("https://www.zhipin.com/web/geek/chat");
        when(page.locator("div.btn-sendimg[aria-label='发送图片'], div[aria-label='发送图片'].btn-sendimg"))
                .thenReturn(container);
        when(container.locator("input[type='file'][accept*='image']")).thenReturn(input);
        when(input.first()).thenReturn(input);
        when(input.count()).thenReturn(1);
        doThrow(new RuntimeException("hidden file input is never visible"))
                .when(input).waitFor(any(Locator.WaitForOptions.class));

        Boss boss = new Boss(mock(BossService.class), mock(AiService.class));
        Method sendImageResume = Boss.class.getDeclaredMethod("sendImageResume", Page.class);
        sendImageResume.setAccessible(true);

        assertTrue((Boolean) sendImageResume.invoke(boss, page));
        verify(input, never()).waitFor(any(Locator.WaitForOptions.class));
        verify(input).setInputFiles(any(Path.class));
    }
}
