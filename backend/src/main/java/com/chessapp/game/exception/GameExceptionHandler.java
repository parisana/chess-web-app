package com.chessapp.game.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
/**
 * @author pari on 10/23/26
 */
@RestControllerAdvice
public class GameExceptionHandler {

    @ExceptionHandler(PlayerAlreadyInGameException.class)
    public ProblemDetail handlePlayerAlreadyInGame(
            PlayerAlreadyInGameException exception
    ) {
        var problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                exception.getMessage()
        );

        problem.setTitle("Player already in a game");
        problem.setProperty("code", "PLAYER_ALREADY_IN_GAME");

        return problem;
    }

    @ExceptionHandler(GameNotFoundException.class)
    public ProblemDetail handleGameNotFound(
            GameNotFoundException exception
    ) {
        var problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND,
                exception.getMessage()
        );

        problem.setTitle("Game not found");
        problem.setProperty("code", "GAME_NOT_FOUND");

        return problem;
    }

    @ExceptionHandler(GameAccessDeniedException.class)
    public ProblemDetail handleGameAccessDenied(
            GameAccessDeniedException exception
    ) {
        var problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.FORBIDDEN,
                exception.getMessage()
        );

        problem.setTitle("Game access denied");
        problem.setProperty("code", "GAME_ACCESS_DENIED");

        return problem;
    }
}
