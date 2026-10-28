package com.chessapp.game.exception;

/**
 * @author pari on 10/28/26
 */
public class StaleGameVersionException extends RuntimeException {

    public StaleGameVersionException() {
        super("Game has changed; reload before submitting another move");
    }

    public StaleGameVersionException(Throwable cause) {
        super(
                "Game has changed; reload before submitting another move",
                cause
        );
    }
}
