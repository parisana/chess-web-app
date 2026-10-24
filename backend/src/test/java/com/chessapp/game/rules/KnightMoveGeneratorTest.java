package com.chessapp.game.rules;

import com.chessapp.BoardTestRenderer;
import com.chessapp.game.rules.fen.FenParser;
import com.chessapp.game.rules.model.Move;
import com.chessapp.game.rules.model.Square;
import com.chessapp.game.rules.movement.KnightMoveGenerator;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author pari on 10/24/26
 */
class KnightMoveGeneratorTest {

    private final FenParser parser = new FenParser();
    private final KnightMoveGenerator generator = new KnightMoveGenerator();

    @Test
    void generatesAllEightMovesFromCentralSquare() {
        var position = parser.parse(
                "8/8/8/8/3N4/8/8/8 w - - 0 1"
        );
        var source = Square.fromAlgebraic("d4");

        var moves = generator.generate(position, source);
        var visualization = BoardTestRenderer.render(
                position,
                source,
                moves
        );

        System.out.println(visualization);

        var actualDestinations = moves.stream()
                .map(move -> move.to().toAlgebraic())
                .collect(Collectors.toSet());

        assertEquals(
                Set.of("b3", "b5", "c2", "c6", "e2", "e6", "f3", "f5"),
                actualDestinations,
                visualization
        );
    }

    @Test
    void clipsMovesAtBoardCorner() {
        assertEquals(
                Set.of("b3", "c2"),
                destinations("8/8/8/8/8/8/8/N7 w - - 0 1", "a1")
        );
    }

    @Test
    void jumpsOverStartingPiecesButCannotLandOnFriendlyPawn() {
        var position = parser.parse(
                "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR"
                        + " w KQkq - 0 1"
        );
        var source = Square.fromAlgebraic("b1");

        var moves = generator.generate(
                position,
                source
        );
        var visualization = BoardTestRenderer.render(
                position,
                source,
                moves
        );

        System.out.println(visualization);

        assertEquals(
                Set.of(
                        move("b1", "a3"),
                        move("b1", "c3")
                ),
                moves
        );
    }

    @Test
    void permitsEnemyCaptureButExcludesFriendlyPieceAndEnemyKing() {
        // From d4: b3 contains a friendly pawn, f5 an enemy rook,
        // and f3 the enemy king.
        assertEquals(
                Set.of("b5", "c2", "c6", "e2", "e6", "f5"),
                destinations(
                        "8/8/8/5r2/3N4/1P3k2/8/8 w - - 0 1",
                        "d4"
                )
        );
    }

    @Test
    void generatesMovesForBlackWhenBlackIsToMove() {
        assertEquals(
                Set.of("b6", "c7"),
                destinations("n7/8/8/8/8/8/8/8 b - - 0 1", "a8")
        );
    }

    @Test
    void returnsNoMovesForEmptySquareOrOpponentKnight() {
        var position = parser.parse(
                "8/8/8/8/3n4/8/8/8 w - - 0 1"
        );

        assertAll(
                () -> assertTrue(generator.generate(
                        position,
                        Square.fromAlgebraic("a1")
                ).isEmpty()),
                () -> assertTrue(generator.generate(
                        position,
                        Square.fromAlgebraic("d4")
                ).isEmpty())
        );
    }

    @Test
    void rejectsNonKnightSource() {
        var position = parser.parse(
                "8/8/8/8/3R4/8/8/8 w - - 0 1"
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> generator.generate(
                        position,
                        Square.fromAlgebraic("d4")
                )
        );
    }

    @Test
    void includesMovesThatExposeOwnKingBecauseGenerationIsPseudoLegal() {
        // The knight on e2 shields the white king from the rook on e8.
        var position = parser.parse(
                "4r2k/8/8/8/8/8/4N3/4K3 w - - 0 1"
        );

        assertTrue(
                generator.generate(
                        position,
                        Square.fromAlgebraic("e2")
                ).contains(move("e2", "c3"))
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

    private static Move move(String from, String to) {
        return new Move(
                Square.fromAlgebraic(from),
                Square.fromAlgebraic(to)
        );
    }
}