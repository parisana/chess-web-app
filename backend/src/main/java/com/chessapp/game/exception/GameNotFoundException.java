package com.chessapp.game.exception;

/**
 * @author pari on 10/23/26
 */
public class GameNotFoundException extends RuntimeException {

    public GameNotFoundException() {
        super("Game does not exist");
    }
}
