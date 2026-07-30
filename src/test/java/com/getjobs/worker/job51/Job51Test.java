package com.getjobs.worker.job51;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class Job51Test {
    @Test
    void clicksBatchDeliverWhenPageHasSingleButton() throws Exception {
        Page page = mock(Page.class);
        Locator parent = mock(Locator.class);
        Locator buttons = mock(Locator.class);
        Locator button = mock(Locator.class);

        when(page.locator("div.tabs_in")).thenReturn(parent);
        when(parent.locator("button.p_but")).thenReturn(buttons);
        when(buttons.count()).thenReturn(1);
        when(buttons.first()).thenReturn(button);

        Job51 job51 = new Job51(null);
        job51.setPage(page);

        Method clickBatchDeliverButton = Job51.class.getDeclaredMethod("clickBatchDeliverButton");
        clickBatchDeliverButton.setAccessible(true);
        clickBatchDeliverButton.invoke(job51);

        verify(button).click();
    }

    @Test
    void marksCurrentPageDeliveredWhenAppAdConfirmsSuccess() throws Exception {
        Page page = mock(Page.class);
        Locator empty = mock(Locator.class);
        Locator successContent = mock(Locator.class);
        Locator popupClose = mock(Locator.class);
        Locator dialogBodies = mock(Locator.class);
        Locator dialogBody = mock(Locator.class);
        com.getjobs.application.service.Job51Service service =
                mock(com.getjobs.application.service.Job51Service.class);

        when(page.locator(anyString())).thenReturn(empty);
        when(page.locator("//div[@class='successContent']")).thenReturn(successContent);
        when(page.locator("[class*='van-icon van-icon-cross van-popup__close-icon van-popup__close-icon--top-right']"))
                .thenReturn(popupClose);
        when(page.locator(".el-dialog__body")).thenReturn(dialogBodies);
        when(successContent.count()).thenReturn(1);
        when(successContent.textContent()).thenReturn("投递成功！快来扫码下载");
        when(popupClose.count()).thenReturn(1);
        when(dialogBodies.count()).thenReturn(1);
        when(dialogBodies.first()).thenReturn(dialogBody);
        when(dialogBody.innerText()).thenReturn("投递成功！HR会优先通过APP在线与你沟通");

        Job51 job51 = new Job51(service);
        job51.setPage(page);
        Field currentPageJobIds = Job51.class.getDeclaredField("currentPageJobIds");
        currentPageJobIds.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<Long> ids = (List<Long>) currentPageJobIds.get(job51);
        ids.addAll(List.of(101L, 102L));

        Method handler = Job51.class.getDeclaredMethod("handleDeliverySuccessDialog", int.class);
        handler.setAccessible(true);
        handler.invoke(job51, 1);

        verify(service).markDeliveredBatch(List.of(101L));
    }

    @Test
    void clicksPageNumberWhenLegacyJumpInputIsMissing() throws Exception {
        Page page = mock(Page.class);
        Locator empty = mock(Locator.class);
        Locator pagination = mock(Locator.class);
        Locator pageNumber = mock(Locator.class);

        when(page.locator(anyString())).thenReturn(empty);
        when(page.locator("div.bottom-page")).thenReturn(pagination);
        when(pagination.getByText(eq("2"), any(Locator.GetByTextOptions.class))).thenReturn(pageNumber);
        when(pageNumber.count()).thenReturn(1);
        when(pageNumber.first()).thenReturn(pageNumber);

        Job51 job51 = new Job51(null);
        job51.setPage(page);

        Method jumpToPage = Job51.class.getDeclaredMethod("jumpToPage", int.class);
        jumpToPage.setAccessible(true);

        assertTrue((boolean) jumpToPage.invoke(job51, 2));
        verify(pageNumber).click();
    }
}
