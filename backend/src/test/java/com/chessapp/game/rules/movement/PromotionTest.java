package com.chessapp.game.rules.movement;

import com.chessapp.BoardTestRenderer;
import com.chessapp.game.rules.fen.FenParser;
import com.chessapp.game.rules.fen.FenSerializer;
import com.chessapp.game.rules.model.Move;
import com.chessapp.game.rules.model.PieceType;
import com.chessapp.game.rules.model.Square;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PromotionTest {

    private final FenParser parser = new FenParser();
    private final FenSerializer serializer = new FenSerializer();
    private final PawnMoveGenerator pawnGenerator = new PawnMoveGenerator();

    private final PseudoLegalMoveGenerator generator =
            MovementTestFixtures.pseudoLegalGenerator();
    private final LegalMoveGenerator legalGenerator =
            MovementTestFixtures.legalGenerator(generator);

    private final MoveApplier applier = new MoveApplier(generator);

    @ParameterizedTest
    @CsvSource({
            // Quiet promotions.
            "'k7/6P1/8/8/8/8/8/K7 w - - 0 20', g7, g8",
            "'7k/8/8/8/8/8/1p6/7K b - - 0 20', b2, b1",

            // Quiet promotion and captures on both diagonals.
            "'k1n1r3/3P4/8/8/8/8/8/K7 w - - 0 20', d7, 'c8 d8 e8'",
            "'k7/8/8/8/8/8/3p4/2N1R2K b - - 0 20', d2, 'c1 d1 e1'",

            // Forward square blocked; only a promotion capture remains.
            "'k5nr/6P1/8/8/8/8/8/K7 w - - 0 20', g7, h8",
            "'7k/8/8/8/8/8/1p6/Rn5K b - - 0 20', b2, a1"
    })
    void generatesFourChoicesForEveryAvailableDestination(
            String fen,
            String from,
            String destinations
    ) {
        var position = parser.parse(fen);
        var source = Square.fromAlgebraic(from);
        Set<Move> expected = new HashSet<>();

        for (String destination : destinations.split(" ")) {
            expected.addAll(promotionMoves(
                    source,
                    Square.fromAlgebraic(destination)
            ));
        }

        var actual = pawnGenerator.generate(position, source);
        var visualization = BoardTestRenderer.render(
                position, source, actual
        );

        System.out.println(visualization);

        assertAll(
                () -> assertEquals(expected, actual, visualization),
                () -> assertEquals(fen, serializer.serialize(position)),
                () -> assertThrows(
                        UnsupportedOperationException.class,
                        actual::clear
                )
        );
    }

    @ParameterizedTest
    @EnumSource(
            value = PieceType.class,
            names = {"QUEEN", "ROOK", "BISHOP", "KNIGHT"}
    )
    void appliesEachChoiceForBothColorsAndCaptureKinds(PieceType type) {
        String whiteSymbol = switch (type) {
            case QUEEN -> "Q";
            case ROOK -> "R";
            case BISHOP -> "B";
            case KNIGHT -> "N";
            default -> throw new IllegalArgumentException(
                    "Unexpected promotion type"
            );
        };

        String blackSymbol = whiteSymbol.toLowerCase(
                java.util.Locale.ROOT
        );

        // original FEN, source, destination, expected FEN
        String[][] fixtures = {
                {
                        "k7/6P1/8/8/8/8/8/K7 w - - 9 20",
                        "g7",
                        "g8",
                        "k5" + whiteSymbol + "1/8/8/8/8/8/8/K7 b - - 0 20"
                },
                {
                        "7k/8/8/8/8/8/1p6/7K b - - 9 20",
                        "b2",
                        "b1",
                        "7k/8/8/8/8/8/8/1" + blackSymbol + "5K w - - 0 21"
                },
                {
                        // Capturing h8 removes Black's kingside right.
                        "4k2r/6P1/8/8/8/8/8/K7 w k - 9 20",
                        "g7",
                        "h8",
                        "4k2" + whiteSymbol + "/8/8/8/8/8/8/K7 b - - 0 20"
                },
                {
                        // Capturing a1 removes White's queenside right.
                        "7k/8/8/8/8/8/1p6/R3K3 b Q - 9 20",
                        "b2",
                        "a1",
                        "7k/8/8/8/8/8/8/" + blackSymbol + "3K3 w - - 0 21"
                }
        };

        for (String[] fixture : fixtures) {
            var original = parser.parse(fixture[0]);
            var move = new Move(
                    Square.fromAlgebraic(fixture[1]),
                    Square.fromAlgebraic(fixture[2]),
                    Optional.of(type)
            );

            var result = applier.apply(original, move);
            String context = type + ": " + fixture[0];

            assertAll(
                    () -> assertEquals(
                            fixture[3],
                            serializer.serialize(result),
                            context
                    ),
                    () -> assertEquals(
                            fixture[0],
                            serializer.serialize(original),
                            context
                    ),
                    () -> assertNotSame(
                            original.board(),
                            result.board(),
                            context
                    )
            );
        }
    }

    @ParameterizedTest
    @CsvSource({
            "'k7/6P1/8/8/8/8/8/K7 w - - 0 20', g7, g8",
            "'7k/8/8/8/8/8/1p6/7K b - - 0 20', b2, b1"
    })
    void rejectsPromotionWithoutAnExplicitChoice(
            String fen,
            String from,
            String to
    ) {
        assertThrows(
                IllegalArgumentException.class,
                () -> applier.apply(
                        parser.parse(fen),
                        new Move(
                                Square.fromAlgebraic(from),
                                Square.fromAlgebraic(to)
                        )
                )
        );
    }

    @ParameterizedTest
    @CsvSource({
            // Ordinary pawn advance.
            "'7k/8/8/8/8/8/3P4/K7 w - - 0 20', d2, d3",

            // Ordinary knight move.
            "'7k/8/8/8/8/8/8/KN6 w - - 0 20', b1, c3"
    })
    void rejectsPromotionChoiceOnAnOrdinaryMove(
            String fen,
            String from,
            String to
    ) {
        assertThrows(
                IllegalArgumentException.class,
                () -> applier.apply(
                        parser.parse(fen),
                        new Move(
                                Square.fromAlgebraic(from),
                                Square.fromAlgebraic(to),
                                Optional.of(PieceType.QUEEN)
                        )
                )
        );
    }

    @ParameterizedTest
    @CsvSource({
            // Safe quiet promotions retain all four choices.
            "'k7/6P1/8/8/8/8/8/K7 w - - 0 20', g7, g8, g8",
            "'7k/8/8/8/8/8/1p6/7K b - - 0 20', b2, b1, b1",

            // Leaving the rank exposes the moving side's king to a rook.
            // The '-' in this test’s final column means “no expected legal moves from this source”; it is separate from the FEN fields.
            "'8/K5Pr/8/8/8/8/8/k7 w - - 0 20', g7, g8, -",
            "'K7/8/8/8/8/8/k5pR/8 b - - 0 20', g2, g1, -",

            // Only capturing the checking rook resolves check.
            "'k6r/6P1/8/8/8/8/8/7K w - - 0 20', g7, 'g8 h8', h8",
            "'k7/8/8/8/8/8/1p6/R6K b - - 0 20', b2, 'a1 b1', a1",

            // Promotion can also block the checking rook's line.
            "'K6r/3P4/8/8/8/8/8/k7 w - - 0 20', d7, d8, d8",
            "'K7/8/8/8/8/8/3p4/k6R b - - 0 20', d2, d1, d1"
    })
    void filtersPromotionChoicesUsingResultingKingSafety(
            String fen,
            String from,
            String candidateDestinations,
            String legalDestination
    ) {
        var position = parser.parse(fen);
        var source = Square.fromAlgebraic(from);

        Set<Move> expectedCandidates = new HashSet<>();

        for (String destination : candidateDestinations.split(" ")) {
            expectedCandidates.addAll(promotionMoves(
                    source,
                    Square.fromAlgebraic(destination)
            ));
        }
        Set<Move> expectedLegal = legalDestination.equals("-")
                ? Set.of()
                : promotionMoves(
                source,
                Square.fromAlgebraic(legalDestination)
        );

        var candidates = generator.generate(position, source);
        var legalMoves = legalGenerator.generate(position, source);

        String visualization =
                "Pseudo-legal candidates:\n"
                        + BoardTestRenderer.render(
                        position, source, candidates
                )
                        + "\nAfter king-safety filtering:\n"
                        + BoardTestRenderer.render(
                        position, source, legalMoves
                );

        System.out.println(visualization);

        assertAll(
                () -> assertEquals(
                        expectedCandidates,
                        candidates,
                        visualization
                ),
                () -> assertEquals(
                        expectedLegal,
                        legalMoves,
                        visualization
                ),
                () -> assertEquals(
                        fen,
                        serializer.serialize(position)
                ),
                () -> assertThrows(
                        UnsupportedOperationException.class,
                        legalMoves::clear
                )
        );
    }

    @ParameterizedTest
    @CsvSource({
            // Promotion expires the previous en passant opportunity.
            "'k7/6P1/8/3p4/8/8/8/K7 w - d6 0 20', "
                    + "g7, g8, KNIGHT, "
                    + "'k5N1/8/8/3p4/8/8/8/K7 b - - 0 20'",

            "'7k/8/8/8/3P4/8/1p6/7K b - d3 0 20', "
                    + "b2, b1, BISHOP, "
                    + "'7k/8/8/8/3P4/8/8/1b5K w - - 0 21'",

            // A promoted corner rook does not create castling rights.
            "'k7/7P/8/8/8/8/8/K7 w - - 9 20', "
                    + "h7, h8, ROOK, "
                    + "'k6R/8/8/8/8/8/8/K7 b - - 0 20'",

            "'7k/8/8/8/8/8/p7/7K b - - 9 20', "
                    + "a2, a1, ROOK, "
                    + "'7k/8/8/8/8/8/8/r6K w - - 0 21'"
    })
    void promotionPreservesSharedMetadataRules(
            String originalFen,
            String from,
            String to,
            PieceType choice,
            String expectedFen
    ) {
        var original = parser.parse(originalFen);
        var move = new Move(
                Square.fromAlgebraic(from),
                Square.fromAlgebraic(to),
                Optional.of(choice)
        );

        var result = applier.apply(original, move);
        System.out.println(BoardTestRenderer.render(result, Square.fromAlgebraic(from), Set.of(move)));

        assertAll(
                () -> assertEquals(
                        expectedFen,
                        serializer.serialize(result)
                ),
                () -> assertEquals(
                        originalFen,
                        serializer.serialize(original)
                )
        );
    }

    private Set<Move> promotionMoves(Square from, Square to) {
        return Set.of(
                new Move(from, to, Optional.of(PieceType.QUEEN)),
                new Move(from, to, Optional.of(PieceType.ROOK)),
                new Move(from, to, Optional.of(PieceType.BISHOP)),
                new Move(from, to, Optional.of(PieceType.KNIGHT))
        );
    }
}