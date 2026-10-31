package com.chessapp.game.exception;

/**
 * @author pari on 10/30/26
 */
public class DrawOfferAlreadyPendingException extends RuntimeException {

    public DrawOfferAlreadyPendingException() {
        super("A draw offer is already pending");
    }
}
