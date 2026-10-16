package com.chessapp.auth.exception;

/**
 * @author pari on 10/16/26
 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Invalid email or password");
    }
}

/// Use the same failure for an unknown email, missing local credentials, and an incorrect password. The client should not receive different messages identifying which part failed.
