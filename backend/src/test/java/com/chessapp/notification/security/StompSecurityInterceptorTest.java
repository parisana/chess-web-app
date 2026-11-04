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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StompSecurityInterceptorTest {

    private WebSocketSessionAccess access;
    private StompSecurityInterceptor interceptor;

    @BeforeEach
    void setUp() {
        access = mock(WebSocketSessionAccess.class);

        var properties = new WebSocketProperties(
                "/api/v1/ws",
                "http://localhost:4200",
                "/app",
                "/user",
                "/queue",
                "/updates",
                Duration.ofSeconds(10),
                Duration.ofSeconds(10)
        );

        interceptor = new StompSecurityInterceptor(properties, access);

        when(access.canAccess("socket-1", "42")).thenReturn(true);
    }

    @Test
    void connectUsesHandshakeIdentityAndRequiresCsrf() {
        var headers = authenticated(StompCommand.CONNECT);

        var attributes = new HashMap<String, Object>();
        attributes.put(
                SessionHandshakeInterceptor.HTTP_SESSION_ID,
                "http-1"
        );
        attributes.put(
                SessionHandshakeInterceptor.PLAYER_NAME,
                "42"
        );
        attributes.put(
                SessionHandshakeInterceptor.CSRF_TOKEN,
                "csrf-value"
        );

        headers.setSessionAttributes(attributes);
        headers.setNativeHeader("X-XSRF-TOKEN", "csrf-value");

        Message<?> message = message(headers);

        assertSame(message, interceptor.preSend(message, null));

        verify(access).register("socket-1", "http-1", "42");

        assertEquals("42", headers.getUser().getName());
        assertNull(headers.getNativeHeader("X-XSRF-TOKEN"));
        assertFalse(attributes.containsKey(
                SessionHandshakeInterceptor.CSRF_TOKEN
        ));
    }

    @Test
    void connectRejectsMissingCsrfEvenWithAuthenticatedHandshake() {
        var headers = authenticated(StompCommand.CONNECT);

        headers.setSessionAttributes(new HashMap<>(java.util.Map.of(
                SessionHandshakeInterceptor.HTTP_SESSION_ID, "http-1",
                SessionHandshakeInterceptor.PLAYER_NAME, "42",
                SessionHandshakeInterceptor.CSRF_TOKEN, "expected"
        )));

        assertThrows(
                AccessDeniedException.class,
                () -> interceptor.preSend(message(headers), null)
        );

        verify(access, never()).register(any(), any(), any());
    }

    @Test
    void bearerHeaderCannotReplaceAuthenticatedHandshake() {
        var headers = StompHeaderAccessor.create(StompCommand.CONNECT);
        headers.setNativeHeader("Authorization", "Bearer old-token");

        assertThrows(
                BadCredentialsException.class,
                () -> interceptor.preSend(message(headers), null)
        );
    }

    @Test
    void permitsExactPrivateSubscription() {
        var headers = authenticated(StompCommand.SUBSCRIBE);
        headers.setDestination("/user/queue/updates");

        Message<?> message = message(headers);

        assertSame(message, interceptor.preSend(message, null));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/queue/updates",
            "/topic/games/1",
            "/user/99/queue/updates",
            "/user/queue/updates/extra",
            "/user/queue/*"
    })
    void rejectsOtherDestinations(String destination) {
        var headers = authenticated(StompCommand.SUBSCRIBE);
        headers.setDestination(destination);

        assertThrows(
                AccessDeniedException.class,
                () -> interceptor.preSend(message(headers), null)
        );
    }

    @Test
    void rejectsOperationsAfterHttpSessionInvalidation() {
        when(access.canAccess("socket-1", "42")).thenReturn(false);

        var headers = authenticated(StompCommand.SUBSCRIBE);
        headers.setDestination("/user/queue/updates");

        assertThrows(
                BadCredentialsException.class,
                () -> interceptor.preSend(message(headers), null)
        );
    }

    @Test
    void rejectsClientPublishing() {
        var headers = authenticated(StompCommand.SEND);
        headers.setDestination("/user/queue/updates");

        assertThrows(
                AccessDeniedException.class,
                () -> interceptor.preSend(message(headers), null)
        );
    }

    @Test
    void permitsDisconnectAfterSessionInvalidation() {
        when(access.canAccess("socket-1", "42")).thenReturn(false);

        var headers = authenticated(StompCommand.DISCONNECT);
        Message<?> message = message(headers);

        assertSame(message, interceptor.preSend(message, null));
    }

    private StompHeaderAccessor authenticated(StompCommand command) {
        var headers = StompHeaderAccessor.create(command);

        headers.setUser(
                UsernamePasswordAuthenticationToken.authenticated(
                        "42",
                        null,
                        List.of()
                )
        );

        return headers;
    }

    private Message<?> message(StompHeaderAccessor headers) {
        headers.setSessionId("socket-1");
        headers.setLeaveMutable(true);

        return MessageBuilder.createMessage(
                new byte[0],
                headers.getMessageHeaders()
        );
    }
}