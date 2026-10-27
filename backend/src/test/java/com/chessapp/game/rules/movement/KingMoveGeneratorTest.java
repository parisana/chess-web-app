package com.chessapp.game.rules.movement;

import com.chessapp.BoardTestRenderer;
import com.chessapp.game.rules.fen.FenParser;
import com.chessapp.game.rules.model.PieceType;
import com.chessapp.game.rules.model.Square;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author pari on 10/26/26
 */
class KingMoveGeneratorTest {

    private final FenParser parser = new FenParser();
    private final KingMoveGenerator generator = new KingMoveGenerator();

    @Test
    void generatesAllEightAdjacentDestinations() {
        var position = parser.parse(
                "7k/8/8/8/3K4/8/8/8 w - - 0 20"
        );
        var source = Square.fromAlgebraic("d4");

        var moves = generator.generate(position, source);
        var visualization = BoardTestRenderer.render(
                position, source, moves
        );

        System.out.println(visualization);

        assertAll(
                () -> assertEquals(PieceType.KING, generator.pieceType()),
                () -> assertEquals(
                        Set.of(
                                "c3", "d3", "e3",
                                "c4", "e4",
                                "c5", "d5", "e5"
                        ),
                        moves.stream()
                                .map(move -> move.to().toAlgebraic())
                                .collect(Collectors.toSet()),
                        visualization
                )
        );
    }

    @Test
    void clipsMovementAtWhiteAndBlackCorners() {
        assertAll(
                () -> assertEquals(
                        Set.of("a2", "b1", "b2"),
                        destinations(
                                "7k/8/8/8/8/8/8/K7 w - - 0 20",
                                "a1"
                        )
                ),
                () -> assertEquals(
                        Set.of("g7", "g8", "h7"),
                        destinations(
                                "7k/8/8/8/8/8/8/K7 b - - 0 20",
                                "h8"
                        )
                )
        );
    }

    @Test
    void excludesFriendlyOccupantsAndAllowsEnemyCaptures() {
        // Friendly pawn on c4; capturable Black pawn on e4.
        assertEquals(
                Set.of("c3", "d3", "e3", "e4", "c5", "d5", "e5"),
                destinations(
                        "7k/8/8/8/2PKp3/8/8/8 w - - 0 20",
                        "d4"
                )
        );
    }

    @Test
    void returnsNoMovesForEmptySquareOrOpposingKing() {
        var position = parser.parse(
                "7k/8/8/8/3K4/8/8/8 w - - 0 20"
        );

        assertAll(
                () -> assertTrue(generator.generate(
                        position, Square.fromAlgebraic("a1")
                ).isEmpty()),
                () -> assertTrue(generator.generate(
                        position, Square.fromAlgebraic("h8")
                ).isEmpty())
        );
    }

    @Test
    void rejectsNonKingSource() {
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
    void excludesEnemyKingCapture() {
        // Deliberately invalid fixture: adjacent kings.
        // Isolates king-capture exclusion before safety filtering.
        assertEquals(
                Set.of("c3", "d3", "e3", "c4", "c5", "d5", "e5"),
                destinations(
                        "8/8/8/8/3Kk3/8/8/8 w - - 0 20",
                        "d4"
                )
        );
    }

    @Test
    void generatesBothCastlingSidesAlongsideOrdinaryKingMoves() {
        assertAll(
                () -> assertEquals(
                        Set.of("c1", "d1", "f1", "g1", "d2", "e2", "f2"),
                        destinations(
                                "r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 20",
                                "e1"
                        )
                ),
                () -> assertEquals(
                        Set.of("c8", "d8", "f8", "g8", "d7", "e7", "f7"),
                        destinations(
                                "r3k2r/8/8/8/8/8/8/R3K2R b KQkq - 0 20",
                                "e8"
                        )
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