package com.chessapp.matchmaking.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
/**
 * @author pari on 10/21/26
 */
@RestControllerAdvice
public class MatchmakingExceptionHandler {

    @ExceptionHandler(MatchmakingPlayerUnavailableException.class)
    public ProblemDetail handlePlayerUnavailable(
            MatchmakingPlayerUnavailableException exception
    ) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNAUTHORIZED,
                exception.getMessage()
        );

        problem.setTitle("Authenticated player unavailable");
        problem.setProperty(
                "code",
                MatchmakingErrorCodes.AUTHENTICATED_PLAYER_UNAVAILABLE
        );

        return problem;
    }
}
