package com.chessapp.game.exception;

/**
 * @author pari on 10/28/26
 */
public class GameAlreadyFinishedException extends RuntimeException {

    public GameAlreadyFinishedException() {
        super("Game has already finished");
    }
}
