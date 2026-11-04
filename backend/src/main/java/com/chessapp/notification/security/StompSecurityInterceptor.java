package com.chessapp.notification.security;

import com.chessapp.notification.config.WebSocketProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
@RequiredArgsConstructor
public class StompSecurityInterceptor implements ChannelInterceptor {

    private final WebSocketProperties properties;
    private final WebSocketSessionAccess sessionAccess;

    @Override
    public Message<?> preSend(
            Message<?> message,
            MessageChannel channel
    ) {
        var accessor = MessageHeaderAccessor.getAccessor(
                message,
                StompHeaderAccessor.class
        );

        if (accessor == null) {
            throw new AccessDeniedException(
                    "A STOMP message is required"
            );
        }

        StompCommand command = accessor.getCommand();

        if (command == StompCommand.CONNECT
                || command == StompCommand.STOMP) {
            connect(accessor);
            return message;
        }

        // Disconnect must remain possible after logout or expiry,
        // and when Spring generates cleanup messages.
        if (command == StompCommand.DISCONNECT) {
            return message;
        }

        var auth = requirePlayer(accessor);

        if (!sessionAccess.canAccess(
                accessor.getSessionId(),
                auth.getName()
        )) {
            throw new BadCredentialsException(
                    "The HTTP session is no longer authenticated"
            );
        }

        if (accessor.getMessageType() == SimpMessageType.HEARTBEAT) {
            return message;
        }

        if (command == StompCommand.SUBSCRIBE) {
            if (!properties.subscriptionDestination().equals(
                    accessor.getDestination()
            )) {
                throw new AccessDeniedException(
                        "Subscription destination is not permitted"
                );
            }

            return message;
        }

        if (command == StompCommand.UNSUBSCRIBE) {
            return message;
        }

        // Game commands remain HTTP operations.
        throw new AccessDeniedException(
                "Client message command is not permitted"
        );
    }

    private void connect(StompHeaderAccessor accessor) {
        var auth = requirePlayer(accessor);
        var attributes = accessor.getSessionAttributes();

        if (attributes == null
                || !(attributes.get(
                SessionHandshakeInterceptor.HTTP_SESSION_ID
        ) instanceof String httpSessionId)
                || !(attributes.get(
                SessionHandshakeInterceptor.PLAYER_NAME
        ) instanceof String playerName)
                || !auth.getName().equals(playerName)) {
            throw new BadCredentialsException(
                    "Authenticated handshake metadata is required"
            );
        }

        Object expected = attributes.get(
                SessionHandshakeInterceptor.CSRF_TOKEN
        );

        var supplied = accessor.getNativeHeader("X-XSRF-TOKEN");

        if (!(expected instanceof String expectedToken)
                || supplied == null
                || supplied.size() != 1
                || supplied.getFirst() == null
                || !MessageDigest.isEqual(
                expectedToken.getBytes(StandardCharsets.UTF_8),
                supplied.getFirst().getBytes(StandardCharsets.UTF_8)
        )) {
            throw new AccessDeniedException(
                    "A valid WebSocket CSRF token is required"
            );
        }

        sessionAccess.register(
                accessor.getSessionId(),
                httpSessionId,
                playerName
        );

        // The handshake principal is retained. No bearer-token decoding
        // or replacement identity is needed.
        accessor.removeNativeHeader("Authorization");
        accessor.removeNativeHeader("X-XSRF-TOKEN");
        attributes.remove(SessionHandshakeInterceptor.CSRF_TOKEN);
    }

    private UsernamePasswordAuthenticationToken requirePlayer(
            StompHeaderAccessor accessor
    ) {
        if (!(accessor.getUser()
                instanceof UsernamePasswordAuthenticationToken auth)
                || !auth.isAuthenticated()) {
            throw new BadCredentialsException(
                    "An authenticated Player is required"
            );
        }

        return auth;
    }
}