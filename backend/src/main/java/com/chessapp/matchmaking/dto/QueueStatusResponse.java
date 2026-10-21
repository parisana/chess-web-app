package com.chessapp.matchmaking.dto;

import java.time.Instant;

/**
 * @author pari on 10/21/26
 */
public record QueueStatusResponse(
        QueueStatus status,
        Instant enqueuedAt
) {

    public static QueueStatusResponse notQueued() {
        return new QueueStatusResponse(
                QueueStatus.NOT_QUEUED,
                null
        );
    }

    public static QueueStatusResponse waiting(Instant enqueuedAt) {
        return new QueueStatusResponse(
                QueueStatus.WAITING,
                enqueuedAt
        );
    }
}
