package com.chessapp.matchmaking.dto;

import java.time.Instant;

/**
 * @author pari on 10/21/26
 */
public record QueueStatusResponse(
        QueueStatus status,
        Instant enqueuedAt,
        Long gameId
) {

    public static QueueStatusResponse notQueued() {
        return new QueueStatusResponse(
                QueueStatus.NOT_QUEUED, null, null
        );
    }

    public static QueueStatusResponse waiting(Instant enqueuedAt) {
        return new QueueStatusResponse(
                QueueStatus.WAITING, enqueuedAt, null
        );
    }

    public static QueueStatusResponse matched(Long gameId) {
        return new QueueStatusResponse(
                QueueStatus.MATCHED, null, gameId
        );
    }
}
