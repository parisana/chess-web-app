package com.chessapp.game.rules.movement;

import com.chessapp.BoardTestRenderer;
import com.chessapp.game.rules.fen.FenParser;
import com.chessapp.game.rules.fen.FenSerializer;
import com.chessapp.game.rules.model.ChessPosition;
import com.chessapp.game.rules.model.Move;
import com.chessapp.game.rules.model.Square;
import com.chessapp.game.rules.model.CastlingRights;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author pari on 10/24/26
 */
class MoveApplierTest {

    private final FenParser fenParser = new FenParser();
    private final FenSerializer fenSerializer = new FenSerializer();
    private final PseudoLegalMoveGenerator generator = MovementTestFixtures.pseudoLegalGenerator();
    private final MoveApplier moveApplier =
            new MoveApplier(generator);

    @Test
    void whiteQuietMoveUpdatesMetadataAndPreservesOriginalPosition() {
        String originalFen =
                "4k3/8/8/4p3/3N4/8/8/4K3 w - e6 0 12";

        var original = fenParser.parse(originalFen);
        var move = move("d4", "f5");

        var candidate = moveApplier.apply(original, move);

        printTransition(original, candidate, move);

        assertAll(
                () -> assertEquals(
                        "4k3/8/8/4pN2/8/8/8/4K3 b - - 1 12",
                        fenSerializer.serialize(candidate)
                ),
                // Compare contents through FEN rather than Board identity.
                () -> assertEquals(
                        originalFen,
                        fenSerializer.serialize(original)
                )
        );
    }

    @Test
    void capturingBlackCornerRookRemovesOnlyItsCastlingRight() {
        var original = fenParser.parse(
                "r3k2r/5N2/8/8/8/8/8/4K3 w kq - 9 12"
        );
        var move = move("f7", "h8");

        var candidate = moveApplier.apply(original, move);

        printTransition(original, candidate, move);

        assertEquals(
                "r3k2N/8/8/8/8/8/8/4K3 b q - 0 12",
                fenSerializer.serialize(candidate)
        );
    }

    @Test
    void blackCaptureUpdatesFullmoveNumberAndWhiteCastlingRights() {
        var original = fenParser.parse(
                "4k3/8/8/8/8/1n6/8/R3K2R b KQ - 8 12"
        );
        var move = move("b3", "a1");

        var candidate = moveApplier.apply(original, move);

        printTransition(original, candidate, move);

        assertEquals(
                "4k3/8/8/8/8/8/8/n3K2R w K - 0 13",
                fenSerializer.serialize(candidate)
        );
    }

    @Test
    void rejectsDestinationOutsideKnightMovementPattern() {
        var position = fenParser.parse(
                "4k3/8/8/8/3N4/8/8/4K3 w - - 0 1"
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> moveApplier.apply(position, move("d4", "d5"))
        );
    }

    @Test
    void rejectsCaptureOfFriendlyPiece() {
        var position = fenParser.parse(
                "4k3/8/8/5P2/3N4/8/8/4K3 w - - 0 1"
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> moveApplier.apply(position, move("d4", "f5"))
        );
    }

    @Test
    void stillAllowsCandidateThatExposesOwnKing() {
        var position = fenParser.parse(
                "4r2k/8/8/8/8/8/4N3/4K3 w - - 0 1"
        );

        var candidate = moveApplier.apply(
                position,
                move("e2", "c3")
        );

        // MoveApplier deliberately leaves king safety to LegalMoveGenerator.
        assertEquals(
                "4r2k/8/8/8/8/2N5/8/4K3 b - - 1 1",
                fenSerializer.serialize(candidate)
        );
    }

