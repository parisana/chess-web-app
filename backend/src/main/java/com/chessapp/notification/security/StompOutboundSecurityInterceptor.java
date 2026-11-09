package com.chessapp.notification.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.support.ExecutorChannelInterceptor;
import org.springframework.stereotype.Component;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.messaging.support.MessageBuilder;

import java.util.concurrent.TimeUnit;
/**
 * @author pari on 10/29/26
 */
@Component
//@RequiredArgsConstructor
@Slf4j
public class StompOutboundSecurityInterceptor
        implements ExecutorChannelInterceptor {

    private static final String OUTBOUND_SUBMITTED_AT =
            "chessOutboundSubmittedAtNanos";

    private final WebSocketSessionAccess sessionAccess;
    private final Timer dispatchWait;
    private final Timer sessionValidation;

    public StompOutboundSecurityInterceptor(
            WebSocketSessionAccess sessionAccess,
            MeterRegistry registry
    ) {
        this.sessionAccess = sessionAccess;
        this.dispatchWait = registry.timer("chess.ws.outbound.dispatch.wait");
        this.sessionValidation = registry.timer("chess.ws.outbound.session.validation");
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        if (!isApplicationMessage(message)) {
            return message;
        }

        // Stamp the handoff to the outbound executor.
        return MessageBuilder.fromMessage(message)
                .setHeader(OUTBOUND_SUBMITTED_AT, System.nanoTime())
                .build();
    }

    @Override
    public Message<?> beforeHandle(
            Message<?> message,
            MessageChannel channel,
            MessageHandler handler
    ) {
        if (!isApplicationMessage(message)) {
            return message;
        }

        if (message.getHeaders().get(OUTBOUND_SUBMITTED_AT) instanceof Long submittedAt) {
            dispatchWait.record(System.nanoTime() - submittedAt, TimeUnit.NANOSECONDS);
        }

        String socketId = SimpMessageHeaderAccessor.getSessionId(message.getHeaders());
        long validationStartedAt = System.nanoTime();
        try {
            return sessionAccess.canReceive(socketId) ? message : null;
        } finally {
            sessionValidation.record(
                    System.nanoTime() - validationStartedAt, TimeUnit.NANOSECONDS);
        }
    }

    private static boolean isApplicationMessage(Message<?> message) {
        return SimpMessageHeaderAccessor.getMessageType(message.getHeaders())
                == SimpMessageType.MESSAGE;
    }
}
