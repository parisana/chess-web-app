package com.chessapp.player.exception;

import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * @author pari on 10/16/26
 */
@Slf4j
@RestControllerAdvice
public class PlayerExceptionHandler {

    @ExceptionHandler(DuplicatePlayerException.class)
    public ProblemDetail handleDuplicatePlayer(
            DuplicatePlayerException exception
    ) {
        return conflict(exception.getCode(), exception.getMessage());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrityViolation(
            DataIntegrityViolationException exception
    ) {
        String constraint = findConstraintName(exception);

        if ("uk_players_username".equals(constraint)) {
            return conflict(
                    "USERNAME_ALREADY_EXISTS",
                    "Username is already taken"
            );
        }

        if ("uk_players_email".equals(constraint)) {
            return conflict(
                    "EMAIL_ALREADY_EXISTS",
                    "Email is already registered"
            );
        }

        // This advice only translates constraints owned by player.
        throw exception;
    }

    private static ProblemDetail conflict(String code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                detail
        );

        problem.setTitle("Player identity conflict");
        problem.setProperty("code", code);

        return problem;
    }

    private static String findConstraintName(Throwable exception) {
        Throwable current = exception;

        while (current != null) {
            if (current instanceof ConstraintViolationException violation) {
                return violation.getConstraintName();
            }

            current = current.getCause();
        }

        return null;
    }
}