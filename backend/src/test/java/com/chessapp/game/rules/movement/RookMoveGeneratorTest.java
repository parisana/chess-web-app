package com.chessapp.game.rules.movement;

import com.chessapp.BoardTestRenderer;
import com.chessapp.game.rules.fen.FenParser;
import com.chessapp.game.rules.model.Square;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author pari on 10/25/26
 */
class RookMoveGeneratorTest {

    private final FenParser parser = new FenParser();
    private final RookMoveGenerator generator = new RookMoveGenerator();

    @Test
    void generatesFourRaysFromCentralSquare() {
        var position = parser.parse(
                "7k/8/8/8/3R4/8/8/K7 w - - 0 20"
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
                        "a4", "b4", "c4", "e4", "f4", "g4", "h4",
                        "d1", "d2", "d3", "d5", "d6", "d7", "d8"
                ),
                destinations,
                visualization
        );
    }

    @Test
    void stopsAtFriendlyPiecesAndAfterEnemyCaptures() {
        // Friendly pawns block b4 and d2.
        // Enemy pawns on d6 and f4 may be captured, but block beyond.
        var position = parser.parse(
                "7k/8/3p4/8/1P1R1p2/8/3P4/K7 w - - 0 20"
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
                Set.of("c4", "e4", "f4", "d3", "d5", "d6"),
                destinations,
                visualization
        );
    }

    @Test
    void generatesMovesForBlackAtBoardCorner() {
        var position = parser.parse(
                "r6k/8/8/8/8/8/8/7K b - - 0 20"
        );

        var destinations = generator.generate(
                        position,
                        Square.fromAlgebraic("a8")
                ).stream()
                .map(move -> move.to().toAlgebraic())
                .collect(Collectors.toSet());

        // Black's king on h8 blocks the final square of that ray.
        assertEquals(
                Set.of(
                        "b8", "c8", "d8", "e8", "f8", "g8",
                        "a1", "a2", "a3", "a4", "a5", "a6", "a7"
                ),
                destinations
        );
    }

    @Test
    void returnsNoMovesForEmptySquareOrOpponentRook() {
        var position = parser.parse(
                "7k/8/8/8/3r4/8/8/K7 w - - 0 20"
        );

        assertAll(
                () -> assertTrue(generator.generate(
                        position, Square.fromAlgebraic("b2")
                ).isEmpty()),
                () -> assertTrue(generator.generate(
                        position, Square.fromAlgebraic("d4")
                ).isEmpty())
        );
    }

    @Test
    void rejectsNonRookSource() {
        var position = parser.parse(
                "7k/8/8/8/3N4/8/8/K7 w - - 0 20"
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
        // while White is to move. This isolates defensive generation.
        var position = parser.parse(
                "8/8/3k4/8/3R4/8/8/K7 w - - 0 20"
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
                        "a4", "b4", "c4", "e4", "f4", "g4", "h4",
                        "d1", "d2", "d3", "d5"
                ),
                destinations,
                visualization
        );
    }
}