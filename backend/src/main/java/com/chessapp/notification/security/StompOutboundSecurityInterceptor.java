package com.chessapp.notification.security;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.support.ExecutorChannelInterceptor;
import org.springframework.stereotype.Component;
/**
 * @author pari on 10/29/26
 */
@Component
@RequiredArgsConstructor
public class StompOutboundSecurityInterceptor
        implements ExecutorChannelInterceptor {

    private final WebSocketSessionAccess sessionAccess;

    @Override
    public Message<?> preSend(
            Message<?> message,
            MessageChannel channel
    ) {
        // Returning null from a channel interceptor prevents that message from continuing.
        return permitted(message) ? message : null;
    }

    @Override
    public Message<?> beforeHandle(
            Message<?> message,
            MessageChannel channel,
            MessageHandler handler
    ) {
        // Check again after waiting in the outbound executor:
        // the token may have expired since preSend().
        return permitted(message) ? message : null;
    }

    private boolean permitted(Message<?> message) {
        var type = SimpMessageHeaderAccessor.getMessageType(
                message.getHeaders()
        );

        // Leave protocol establishment, heartbeats and cleanup alone.
        if (type != SimpMessageType.MESSAGE) {
            return true;
        }

        String sessionId =
                SimpMessageHeaderAccessor.getSessionId(
                        message.getHeaders()
                );

        // Fail closed for application messages without an authenticated,
        // unexpired session.
        return sessionAccess.canReceive(sessionId);
    }
}
