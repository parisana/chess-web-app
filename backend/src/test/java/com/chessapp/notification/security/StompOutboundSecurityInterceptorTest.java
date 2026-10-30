package com.chessapp.notification.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.CloseStatus;

import java.time.Clock;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StompOutboundSecurityInterceptorTest {

    private static final Instant NOW =
            Instant.parse("2026-10-29T06:00:00Z");

    private Clock clock;
    private WebSocketSessionAccess access;
    private StompOutboundSecurityInterceptor interceptor;

    @BeforeEach
    void setUp() {
        clock = mock(Clock.class);
        when(clock.instant()).thenReturn(NOW);

        access = new WebSocketSessionAccess(clock);
        interceptor = new StompOutboundSecurityInterceptor(access);
    }

    @Test
    void permitsMessagesForAnUnexpiredAuthenticatedSession() {
        access.register("session-1", NOW.plusSeconds(60));
        Message<?> message = applicationMessage("session-1");

        assertSame(message, interceptor.preSend(message, null));
        assertSame(
                message,
                interceptor.beforeHandle(message, null, null)
        );
    }

    @Test
    void rejectsMessagesForAnUnknownSession() {
        Message<?> message = applicationMessage("unknown");

        assertNull(interceptor.preSend(message, null));
    }

    @Test
    void rejectsMessagesWithoutASessionId() {
        Message<?> message = applicationMessage(null);

        assertNull(interceptor.preSend(message, null));
    }

    @Test
    void rechecksExpiryAfterWaitingInTheExecutor() {
        access.register("session-1", NOW.plusSeconds(1));
        Message<?> message = applicationMessage("session-1");

        assertSame(message, interceptor.preSend(message, null));

        when(clock.instant()).thenReturn(NOW.plusSeconds(1));

        assertNull(
                interceptor.beforeHandle(message, null, null)
        );
    }

    @Test
    void disconnectRemovesAccessAndCanBeProcessedTwice() {
        access.register("session-1", NOW.plusSeconds(60));
        Message<byte[]> message = applicationMessage("session-1");

        var event = new SessionDisconnectEvent(
                this,
                message,
                "session-1",
                CloseStatus.NORMAL
        );

        access.onDisconnected(event);
        access.onDisconnected(event);

        assertFalse(access.canReceive("session-1"));
        assertNull(interceptor.preSend(message, null));
    }

    private Message<byte[]> applicationMessage(String sessionId) {
        var headers = SimpMessageHeaderAccessor.create(
                SimpMessageType.MESSAGE
        );

        headers.setSessionId(sessionId);

        return MessageBuilder.createMessage(
                new byte[0],
                headers.getMessageHeaders()
        );
    }
}