    @ParameterizedTest
    @CsvSource({
            "w, h1, h2, false, true,  true,  true",
            "w, a1, a2, true,  false, true,  true",
            "b, h8, h7, true,  true,  false, true",
            "b, a8, a7, true,  true,  true,  false"
    })
    void movingCornerRookRemovesOnlyItsCastlingRight(
            String sideToMove,
            String from,
            String to,
            boolean whiteKingSide,
            boolean whiteQueenSide,
            boolean blackKingSide,
            boolean blackQueenSide
    ) {
        var position = fenParser.parse(
                "r3k2r/8/8/8/8/8/8/R3K2R "
                        + sideToMove + " KQkq - 0 20"
        );

        var candidate = moveApplier.apply(position, move(from, to));

        assertEquals(
                new CastlingRights(
                        whiteKingSide,
                        whiteQueenSide,
                        blackKingSide,
                        blackQueenSide
                ),
                candidate.castlingRights()
        );
    }

    @Test
    void rookCaptureCanRemoveCastlingRightsForBothPlayers() {
        String originalFen =
                "r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 8 20";

        var original = fenParser.parse(originalFen);
        var selectedMove = move("a1", "a8");

        var candidate = moveApplier.apply(original, selectedMove);

        printTransition(original, candidate, selectedMove);

        assertAll(
                () -> assertEquals(
                        "R3k2r/8/8/8/8/8/8/4K2R b Kk - 0 20",
                        fenSerializer.serialize(candidate)
                ),
                () -> assertEquals(
                        originalFen,
                        fenSerializer.serialize(original)
                )
        );
    }

    @Test
    void returningRookToOriginalCornerDoesNotRestoreCastlingRight() {
        var original = fenParser.parse(
                "1n4k1/8/8/8/8/8/8/R3K2R w KQ - 0 20"
        );

        var afterRookLeaves = moveApplier.apply(
                original,
                move("a1", "a2")
        );

        // A supported Black reply allows White to move again.
        var afterBlackReply = moveApplier.apply(
                afterRookLeaves,
                move("b8", "c6")
        );

        var afterRookReturns = moveApplier.apply(
                afterBlackReply,
                move("a2", "a1")
        );

        var remainingRights = new CastlingRights(
                true, false, false, false
        );

        assertAll(
                () -> assertEquals(
                        remainingRights,
                        afterRookLeaves.castlingRights()
                ),
                () -> assertEquals(
                        remainingRights,
                        afterRookReturns.castlingRights()
                )
        );
    }

    @Test
    void bishopCaptureRemovesCapturedCornerRooksCastlingRight() {
        String originalFen =
                "r3k2r/6B1/8/8/8/8/8/4K3 w kq - 9 12";

        var original = fenParser.parse(originalFen);
        var selectedMove = move("g7", "h8");

        var candidate = moveApplier.apply(original, selectedMove);

        printTransition(original, candidate, selectedMove);

        assertAll(
                () -> assertEquals(
                        "r3k2B/8/8/8/8/8/8/4K3 b q - 0 12",
                        fenSerializer.serialize(candidate)
                ),
                () -> assertEquals(
                        originalFen,
                        fenSerializer.serialize(original)
                )
        );
    }

    @Test
    void blackQuietBishopMoveUpdatesCountersAndTurn() {
        var original = fenParser.parse(
                "b5k1/8/8/8/8/8/8/1K6 b - - 4 20"
        );

        var candidate = moveApplier.apply(
                original,
                move("a8", "b7")
        );

        assertEquals(
                "6k1/1b6/8/8/8/8/8/1K6 w - - 5 21",
                fenSerializer.serialize(candidate)
        );
    }

    @Test
    void queenCaptureRemovesCapturedCornerRooksCastlingRight() {
        String originalFen =
                "r3k2r/6Q1/8/8/8/8/8/4K3 w kq - 9 12";

        var original = fenParser.parse(originalFen);
        var selectedMove = move("g7", "h8");

        var candidate = moveApplier.apply(original, selectedMove);

        printTransition(original, candidate, selectedMove);

        assertAll(
                () -> assertEquals(
                        "r3k2Q/8/8/8/8/8/8/4K3 b q - 0 12",
                        fenSerializer.serialize(candidate)
                ),
                () -> assertEquals(
                        originalFen,
                        fenSerializer.serialize(original)
                )
        );
    }

