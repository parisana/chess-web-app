package com.chessapp.matchmaking.exception;

/**
 * @author pari on 10/21/26
 * A missing-player exception
 * A correctly signed JWT can outlive its player record. We therefore check player existence before queue operations.
 */
public class MatchmakingPlayerUnavailableException
        extends RuntimeException {

    public MatchmakingPlayerUnavailableException() {
        super("Authenticated player no longer exists");
    }
}
