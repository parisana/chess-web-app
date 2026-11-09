package com.chessapp.notification.config;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class NotificationDeliveryConfigTest {

    @Test
    void saturatedExecutorRejectsInsteadOfRunningDeliveryOnTheCaller() throws InterruptedException {
        var properties = new NotificationDeliveryProperties(1, 1);
        var executor = new NotificationDeliveryConfig().gameNotificationExecutor(properties);
        var running = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        executor.initialize();

        try {
            executor.execute(() -> {
                running.countDown();
                try {
                    release.await();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
            });
            assertTrue(running.await(5, TimeUnit.SECONDS), "Worker must start before filling the queue");
            executor.execute(() -> { }); // The only queue slot is now occupied.

            // Protect the actual configuration, not just a mocked rejecting executor.
            assertThrows(RejectedExecutionException.class,
                    () -> executor.execute(() -> fail("Must not run on the calling thread")));
        } finally {
            release.countDown();
            executor.shutdown();
        }
    }
}
