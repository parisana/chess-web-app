package com.chessapp.game.rules.movement;

import com.chessapp.BoardTestRenderer;
import com.chessapp.game.rules.fen.FenParser;
import com.chessapp.game.rules.model.Square;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author pari on 10/26/26
 */
class QueenMoveGeneratorTest {

    private final FenParser parser = new FenParser();
    private final QueenMoveGenerator generator =
            new QueenMoveGenerator();

    @Test
    void generatesEightRaysFromCentralSquare() {
        var position = parser.parse(
                "6k1/8/8/8/3Q4/8/8/1K6 w - - 0 20"
        );
        var source = Square.fromAlgebraic("d4");

        var moves = generator.generate(position, source);
        var visualization = BoardTestRenderer.render(
                position, source, moves
        );

        System.out.println(visualization);

        var destinations = moves.stream()
                .map(move -> move.to().toAlgebraic())
                .collect(Collectors.toSet());

        assertEquals(
                Set.of(
                        // Orthogonal rays.
                        "a4", "b4", "c4", "e4", "f4", "g4", "h4",
                        "d1", "d2", "d3", "d5", "d6", "d7", "d8",
                        // Diagonal rays.
                        "c5", "b6", "a7",
                        "e5", "f6", "g7", "h8",
                        "c3", "b2", "a1",
                        "e3", "f2", "g1"
                ),
                destinations,
                visualization
        );
    }

    @Test
    void respectsBlockersAndCapturesAcrossBothRayTypes() {
        // Every ray meets a friendly or opposing pawn.
        var position = parser.parse(
                "6k1/8/1p1p1p2/8/1P1Q1p2/8/1P1P1P2/1K6"
                        + " w - - 0 20"
        );
        var source = Square.fromAlgebraic("d4");

        var moves = generator.generate(position, source);
        var visualization = BoardTestRenderer.render(
                position, source, moves
        );

        System.out.println(visualization);

        var destinations = moves.stream()
                .map(move -> move.to().toAlgebraic())
                .collect(Collectors.toSet());

        assertEquals(
                Set.of(
                        "c4", "e4", "f4", "d3", "d5", "d6",
                        "c5", "b6", "e5", "f6", "c3", "e3"
                ),
                destinations,
                visualization
        );
    }

    @Test
    void generatesMovesForBlackAtBoardCorner() {
        // Black's king on g8 blocks the horizontal ray.
        assertEquals(
                Set.of(
                        "b8", "c8", "d8", "e8", "f8",
                        "a7", "a6", "a5", "a4", "a3", "a2", "a1",
                        "b7", "c6", "d5", "e4", "f3", "g2", "h1"
                ),
                destinations(
                        "q5k1/8/8/8/8/8/8/1K6 b - - 0 20",
                        "a8"
                )
        );
    }

    @Test
    void returnsNoMovesForEmptySquareOrOpposingQueen() {
        var position = parser.parse(
                "6k1/8/8/8/3q4/8/8/1K6 w - - 0 20"
        );

        assertAll(
                () -> assertTrue(generator.generate(
                        position, Square.fromAlgebraic("c2")
                ).isEmpty()),
                () -> assertTrue(generator.generate(
                        position, Square.fromAlgebraic("d4")
                ).isEmpty())
        );
    }

    @Test
    void rejectsNonQueenSource() {
        var position = parser.parse(
                "6k1/8/8/8/3B4/8/8/1K6 w - - 0 20"
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> generator.generate(
                        position, Square.fromAlgebraic("d4")
                )
        );
    }

    @Test
    void excludesEnemyKingAndStopsRayAtItsSquare() {
        // Deliberately invalid position: Black is already in check
        // while White is to move. Isolates defensive generation.
        var destinations = destinations(
                "8/8/5k2/8/3Q4/8/8/1K6 w - - 0 20",
                "d4"
        );

        assertAll(
                () -> assertTrue(destinations.contains("e5")),
                () -> assertFalse(destinations.contains("f6")),
                () -> assertFalse(destinations.contains("g7")),
                () -> assertFalse(destinations.contains("h8")),
                () -> assertEquals(24, destinations.size())
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