package com.chessapp.notification.security;

import com.chessapp.notification.config.WebSocketProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * @author pari on 10/29/26
 */
class StompSecurityInterceptorTest {

    private static final Instant NOW =
            Instant.parse("2026-10-29T06:00:00Z");

    private JwtDecoder jwtDecoder;
    private StompSecurityInterceptor interceptor;
    private WebSocketProperties properties;

    @BeforeEach
    void setUp() {
        jwtDecoder = mock(JwtDecoder.class);

        properties = new WebSocketProperties(
                "/api/v1/ws",
                "http://localhost:4200",
                "/app",
                "/user",
                "/queue",
                "/updates",
                Duration.ofSeconds(10),
                Duration.ofSeconds(10)
        );

        interceptor = new StompSecurityInterceptor(
                jwtDecoder,
                Clock.fixed(NOW, ZoneOffset.UTC),
                properties,
                new WebSocketSessionAccess(
                        Clock.fixed(NOW, ZoneOffset.UTC)
                )
        );
    }

    @Test
    void authenticatesConnectUsingTheExistingDecoder() {
        Jwt jwt = token(NOW.plusSeconds(600));
        when(jwtDecoder.decode("valid-token")).thenReturn(jwt);

        var accessor =
                StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setNativeHeader(
                "Authorization",
                "Bearer valid-token"
        );

        Message<?> message = message(accessor);

        assertSame(
                message,
                interceptor.preSend(message, null)
        );

        var authentication = assertInstanceOf(
                JwtAuthenticationToken.class,
                accessor.getUser()
        );

        assertTrue(authentication.isAuthenticated());
        assertEquals("42", authentication.getName());
        assertSame(jwt, authentication.getToken());
        assertNull(accessor.getNativeHeader("Authorization"));
        verify(jwtDecoder).decode("valid-token");
    }

    @Test
    void rejectsConnectWithoutCredentials() {
        var accessor =
                StompHeaderAccessor.create(StompCommand.CONNECT);

        assertThrows(
                BadCredentialsException.class,
                () -> interceptor.preSend(message(accessor), null)
        );

        verifyNoInteractions(jwtDecoder);
    }

    @Test
    void rejectsInvalidToken() {
        when(jwtDecoder.decode("invalid"))
                .thenThrow(new JwtException("Invalid token"));

        var accessor =
                StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setNativeHeader(
                "Authorization",
                "Bearer invalid"
        );

        assertThrows(
                BadCredentialsException.class,
                () -> interceptor.preSend(message(accessor), null)
        );
    }

    @Test
    void permitsTheAuthenticatedPrivateSubscription() {
        var accessor = authenticated(StompCommand.SUBSCRIBE);
        accessor.setDestination(
                properties.subscriptionDestination()
        );

        Message<?> message = message(accessor);

        assertSame(
                message,
                interceptor.preSend(message, null)
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/queue/updates",
            "/topic/games/1",
            "/user/99/queue/updates",
            "/user/queue/updates/extra",
            "/user/queue/*"
    })
    void rejectsOtherSubscriptionDestinations(String destination) {
        var accessor = authenticated(StompCommand.SUBSCRIBE);
        accessor.setDestination(destination);

        assertThrows(
                AccessDeniedException.class,
                () -> interceptor.preSend(message(accessor), null)
        );
    }

    @Test
    void rejectsAnonymousSubscription() {
        var accessor =
                StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination(
                properties.subscriptionDestination()
        );

        assertThrows(
                AccessDeniedException.class,
                () -> interceptor.preSend(message(accessor), null)
        );
    }

    @Test
    void rejectsClientPublishingEvenToItsPrivateDestination() {
        var accessor = authenticated(StompCommand.SEND);
        accessor.setDestination(
                properties.subscriptionDestination()
        );

        assertThrows(
                AccessDeniedException.class,
                () -> interceptor.preSend(message(accessor), null)
        );
    }

    @Test
    void rejectsSubscriptionWhenTheTokenHasExpired() {
        var accessor =
                StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setUser(
                new JwtAuthenticationToken(
                        token(NOW),
                        List.of(),
                        "42"
                )
        );
        accessor.setDestination(
                properties.subscriptionDestination()
        );

        assertThrows(
                BadCredentialsException.class,
                () -> interceptor.preSend(message(accessor), null)
        );
    }

    private StompHeaderAccessor authenticated(
            StompCommand command
    ) {
        var accessor = StompHeaderAccessor.create(command);

        accessor.setUser(
                new JwtAuthenticationToken(
                        token(NOW.plusSeconds(600)),
                        List.of(),
                        "42"
                )
        );

        return accessor;
    }

    private Jwt token(Instant expiresAt) {
        return Jwt.withTokenValue("valid-token")
                .header("alg", "HS256")
                .subject("42")
                .issuedAt(NOW.minusSeconds(60))
                .expiresAt(expiresAt)
                .build();
    }

    private Message<?> message(StompHeaderAccessor accessor) {
        accessor.setSessionId("test-session");
        // Keep the accessor mutable, matching inbound channel handling.
        accessor.setLeaveMutable(true);

        return MessageBuilder.createMessage(
                new byte[0],
                accessor.getMessageHeaders()
        );
    }
}