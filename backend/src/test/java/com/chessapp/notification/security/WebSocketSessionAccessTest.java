package com.chessapp.notification.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.session.MapSession;
import org.springframework.session.SessionRepository;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WebSocketSessionAccessTest {

    private SessionRepository<?> repository;
    private WebSocketSessionAccess access;
    private MapSession session;

    @BeforeEach
    void setUp() {
        repository = mock(SessionRepository.class);
        access = new WebSocketSessionAccess(repository);

        session = new MapSession("http-1");
        session.setMaxInactiveInterval(Duration.ofMinutes(30));

        setPlayer("42");

        doReturn(session).when(repository).findById("http-1");
    }

    @Test
    void deletingHttpSessionStopsAccessForAllItsTabs() {
        access.register("tab-a", "http-1", "42");
        access.register("tab-b", "http-1", "42");

        assertTrue(access.canReceive("tab-a"));
        assertTrue(access.canReceive("tab-b"));

        doReturn(null).when(repository).findById("http-1");

        assertFalse(access.canReceive("tab-a"));
        assertFalse(access.canReceive("tab-b"));
    }

    @Test
    void expiredSessionCannotReceiveEvenBeforeCleanup() {
        access.register("socket-1", "http-1", "42");

        session.setLastAccessedTime(Instant.EPOCH);

        assertFalse(access.canReceive("socket-1"));
    }

    @Test
    void changingPlayerIdentityDoesNotTransferAnExistingSocket() {
        access.register("socket-1", "http-1", "42");

        setPlayer("99");

        assertFalse(access.canReceive("socket-1"));
    }

    @Test
    void accessChecksDoNotRefreshSessionActivity() {
        Instant lastAccessed = session.getLastAccessedTime();

        access.register("socket-1", "http-1", "42");

        assertTrue(access.canReceive("socket-1"));
        assertEquals(lastAccessed, session.getLastAccessedTime());

        // This class must never save a session merely to check access.
        verify(repository, never()).save(any());
    }

    @Test
    void disconnectCleanupIsIdempotent() {
        access.register("socket-1", "http-1", "42");

        var headers = StompHeaderAccessor.create(StompCommand.DISCONNECT);
        headers.setSessionId("socket-1");

        var message = MessageBuilder.createMessage(
                new byte[0],
                headers.getMessageHeaders()
        );

        var event = new SessionDisconnectEvent(
                this,
                message,
                "socket-1",
                CloseStatus.NORMAL
        );

        access.onDisconnected(event);
        access.onDisconnected(event);

        assertFalse(access.canReceive("socket-1"));
    }

    private void setPlayer(String playerName) {
        var context = SecurityContextHolder.createEmptyContext();

        context.setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        playerName,
                        null,
                        List.of()
                )
        );

        session.setAttribute(
                HttpSessionSecurityContextRepository
                        .SPRING_SECURITY_CONTEXT_KEY,
                context
        );
    }
}