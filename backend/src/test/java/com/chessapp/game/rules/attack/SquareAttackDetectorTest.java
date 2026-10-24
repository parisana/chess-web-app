package com.chessapp.game.rules.attack;

import com.chessapp.BoardTestRenderer;
import com.chessapp.game.rules.fen.FenParser;
import com.chessapp.game.rules.model.PieceColor;
import com.chessapp.game.rules.model.Square;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author pari on 10/24/26
 */
class SquareAttackDetectorTest {

    private final FenParser parser = new FenParser();
    private final SquareAttackDetector detector = new SquareAttackDetector();

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "8/8/8/4p3/8/8/8/8 b - - 0 1 | d4",
            "8/8/8/5n2/8/8/8/8 w - - 0 1 | d4",
            "8/8/8/3k4/8/8/8/8 w - - 0 1 | d4",
            "3r4/8/8/8/8/8/8/8 w - - 0 1 | d4",
            "7b/8/8/8/8/8/8/8 w - - 0 1 | d4",
            "8/8/8/8/7q/8/8/8 w - - 0 1 | d4",
            "8/8/8/8/8/8/1q6/8 w - - 0 1 | d4"
    })
    void detectsEveryBlackAttackerType(String fen, String target) {
        var position = parser.parse(fen);

        assertTrue(detector.isAttacked(
                position.board(),
                Square.fromAlgebraic(target),
                PieceColor.BLACK
        ));
    }

    @Test
    void pawnAttacksDiagonallyRatherThanForward() {
        var position = parser.parse(
                "8/8/8/4p3/8/8/8/8 w - - 0 1"
        );

        assertAll(
                () -> assertTrue(detector.isAttacked(
                        position.board(),
                        Square.fromAlgebraic("d4"),
                        PieceColor.BLACK
                )),
                () -> assertTrue(detector.isAttacked(
                        position.board(),
                        Square.fromAlgebraic("f4"),
                        PieceColor.BLACK
                )),
                () -> assertFalse(detector.isAttacked(
                        position.board(),
                        Square.fromAlgebraic("e4"),
                        PieceColor.BLACK
                ))
        );
    }

    @Test
    void whitePawnAttacksTowardIncreasingRanks() {
        var position = parser.parse(
                "8/8/8/8/3P4/8/8/8 b - - 0 1"
        );

        assertAll(
                () -> assertTrue(detector.isAttacked(
                        position.board(),
                        Square.fromAlgebraic("e5"),
                        PieceColor.WHITE
                )),
                () -> assertFalse(detector.isAttacked(
                        position.board(),
                        Square.fromAlgebraic("e3"),
                        PieceColor.WHITE
                ))
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {
            // Black rook d8, white pawn d6.
            "k2r4/8/3P4/8/8/8/8/K7 b - - 0 1",
            // Black rook d8, black pawn d6.
            "k2r4/8/3p4/8/8/8/8/K7 b - - 0 1",
            // Black bishop h8, white pawn f6.
            "k6b/8/5P2/8/8/8/8/K7 b - - 0 1",
            // Black bishop h8, black pawn f6.
            "k6b/8/5p2/8/8/8/8/K7 b - - 0 1"
    })
    void eitherColorCanBlockSlidingAttack(String fen) {
        var position = parser.parse(fen);
        var target = Square.fromAlgebraic("d4");

        // Angle brackets highlight the inspected square in this rendering.
        var visualization = BoardTestRenderer.render(
                position,
                target,
                Set.of()
        );

        System.out.println(visualization);

        assertFalse(
                detector.isAttacked(
                        position.board(),
                        target,
                        PieceColor.BLACK
                ),
                visualization
        );
    }

    @Test
    void ignoresPiecesOfOtherColor() {
        var position = parser.parse(
                "3R4/8/8/8/8/8/8/8 w - - 0 1"
        );

        assertFalse(detector.isAttacked(
                position.board(),
                Square.fromAlgebraic("d4"),
                PieceColor.BLACK
        ));
    }

    @Test
    void detectsAttackAtBoardCorner() {
        var position = parser.parse(
                "8/8/8/8/8/8/2n5/8 w - - 0 1"
        );

        assertTrue(detector.isAttacked(
                position.board(),
                Square.fromAlgebraic("a1"),
                PieceColor.BLACK
        ));
    }

    @Test
    void pinnedKnightStillAttacksSquares() {
        // The black knight shields its king from the white rook.
        var position = parser.parse(
                "4k3/4n3/8/8/8/8/8/K3R3 w - - 0 1"
        );
        var target = Square.fromAlgebraic("f5");

        var visualization = BoardTestRenderer.render(
                position,
                target,
                Set.of()
        );

        System.out.println(visualization);

        assertTrue(
                detector.isAttacked(
                        position.board(),
                        target,
                        PieceColor.BLACK
                ),
                visualization
        );
    }

    @Test
    void emptyBoardHasNoAttacks() {
        var position = parser.parse(
                "8/8/8/8/8/8/8/8 w - - 0 1"
        );

        assertFalse(detector.isAttacked(
                position.board(),
                Square.fromAlgebraic("d4"),
                PieceColor.BLACK
        ));
    }
}