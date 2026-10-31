package com.chessapp.game.exception;

/**
 * @author pari on 10/28/26
 */
public final class GameErrorCodes {

    public static final String PLAYER_ALREADY_IN_GAME =
            "PLAYER_ALREADY_IN_GAME";

    public static final String GAME_NOT_FOUND =
            "GAME_NOT_FOUND";

    public static final String GAME_ACCESS_DENIED =
            "GAME_ACCESS_DENIED";

    public static final String GAME_ALREADY_FINISHED =
            "GAME_ALREADY_FINISHED";

    public static final String STALE_GAME_VERSION =
            "STALE_GAME_VERSION";

    public static final String NOT_PLAYERS_TURN =
            "NOT_PLAYERS_TURN";

    public static final String ILLEGAL_GAME_MOVE =
            "ILLEGAL_GAME_MOVE";

    public static final String DRAW_OFFER_ALREADY_PENDING =
            "DRAW_OFFER_ALREADY_PENDING";

    public static final String DRAW_OFFER_NOT_FOUND =
            "DRAW_OFFER_NOT_FOUND";

    public static final String DRAW_OFFER_ACTION_NOT_ALLOWED =
            "DRAW_OFFER_ACTION_NOT_ALLOWED";

    public static final String PGN_EXPORT_UNAVAILABLE =
            "PGN_EXPORT_UNAVAILABLE";

    private GameErrorCodes() {
    }
}
