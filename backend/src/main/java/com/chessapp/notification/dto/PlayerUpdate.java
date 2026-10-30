package com.chessapp.notification.dto;

/**
 * @author pari on 10/29/26
 * A hint to read authoritative state through the protected HTTP API.
 * The version lets clients ignore updates they have already observed.
 * @see com.chessapp.game.event.GameChangedEvent.Type for valid types
 */
public record PlayerUpdate(
        String type,
        long gameId,
        long version
) {
}