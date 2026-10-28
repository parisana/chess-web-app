package com.chessapp.game.dto;

import com.chessapp.game.rules.model.PieceType;
import com.chessapp.game.rules.model.Square;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class SubmitMoveRequestTest {

    @Test
    void validRequestsConvertWithoutDecidingChessLegality() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();

            // Valid request shape, although e2-e5 is illegal in the opening.
            var ordinary = new SubmitMoveRequest(
                    0L, "e2", "e5", null
            );

            assertTrue(validator.validate(ordinary).isEmpty());

            var move = ordinary.toMove();

            assertEquals(Square.fromAlgebraic("e2"), move.from());
            assertEquals(Square.fromAlgebraic("e5"), move.to());
            assertEquals(Optional.empty(), move.promotion());

            for (var choice : List.of(
                    PieceType.QUEEN,
                    PieceType.ROOK,
                    PieceType.BISHOP,
                    PieceType.KNIGHT
            )) {
                var promotion = new SubmitMoveRequest(
                        7L, "e7", "e8", choice.name()
                );

                assertTrue(validator.validate(promotion).isEmpty());
                assertEquals(
                        Optional.of(choice),
                        promotion.toMove().promotion()
                );
            }
        }
    }

    @Test
    void rejectsInvalidRequestValues() {
        var invalidRequests = List.of(
                new SubmitMoveRequest(null, "e2", "e4", null),
                new SubmitMoveRequest(-1L, "e2", "e4", null),
                new SubmitMoveRequest(0L, null, "e4", null),
                new SubmitMoveRequest(0L, "e2", null, null),
                new SubmitMoveRequest(0L, "", "e4", null),
                new SubmitMoveRequest(0L, "e2", " ", null),
                new SubmitMoveRequest(0L, "E2", "e4", null),
                new SubmitMoveRequest(0L, "e2", "i4", null),
                new SubmitMoveRequest(0L, "a0", "a1", null),
                new SubmitMoveRequest(0L, "e2", "e2", null),
                new SubmitMoveRequest(0L, "e7", "e8", "KING"),
                new SubmitMoveRequest(0L, "e7", "e8", "PAWN"),
                new SubmitMoveRequest(0L, "e7", "e8", "queen"),
                new SubmitMoveRequest(0L, "e7", "e8", "")
        );

        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();

            for (var request : invalidRequests) {
                assertFalse(
                        validator.validate(request).isEmpty(),
                        () -> "Expected validation failure for " + request
                );
            }
        }
    }
}