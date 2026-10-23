package com.chessapp.game.exception;

/**
 * @author pari on 10/23/26
 */
public class GameAccessDeniedException extends RuntimeException {

    public GameAccessDeniedException() {
        super("Only participants can access this game");
    }
}
