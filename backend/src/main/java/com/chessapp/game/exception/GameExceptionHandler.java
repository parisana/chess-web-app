package com.chessapp.game.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
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
        return problem(
                HttpStatus.CONFLICT,
                "Player already in a game",
                GameErrorCodes.PLAYER_ALREADY_IN_GAME,
                exception
        );
    }

    @ExceptionHandler(GameNotFoundException.class)
    public ProblemDetail handleGameNotFound(
            GameNotFoundException exception
    ) {
        return problem(
                HttpStatus.NOT_FOUND,
                "Game not found",
                GameErrorCodes.GAME_NOT_FOUND,
                exception
        );
    }

    @ExceptionHandler(GameAccessDeniedException.class)
    public ProblemDetail handleGameAccessDenied(
            GameAccessDeniedException exception
    ) {
        return problem(
                HttpStatus.FORBIDDEN,
                "Game access denied",
                GameErrorCodes.GAME_ACCESS_DENIED,
                exception
        );
    }

    @ExceptionHandler(GameAlreadyFinishedException.class)
    public ProblemDetail handleGameAlreadyFinished(
            GameAlreadyFinishedException exception
    ) {
        return problem(
                HttpStatus.CONFLICT,
                "Game already finished",
                GameErrorCodes.GAME_ALREADY_FINISHED,
                exception
        );
    }

    @ExceptionHandler(StaleGameVersionException.class)
    public ProblemDetail handleStaleGameVersion(
            StaleGameVersionException exception
    ) {
        return problem(
                HttpStatus.CONFLICT,
                "Stale game version",
                GameErrorCodes.STALE_GAME_VERSION,
                exception
        );
    }

    @ExceptionHandler(NotPlayersTurnException.class)
    public ProblemDetail handleNotPlayersTurn(
            NotPlayersTurnException exception
    ) {
        return problem(
                HttpStatus.CONFLICT,
                "Not player's turn",
                GameErrorCodes.NOT_PLAYERS_TURN,
                exception
        );
    }

    @ExceptionHandler(IllegalGameMoveException.class)
    public ProblemDetail handleIllegalGameMove(
            IllegalGameMoveException exception
    ) {
        return problem(
                HttpStatusCode.valueOf(422), // unprocessable request
                "Illegal game move",
                GameErrorCodes.ILLEGAL_GAME_MOVE,
                exception
        );
    }

    @ExceptionHandler(DrawOfferAlreadyPendingException.class)
    public ProblemDetail handleDrawOfferAlreadyPending(
            DrawOfferAlreadyPendingException exception
    ) {
        return problem(
                HttpStatus.CONFLICT,
                "Draw offer already pending",
                GameErrorCodes.DRAW_OFFER_ALREADY_PENDING,
                exception
        );
    }

    @ExceptionHandler(DrawOfferNotFoundException.class)
    public ProblemDetail handleDrawOfferNotFound(
            DrawOfferNotFoundException exception
    ) {
        return problem(
                HttpStatus.CONFLICT,
                "Draw offer not found",
                GameErrorCodes.DRAW_OFFER_NOT_FOUND,
                exception
        );
    }

    @ExceptionHandler(DrawOfferActionNotAllowedException.class)
    public ProblemDetail handleDrawOfferActionNotAllowed(
            DrawOfferActionNotAllowedException exception
    ) {
        return problem(
                HttpStatus.CONFLICT,
                "Draw offer action not allowed",
                GameErrorCodes.DRAW_OFFER_ACTION_NOT_ALLOWED,
                exception
        );
    }

    @ExceptionHandler(PgnExportUnavailableException.class)
    public ProblemDetail handlePgnExportUnavailable(
            PgnExportUnavailableException exception
    ) {
        return problem(
                HttpStatus.CONFLICT,
                "PGN export unavailable",
                GameErrorCodes.PGN_EXPORT_UNAVAILABLE,
                exception
        );
    }

    private static ProblemDetail problem(
            HttpStatusCode status,
            String title,
            String code,
            RuntimeException exception
    ) {
        var problem = ProblemDetail.forStatusAndDetail(
                status,
                exception.getMessage()
        );

        problem.setTitle(title);
        problem.setProperty("code", code);

        return problem;
    }
}
