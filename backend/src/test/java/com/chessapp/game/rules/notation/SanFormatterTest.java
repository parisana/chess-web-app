package com.chessapp.game.rules.notation;

import com.chessapp.game.rules.ChessRules;
import com.chessapp.game.rules.StandardChessRules;
import com.chessapp.game.rules.attack.SquareAttackDetector;
import com.chessapp.game.rules.evaluation.PositionEvaluator;
import com.chessapp.game.rules.fen.FenParser;
import com.chessapp.game.rules.model.Move;
import com.chessapp.game.rules.model.PieceType;
import com.chessapp.game.rules.model.Square;
import com.chessapp.game.rules.movement.MoveApplier;
import com.chessapp.game.rules.movement.MovementTestFixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author pari on 10/30/26
 */
class SanFormatterTest {

    private final FenParser parser = new FenParser();
    private final SanFormatter formatter = new SanFormatter();
    private final ChessRules rules = createRules();

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            // Ordinary pawn advance.
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR"
                    + " w KQkq - 0 1"
                    + " | e2 | e4 | - | e4",

            // Pawn and knight captures.
            "7k/8/8/3p4/4P3/8/8/K7 w - - 0 1"
                    + " | e4 | d5 | - | exd5",
            "7k/8/8/3p4/5N2/8/8/K7 w - - 0 1"
                    + " | f4 | d5 | - | Nxd5",

            // Castling for both colors and sides.
            "r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1"
                    + " | e1 | g1 | - | O-O",
            "r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1"
                    + " | e1 | c1 | - | O-O-O",
            "r3k2r/8/8/8/8/8/8/R3K2R b KQkq - 0 1"
                    + " | e8 | g8 | - | O-O",
            "r3k2r/8/8/8/8/8/8/R3K2R b KQkq - 0 1"
                    + " | e8 | c8 | - | O-O-O",

            // En passant is formatted as a pawn capture.
            "7k/8/8/3pP3/8/8/8/K7 w - d6 0 20"
                    + " | e5 | d6 | - | exd6",

            // Quiet and capture underpromotions.
            "7k/4P3/8/8/8/8/8/K7 w - - 0 20"
                    + " | e7 | e8 | KNIGHT | e8=N",
            "3r3k/4P3/8/8/8/8/8/K7 w - - 0 20"
                    + " | e7 | d8 | KNIGHT | exd8=N",

            // Promotion giving check.
            "7k/4P3/8/8/8/8/8/K7 w - - 0 20"
                    + " | e7 | e8 | QUEEN | e8=Q+",

            // Check, checkmate and stalemate.
            "7k/8/8/8/8/8/8/K3R3 w - - 4 20"
                    + " | e1 | h1 | - | Rh1+",
            "7k/8/5KQ1/8/8/8/8/8 w - - 4 20"
                    + " | g6 | g7 | - | Qg7#",
            "7k/8/6K1/5Q2/8/8/8/8 w - - 4 20"
                    + " | f5 | f7 | - | Qf7"
    })
    void formatsAcceptedMoves(
            String fen,
            String from,
            String to,
            String promotion,
            String expected
    ) {
        assertSan(fen, from, to, promotion, expected);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            // Knights b1 and f1 can both reach d2: file is sufficient.
            "7k/8/8/8/8/8/8/KN3N2 w - - 0 1"
                    + " | b1 | d2 | Nbd2",

            // Knights b1 and b3 share a file: rank is required.
            "7k/8/8/8/8/1N6/8/KN6 w - - 0 1"
                    + " | b1 | d2 | N1d2",

            // b3 shares the source file; f1 shares the source rank.
            "7k/8/8/8/8/1N6/8/KN3N2 w - - 0 1"
                    + " | b1 | d2 | Nb1d2",

            // e2 is pinned to the king by the rook on e8.
            // Only the knight on g2 can legally reach f4.
            "4r2k/8/8/8/8/8/4N1N1/4K3 w - - 0 1"
                    + " | g2 | f4 | Nf4"
    })
    void disambiguatesUsingOnlyLegalAlternatives(
            String fen,
            String from,
            String to,
            String expected
    ) {
        assertSan(fen, from, to, "-", expected);
    }

    @Test
    void rejectsAMoveOutsideTheSuppliedLegalSet() {
        var position = rules.initialPosition();
        var illegal = new Move(
                Square.fromAlgebraic("e2"),
                Square.fromAlgebraic("e5")
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> formatter.format(
                        position,
                        illegal,
                        rules.legalMoves(position),
                        rules.evaluate(position)
                )
        );
    }

    private void assertSan(
            String fen,
            String from,
            String to,
            String promotion,
            String expected
    ) {
        var position = parser.parse(fen);

        var move = new Move(
                Square.fromAlgebraic(from),
                Square.fromAlgebraic(to),
                promotion.equals("-")
                        ? Optional.empty()
                        : Optional.of(PieceType.valueOf(promotion))
        );

        var legalMoves = rules.legalMoves(position);
        assertTrue(legalMoves.contains(move), "Fixture move must be legal");

        var nextPosition = rules.applyMove(position, move);
        var assessment = rules.evaluate(nextPosition);

        assertEquals(
                expected,
                formatter.format(
                        position,
                        move,
                        legalMoves,
                        assessment
                )
        );
    }

    private static ChessRules createRules() {
        var dispatcher = MovementTestFixtures.pseudoLegalGenerator();
        var legalGenerator =
                MovementTestFixtures.legalGenerator(dispatcher);

        return new StandardChessRules(
                new FenParser(),
                legalGenerator,
                new MoveApplier(dispatcher),
                new PositionEvaluator(
                        legalGenerator,
                        new SquareAttackDetector()
                )
        );
    }
}