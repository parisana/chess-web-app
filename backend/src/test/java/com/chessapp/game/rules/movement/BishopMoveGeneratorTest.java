package com.chessapp.game.rules.movement;

import com.chessapp.BoardTestRenderer;
import com.chessapp.game.rules.fen.FenParser;
import com.chessapp.game.rules.model.Square;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class BishopMoveGeneratorTest {

    private final FenParser parser = new FenParser();
    private final BishopMoveGenerator generator =
            new BishopMoveGenerator();

    @Test
    void generatesFourDiagonalsFromCentralSquare() {
        var position = parser.parse(
                "6k1/8/8/8/3B4/8/8/1K6 w - - 0 20"
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
    void stopsAtFriendlyPiecesAndAfterEnemyCaptures() {
        // Friendly pawns on b2 and f2 block the lower diagonals.
        // Enemy pawns on b6 and f6 are capturable and end their rays.
        var position = parser.parse(
                "6k1/8/1p3p2/8/3B4/8/1P3P2/1K6 w - - 0 20"
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
                Set.of("c5", "b6", "e5", "f6", "c3", "e3"),
                destinations,
                visualization
        );
    }

    @Test
    void generatesMovesForBlackAtBoardCorner() {
        assertEquals(
                Set.of("b7", "c6", "d5", "e4", "f3", "g2", "h1"),
                destinations(
                        "b5k1/8/8/8/8/8/8/1K6 b - - 0 20",
                        "a8"
                )
        );
    }

    @Test
    void returnsNoMovesForEmptySquareOrOpposingBishop() {
        var position = parser.parse(
                "6k1/8/8/8/3b4/8/8/1K6 w - - 0 20"
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
    void rejectsNonBishopSource() {
        var position = parser.parse(
                "6k1/8/8/8/3N4/8/8/1K6 w - - 0 20"
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
        assertEquals(
                Set.of(
                        "c5", "b6", "a7",
                        "e5",
                        "c3", "b2", "a1",
                        "e3", "f2", "g1"
                ),
                destinations(
                        "8/8/5k2/8/3B4/8/8/1K6 w - - 0 20",
                        "d4"
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