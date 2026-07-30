package com.getjobs.worker.boss;

import com.getjobs.application.service.AiService;
import com.getjobs.application.service.BossService;
import com.getjobs.worker.utils.Job;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.lang.reflect.Method;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BossChatTimingTest {

    @Test
    void generatesGreetingBeforeOpeningChat() throws Exception {
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
        when(detailPage.locator("div#chat-input.chat-input[contenteditable='true'], textarea.input-area, [contenteditable='true'][role='textbox']"))
                .thenReturn(input);
        when(input.count()).thenReturn(1);
        when(input.first()).thenReturn(input);
        when(input.isVisible()).thenReturn(true);
        when(input.evaluate("el => el.tagName.toLowerCase()"))
                .thenThrow(new RuntimeException("element was detached from the DOM during navigation"))
                .thenReturn("textarea");
        when(detailPage.locator("div.send-message, button[type='send'].btn-send, button.btn-send")).thenReturn(sendButton);
        when(sendButton.count()).thenReturn(1);
        when(sendButton.first()).thenReturn(sendButton);
        when(sendButton.isVisible()).thenReturn(true);
        when(sendButton.isEnabled()).thenReturn(true);
        when(empty.first()).thenReturn(empty);
        when(aiService.sendRequest(anyString())).thenReturn("你好，我对这个岗位很感兴趣");

        BossConfig config = new BossConfig();
        config.setDebugger(false);
        config.setEnableAI(true);
        config.setSayHi("你好");
        config.setSendImgResume(false);

        Job job = new Job();
        job.setJobName("Java开发工程师");
        job.setCompanyName("测试公司");
        job.setSalary("20-30K");
        job.setJobInfo("负责Java服务开发");

        Boss boss = new Boss(bossService, aiService);
        boss.setPage(searchPage);
        boss.setConfig(config);

        Method submit = Boss.class.getDeclaredMethod("resumeSubmission", String.class, Job.class);
        submit.setAccessible(true);
        submit.invoke(boss, "Java", job);

        InOrder order = inOrder(aiService, chatButton);
        order.verify(aiService).sendRequest(anyString());
        order.verify(chatButton).click();
    }
}
