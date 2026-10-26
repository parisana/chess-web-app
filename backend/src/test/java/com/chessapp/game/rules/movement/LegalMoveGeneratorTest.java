package com.chessapp.game.rules.movement;

import com.chessapp.BoardTestRenderer;
import com.chessapp.game.rules.fen.FenParser;
import com.chessapp.game.rules.model.Move;
import com.chessapp.game.rules.model.Square;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author pari on 10/24/26
 */
class LegalMoveGeneratorTest {

    private final FenParser parser = new FenParser();
    private final PseudoLegalMoveGenerator pseudoLegalGenerator =
            MovementTestFixtures.pseudoLegalGenerator();

    private final LegalMoveGenerator legalGenerator =
            MovementTestFixtures.legalGenerator(pseudoLegalGenerator);

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

    @Test
    void pinnedBishopCanMoveAlongPinLineButCannotExposeOwnKing() {
        // h6-g5-f4-e3-d2-c1: White's bishop shields its king.
        var position = parser.parse(
                "6k1/8/7b/8/8/8/3B4/2K5 w - - 0 20"
        );
        var source = Square.fromAlgebraic("d2");

        var candidates = pseudoLegalGenerator.generate(position, source);
        var legalMoves = legalGenerator.generate(position, source);

        System.out.println("Pseudo-legal bishop candidates:");
        System.out.println(BoardTestRenderer.render(
                position, source, candidates
        ));

        var visualization = BoardTestRenderer.render(
                position, source, legalMoves
        );

        System.out.println("After king-safety filtering:");
        System.out.println(visualization);

        assertAll(
                // This diagonal move leaves the pin line.
                () -> assertTrue(candidates.contains(move("d2", "c3"))),
                () -> assertEquals(
                        Set.of(
                                move("d2", "e3"),
                                move("d2", "f4"),
                                move("d2", "g5"),
                                move("d2", "h6")
                        ),
                        legalMoves,
                        visualization
                )
        );
    }

    @Test
    void filtersPinnedBishopMovesUsingBlacksKing() {
        // h3-g4-f5-e6-d7-c8: Black's bishop shields its king.
        var position = parser.parse(
                "2k5/3b4/8/8/8/7B/8/6K1 b - - 0 20"
        );

        assertEquals(
                Set.of(
                        move("d7", "e6"),
                        move("d7", "f5"),
                        move("d7", "g4"),
                        move("d7", "h3")
                ),
                legalGenerator.generate(
                        position,
                        Square.fromAlgebraic("d7")
                )
        );
    }

    @Test
    void bishopCanAnswerCheckByBlockingAttack() {
        // The rook checks along the e-file.
        // From c4, the bishop can interpose on e2 or e6.
        var position = parser.parse(
                "4r2k/8/8/8/2B5/8/8/4K3 w - - 0 20"
        );
        var source = Square.fromAlgebraic("c4");

        var legalMoves = legalGenerator.generate(position, source);
        var visualization = BoardTestRenderer.render(
                position, source, legalMoves
        );

        System.out.println(visualization);

        assertEquals(
                Set.of(
                        move("c4", "e2"),
                        move("c4", "e6")
                ),
                legalMoves,
                visualization
        );
    }

    @Test
    void bishopCanAnswerCheckByCapturingAttacker() {
        var position = parser.parse(
                "7k/8/8/8/4r3/8/2B5/4K3 w - - 0 20"
        );

        final Square sourceSquare = Square.fromAlgebraic("c2");
        final Set<Move> legalMoves = legalGenerator.generate(
                position,
                sourceSquare
        );
        assertEquals(
                Set.of(move("c2", "e4")),
                legalMoves
        );
        System.out.println("Legal Moves: ");
        System.out.println(BoardTestRenderer.render(position, sourceSquare, legalMoves));
    }

