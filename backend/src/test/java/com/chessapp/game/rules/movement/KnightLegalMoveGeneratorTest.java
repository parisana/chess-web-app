package com.chessapp.game.rules.movement;

import com.chessapp.BoardTestRenderer;
import com.chessapp.game.rules.attack.SquareAttackDetector;
import com.chessapp.game.rules.fen.FenParser;
import com.chessapp.game.rules.movement.KnightLegalMoveGenerator;
import com.chessapp.game.rules.movement.KnightMoveApplier;
import com.chessapp.game.rules.movement.KnightMoveGenerator;
import com.chessapp.game.rules.model.Move;
import com.chessapp.game.rules.model.Square;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author pari on 10/24/26
 */
class KnightLegalMoveGeneratorTest {

    private final FenParser parser = new FenParser();
    private final KnightMoveGenerator pseudoLegalGenerator =
            new KnightMoveGenerator();

    private final KnightLegalMoveGenerator legalGenerator =
            new KnightLegalMoveGenerator(
                    pseudoLegalGenerator,
                    new KnightMoveApplier(pseudoLegalGenerator),
                    new SquareAttackDetector()
            );

    @Test
    void retainsStartingKnightMovesWhenKingRemainsSafe() {
        var position = parser.parse(
                "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR"
                        + " w KQkq - 0 1"
        );

        assertEquals(
                Set.of(move("b1", "a3"), move("b1", "c3")),
                legalGenerator.generate(
                        position,
                        Square.fromAlgebraic("b1")
                )
        );
    }

    @Test
    void removesMovesThatExposeOwnKing() {
        var position = parser.parse(
                "4r2k/8/8/8/8/8/4N3/4K3 w - - 0 1"
        );
        var source = Square.fromAlgebraic("e2");

        var candidates = pseudoLegalGenerator.generate(position, source);
        var legalMoves = legalGenerator.generate(position, source);

        System.out.println("Pseudo-legal knight candidates:");
        System.out.println(BoardTestRenderer.render(
                position, source, candidates
        ));

        System.out.println("After king-safety filtering:");
        System.out.println(BoardTestRenderer.render(
                position, source, legalMoves
        ));

        assertAll(
                () -> assertFalse(candidates.isEmpty()),
                () -> assertTrue(legalMoves.isEmpty())
        );
    }

    @Test
    void allowsBlockingOrCapturingCheckingRook() {
        // White is in check from the rook on e4.
        var position = parser.parse(
                "7k/8/8/8/4r3/2N5/8/4K3 w - - 0 1"
        );
        var source = Square.fromAlgebraic("c3");

        var legalMoves = legalGenerator.generate(position, source);

        var visualization = BoardTestRenderer.render(
                position,
                source,
                legalMoves
        );

        System.out.println(visualization);

        assertEquals(
                Set.of(
                        move("c3", "e2"), // Block the rook's line.
                        move("c3", "e4")  // Capture the checking rook.
                ),
                legalMoves,
                visualization
        );
    }

    @Test
    void filtersMovesForBlackUsingBlacksKing() {
        var position = parser.parse(
                "4k3/4n3/8/8/8/8/8/K3R3 b - - 0 1"
        );

        assertTrue(
                legalGenerator.generate(
                        position,
                        Square.fromAlgebraic("e7")
                ).isEmpty()
        );
    }

    @Test
    void rejectsPositionWithoutBothKings() {
        // Deliberately invalid fixture: Black's king is missing.
        var position = parser.parse(
                "8/8/8/8/3N4/8/8/4K3 w - - 0 1"
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> legalGenerator.generate(
                        position,
                        Square.fromAlgebraic("d4")
                )
        );
    }

    private static Move move(String from, String to) {
        return new Move(
                Square.fromAlgebraic(from),
                Square.fromAlgebraic(to)
        );
    }
}