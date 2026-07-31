package com.getjobs.worker.boss;

import com.getjobs.application.service.ConfigService;
import com.getjobs.worker.manager.PlaywrightManager;
import com.getjobs.worker.service.BossJobService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BossJobServiceConcurrencyTest {

    @Test
    void acceptsOnlyOneConcurrentDeliveryStart() throws Exception {
        PlaywrightManager manager = mock(PlaywrightManager.class);
        ConfigService configService = mock(ConfigService.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<Boss> bossProvider = mock(ObjectProvider.class);
        CyclicBarrier bothRequestsPassedInitialGuard = new CyclicBarrier(2);
        AtomicInteger executions = new AtomicInteger();

        when(manager.hasPage("boss")).thenAnswer(invocation -> {
            try {
                bothRequestsPassedInitialGuard.await(2, TimeUnit.SECONDS);
            } catch (BrokenBarrierException | TimeoutException ignored) {
                // With an atomic guard only the winning request reaches this point.
            }
            return true;
        });
        when(manager.isLoggedIn("boss")).thenReturn(true);
        when(manager.withPage(eq("boss"), any())).thenAnswer(invocation -> {
            executions.incrementAndGet();
            return 0;
        });

        BossJobService service = new BossJobService(manager, configService, bossProvider);
        List<Object> messages = Collections.synchronizedList(new java.util.ArrayList<>());
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = executor.submit(() -> service.executeDelivery(messages::add));
            Future<?> second = executor.submit(() -> service.executeDelivery(messages::add));
            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        assertEquals(1, executions.get());
    }
}
