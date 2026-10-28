package com.chessapp.game.dto;

import com.chessapp.game.rules.model.Move;
import com.chessapp.game.rules.model.PieceType;
import com.chessapp.game.rules.model.Square;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.Optional;

public record SubmitMoveRequest(
        @NotNull
        @PositiveOrZero
        Long expectedVersion,

        @NotNull
        @Pattern(
                regexp = "[a-h][1-8]",
                message = "must be a square between a1 and h8"
        )
        String from,

        @NotNull
        @Pattern(
                regexp = "[a-h][1-8]",
                message = "must be a square between a1 and h8"
        )
        String to,

        @Pattern(
                regexp = "QUEEN|ROOK|BISHOP|KNIGHT",
                message = "must be QUEEN, ROOK, BISHOP or KNIGHT"
        )
        String promotion
) {

    @JsonIgnore
    @AssertTrue(message = "source and destination squares must differ")
    public boolean isDistinctSquares() {
        // Required-field constraints handle missing coordinates.
        return from == null || to == null || !from.equals(to);
    }

    /**
     * Convert after request validation has succeeded.
     * Board-dependent legality remains the rules pipeline's responsibility.
     */
    public Move toMove() {
        return new Move(
                Square.fromAlgebraic(from),
                Square.fromAlgebraic(to),
                Optional.ofNullable(promotion)
                        .map(PieceType::valueOf)
        );
    }
}