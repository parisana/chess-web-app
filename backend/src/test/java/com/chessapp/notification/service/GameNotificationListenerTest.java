package com.chessapp.notification.service;

import com.chessapp.game.event.GameChangedEvent;
import com.chessapp.notification.config.WebSocketProperties;
import com.chessapp.notification.dto.PlayerUpdate;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.ArrayDeque;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GameNotificationListenerTest {

    private final SimpMessagingTemplate messaging = mock(SimpMessagingTemplate.class);
    private final WebSocketProperties properties = mock(WebSocketProperties.class);
    private final GameChangedEvent event = new GameChangedEvent(42L, 1L, 2L, 7L, GameChangedEvent.Type.GAME_UPDATED);
    private final PlayerUpdate update = new PlayerUpdate("GAME_UPDATED", 42L, 7L);

    @Test
    void submitsDeliveryWithoutSendingOnTheCallingThread() {
        var tasks = new ArrayDeque<Runnable>();
        var listener = new GameNotificationListener(messaging, properties, tasks::add);
        when(properties.updatesDestination()).thenReturn("/queue/updates");

        listener.onGameChanged(event);

        // Regression guard: sending inside the callback caused nested pool borrowing.
        verifyNoInteractions(messaging);
        assertEquals(1, tasks.size());
        tasks.remove().run();
        verify(messaging).convertAndSendToUser("1", "/queue/updates", update);
        verify(messaging).convertAndSendToUser("2", "/queue/updates", update);
        verifyNoMoreInteractions(messaging);
    }

    @Test
    void rejectedHandoffDoesNotSendInlineOrPropagate() {
        Executor rejecting = task -> { throw new RejectedExecutionException("Queue full"); };
        var listener = new GameNotificationListener(messaging, properties, rejecting);

        assertDoesNotThrow(() -> listener.onGameChanged(event));
        verifyNoInteractions(messaging);
    }

    @Test
    void firstRecipientFailureDoesNotPreventSecondAttempt() {
        var tasks = new ArrayDeque<Runnable>();
        var listener = new GameNotificationListener(messaging, properties, tasks::add);
        when(properties.updatesDestination()).thenReturn("/queue/updates");
        doThrow(new IllegalStateException("Delivery unavailable"))
                .when(messaging).convertAndSendToUser("1", "/queue/updates", update);

        listener.onGameChanged(event);
        assertDoesNotThrow(() -> tasks.remove().run());
        verify(messaging).convertAndSendToUser("2", "/queue/updates", update);
    }
}
