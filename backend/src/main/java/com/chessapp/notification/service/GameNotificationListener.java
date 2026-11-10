package com.chessapp.notification.service;

import com.chessapp.game.event.GameChangedEvent;
import com.chessapp.notification.config.WebSocketProperties;
import com.chessapp.notification.dto.PlayerUpdate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

@Component
@Slf4j
public class GameNotificationListener {

    private final SimpMessagingTemplate messagingTemplate;
    private final WebSocketProperties properties;
    private final Executor notificationExecutor;

    public GameNotificationListener(SimpMessagingTemplate messagingTemplate, WebSocketProperties properties,
                                    @Qualifier("gameNotificationExecutor") Executor notificationExecutor) {
        this.messagingTemplate = messagingTemplate;
        this.properties = properties;
        this.notificationExecutor = notificationExecutor;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onGameChanged(GameChangedEvent event) {
        try {
            // AFTER_COMMIT precedes transaction-resource cleanup. Handoff must not
            // wait for delivery or run it inline while the game connection is held.
            // Only an immutable event crosses threads; no managed entity is captured.
            notificationExecutor.execute(() -> deliver(event));
        } catch (RejectedExecutionException exception) {
            // Hints are best effort; existing HTTP reconciliation recovers state.
            // Do not retry a committed command or fall back to inline delivery.
            log.warn("Notification queue rejected {} for game {} at version {}",
                    event.type(), event.gameId(), event.version(), exception);
        }
    }

    private void deliver(GameChangedEvent event) {
        var update = new PlayerUpdate(event.type().name(), event.gameId(), event.version(), event.snapshot());
        notifyPlayer(event.whitePlayerId(), update);
        notifyPlayer(event.blackPlayerId(), update);
    }

    private void notifyPlayer(long playerId, PlayerUpdate update) {
        try {
            messagingTemplate.convertAndSendToUser(Long.toString(playerId), properties.updatesDestination(), update);
        } catch (RuntimeException exception) {
            // Preserve the other participant's delivery attempt and fail-closed
            // outbound session checks even if this participant cannot be notified.
            log.warn("Could not enqueue {} for Player {} and game {}",
                    update.type(), playerId, update.gameId(), exception);
        }
    }
}
