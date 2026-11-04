package com.chessapp.notification.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StompOutboundSecurityInterceptorTest {

    private WebSocketSessionAccess access;
    private StompOutboundSecurityInterceptor interceptor;

    @BeforeEach
    void setUp() {
        access = mock(WebSocketSessionAccess.class);
        interceptor = new StompOutboundSecurityInterceptor(access);
    }

    @Test
    void permitsMessagesForAnUnexpiredAuthenticatedSession() {
        when(access.canReceive("session-1")).thenReturn(true);
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
    void rechecksSessionAccessAfterWaitingInTheExecutor() {
        when(access.canReceive("session-1")).thenReturn(true);

        Message<?> message = applicationMessage("session-1");

        assertSame(message, interceptor.preSend(message, null));

        // Simulate logout or expiry while waiting in the executor.
        when(access.canReceive("session-1")).thenReturn(false);

        assertNull(
                interceptor.beforeHandle(message, null, null)
        );
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