    @Test
    void blackQuietQueenMoveUpdatesCountersAndTurn() {
        var original = fenParser.parse(
                "q5k1/8/8/8/8/8/8/1K6 b - - 4 20"
        );

        var candidate = moveApplier.apply(
                original,
                move("a8", "b7")
        );

        assertEquals(
                "6k1/1q6/8/8/8/8/8/1K6 w - - 5 21",
                fenSerializer.serialize(candidate)
        );
    }

    @Test
    void whiteKingMoveRemovesOnlyWhiteCastlingRights() {
        String originalFen =
                "r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 4 20";
        var original = fenParser.parse(originalFen);
        var move = move("e1", "f1");

        var candidate = moveApplier.apply(original, move);

        printTransition(original, candidate, move);

        assertAll(
                () -> assertEquals(
                        "r3k2r/8/8/8/8/8/8/R4K1R b kq - 5 20",
                        fenSerializer.serialize(candidate)
                ),
                () -> assertEquals(
                        originalFen,
                        fenSerializer.serialize(original)
                )
        );
    }

    @Test
    void blackKingMoveRemovesOnlyBlackCastlingRights() {
        String originalFen =
                "r3k2r/8/8/8/8/8/8/R3K2R b KQkq - 4 20";
        var original = fenParser.parse(originalFen);

        var candidate = moveApplier.apply(
                original,
                move("e8", "f8")
        );

        assertAll(
                () -> assertEquals(
                        "r4k1r/8/8/8/8/8/8/R3K2R w KQ - 5 21",
                        fenSerializer.serialize(candidate)
                ),
                () -> assertEquals(
                        originalFen,
                        fenSerializer.serialize(original)
                )
        );
    }

    @Test
    void returningKingsNeverRestoresCastlingRights() {
        String originalFen =
                "r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 4 20";
        var original = fenParser.parse(originalFen);

        var afterWhiteLeaves = moveApplier.apply(
                original,
                move("e1", "f1")
        );
        var afterBlackLeaves = moveApplier.apply(
                afterWhiteLeaves,
                move("e8", "f8")
        );
        var afterWhiteReturns = moveApplier.apply(
                afterBlackLeaves,
                move("f1", "e1")
        );
        var afterBlackReturns = moveApplier.apply(
                afterWhiteReturns,
                move("f8", "e8")
        );

        assertAll(
                () -> assertEquals(
                        new CastlingRights(false, false, false, false),
                        afterWhiteReturns.castlingRights()
                ),
                () -> assertEquals(
                        // Placement returns to its original arrangement,
                        // but castling rights remain lost.
                        "r3k2r/8/8/8/8/8/8/R3K2R w - - 8 22",
                        fenSerializer.serialize(afterBlackReturns)
                ),
                () -> assertEquals(
                        originalFen,
                        fenSerializer.serialize(original)
                )
        );
    }

    @Test
    void kingCaptureResetsClockAndRemovesCapturedCornerRookRight() {
        String originalFen =
                "r3k2r/6K1/8/8/8/8/8/8 w kq - 4 20";
        var original = fenParser.parse(originalFen);
        var capture = move("g7", "h8");

        var candidate = moveApplier.apply(original, capture);

        printTransition(original, candidate, capture);

        assertAll(
                () -> assertEquals(
                        "r3k2K/8/8/8/8/8/8/8 b q - 0 20",
                        fenSerializer.serialize(candidate)
                ),
                () -> assertEquals(
                        originalFen,
                        fenSerializer.serialize(original)
                )
        );
    }

