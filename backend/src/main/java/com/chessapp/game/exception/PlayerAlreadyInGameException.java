package com.chessapp.game.exception;

/**
 * @author pari on 10/22/26
 */
public class PlayerAlreadyInGameException extends RuntimeException {

    public PlayerAlreadyInGameException() {
        super("One or both players already have an active game");
    }
}
