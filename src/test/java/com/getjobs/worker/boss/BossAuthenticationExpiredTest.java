package com.getjobs.worker.boss;

import com.getjobs.application.service.AiService;
import com.getjobs.application.service.BossService;
import com.getjobs.application.service.ConfigService;
import com.getjobs.worker.dto.JobProgressMessage;
import com.getjobs.worker.manager.PlaywrightManager;
import com.getjobs.worker.service.BossJobService;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BossAuthenticationExpiredTest {

    @Test
    void loginDialogStopsSubmissionWithDedicatedException() throws Exception {
        Page page = mock(Page.class);
        Locator body = mock(Locator.class);
        when(page.url()).thenReturn("https://www.zhipin.com/job_detail/test.html");
        when(page.locator("body")).thenReturn(body);
        when(body.count()).thenReturn(1);
        when(body.first()).thenReturn(body);
        when(body.innerText()).thenReturn("立即登录，享受优质服务");

        Boss boss = new Boss(mock(BossService.class), mock(AiService.class));
        Method check = Boss.class.getDeclaredMethod("ensureBossSession", Page.class);
        check.setAccessible(true);

        InvocationTargetException error = assertThrows(InvocationTargetException.class, () -> check.invoke(boss, page));
        assertInstanceOf(BossAuthenticationExpiredException.class, error.getCause());
    }

    @Test
    void recognizesAuthenticationResponses() {
        assertEquals(true, Boss.isAuthenticationStatus(401));
        assertEquals(true, Boss.isAuthenticationStatus(403));
        assertEquals(false, Boss.isAuthenticationStatus(500));
    }

    @Test
    void serviceStopsBatchAndEmitsExpiredCode() {
        PlaywrightManager manager = mock(PlaywrightManager.class);
        ConfigService configService = mock(ConfigService.class);
        ObjectProvider<Boss> bossProvider = mock(ObjectProvider.class);
        when(manager.hasPage("boss")).thenReturn(true);
        when(manager.isLoggedIn("boss")).thenReturn(true);
        doThrow(new BossAuthenticationExpiredException("expired"))
                .when(manager).withPage(eq("boss"), any());

        BossJobService service = new BossJobService(manager, configService, bossProvider);
        List<JobProgressMessage> messages = new ArrayList<>();
        service.executeDelivery(messages::add);

        verify(manager).handleBossAuthenticationExpired();
        assertEquals(false, service.isRunning());
        JobProgressMessage expired = messages.stream()
                .filter(message -> "BOSS_COOKIE_EXPIRED".equals(message.getCode()))
                .findFirst()
                .orElseThrow();
        assertEquals("error", expired.getType());
    }

    @Test
    void serviceStopsBatchAndEmitsDailyLimitCode() {
        PlaywrightManager manager = mock(PlaywrightManager.class);
        ConfigService configService = mock(ConfigService.class);
        ObjectProvider<Boss> bossProvider = mock(ObjectProvider.class);
        when(manager.hasPage("boss")).thenReturn(true);
        when(manager.isLoggedIn("boss")).thenReturn(true);
        doThrow(new BossDailyDeliveryLimitReachedException("limit"))
                .when(manager).withPage(eq("boss"), any());

        BossJobService service = new BossJobService(manager, configService, bossProvider);
        List<JobProgressMessage> messages = new ArrayList<>();
        service.executeDelivery(messages::add);

        assertEquals(false, service.isRunning());
        JobProgressMessage limit = messages.stream()
                .filter(message -> "BOSS_DAILY_DELIVERY_LIMIT_REACHED".equals(message.getCode()))
                .findFirst()
                .orElseThrow();
        assertEquals("warning", limit.getType());
        assertEquals(0, messages.stream().filter(message -> "success".equals(message.getType())).count());
    }

    @Test
    void loginStatusCanChangeFromLoggedInToLoggedOut() throws Exception {
        PlaywrightManager manager = new PlaywrightManager();
        Page page = mock(Page.class);
        when(page.locator(any(String.class))).thenReturn(mock(Locator.class));
        setField(manager, "bossPage", page);
        manager.setLoginStatus("boss", true);
        List<Boolean> changes = new ArrayList<>();
        manager.addLoginStatusListener(change -> changes.add(change.isLoggedIn()));

        Method check = PlaywrightManager.class.getDeclaredMethod("checkLoginStatus", Page.class, String.class);
        check.setAccessible(true);
        check.invoke(manager, page, "boss");

        assertEquals(false, manager.isLoggedIn("boss"));
        assertEquals(List.of(false), changes);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
