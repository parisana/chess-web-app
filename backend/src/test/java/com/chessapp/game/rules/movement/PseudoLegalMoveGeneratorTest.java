package com.chessapp.game.rules.movement;

import com.chessapp.BoardTestRenderer;
import com.chessapp.game.rules.fen.FenParser;
import com.chessapp.game.rules.model.ChessPosition;
import com.chessapp.game.rules.model.Move;
import com.chessapp.game.rules.model.Square;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author pari on 10/25/26
 */
class PseudoLegalMoveGeneratorTest {

    private final FenParser parser = new FenParser();

    private final PseudoLegalMoveGenerator generator =
            MovementTestFixtures.pseudoLegalGenerator();

    @Test
    void dispatchesKnightMovement() {
        final Set<String> destinationSquares = destinations(
                "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR"
                        + " w KQkq - 0 1",
                "b1"
        );
        assertEquals(
                Set.of("a3", "c3"),
                destinationSquares
        );
    }

    @Test
    void dispatchesRookMovement() {
        assertEquals(
                Set.of(
                        "a2", "a3", "a4", "a5", "a6", "a7", "a8",
                        "b1", "c1", "d1"
                ),
                destinations(
                        "7k/8/8/8/8/8/8/R3K3 w - - 0 20",
                        "a1"
                )
        );
    }

    @Test
    void dispatchesMovementForBlack() {
        assertEquals(
                Set.of("b6", "c7"),
                destinations(
                        "n6k/8/8/8/8/8/8/K7 b - - 0 20",
                        "a8"
                )
        );
    }

    @Test
    void returnsNoMovesForEmptySquareOrOpposingPiece() {
        var position = parser.parse(
                "7k/8/8/8/3r4/8/8/K7 w - - 0 20"
        );

        assertAll(
                () -> assertTrue(generator.generate(
                        position,
                        Square.fromAlgebraic("b2")
                ).isEmpty()),
                () -> assertTrue(generator.generate(
                        position,
                        Square.fromAlgebraic("d4")
                ).isEmpty())
        );
    }

    @Test
    void rejectsUnsupportedPieceInsteadOfReturningNoMoves() {
        var position = parser.parse(
                "7k/8/8/8/3P4/8/8/K7 w - - 0 20"
        );

        assertThrows(
                UnsupportedOperationException.class,
                () -> generator.generate(
                        position,
                        Square.fromAlgebraic("d4")
                )
        );
    }

    @Test
    void rejectsDuplicateStrategiesForSamePieceType() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new PseudoLegalMoveGenerator(
                        new KnightMoveGenerator(),
                        new KnightMoveGenerator()
                )
        );
    }

    @Test
    void dispatchesBishopMovement() {
        final String positionFen = "6k1/8/8/8/8/8/8/BK6 w - - 0 20";
        final String sourceSquareStr = "a1";
        final Set<String> destinationSquares = destinations(
                positionFen,
                sourceSquareStr
        );
        final String visualization = BoardTestRenderer.render(parser.parse(positionFen), Square.fromAlgebraic(sourceSquareStr),
                destinationSquares.stream().map(str-> new Move(Square.fromAlgebraic(sourceSquareStr), Square.fromAlgebraic(str))).collect(Collectors.toUnmodifiableSet()));
        System.out.println(visualization);
        assertEquals(
                Set.of("b2", "c3", "d4", "e5", "f6", "g7", "h8"),
                destinationSquares
        );
    }

    @Test
    void dispatchesQueenMovement() {
        // White's king on b1 blocks horizontal movement from a1.
        assertEquals(
                Set.of(
                        "a2", "a3", "a4", "a5", "a6", "a7", "a8",
                        "b2", "c3", "d4", "e5", "f6", "g7", "h8"
                ),
                destinations(
                        "6k1/8/8/8/8/8/8/QK6 w - - 0 20",
                        "a1"
                )
        );
    }

    private Set<String> destinations(String fen, String source) {
        return generator.generate(
                        parser.parse(fen),
                        Square.fromAlgebraic(source)
                ).stream()
                .map(move -> move.to().toAlgebraic())
                .collect(Collectors.toSet());
    }
}