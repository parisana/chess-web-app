package com.chessapp.game.dto;

import com.chessapp.game.entity.Game;
import com.chessapp.game.entity.GameStatus;
import com.chessapp.game.entity.GameTerminationReason;
import com.chessapp.game.rules.model.PieceColor;

import java.time.Instant;

/**
 * @author pari on 11/5/26
 * A finished game viewed from the subject Player's perspective.

 * playerColor, opponent details and outcome are relative to that Player,
 * who may differ from the authenticated viewer.

 * Access authorization belongs to the calling application boundary.
 * Starting details remain null when historical snapshots are unavailable.
 */
public record PlayedGameResponse(
        Long gameId,
        PieceColor playerColor,
        Long opponentPlayerId,
        String opponentUsernameAtStart,
        Integer opponentRatingAtStart,
        Outcome outcome,
        GameTerminationReason terminationReason,
        Instant finishedAt
) {
    public enum Outcome {
        WIN,
        LOSS,
        DRAW
    }

    /**
     * Maps a participant's finished game to their own perspective.
     * Starting details remain null for games without recorded snapshots.
     */
    public static PlayedGameResponse from(
            Game game,
            long subjectPlayerId
    ) {
        boolean isWhite =
                game.getWhitePlayerId().equals(subjectPlayerId);
        boolean isBlack =
                game.getBlackPlayerId().equals(subjectPlayerId);

        if (!isWhite && !isBlack) {
            throw new IllegalArgumentException(
                    "A game summary requires a participating Player"
            );
        }

        if (game.getStatus() != GameStatus.FINISHED) {
            throw new IllegalArgumentException(
                    "Played-game summaries require a finished game"
            );
        }

        Outcome outcome = switch (game.getResult()) {
            case DRAW -> Outcome.DRAW;
            case WHITE_WIN -> isWhite ? Outcome.WIN : Outcome.LOSS;
            case BLACK_WIN -> isBlack ? Outcome.WIN : Outcome.LOSS;
        };

        return new PlayedGameResponse(
                game.getId(),
                isWhite ? PieceColor.WHITE : PieceColor.BLACK,
                isWhite
                        ? game.getBlackPlayerId()
                        : game.getWhitePlayerId(),
                isWhite
                        ? game.getBlackUsernameAtStart()
                        : game.getWhiteUsernameAtStart(),
                isWhite
                        ? game.getBlackRatingAtStart()
                        : game.getWhiteRatingAtStart(),
                outcome,
                game.getTerminationReason(),
                game.getFinishedAt()
        );
    }
}
