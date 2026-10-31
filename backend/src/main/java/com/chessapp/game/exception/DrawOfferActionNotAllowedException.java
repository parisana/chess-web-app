package com.chessapp.game.exception;

/**
 * @author pari on 10/30/26
 */
public class DrawOfferActionNotAllowedException extends RuntimeException {

    public DrawOfferActionNotAllowedException() {
        super(
                "Only the opponent may accept or decline an offer; "
                        + "only its sender may withdraw it"
        );
    }
}
