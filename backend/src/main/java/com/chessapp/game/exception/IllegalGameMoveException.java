package com.chessapp.game.exception;

/**
 * @author pari on 10/28/26
 */
public class IllegalGameMoveException extends RuntimeException {

    public IllegalGameMoveException() {
        super("Move is not legal for the current position");
    }
}