    @Test
    void orthogonallyPinnedQueenMustStayOnPinLine() {
        // White's queen shields its king from the rook on e8.
        var position = parser.parse(
                "4r2k/8/8/8/8/8/4Q3/4K3 w - - 0 20"
        );
        var source = Square.fromAlgebraic("e2");

        var candidates = pseudoLegalGenerator.generate(position, source);
        var legalMoves = legalGenerator.generate(position, source);

        System.out.println("Pseudo-legal queen candidates:");
        System.out.println(BoardTestRenderer.render(
                position, source, candidates
        ));

        var visualization = BoardTestRenderer.render(
                position, source, legalMoves
        );

        System.out.println("After king-safety filtering:");
        System.out.println(visualization);

        assertAll(
                () -> assertTrue(candidates.contains(move("e2", "d3"))),
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
    void diagonallyPinnedBlackQueenMustStayOnPinLine() {
        // h3-g4-f5-e6-d7-c8: Black's queen shields its king.
        var position = parser.parse(
                "2k5/3q4/8/8/8/7B/8/6K1 b - - 0 20"
        );

        assertEquals(
                Set.of(
                        move("d7", "e6"),
                        move("d7", "f5"),
                        move("d7", "g4"),
                        move("d7", "h3")
                ),
                legalGenerator.generate(
                        position,
                        Square.fromAlgebraic("d7")
                )
        );
    }

    @Test
    void queenCanAnswerCheckByBlockingOrCapturingAttacker() {
        // The rook on e4 checks White's king along the e-file.
        var position = parser.parse(
                "7k/8/8/8/4r3/8/2Q5/4K3 w - - 0 20"
        );
        var source = Square.fromAlgebraic("c2");

        var legalMoves = legalGenerator.generate(position, source);
        var visualization = BoardTestRenderer.render(
                position, source, legalMoves
        );

        System.out.println(visualization);

        assertEquals(
                Set.of(
                        move("c2", "e2"), // Block the checking line.
                        move("c2", "e4")  // Capture the checking rook.
                ),
                legalMoves,
                visualization
        );
    }

    @Test
    void whiteKingCanEscapeCheckButCannotStayOnAttackedFile() {
        // The rook checks along the e-file.
        var position = parser.parse(
                "4r2k/8/8/8/8/8/8/4K3 w - - 0 20"
        );
        var source = Square.fromAlgebraic("e1");

        var candidates = pseudoLegalGenerator.generate(position, source);
        var legalMoves = legalGenerator.generate(position, source);
        var visualization = BoardTestRenderer.render(
                position, source, legalMoves
        );

        System.out.println(visualization);

        assertAll(
                () -> assertTrue(candidates.contains(move("e1", "e2"))),
                () -> assertEquals(
                        Set.of(
                                move("e1", "d1"),
                                move("e1", "f1"),
                                move("e1", "d2"),
                                move("e1", "f2")
                        ),
                        legalMoves,
                        visualization
                )
        );
    }

    @Test
    void blackKingCannotMoveAdjacentToWhiteKing() {
        var position = parser.parse(
                "8/8/8/4k3/8/4K3/8/8 b - - 0 20"
        );
        var source = Square.fromAlgebraic("e5");

        var candidates = pseudoLegalGenerator.generate(position, source);
        var legalMoves = legalGenerator.generate(position, source);

        assertAll(
                // These satisfy ordinary movement but violate king safety.
                () -> assertTrue(candidates.contains(move("e5", "e4"))),
                () -> assertEquals(
                        Set.of(
                                move("e5", "d5"),
                                move("e5", "f5"),
                                move("e5", "d6"),
                                move("e5", "e6"),
                                move("e5", "f6")
                        ),
                        legalMoves
                )
        );
    }

    @Test
    void rejectsDestinationAttackedAfterKingVacatesSource() {
        // The king on d4 currently blocks the rook's attack toward e4. [e4 becomes attacked only after king leaves d4]
        var position = parser.parse(
                "7k/8/8/8/r2K4/8/8/8 w - - 0 20"
        );
        var source = Square.fromAlgebraic("d4");

        var candidates = pseudoLegalGenerator.generate(position, source);
        var legalMoves = legalGenerator.generate(position, source);
        var visualization = BoardTestRenderer.render(
                position, source, legalMoves
        );

        System.out.println(visualization);

        assertAll(
                () -> assertTrue(candidates.contains(move("d4", "e4"))),
                () -> assertEquals(
                        Set.of(
                                move("d4", "c3"),
                                move("d4", "d3"),
                                move("d4", "e3"),
                                move("d4", "c5"),
                                move("d4", "d5"),
                                move("d4", "e5")
                        ),
                        legalMoves,
                        visualization
                )
        );
    }

    @Test
    void kingCanCaptureAnUndefendedCheckingPiece() {
        var position = parser.parse(
                "7k/8/8/8/8/8/4r3/4K3 w - - 0 20"
        );

        assertTrue(
                legalGenerator.generate(
                        position,
                        Square.fromAlgebraic("e1")
                ).contains(move("e1", "e2"))
        );
    }

    @Test
    void kingCannotCapturePieceDefendedByBishop() {
        // The rook checks e1; the bishop on b5 defends the rook on e2.
        var position = parser.parse(
                "7k/8/8/1b6/8/8/4r3/4K3 w - - 0 20"
        );
        var source = Square.fromAlgebraic("e1");
        var capture = move("e1", "e2");

        assertAll(
                () -> assertTrue(
                        pseudoLegalGenerator.generate(position, source)
                                .contains(capture)
                ),
                () -> assertFalse(
                        legalGenerator.generate(position, source)
                                .contains(capture)
                )
        );
    }

    @Test
    void kingCannotEnterSquareAttackedByPinnedKnight() {
        // The rook on e1 pins the knight against Black's king.
        // The knight still attacks d5 for White's king-safety check.
        var position = parser.parse(
                "4k3/4n3/8/8/2K5/8/8/4R3 w - - 0 20"
        );
        var source = Square.fromAlgebraic("c4");
        var destination = move("c4", "d5");

        assertAll(
                () -> assertTrue(
                        pseudoLegalGenerator.generate(position, source)
                                .contains(destination)
                ),
                () -> assertFalse(
                        legalGenerator.generate(position, source)
                                .contains(destination)
                )
        );
    }

    @ParameterizedTest
    @CsvSource({
            // Black pawn and knight attack White's proposed destination f2.
            "'k7/8/8/8/8/4p3/8/4K3 w - - 0 20', e1, f2",
            "'k7/8/8/8/8/7n/8/4K3 w - - 0 20', e1, f2",

            // White pawn and knight attack Black's proposed destination f7.
            "'4k3/8/4P3/8/8/8/8/K7 b - - 0 20', e8, f7",
            "'4k3/8/7N/8/8/8/8/K7 b - - 0 20', e8, f7"
    })
    void kingCannotEnterPawnOrKnightAttack(
            String fen,
            String from,
            String to
    ) {
        var position = parser.parse(fen);
        var source = Square.fromAlgebraic(from);
        var proposedMove = move(from, to);
        var visualization = BoardTestRenderer.render(
                position, source, Set.of(proposedMove)
        );
        System.out.println("Proposed move: ");
        System.out.println(visualization);

        assertAll(
                () -> assertTrue(
                        pseudoLegalGenerator.generate(position, source)
                                .contains(proposedMove)
                ),
                () -> assertFalse(
                        legalGenerator.generate(position, source)
                                .contains(proposedMove)
                )
        );
    }

    @Test
    void blackKingCannotMoveAlongAttackExposedByVacatingSource() {
        // Black's king on d5 currently shields e5 from White's rook.
        var position = parser.parse(
                "8/8/8/R2k4/8/8/8/7K b - - 0 20"
        );
        var source = Square.fromAlgebraic("d5");

        var candidates = pseudoLegalGenerator.generate(position, source);
        var legalMoves = legalGenerator.generate(position, source);
        var visualization = BoardTestRenderer.render(
                position, source, legalMoves
        );

        System.out.println(visualization);

        assertAll(
                () -> assertTrue(candidates.contains(move("d5", "e5"))),
                () -> assertEquals(
                        Set.of(
                                move("d5", "c4"),
                                move("d5", "d4"),
                                move("d5", "e4"),
                                move("d5", "c6"),
                                move("d5", "d6"),
                                move("d5", "e6")
                        ),
                        legalMoves,
                        visualization
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