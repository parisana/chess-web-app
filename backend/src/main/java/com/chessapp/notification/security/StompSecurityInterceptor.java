package com.chessapp.notification.security;

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
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import com.chessapp.notification.config.WebSocketProperties;

import java.time.Clock;
import java.util.List;

@Component
@RequiredArgsConstructor
public class StompSecurityInterceptor implements ChannelInterceptor {

    private final JwtDecoder jwtDecoder;
    private final Clock clock;
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

        var command = accessor.getCommand();

        if (command == StompCommand.CONNECT
                || command == StompCommand.STOMP) {
            authenticate(accessor);
            return message;
        }

        // Spring can generate DISCONNECT during connection cleanup.
        // Cleanup must remain possible even without a valid identity.
        if (command == StompCommand.DISCONNECT) {
            return message;
        }

        requireAuthenticatedPlayer(accessor);

        // Heartbeats have no STOMP command or destination.
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

        // Moves use HTTP. Clients cannot publish to application,
        // broker or another Player's destinations.
        throw new AccessDeniedException(
                "Client message command is not permitted"
        );
    }

    private void authenticate(StompHeaderAccessor accessor) {
        List<String> headers =
                accessor.getNativeHeader("Authorization");

        if (headers == null || headers.size() != 1) {
            throw new BadCredentialsException(
                    "One bearer token is required"
            );
        }

        String authorization = headers.getFirst();

        if (authorization == null
                || !authorization.startsWith("Bearer ")
                || authorization.substring(7).isBlank()) {
            throw new BadCredentialsException(
                    "A bearer token is required"
            );
        }

        Jwt jwt;

        try {
            // Reuse signature, issuer, subject and timestamp validation
            // from the existing HTTP security configuration.
            jwt = jwtDecoder.decode(authorization.substring(7));
        } catch (JwtException exception) {
            throw new BadCredentialsException(
                    "Invalid WebSocket token",
                    exception
            );
        }

        requireUnexpired(jwt);

        // Canonicalize the name used for private message routing.
        // JwtConfig already validates a positive Long subject.
        String playerName =
                Long.toString(Long.parseLong(jwt.getSubject()));

        accessor.setUser(
                new JwtAuthenticationToken(
                        jwt,
                        List.of(),
                        playerName
                )
        );
        sessionAccess.register(
                accessor.getSessionId(),
                jwt.getExpiresAt()
        );

        // The JWT is retained in the authenticated principal.
        // Do not retain a second copy in the native message headers.
        accessor.removeNativeHeader("Authorization");
    }

    private void requireAuthenticatedPlayer(
            StompHeaderAccessor accessor
    ) {
        if (!(accessor.getUser()
                instanceof JwtAuthenticationToken authentication)
                || !authentication.isAuthenticated()) {
            throw new AccessDeniedException(
                    "An authenticated Player is required"
            );
        }

        requireUnexpired(authentication.getToken());
    }

    private void requireUnexpired(Jwt jwt) {
        // A long-lived connection must not accept new operations
        // after the token's stated expiry.
        if (jwt.getExpiresAt() == null
                || !clock.instant().isBefore(jwt.getExpiresAt())) {
            throw new BadCredentialsException(
                    "The WebSocket token has expired"
            );
        }
    }
}