    @ParameterizedTest
    @CsvSource({
            // Quiet pawn moves reset the halfmove clock for both colors.
            "'7k/8/8/8/3P4/8/8/K7 w - - 7 20', d4, d5, "
                    + "'7k/8/8/3P4/8/8/8/K7 b - - 0 20'",
            "'7k/8/8/3p4/8/8/8/K7 b - - 7 20', d5, d4, "
                    + "'7k/8/8/8/3p4/8/8/K7 w - - 0 21'",

            // Double advances record the passed-over square.
            "'7k/8/8/8/8/8/3P4/K7 w - - 7 20', d2, d4, "
                    + "'7k/8/8/8/3P4/8/8/K7 b - d3 0 20'",
            "'7k/3p4/8/8/8/8/8/K7 b - - 7 20', d7, d5, "
                    + "'7k/8/8/3p4/8/8/8/K7 w - d6 0 21'",

            // Ordinary diagonal captures use shared board relocation.
            "'7k/8/8/4n3/3P4/8/8/K7 w - - 7 20', d4, e5, "
                    + "'7k/8/8/4P3/8/8/8/K7 b - - 0 20'",
            "'7k/8/8/3p4/4N3/8/8/K7 b - - 7 20', d5, e4, "
                    + "'7k/8/8/8/4p3/8/8/K7 w - - 0 21'",

            // An ordinary advance expires an unrelated previous target.
            "'7k/8/8/3p4/8/P7/8/K7 w - d6 0 20', a3, a4, "
                    + "'7k/8/8/3p4/P7/8/8/K7 b - - 0 20'",

            // A new double advance replaces the previous target.
            "'7k/8/8/3p4/8/8/P7/K7 w - d6 0 20', a2, a4, "
                    + "'7k/8/8/3p4/P7/8/8/K7 b - a3 0 20'",
            "'7k/p7/8/8/3P4/8/8/K7 b - d3 0 20', a7, a5, "
                    + "'7k/8/8/p7/3P4/8/8/K7 w - a6 0 21'"
    })
    void pawnMoveUpdatesMetadataAndPreservesOriginal(
            String originalFen,
            String from,
            String to,
            String expectedFen
    ) {
        var original = fenParser.parse(originalFen);
        var pawnMove = move(from, to);

        var candidate = moveApplier.apply(original, pawnMove);

        printTransition(original, candidate, pawnMove);

        assertAll(
                () -> assertEquals(
                        expectedFen,
                        fenSerializer.serialize(candidate)
                ),
                () -> assertEquals(
                        originalFen,
                        fenSerializer.serialize(original)
                )
        );
    }

    @ParameterizedTest
    @CsvSource({
            // En passant capture.
            "'7k/8/8/3pP3/8/8/8/K7 w - d6 0 20', e5, d6",
            "'7k/8/8/8/3Pp3/8/8/K7 b - d3 0 20', e4, d3"
    })
    void rejectsUnsupportedPawnSpecialMoves(
            String fen,
            String from,
            String to
    ) {
        assertThrows(
                UnsupportedOperationException.class,
                () -> moveApplier.apply(
                        fenParser.parse(fen),
                        move(from, to)
                )
        );
    }

    private void printTransition(
            ChessPosition original,
            ChessPosition candidate,
            Move move
    ) {
        System.out.println(
                "\nBefore " + move.from().toAlgebraic()
                        + "->" + move.to().toAlgebraic()
        );

        // Show only the selected candidate to keep this transition clear.
        System.out.println(BoardTestRenderer.render(
                original,
                move.from(),
                Set.of(move)
        ));

        System.out.println("After — candidate position");

        // Highlight the relocated piece; no next-turn moves are supplied.
        System.out.println(BoardTestRenderer.render(
                candidate,
                move.to(),
                Set.of()
        ));

        System.out.println("FEN: " + fenSerializer.serialize(candidate));
    }

    private static Move move(String from, String to) {
        return new Move(
                Square.fromAlgebraic(from),
                Square.fromAlgebraic(to)
        );
    }
}