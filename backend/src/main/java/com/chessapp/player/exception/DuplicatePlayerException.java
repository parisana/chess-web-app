package com.chessapp.player.exception;

import lombok.Getter;

/**
 * @author pari on 10/16/26
 */
@Getter
public class DuplicatePlayerException extends RuntimeException{
    private final String code;

    public DuplicatePlayerException(String code, String message) {
        super(message);
        this.code = code;
    }
}
