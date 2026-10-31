package com.chessapp.game.exception;

/**
 * @author pari on 10/30/26
 */
public class DrawOfferNotFoundException extends RuntimeException {

    public DrawOfferNotFoundException() {
        super("There is no pending draw offer");
    }
}
