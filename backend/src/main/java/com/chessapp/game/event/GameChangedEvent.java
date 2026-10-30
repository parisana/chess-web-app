package com.chessapp.game.event;

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
        Type type
) {

    public enum Type {
        GAME_CREATED,
        GAME_UPDATED
    }
}
