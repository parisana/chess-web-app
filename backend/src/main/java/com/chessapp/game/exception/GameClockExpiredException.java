package com.chessapp.game.exception;

/**
 * @author pari on 11/2/26
 */
public class GameClockExpiredException extends RuntimeException {

    public GameClockExpiredException() {
        super("The game clock has expired; refresh the game");
    }
}
