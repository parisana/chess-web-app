package com.chessapp.game.exception;

/**
 * @author pari on 10/28/26
 */
public class NotPlayersTurnException extends RuntimeException {

    public NotPlayersTurnException() {
        super("Player does not own the current turn");
    }
}
