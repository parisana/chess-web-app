package com.chessapp.notification.service;

import com.chessapp.game.event.GameChangedEvent;
import com.chessapp.notification.config.WebSocketProperties;
import com.chessapp.notification.dto.PlayerUpdate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * @author pari on 10/29/26
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class GameNotificationListener {

    private final SimpMessagingTemplate messagingTemplate;
    private final WebSocketProperties properties;

    @TransactionalEventListener(
            phase = TransactionPhase.AFTER_COMMIT
    )
    public void onGameChanged(GameChangedEvent event) {
        var update = new PlayerUpdate(
                event.type().name(),
                event.gameId(),
                event.version()
        );

        notifyPlayer(event.whitePlayerId(), update);
        notifyPlayer(event.blackPlayerId(), update);
    }

    private void notifyPlayer(
            long playerId,
            PlayerUpdate update
    ) {
        try {
            messagingTemplate.convertAndSendToUser(
                    Long.toString(playerId),
                    properties.updatesDestination(),
                    update
            );
        } catch (RuntimeException exception) {
            // The database transaction has already committed.
            // A delivery failure must not make the HTTP command look
            // unsuccessful or prevent attempting the other recipient.
            log.warn(
                    "Could not enqueue {} for Player {} and game {}",
                    update.type(),
                    playerId,
                    update.gameId(),
                    exception
            );
        }
    }
}