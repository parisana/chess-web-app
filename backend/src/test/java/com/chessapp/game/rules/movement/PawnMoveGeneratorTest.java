package com.chessapp.game.rules.movement;

import com.chessapp.BoardTestRenderer;
import com.chessapp.game.rules.fen.FenParser;
import com.chessapp.game.rules.fen.FenSerializer;
import com.chessapp.game.rules.model.PieceType;
import com.chessapp.game.rules.model.Square;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class PawnMoveGeneratorTest {

    private final FenParser parser = new FenParser();
    private final FenSerializer serializer = new FenSerializer();
    private final PawnMoveGenerator generator = new PawnMoveGenerator();

    @ParameterizedTest
    @CsvSource({
            // Starting ranks allow one-square and two-square advances.
            "'7k/8/8/8/8/8/3P4/K7 w - - 0 1', d2, 'd3 d4'",
            "'7k/3p4/8/8/8/8/8/K7 b - - 0 1', d7, 'd6 d5'",

            // Away from the starting rank, only one square forward.
            "'7k/8/8/8/3P4/8/8/K7 w - - 0 20', d4, d5",
            "'7k/8/8/3p4/8/8/8/K7 b - - 0 20', d5, d4",

            // An occupied double-advance destination still permits one step.
            "'7k/8/8/8/3n4/8/3P4/K7 w - - 0 20', d2, d3",
            "'7k/3p4/8/3N4/8/8/8/K7 b - - 0 20', d7, d6",

            // Captures use forward diagonals, alongside a quiet advance.
            "'7k/8/8/2n1b3/3P4/8/8/K7 w - - 0 20', d4, 'c5 d5 e5'",
            "'7k/8/8/3p4/2N1B3/8/8/K7 b - - 0 20', d5, 'c4 d4 e4'",

            // Edge files must not wrap around the board.
            "'7k/8/8/8/8/1n6/P7/7K w - - 0 20', a2, 'a3 a4 b3'",
            "'k7/7p/6N1/8/8/8/8/K7 b - - 0 20', h7, 'h6 h5 g6'",

            // Friendly diagonal occupants cannot be captured.
            "'7k/8/8/2N1B3/3P4/8/8/K7 w - - 0 20', d4, d5",
            "'7k/8/8/3p4/2n1b3/8/8/K7 b - - 0 20', d5, d4"
    })
    void generatesExpectedOrdinaryDestinations(
            String fen,
            String from,
            String expected
    ) {
        assertEquals(
                Set.of(expected.split(" ")),
                destinations(fen, from)
        );
    }

    @ParameterizedTest
    @CsvSource({
            // Friendly and opposing pieces both block forward movement.
            "'7k/8/8/8/8/3N4/3P4/K7 w - - 0 20', d2",
            "'7k/8/8/8/8/3n4/3P4/K7 w - - 0 20', d2",
            "'7k/3p4/3n4/8/8/8/8/K7 b - - 0 20', d7",
            "'7k/3p4/3N4/8/8/8/8/K7 b - - 0 20', d7"
    })
    void occupiedIntermediateSquareBlocksBothAdvances(
            String fen,
            String from
    ) {
        assertTrue(destinations(fen, from).isEmpty());
    }

    @Test
    void blockedAdvanceDoesNotPreventDiagonalCapture() {
        var position = parser.parse(
                "7k/8/8/2nN4/3P4/8/8/K7 w - - 0 20"
        );
        var source = Square.fromAlgebraic("d4");

        var moves = generator.generate(position, source);
        var visualization = BoardTestRenderer.render(
                position, source, moves
        );

        System.out.println(visualization);

        assertEquals(
                Set.of("c5"),
                moves.stream()
                        .map(move -> move.to().toAlgebraic())
                        .collect(Collectors.toSet()),
                visualization
        );
    }

    @Test
    void respectsSourceAndTurnSelection() {
        var position = parser.parse(
                "7k/3p4/8/8/8/8/3P4/K7 w - - 0 20"
        );

        assertAll(
                () -> assertEquals(
                        PieceType.PAWN,
                        generator.pieceType()
                ),
                () -> assertTrue(generator.generate(
                        position, Square.fromAlgebraic("b2")
                ).isEmpty()),
                () -> assertTrue(generator.generate(
                        position, Square.fromAlgebraic("d7")
                ).isEmpty()),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> generator.generate(
                                position, Square.fromAlgebraic("a1")
                        )
                )
        );
    }

    @Test
    void excludesOpposingKingCapture() {
        // Deliberately invalid fixture: Black is already in pawn check
        // on White's turn. Isolates king-capture exclusion.
        assertEquals(
                Set.of("d5"),
                destinations(
                        "8/8/8/4k3/3P4/8/8/K7 w - - 0 20",
                        "d4"
                )
        );
    }

    @ParameterizedTest
    @CsvSource({
            "'7k/8/8/3pP3/8/8/8/K7 w - d6 0 20', e5, 'd6 e6'",
            "'7k/8/8/3Pp3/8/8/8/K7 w - e6 0 20', d5, 'd6 e6'",
            "'7k/8/8/8/3Pp3/8/8/K7 b - d3 0 20', e4, 'd3 e3'",
            "'7k/8/8/8/3pP3/8/8/K7 b - e3 0 20', d4, 'd3 e3'"
    })
    void generatesEnPassantAlongsideOrdinaryAdvance(
            String fen,
            String from,
            String expected
    ) {
        assertEquals(
                Set.of(expected.split(" ")),
                destinations(fen, from)
        );
    }

    @Test
    void unrelatedEnPassantTargetDoesNotPreventOrdinaryMovement() {
        assertEquals(
                Set.of("a3", "a4"),
                destinations(
                        "7k/8/8/3p4/8/8/P7/K7 w - d6 0 20",
                        "a2"
                )
        );
    }

    @ParameterizedTest
    @CsvSource({
            "'P6k/8/8/8/8/8/8/K7 w - - 0 20', a8",
            "'7k/8/8/8/8/8/8/p6K b - - 0 20', a1"
    })
    void rejectsPawnOnTerminalRank(String fen, String from) {
        // Deliberately invalid fixtures: unpromoted pawns on terminal ranks.
        assertThrows(
                IllegalArgumentException.class,
                () -> generator.generate(
                        parser.parse(fen),
                        Square.fromAlgebraic(from)
                )
        );
    }

    @Test
    void preservesInputPositionAndReturnsImmutableCandidates() {
        String originalFen =
                "7k/8/8/8/8/8/3P4/K7 w - - 0 1";
        var position = parser.parse(originalFen);

        var moves = generator.generate(
                position, Square.fromAlgebraic("d2")
        );

        assertAll(
                () -> assertEquals(2, moves.size()),
                () -> assertEquals(
                        originalFen,
                        serializer.serialize(position)
                ),
                () -> assertThrows(
                        UnsupportedOperationException.class,
                        moves::clear
                )
        );
    }

    private Set<String> destinations(String fen, String from) {
        return generator.generate(
                        parser.parse(fen),
                        Square.fromAlgebraic(from)
                ).stream()
                .map(move -> move.to().toAlgebraic())
                .collect(Collectors.toSet());
    }
}