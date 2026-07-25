package com.getjobs.worker.job51;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

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
}
