package com.getjobs.worker.boss;

import com.getjobs.application.service.AiService;
import com.getjobs.application.service.BossService;
import com.getjobs.worker.utils.Job;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Response;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Predicate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertEquals;

class BossChatTimingTest {

    @Test
    void completesGreetingImageAndDeliveryStatusInOrder() throws Exception {
        BossService bossService = mock(BossService.class);
        AiService aiService = mock(AiService.class);
        Page searchPage = mock(Page.class);
        BrowserContext context = mock(BrowserContext.class);
        Page detailPage = mock(Page.class);
        Locator empty = mock(Locator.class);
        Locator moreInfo = mock(Locator.class);
        Locator chatButton = mock(Locator.class);
        Locator continueRoot = mock(Locator.class);
        Locator continueButton = mock(Locator.class);
        Locator input = mock(Locator.class);
        Locator sendButton = mock(Locator.class);
        Locator imageContainer = mock(Locator.class);
        Locator imageInput = mock(Locator.class);
        Response friendAddResponse = mock(Response.class);

        when(searchPage.locator(anyString())).thenReturn(empty);
        when(searchPage.locator("a.more-job-btn")).thenReturn(moreInfo);
        when(moreInfo.count()).thenReturn(1);
        when(moreInfo.first()).thenReturn(moreInfo);
        when(moreInfo.getAttribute("href")).thenReturn("/job_detail/test.html");
        when(searchPage.context()).thenReturn(context);
        when(context.newPage()).thenReturn(detailPage);

        when(detailPage.locator(anyString())).thenReturn(empty);
        when(detailPage.locator("a.btn-startchat, a.op-btn-chat, a:has-text('立即沟通'), button:has-text('立即沟通'), [role='button']:has-text('立即沟通')")).thenReturn(chatButton);
        when(chatButton.count()).thenReturn(1);
        when(chatButton.first()).thenReturn(chatButton);
        when(chatButton.nth(anyInt())).thenReturn(chatButton);
        when(chatButton.isVisible()).thenReturn(true);
        when(chatButton.textContent()).thenReturn("立即沟通");
        when(detailPage.locator(".greet-boss-pop .dialog-container")).thenReturn(continueRoot);
        when(continueRoot.getByText(eq("继续沟通"), any(Locator.GetByTextOptions.class))).thenReturn(continueButton);
        when(continueButton.count()).thenReturn(0);
        when(detailPage.locator("#chat-input, textarea.input-area, div.chat-input[contenteditable], [contenteditable='true'][role='textbox']"))
                .thenReturn(input);
        when(input.count()).thenReturn(1);
        when(input.first()).thenReturn(input);
        when(input.nth(anyInt())).thenReturn(input);
        when(input.isVisible()).thenReturn(true);
        when(input.evaluate("el => el.tagName.toLowerCase()"))
                .thenThrow(new RuntimeException("element was detached from the DOM during navigation"))
                .thenReturn("textarea");
        when(input.evaluate("el => el.value ?? el.textContent ?? ''")).thenReturn("");
        when(detailPage.url()).thenReturn("https://www.zhipin.com/web/geek/chat");
        when(chatButton.getAttribute("redirect-url")).thenReturn("/web/geek/chat?id=test");
        when(detailPage.waitForResponse(org.mockito.ArgumentMatchers.<Predicate<Response>>any(),
                any(Page.WaitForResponseOptions.class), any(Runnable.class)))
                .thenReturn(friendAddResponse);
        when(friendAddResponse.text()).thenReturn("{\"chatRemindDialog\":{\"content\":\"还剩30次沟通机会\"}}");
        when(friendAddResponse.status()).thenReturn(200);
        when(detailPage.locator("div.send-message, button[type='send'].btn-send, button.btn-send")).thenReturn(sendButton);
        when(sendButton.count()).thenReturn(1);
        when(sendButton.first()).thenReturn(sendButton);
        when(sendButton.isVisible()).thenReturn(true);
        when(sendButton.isEnabled()).thenReturn(true);
        when(detailPage.locator("div.btn-sendimg[aria-label='发送图片'], div[aria-label='发送图片'].btn-sendimg"))
                .thenReturn(imageContainer);
        when(imageContainer.locator("input[type='file'][accept*='image']")).thenReturn(imageInput);
        when(imageInput.first()).thenReturn(imageInput);
        when(imageInput.count()).thenReturn(1);
        when(empty.first()).thenReturn(empty);
        when(aiService.sendRequest(anyString())).thenReturn("你好，我对这个岗位很感兴趣");

        BossConfig config = new BossConfig();
        config.setDebugger(false);
        config.setEnableAI(true);
        config.setSayHi("你好");
        config.setSendImgResume(true);

        Job job = new Job();
        job.setJobName("Java开发工程师");
        job.setCompanyName("测试公司");
        job.setSalary("20-30K");
        job.setJobInfo("负责Java服务开发");

        Boss boss = new Boss(bossService, aiService);
        boss.setPage(searchPage);
        boss.setConfig(config);
        Field userIdMap = Boss.class.getDeclaredField("encryptIdToUserId");
        userIdMap.setAccessible(true);
        @SuppressWarnings("unchecked")
        ConcurrentMap<String, String> encryptIdToUserId =
                (ConcurrentMap<String, String>) userIdMap.get(boss);
        encryptIdToUserId.put("test", "user");

        Method submit = Boss.class.getDeclaredMethod("resumeSubmission", String.class, Job.class);
        submit.setAccessible(true);
        submit.invoke(boss, "Java", job);

        InOrder order = inOrder(aiService, chatButton, detailPage, sendButton, imageInput, bossService);
        order.verify(aiService).sendRequest(anyString());
        order.verify(chatButton).click();
        order.verify(detailPage).navigate("https://www.zhipin.com/web/geek/chat?id=test");
        order.verify(sendButton).click();
        order.verify(imageInput).setInputFiles(any(Path.class));
        order.verify(bossService).updateDeliveryStatus("test", "user", "已投递");
        verify(empty, never()).click();
        assertEquals(1, boss.getResultList().size());
    }
}
