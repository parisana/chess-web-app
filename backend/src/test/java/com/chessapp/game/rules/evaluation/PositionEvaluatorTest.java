package com.chessapp.game.rules.evaluation;

import com.chessapp.BoardTestRenderer;
import com.chessapp.game.rules.attack.SquareAttackDetector;
import com.chessapp.game.rules.fen.FenParser;
import com.chessapp.game.rules.fen.FenSerializer;
import com.chessapp.game.rules.model.Move;
import com.chessapp.game.rules.model.Square;
import com.chessapp.game.rules.movement.KingMoveGenerator;
import com.chessapp.game.rules.movement.LegalMoveGenerator;
import com.chessapp.game.rules.movement.MovementTestFixtures;
import com.chessapp.game.rules.movement.PseudoLegalMoveGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PositionEvaluatorTest {

    private final FenParser parser = new FenParser();
    private final FenSerializer serializer = new FenSerializer();

    private final LegalMoveGenerator legalGenerator =
            MovementTestFixtures.legalGenerator(
                    MovementTestFixtures.pseudoLegalGenerator()
            );

    private final PositionEvaluator evaluator = new PositionEvaluator(
            legalGenerator,
            new SquareAttackDetector()
    );

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            // Normal: White's initial position; Black's reply after e2-e4.
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR"
                    + " w KQkq - 0 1 | e1 | NORMAL",
            "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR"
                    + " b KQkq e3 0 1 | e8 | NORMAL",

            // Check: the rook attacks the king, which has safe escapes.
            "4r2k/8/8/8/8/8/8/4K3"
                    + " w - - 0 20 | e1 | CHECK",
            "4k3/8/8/8/8/8/8/K3R3"
                    + " b - - 0 20 | e8 | CHECK",

            // Checkmate: the queen checks, covers escapes and is protected.
            "7k/6Q1/5K2/8/8/8/8/8"
                    + " b - - 0 20 | h8 | CHECKMATE",
            "8/8/8/8/8/5k2/6q1/7K"
                    + " w - - 0 20 | h1 | CHECKMATE",

            // Stalemate: the king is safe, but every destination is attacked.
            "7k/5Q2/6K1/8/8/8/8/8"
                    + " b - - 0 20 | h8 | STALEMATE",
            "8/8/8/8/8/6k1/5q2/7K"
                    + " w - - 0 20 | h1 | STALEMATE",
            // Check with an available en passant reply that removes the checking pawn.
            "7k/8/8/3pP3/4K3/8/8/8"
                    + " w - d6 0 20 | e4 | CHECK",
            "8/8/8/4k3/3Pp3/8/8/7K"
                    + " b - d3 0 20 | e5 | CHECK",
    })
    void assessesSideToMoveAndPreservesOriginalPosition(
            String fen,
            String kingSquare,
            PositionStatus expectedStatus
    ) {
        var position = parser.parse(fen);
        var actualStatus = evaluator.evaluate(position);

        var visualization = BoardTestRenderer.render(
                position,
                Square.fromAlgebraic(kingSquare),
                legalGenerator.generate(position)
        );

        System.out.println("Assessment: " + actualStatus);
        System.out.println(visualization);

        assertAll(
                () -> assertEquals(
                        expectedStatus, actualStatus, visualization
                ),
                () -> assertEquals(fen, serializer.serialize(position))
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {
            // Deliberately invalid defensive fixtures: one king is absent.
            "8/8/8/8/8/8/8/4K3 w - - 0 1",
            "4k3/8/8/8/8/8/8/8 b - - 0 1"
    })
    void rejectsMissingKingsInsteadOfReportingAnOutcome(String fen) {
        var position = parser.parse(fen);

        assertThrows(
                IllegalArgumentException.class,
                () -> evaluator.evaluate(position)
        );
    }

    @Test
    void propagatesUnsupportedMovementInsteadOfReportingAnOutcome() {
        // Deliberately incomplete registry: only king movement is supported.
        var incompleteGenerator = MovementTestFixtures.legalGenerator(
                new PseudoLegalMoveGenerator(new KingMoveGenerator())
        );
        var incompleteEvaluator = new PositionEvaluator(
                incompleteGenerator,
                new SquareAttackDetector()
        );

        var position = parser.parse(
                "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR"
                        + " w KQkq - 0 1"
        );

        assertThrows(
                UnsupportedOperationException.class,
                () -> incompleteEvaluator.evaluate(position)
        );
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "7k/8/8/8/8/8/PP3N2/K6r"
                    + " w - - 0 20 | a1 | f2 | d1 | h1",
            "k6R/pp3n2/8/8/8/8/8/7K"
                    + " b - - 0 20 | a8 | f7 | d8 | h8"
    })
    void reportsCheckWhenOnlyKnightBlockingOrCaptureRepliesExist(
            String fen,
            String kingSquare,
            String knightFrom,
            String blockingTo,
            String captureTo
    ) {
        var position = parser.parse(fen);
        var legalMoves = legalGenerator.generate(position);

        // Own pawns occupy two neighboring squares. The remaining king
        // destination is attacked, but the knight can block or capture.
        var expectedReplies = Set.of(
                move(knightFrom, blockingTo),
                move(knightFrom, captureTo)
        );

        var visualization = BoardTestRenderer.render(
                position,
                Square.fromAlgebraic(kingSquare),
                legalMoves
        );
        System.out.println(visualization);

        assertAll(
                () -> assertEquals(
                        expectedReplies, legalMoves, visualization
                ),
                () -> assertEquals(
                        PositionStatus.CHECK,
                        evaluator.evaluate(position),
                        visualization
                ),
                () -> assertEquals(fen, serializer.serialize(position))
        );
    }

    private static Move move(String from, String to) {
        return new Move(
                Square.fromAlgebraic(from),
                Square.fromAlgebraic(to)
        );
    }
}