package com.getjobs.worker.boss;

import com.getjobs.application.service.AiService;
import com.getjobs.application.service.BossService;
import com.getjobs.worker.utils.Job;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BossContinuousDeliveryTest {

    @Test
    void skipsOneTimedOutSubmissionInsteadOfStoppingTheBatch() throws Exception {
        BossService bossService = mock(BossService.class);
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
        when(input.evaluate("el => el.tagName.toLowerCase()")).thenReturn("textarea");
        when(detailPage.locator("div.send-message, button[type='send'].btn-send, button.btn-send")).thenReturn(sendButton);
        when(sendButton.count()).thenReturn(1);
        when(sendButton.first()).thenReturn(sendButton);
        when(sendButton.isVisible()).thenReturn(true);
        when(sendButton.isEnabled()).thenReturn(true);
        when(empty.first()).thenReturn(empty);
        org.mockito.Mockito.doThrow(new RuntimeException("Timeout 30000ms exceeded"))
                .when(sendButton).click();

        BossConfig config = new BossConfig();
        config.setDebugger(false);
        config.setEnableAI(false);
        config.setSayHi("你好");
        config.setSendImgResume(false);

        Job job = new Job();
        job.setJobName("Java开发工程师");
        job.setCompanyName("测试公司");

        Boss boss = new Boss(bossService, mock(AiService.class));
        boss.setPage(searchPage);
        boss.setConfig(config);

        Method submit = Boss.class.getDeclaredMethod("resumeSubmission", String.class, Job.class);
        submit.setAccessible(true);

        assertDoesNotThrow(() -> submit.invoke(boss, "Java", job));
    }

    @Test
    void recognizesBossDailyDeliveryLimitMessages() {
        assertTrue(Boss.isPlatformDeliveryLimitMessage("今日沟通人数已达上限，明天再来吧"));
        assertFalse(Boss.isPlatformDeliveryLimitMessage("该职位暂时不能沟通"));
    }

    @Test
    void treatsRemainingQuotaReminderAsAcceptedCommunication() {
        assertTrue(Boss.isSoftCommunicationAccepted("{\"chatRemindDialog\":{\"content\":\"还剩30次沟通机会\"}}"));
        assertFalse(Boss.isSoftCommunicationAccepted("{\"chatRemindDialog\":{\"content\":\"今日沟通人数已达上限\"}}"));
        assertFalse(Boss.isSoftCommunicationAccepted("{\"code\":0}"));
    }
}
