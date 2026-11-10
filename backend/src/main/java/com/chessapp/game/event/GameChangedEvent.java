package com.chessapp.game.event;

import com.chessapp.game.dto.GameResponse;

/**
 * @author pari on 10/29/26
 * An immutable description of a game change.
 * No managed entity or WebSocket dependency crosses this boundary.
 */
public record GameChangedEvent(
        long gameId,
        long whitePlayerId,
        long blackPlayerId,
        long version,
        Type type,
        GameResponse snapshot
) {

    public enum Type {
        GAME_CREATED,
        GAME_UPDATED
    }
}
