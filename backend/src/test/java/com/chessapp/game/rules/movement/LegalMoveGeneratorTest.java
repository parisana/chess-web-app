package com.chessapp.game.rules.movement;

import com.chessapp.BoardTestRenderer;
import com.chessapp.game.rules.attack.SquareAttackDetector;
import com.chessapp.game.rules.fen.FenParser;
import com.chessapp.game.rules.model.Move;
import com.chessapp.game.rules.model.Square;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author pari on 10/24/26
 */
class LegalMoveGeneratorTest {

    private final FenParser parser = new FenParser();
    private final PseudoLegalMoveGenerator pseudoLegalGenerator =
            new PseudoLegalMoveGenerator(
                    new KnightMoveGenerator(),
                    new RookMoveGenerator()
            );

    private final LegalMoveGenerator legalGenerator =
            new LegalMoveGenerator(
                    pseudoLegalGenerator,
                    new MoveApplier(pseudoLegalGenerator),
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

    @Test
    void pinnedRookCanMoveAlongPinLineButCannotExposeOwnKing() {
        var position = parser.parse(
                "4r2k/8/8/8/8/8/4R3/4K3 w - - 0 20"
        );
        var source = Square.fromAlgebraic("e2");

        var candidates = pseudoLegalGenerator.generate(position, source);
        var legalMoves = legalGenerator.generate(position, source);

        System.out.println("Pseudo-legal rook candidates:");
        System.out.println(BoardTestRenderer.render(
                position, source, candidates
        ));

        var visualization = BoardTestRenderer.render(
                position, source, legalMoves
        );

        System.out.println("After king-safety filtering:");
        System.out.println(visualization);

        assertAll(
                () -> assertTrue(candidates.contains(move("e2", "d2"))),
                () -> assertEquals(
                        Set.of(
                                move("e2", "e3"),
                                move("e2", "e4"),
                                move("e2", "e5"),
                                move("e2", "e6"),
                                move("e2", "e7"),
                                move("e2", "e8")
                        ),
                        legalMoves,
                        visualization
                )
        );
    }

    @Test
    void filtersPinnedRookMovesUsingBlacksKing() {
        var position = parser.parse(
                "4k3/4r3/8/8/8/8/8/K3R3 b - - 0 20"
        );
        var source = Square.fromAlgebraic("e7");

        var legalMoves = legalGenerator.generate(position, source);
        var visualization = BoardTestRenderer.render(
                position, source, legalMoves
        );
        System.out.println("Legal Moves:");
        System.out.println(visualization);

        assertEquals(
                Set.of(
                        move("e7", "e6"),
                        move("e7", "e5"),
                        move("e7", "e4"),
                        move("e7", "e3"),
                        move("e7", "e2"),
                        move("e7", "e1")
                ),
                legalMoves,
                visualization
        );
    }

    @Test
    void rookCanAnswerCheckByBlockingAttack() {
        var position = parser.parse(
                "4r2k/8/8/8/8/8/R7/4K3 w - - 0 20"
        );
        var source = Square.fromAlgebraic("a2");

        var legalMoves = legalGenerator.generate(position, source);
        var visualization = BoardTestRenderer.render(
                position, source, legalMoves
        );

        System.out.println(visualization);

        assertEquals(
                Set.of(move("a2", "e2")),
                legalMoves,
                visualization
        );
    }

    @Test
    void rookCanAnswerCheckByCapturingAttacker() {
        var position = parser.parse(
                "7k/8/8/8/R3r3/8/8/4K3 w - - 0 20"
        );
        var source = Square.fromAlgebraic("a4");

        var legalMoves = legalGenerator.generate(position, source);
        var visualization = BoardTestRenderer.render(
                position, source, legalMoves
        );
        System.out.println(visualization);

        assertEquals(
                Set.of(move("a4", "e4")),
                legalMoves,
                visualization
        );
    }

    private static Move move(String from, String to) {
        return new Move(
                Square.fromAlgebraic(from),
                Square.fromAlgebraic(to)
        );
    }
}