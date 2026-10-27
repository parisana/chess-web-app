package com.chessapp.game.rules.movement;

import com.chessapp.BoardTestRenderer;
import com.chessapp.game.rules.fen.FenParser;
import com.chessapp.game.rules.fen.FenSerializer;
import com.chessapp.game.rules.model.Move;
import com.chessapp.game.rules.model.PieceType;
import com.chessapp.game.rules.model.Square;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class CastlingTest {

    private final FenParser parser = new FenParser();
    private final FenSerializer serializer = new FenSerializer();

    private final PseudoLegalMoveGenerator generator =
            MovementTestFixtures.pseudoLegalGenerator();

    private final LegalMoveGenerator legalGenerator =
            MovementTestFixtures.legalGenerator(generator);

    private final MoveApplier applier = new MoveApplier(generator);

    /**
     * Verifies king and rook relocation and the resulting FEN for both colors
     * and castling sides. Also checks that applying the move returns a new board
     * and leaves the original placement and metadata unchanged.
     */
    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 4 20 | e1 | g1 | "
                    + "r3k2r/8/8/8/8/8/8/R4RK1 b kq - 5 20",
            "r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 4 20 | e1 | c1 | "
                    + "r3k2r/8/8/8/8/8/8/2KR3R b kq - 5 20",
            "r3k2r/8/8/8/8/8/8/R3K2R b KQkq - 4 20 | e8 | g8 | "
                    + "r4rk1/8/8/8/8/8/8/R3K2R w KQ - 5 21",
            "r3k2r/8/8/8/8/8/8/R3K2R b KQkq - 4 20 | e8 | c8 | "
                    + "2kr3r/8/8/8/8/8/8/R3K2R w KQ - 5 21"
    })
    void appliesCastlingAndPreservesOriginalPosition(
            String originalFen,
            String from,
            String to,
            String expectedFen
    ) {
        var original = parser.parse(originalFen);
        var selectedMove = move(from, to);

        var candidates = generator.generate(
                original, selectedMove.from()
        );
        var visualization = BoardTestRenderer.render(
                original, selectedMove.from(), candidates
        );

        assertTrue(candidates.contains(selectedMove), visualization);

        var result = applier.apply(original, selectedMove);

        System.out.println(visualization);
        System.out.println("After castling:");
        System.out.println(BoardTestRenderer.render(
                result, selectedMove.to(), Set.of()
        ));

        assertAll(
                () -> assertEquals(
                        expectedFen, serializer.serialize(result)
                ),
                () -> assertEquals(
                        originalFen, serializer.serialize(original)
                ),
                () -> assertNotSame(original.board(), result.board())
        );
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            // Placement permits both sides, but only one right exists.
            "r3k2r/8/8/8/8/8/8/R3K2R w Kkq - 0 20 | e1 | g1 | c1",
            "r3k2r/8/8/8/8/8/8/R3K2R w Qkq - 0 20 | e1 | c1 | g1",
            "r3k2r/8/8/8/8/8/8/R3K2R b KQk - 0 20 | e8 | g8 | c8",
            "r3k2r/8/8/8/8/8/8/R3K2R b KQq - 0 20 | e8 | c8 | g8"
    })
    void generatesOnlySideAllowedByRights(
            String fen,
            String from,
            String allowedTo,
            String forbiddenTo
    ) {
        var position = parser.parse(fen);
        var candidates = generator.generate(
                position, Square.fromAlgebraic(from)
        );
        System.out.println(BoardTestRenderer.render(position, Square.fromAlgebraic(from), candidates));

        assertAll(
                () -> assertTrue(
                        candidates.contains(move(from, allowedTo))
                ),
                () -> assertFalse(
                        candidates.contains(move(from, forbiddenTo))
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> applier.apply(
                                position, move(from, forbiddenTo)
                        )
                )
        );
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 4 20 | e1 | g1",
            "r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 4 20 | e1 | c1",
            "r3k2r/8/8/8/8/8/8/R3K2R b KQkq - 4 20 | e8 | g8",
            "r3k2r/8/8/8/8/8/8/R3K2R b KQkq - 4 20 | e8 | c8"
    })
    void permitsCastlingWhenKingPathIsSafe(
            String fen,
            String from,
            String to
    ) {
        var position = parser.parse(fen);
        var selectedMove = move(from, to);

        var legalMoves = legalGenerator.generate(
                position, selectedMove.from()
        );

        assertAll(
                () -> assertTrue(
                        legalMoves.contains(selectedMove),
                        BoardTestRenderer.render(
                                position, selectedMove.from(), legalMoves
                        )
                ),
                () -> assertEquals(fen, serializer.serialize(position))
        );
    }

    /**
     * Isolates attacks on the king's starting, transit and destination squares.
     * Such moves remain pseudo-legal and applicable, but legal filtering
     * must exclude them without changing the original position.
     */
    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            // White: starting square e1 is attacked.
            "4k3/8/8/8/8/4r3/8/R3K2R w KQ - 0 20 | e1 | g1",
            "4k3/8/8/8/8/4r3/8/R3K2R w KQ - 0 20 | e1 | c1",

            // White: transit square f1 or d1 is attacked.
            "4k3/8/8/8/8/5r2/8/R3K2R w KQ - 0 20 | e1 | g1",
            "4k3/8/8/8/8/3r4/8/R3K2R w KQ - 0 20 | e1 | c1",

            // White: destination g1 or c1 is attacked.
            "4k3/8/8/8/8/6r1/8/R3K2R w KQ - 0 20 | e1 | g1",
            "4k3/8/8/8/8/2r5/8/R3K2R w KQ - 0 20 | e1 | c1",

            // Black: starting square e8 is attacked.
            "r3k2r/8/4R3/8/8/8/8/4K3 b kq - 0 20 | e8 | g8",
            "r3k2r/8/4R3/8/8/8/8/4K3 b kq - 0 20 | e8 | c8",

            // Black: transit square f8 or d8 is attacked.
            "r3k2r/8/5R2/8/8/8/8/4K3 b kq - 0 20 | e8 | g8",
            "r3k2r/8/3R4/8/8/8/8/4K3 b kq - 0 20 | e8 | c8",

            // Black: destination g8 or c8 is attacked.
            "r3k2r/8/6R1/8/8/8/8/4K3 b kq - 0 20 | e8 | g8",
            "r3k2r/8/2R5/8/8/8/8/4K3 b kq - 0 20 | e8 | c8"
    })
    void rejectsCastlingWhenKingPathIsAttacked(
            String fen,
            String from,
            String to
    ) {
        var position = parser.parse(fen);
        var selectedMove = move(from, to);

        var candidates = generator.generate(
                position, selectedMove.from()
        );
        var legalMoves = legalGenerator.generate(
                position, selectedMove.from()
        );

        var visualization = BoardTestRenderer.render(
                position, selectedMove.from(), candidates
        );

        System.out.println("Before king-safety filtering:");
        System.out.println(visualization);
        System.out.println("After king-safety filtering:");
        System.out.println(BoardTestRenderer.render(
                position, selectedMove.from(), legalMoves
        ));

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
    @CsvSource(delimiter = '|', value = {
            // White kingside: f1 and g1.
            "4k3/8/8/8/8/8/8/R3KN1R w KQ - 0 20 | e1 | g1",
            "4k3/8/8/8/8/8/8/R3K1NR w KQ - 0 20 | e1 | g1",

            // White queenside: b1, c1 and d1.
            "4k3/8/8/8/8/8/8/RN2K2R w KQ - 0 20 | e1 | c1",
            "4k3/8/8/8/8/8/8/R1N1K2R w KQ - 0 20 | e1 | c1",
            "4k3/8/8/8/8/8/8/R2NK2R w KQ - 0 20 | e1 | c1",

            // Black kingside: f8 and g8.
            "r3kn1r/8/8/8/8/8/8/4K3 b kq - 0 20 | e8 | g8",
            "r3k1nr/8/8/8/8/8/8/4K3 b kq - 0 20 | e8 | g8",

            // Black queenside: b8, c8 and d8.
            "rn2k2r/8/8/8/8/8/8/4K3 b kq - 0 20 | e8 | c8",
            "r1n1k2r/8/8/8/8/8/8/4K3 b kq - 0 20 | e8 | c8",
            "r2nk2r/8/8/8/8/8/8/4K3 b kq - 0 20 | e8 | c8",

            // Opposing occupants also block the path; castling cannot capture.
            "4k3/8/8/8/8/8/8/R3Kn1R w KQ - 0 20 | e1 | g1",
            "r3kN1r/8/8/8/8/8/8/4K3 b kq - 0 20 | e8 | g8"
    })
    void blockedPathOmitsCastlingCandidate(
            String fen,
            String from,
            String to
    ) {
        var position = parser.parse(fen);
        var selectedMove = move(from, to);
        System.out.println(BoardTestRenderer.render(position, Square.fromAlgebraic(from), Set.of(selectedMove)));

        assertAll(
                () -> assertFalse(
                        generator.generate(
                                position, selectedMove.from()
                        ).contains(selectedMove)
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> applier.apply(position, selectedMove)
                ),
                () -> assertEquals(fen, serializer.serialize(position))
        );
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            // Correct placement, but no castling rights.
            "4k3/8/8/8/8/8/8/R3K2R w - - 0 20 | e1 | g1 | e2",
            "4k3/8/8/8/8/8/8/R3K2R w - - 0 20 | e1 | c1 | e2",
            "r3k2r/8/8/8/8/8/8/4K3 b - - 0 20 | e8 | g8 | e7",
            "r3k2r/8/8/8/8/8/8/4K3 b - - 0 20 | e8 | c8 | e7",

            // Deliberately inconsistent rights: required corner rook is absent.
            "4k3/8/8/8/8/8/8/R3K3 w KQ - 0 20 | e1 | g1 | e2",
            "4k3/8/8/8/8/8/8/4K2R w KQ - 0 20 | e1 | c1 | e2",
            "r3k3/8/8/8/8/8/8/4K3 b kq - 0 20 | e8 | g8 | e7",
            "4k2r/8/8/8/8/8/8/4K3 b kq - 0 20 | e8 | c8 | e7",

            // Deliberately inconsistent rights: corner piece is not a rook.
            "4k3/8/8/8/8/8/8/R3K2N w KQ - 0 20 | e1 | g1 | e2",
            "4k3/8/8/8/8/8/8/N3K2R w KQ - 0 20 | e1 | c1 | e2",
            "r3k2n/8/8/8/8/8/8/4K3 b kq - 0 20 | e8 | g8 | e7",
            "n3k2r/8/8/8/8/8/8/4K3 b kq - 0 20 | e8 | c8 | e7",

            // Deliberately inconsistent rights: corner rook has the wrong color.
            // These positions also put the moving king in check.
            "4k3/8/8/8/8/8/8/R3K2r w KQ - 0 20 | e1 | g1 | e2",
            "4k3/8/8/8/8/8/8/r3K2R w KQ - 0 20 | e1 | c1 | e2",
            "r3k2R/8/8/8/8/8/8/4K3 b kq - 0 20 | e8 | g8 | e7",
            "R3k2r/8/8/8/8/8/8/4K3 b kq - 0 20 | e8 | c8 | e7",

            // Deliberately inconsistent rights: king is away from its home square.
            "4k3/8/8/8/8/8/8/R2K3R w KQ - 0 20 | d1 | f1 | d2",
            "r2k3r/8/8/8/8/8/8/4K3 b kq - 0 20 | d8 | f8 | d7"
    })
    void unavailableCastlingPreservesOrdinaryKingMoves(
            String fen,
            String from,
            String to,
            String ordinaryTo
    ) {
        var position = parser.parse(fen);
        var selectedMove = move(from, to);
        var candidates = generator.generate(
                position, selectedMove.from()
        );

        assertAll(
                () -> assertFalse(candidates.contains(selectedMove)),
                () -> assertTrue(
                        candidates.contains(move(from, ordinaryTo))
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> applier.apply(position, selectedMove)
                ),
                () -> assertEquals(fen, serializer.serialize(position))
        );
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            // White's castling rook is attacked on h1 or a1.
            "4k3/8/8/8/8/7r/8/R3K2R w KQ - 0 20 | e1 | g1",
            "4k3/8/8/8/8/r7/8/R3K2R w KQ - 0 20 | e1 | c1",

            // Black's castling rook is attacked on h8 or a8.
            "r3k2r/8/7R/8/8/8/8/4K3 b kq - 0 20 | e8 | g8",
            "r3k2r/8/R7/8/8/8/8/4K3 b kq - 0 20 | e8 | c8",

            // Only b1/b8 is attacked; the king's path remains safe.
            "4k3/8/8/8/8/1r6/8/R3K2R w KQ - 0 20 | e1 | c1",
            "r3k2r/8/1R6/8/8/8/8/4K3 b kq - 0 20 | e8 | c8"
    })
    void permitsCastlingWhenAttacksDoNotReachKingPath(
            String fen,
            String from,
            String to
    ) {
        var position = parser.parse(fen);
        var selectedMove = move(from, to);
        var legalMoves = legalGenerator.generate(
                position, selectedMove.from()
        );

        assertTrue(
                legalMoves.contains(selectedMove),
                BoardTestRenderer.render(
                        position, selectedMove.from(), legalMoves
                )
        );
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            // Rh1 pins Nh2 against Kh8, but Nh2 still attacks f1.
            "7k/8/8/8/8/8/7n/4K2R w K - 0 20 | e1 | g1",

            // Rh8 pins Nh7 against Kh1, but Nh7 still attacks f8.
            "4k2r/7N/8/8/8/8/8/7K b k - 0 20 | e8 | g8"
    })
    void pinnedKnightStillPreventsCastlingThroughItsAttack(
            String fen,
            String from,
            String to
    ) {
        var position = parser.parse(fen);
        var selectedMove = move(from, to);

        var candidates = generator.generate(
                position, selectedMove.from()
        );
        var legalMoves = legalGenerator.generate(
                position, selectedMove.from()
        );

        System.out.println(BoardTestRenderer.render(
                position, selectedMove.from(), candidates
        ));

        assertAll(
                () -> assertTrue(candidates.contains(selectedMove)),
                () -> assertFalse(legalMoves.contains(selectedMove)),
                () -> assertEquals(fen, serializer.serialize(position))
        );
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            // Black just advanced d7-d5.
            "r3k2r/8/8/3p4/8/8/8/R3K2R w KQkq d6 0 20 | e1 | g1 | "
                    + "r3k2r/8/8/3p4/8/8/8/R4RK1 b kq - 1 20",

            // White just advanced d2-d4.
            "r3k2r/8/8/8/3P4/8/8/R3K2R b KQkq d3 0 20 | e8 | c8 | "
                    + "2kr3r/8/8/8/3P4/8/8/R3K2R w KQ - 1 21"
    })
    void castlingExpiresEnPassantTarget(
            String originalFen,
            String from,
            String to,
            String expectedFen
    ) {
        var original = parser.parse(originalFen);
        var selectedMove = move(from, to);

        assertTrue(
                legalGenerator.generate(
                        original, selectedMove.from()
                ).contains(selectedMove)
        );

        var result = applier.apply(original, selectedMove);

        assertAll(
                () -> assertTrue(result.enPassantTarget().isEmpty()),
                () -> assertEquals(
                        expectedFen, serializer.serialize(result)
                ),
                () -> assertEquals(
                        originalFen, serializer.serialize(original)
                )
        );
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 4 20 | e1 | g1",
            "r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 4 20 | e1 | c1",
            "r3k2r/8/8/8/8/8/8/R3K2R b KQkq - 4 20 | e8 | g8",
            "r3k2r/8/8/8/8/8/8/R3K2R b KQkq - 4 20 | e8 | c8"
    })
    void rejectsPromotionChoiceOnCastling(
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