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

    private final FenParser parser = new FenParser();
    private final FenSerializer serializer = new FenSerializer();
    private final PseudoLegalMoveGenerator generator = new PseudoLegalMoveGenerator(
            new KnightMoveGenerator(),
            new RookMoveGenerator()
    );
    private final MoveApplier applier =
            new MoveApplier(generator);

    @Test
    void whiteQuietMoveUpdatesMetadataAndPreservesOriginalPosition() {
        String originalFen =
                "4k3/8/8/4p3/3N4/8/8/4K3 w - e6 0 12";

        var original = parser.parse(originalFen);
        var move = move("d4", "f5");

        var candidate = applier.apply(original, move);

        printTransition(original, candidate, move);

        assertAll(
                () -> assertEquals(
                        "4k3/8/8/4pN2/8/8/8/4K3 b - - 1 12",
                        serializer.serialize(candidate)
                ),
                // Compare contents through FEN rather than Board identity.
                () -> assertEquals(
                        originalFen,
                        serializer.serialize(original)
                )
        );
    }

    @Test
    void capturingBlackCornerRookRemovesOnlyItsCastlingRight() {
        var original = parser.parse(
                "r3k2r/5N2/8/8/8/8/8/4K3 w kq - 9 12"
        );
        var move = move("f7", "h8");

        var candidate = applier.apply(original, move);

        printTransition(original, candidate, move);

        assertEquals(
                "r3k2N/8/8/8/8/8/8/4K3 b q - 0 12",
                serializer.serialize(candidate)
        );
    }

    @Test
    void blackCaptureUpdatesFullmoveNumberAndWhiteCastlingRights() {
        var original = parser.parse(
                "4k3/8/8/8/8/1n6/8/R3K2R b KQ - 8 12"
        );
        var move = move("b3", "a1");

        var candidate = applier.apply(original, move);

        printTransition(original, candidate, move);

        assertEquals(
                "4k3/8/8/8/8/8/8/n3K2R w K - 0 13",
                serializer.serialize(candidate)
        );
    }

    @Test
    void rejectsDestinationOutsideKnightMovementPattern() {
        var position = parser.parse(
                "4k3/8/8/8/3N4/8/8/4K3 w - - 0 1"
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> applier.apply(position, move("d4", "d5"))
        );
    }

    @Test
    void rejectsCaptureOfFriendlyPiece() {
        var position = parser.parse(
                "4k3/8/8/5P2/3N4/8/8/4K3 w - - 0 1"
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> applier.apply(position, move("d4", "f5"))
        );
    }

    @Test
    void stillAllowsCandidateThatExposesOwnKing() {
        var position = parser.parse(
                "4r2k/8/8/8/8/8/4N3/4K3 w - - 0 1"
        );

        var candidate = applier.apply(
                position,
                move("e2", "c3")
        );

        // MoveApplier deliberately leaves king safety to LegalMoveGenerator.
        assertEquals(
                "4r2k/8/8/8/8/2N5/8/4K3 b - - 1 1",
                serializer.serialize(candidate)
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
        var position = parser.parse(
                "r3k2r/8/8/8/8/8/8/R3K2R "
                        + sideToMove + " KQkq - 0 20"
        );

        var candidate = applier.apply(position, move(from, to));

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

        var original = parser.parse(originalFen);
        var selectedMove = move("a1", "a8");

        var candidate = applier.apply(original, selectedMove);

        printTransition(original, candidate, selectedMove);

        assertAll(
                () -> assertEquals(
                        "R3k2r/8/8/8/8/8/8/4K2R b Kk - 0 20",
                        serializer.serialize(candidate)
                ),
                () -> assertEquals(
                        originalFen,
                        serializer.serialize(original)
                )
        );
    }

    @Test
    void returningRookToOriginalCornerDoesNotRestoreCastlingRight() {
        var original = parser.parse(
                "1n4k1/8/8/8/8/8/8/R3K2R w KQ - 0 20"
        );

        var afterRookLeaves = applier.apply(
                original,
                move("a1", "a2")
        );

        // A supported Black reply allows White to move again.
        var afterBlackReply = applier.apply(
                afterRookLeaves,
                move("b8", "c6")
        );

        var afterRookReturns = applier.apply(
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

        System.out.println("FEN: " + serializer.serialize(candidate));
    }

    private static Move move(String from, String to) {
        return new Move(
                Square.fromAlgebraic(from),
                Square.fromAlgebraic(to)
        );
    }
}