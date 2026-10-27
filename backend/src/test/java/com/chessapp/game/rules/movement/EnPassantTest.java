package com.chessapp.game.rules.movement;

import com.chessapp.BoardTestRenderer;
import com.chessapp.game.rules.fen.FenParser;
import com.chessapp.game.rules.fen.FenSerializer;
import com.chessapp.game.rules.model.Move;
import com.chessapp.game.rules.model.PieceType;
import com.chessapp.game.rules.model.Square;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class EnPassantTest {

    private final FenParser parser = new FenParser();
    private final FenSerializer serializer = new FenSerializer();

    private final PseudoLegalMoveGenerator generator =
            MovementTestFixtures.pseudoLegalGenerator();

    private final MoveApplier applier = new MoveApplier(generator);

    private final LegalMoveGenerator legalGenerator =
            MovementTestFixtures.legalGenerator(generator);

    @ParameterizedTest
    @CsvSource({
            // No recorded opportunity, despite adjacent opposing pawns.
            "'7k/8/8/3pP3/8/8/8/K7 w - - 0 20', e5, d6, e6",
            "'7k/8/8/8/3Pp3/8/8/K7 b - - 0 20', e4, d3, e3",

            // Deliberately inconsistent: captured pawn is absent.
            "'7k/8/8/4P3/8/8/8/K7 w - d6 0 20', e5, d6, e6",
            "'7k/8/8/8/4p3/8/8/K7 b - d3 0 20', e4, d3, e3",

            // Deliberately inconsistent: adjacent pawn has the same color.
            "'7k/8/8/3PP3/8/8/8/K7 w - d6 0 20', e5, d6, e6",
            "'7k/8/8/8/3pp3/8/8/K7 b - d3 0 20', e4, d3, e3",

            // Deliberately inconsistent: adjacent piece is not a pawn.
            "'7k/8/8/3nP3/8/8/8/K7 w - d6 0 20', e5, d6, e6",
            "'7k/8/8/8/3Np3/8/8/K7 b - d3 0 20', e4, d3, e3",

            // Deliberately inconsistent: target matches a forward diagonal,
            // but the capturing pawn is on the wrong rank.
            "'7k/8/8/8/8/8/3pP3/K7 w - d3 0 20', e2, d3, e3",
            "'7k/3Pp3/8/8/8/8/8/K7 b - d6 0 20', e7, d6, e6"
    })
    void unavailableEnPassantPreservesOrdinaryAdvance(
            String fen,
            String from,
            String capture,
            String advance
    ) {
        var position = parser.parse(fen);
        var unavailableCapture = move(from, capture);
        var ordinaryAdvance = move(from, advance);

        var candidates = generator.generate(
                position, Square.fromAlgebraic(from)
        );
        System.out.println(BoardTestRenderer.render(position, Square.fromAlgebraic(from), candidates));

        assertAll(
                () -> assertFalse(candidates.contains(unavailableCapture)),
                () -> assertTrue(candidates.contains(ordinaryAdvance)),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> applier.apply(position, unavailableCapture)
                ),
                () -> assertEquals(fen, serializer.serialize(position))
        );
    }

    @Test
    void occupiedEnemyDestinationUsesOrdinaryCapture() {
        // Deliberately inconsistent metadata: d6 is occupied.
        String fen = "7k/8/3n4/3pP3/8/8/8/K7 w - d6 0 20";
        var original = parser.parse(fen);
        var selectedMove = move("e5", "d6");

        var candidate = applier.apply(original, selectedMove);
        System.out.println(BoardTestRenderer.render(original, Square.fromAlgebraic("e5"), Set.of(selectedMove)));
        System.out.println("candidate moves:");
        System.out.println(BoardTestRenderer.render(candidate, Square.fromAlgebraic("e5"), Set.of(selectedMove)));

        assertAll(
                () -> assertEquals(
                        "7k/8/3P4/3p4/8/8/8/K7 b - - 0 20",
                        serializer.serialize(candidate)
                ),
                () -> assertEquals(fen, serializer.serialize(original))
        );
    }

    @Test
    void occupiedFriendlyDestinationCannotBeCaptured() {
        // Deliberately inconsistent metadata: d6 contains a friendly knight.
        var position = parser.parse(
                "7k/8/3N4/3pP3/8/8/8/K7 w - d6 0 20"
        );
        var selectedMove = move("e5", "d6");
        System.out.println("Deliberately inconsistent chess position");
        System.out.println(BoardTestRenderer.render(position, Square.fromAlgebraic("e5"), Set.of(selectedMove)));

        assertAll(
                () -> assertEquals(
                        Set.of(move("e5", "e6")),
                        generator.generate(
                                position, Square.fromAlgebraic("e5")
                        )
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> applier.apply(position, selectedMove)
                )
        );
    }

    @Test
    void enPassantOpportunityExpiresAfterAnotherMove() {
        var initial = parser.parse(
                "7k/8/8/8/3p4/8/4P3/K7 w - - 0 20"
        );

        var afterDoubleAdvance = applier.apply(
                initial, move("e2", "e4")
        );
        var capture = move("d4", "e3");

        assertAll(
                () -> assertEquals(
                        "7k/8/8/8/3pP3/8/8/K7 b - e3 0 20",
                        serializer.serialize(afterDoubleAdvance)
                ),
                () -> assertTrue(
                        legalGenerator.generate(
                                afterDoubleAdvance,
                                Square.fromAlgebraic("d4")
                        ).contains(capture)
                )
        );

        var afterDecliningCapture = applier.apply(
                afterDoubleAdvance, move("h8", "g8")
        );
        var afterWhiteReply = applier.apply(
                afterDecliningCapture, move("a1", "b1")
        );

        assertAll(
                () -> assertTrue(
                        afterDecliningCapture.enPassantTarget().isEmpty()
                ),
                () -> assertFalse(
                        generator.generate(
                                afterWhiteReply,
                                Square.fromAlgebraic("d4")
                        ).contains(capture)
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> applier.apply(afterWhiteReply, capture)
                )
        );
    }

    @ParameterizedTest
    @CsvSource({
            // Both pawns leave rank 5, opening a rook's path to White's king.
            "'7k/8/8/r4pPK/8/8/8/8 w - f6 0 20', g5, f6",

            // Both pawns leave rank 4, opening a rook's path to Black's king.
            "'8/8/8/8/R4Ppk/8/8/7K b - f3 0 20', g4, f3",

            // Removing d5 opens the f7-b3 bishop diagonal.
            "'7k/5b2/8/3pP3/8/1K6/8/8 w - d6 0 20', e5, d6",

            // Removing d4 opens the f2-b6 bishop diagonal.
            "'8/8/1k6/8/3Pp3/8/5B2/7K b - d3 0 20', e4, d3"
    })
    void rejectsEnPassantThatExposesOwnKing(
            String fen,
            String from,
            String to
    ) {
        var position = parser.parse(fen);
        var source = Square.fromAlgebraic(from);
        var selectedMove = move(from, to);

        var candidates = generator.generate(position, source);
        var legalMoves = legalGenerator.generate(position, source);
        var visualization = BoardTestRenderer.render(
                position, source, candidates
        );

        System.out.println(visualization);

        assertAll(
                () -> assertTrue(
                        candidates.contains(selectedMove),
                        visualization
                ),
                () -> assertDoesNotThrow(
                        () -> applier.apply(position, selectedMove)
                ),
                () -> assertFalse(
                        legalMoves.contains(selectedMove),
                        visualization
                ),
                () -> assertEquals(fen, serializer.serialize(position))
        );
    }

    @ParameterizedTest
    @CsvSource({
            "'7k/8/8/3pP3/4K3/8/8/8 w - d6 0 20', e5, d6",
            "'8/8/8/4k3/3Pp3/8/8/7K b - d3 0 20', e4, d3"
    })
    void permitsEnPassantThatRemovesCheckingPawn(
            String fen,
            String from,
            String to
    ) {
        var position = parser.parse(fen);
        var selectedMove = move(from, to);

        var legalMoves = legalGenerator.generate(
                position, Square.fromAlgebraic(from)
        );
        System.out.println(BoardTestRenderer.render(position, Square.fromAlgebraic(from), legalMoves));

        assertTrue(legalMoves.contains(selectedMove));
    }

    @ParameterizedTest
    @CsvSource({
            "'r3k2r/8/8/3pP3/8/8/8/R3K2R w KQkq d6 0 20', e5, d6, "
                    + "'r3k2r/8/3P4/8/8/8/8/R3K2R b KQkq - 0 20'",
            "'r3k2r/8/8/8/3Pp3/8/8/R3K2R b KQkq d3 0 20', e4, d3, "
                    + "'r3k2r/8/8/8/8/3p4/8/R3K2R w KQkq - 0 21'"
    })
    void enPassantPreservesExistingCastlingRights(
            String originalFen,
            String from,
            String to,
            String expectedFen
    ) {
        var original = parser.parse(originalFen);

        var candidate = applier.apply(original, move(from, to));

        assertAll(
                () -> assertEquals(
                        original.castlingRights(),
                        candidate.castlingRights()
                ),
                () -> assertEquals(
                        expectedFen,
                        serializer.serialize(candidate)
                ),
                () -> assertEquals(
                        originalFen,
                        serializer.serialize(original)
                )
        );
    }

    @ParameterizedTest
    @CsvSource({
            "'7k/8/8/3pP3/8/8/8/K7 w - d6 0 20', e5, e6, "
                    + "'7k/8/4P3/3p4/8/8/8/K7 b - - 0 20'",
            "'7k/8/8/8/3Pp3/8/8/K7 b - d3 0 20', e4, e3, "
                    + "'7k/8/8/8/3P4/4p3/8/K7 w - - 0 21'"
    })
    void ordinaryAdvanceExpiresAvailableEnPassant(
            String originalFen,
            String from,
            String to,
            String expectedFen
    ) {
        var original = parser.parse(originalFen);

        var candidate = applier.apply(original, move(from, to));

        assertAll(
                () -> assertEquals(
                        expectedFen,
                        serializer.serialize(candidate)
                ),
                () -> assertEquals(
                        originalFen,
                        serializer.serialize(original)
                )
        );
    }

    @ParameterizedTest
    @CsvSource({
            "'7k/8/8/3pP3/8/8/8/K7 w - d6 0 20', e5, d6",
            "'7k/8/8/8/3Pp3/8/8/K7 b - d3 0 20', e4, d3"
    })
    void rejectsPromotionChoiceOnEnPassant(
            String fen,
            String from,
            String to
    ) {
        var position = parser.parse(fen);

        for (var type : new PieceType[] {
                PieceType.QUEEN,
                PieceType.ROOK,
                PieceType.BISHOP,
                PieceType.KNIGHT
        }) {
            var invalidMove = new Move(
                    Square.fromAlgebraic(from),
                    Square.fromAlgebraic(to),
                    Optional.of(type)
            );

            assertThrows(
                    IllegalArgumentException.class,
                    () -> applier.apply(position, invalidMove),
                    type.name()
            );
        }

        assertEquals(fen, serializer.serialize(position));
    }

    private static Move move(String from, String to) {
        return new Move(
                Square.fromAlgebraic(from),
                Square.fromAlgebraic(to)
        );
    }
}