package com.chessapp.notification.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
/**
 * @author pari on 10/29/26
 * check expiry of delivery when a subscribed client remains silent
 */
@Component
@RequiredArgsConstructor
public class WebSocketSessionAccess {

    private final Clock clock;

    // Keep only the expiry needed for outbound access checks.
    // The authenticated principal already retains the JWT.
    private final Map<String, Instant> expirations =
            new ConcurrentHashMap<>();

    public void register(
            String sessionId,
            Instant expiresAt
    ) {
        if (sessionId == null || sessionId.isBlank()
                || expiresAt == null) {
            throw new IllegalArgumentException(
                    "Session ID and token expiry are required"
            );
        }

        if (expirations.putIfAbsent(sessionId, expiresAt) != null) {
            throw new IllegalStateException(
                    "The WebSocket session is already authenticated"
            );
        }
    }

    public boolean canReceive(String sessionId) {
        if (sessionId == null) {
            return false;
        }

        Instant expiresAt = expirations.get(sessionId);

        return expiresAt != null
                && clock.instant().isBefore(expiresAt);
    }

    @EventListener
    public void onDisconnected(SessionDisconnectEvent event) {
        // Disconnect events may be repeated; removal is idempotent.
        expirations.remove(event.getSessionId());
    }
}
