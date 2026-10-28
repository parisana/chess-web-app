package com.chessapp.game.rules;

import com.chessapp.game.rules.fen.FenParser;
import com.chessapp.game.rules.model.*;
import org.junit.jupiter.api.Test;
import com.chessapp.BoardTestRenderer;
import com.chessapp.game.rules.attack.SquareAttackDetector;
import com.chessapp.game.rules.evaluation.PositionEvaluator;
import com.chessapp.game.rules.evaluation.PositionStatus;
import com.chessapp.game.rules.fen.FenSerializer;
import com.chessapp.game.rules.movement.LegalMoveGenerator;
import com.chessapp.game.rules.movement.MoveApplier;
import com.chessapp.game.rules.movement.MovementTestFixtures;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author pari on 10/24/26
 */
class StandardChessRulesTest {

    private final FenParser parser = new FenParser();
    private final FenSerializer serializer = new FenSerializer();

    private final ChessRules rules = createRules();

    @Test
    void initialPositionContainsEveryStartingPiece() {
        var position = rules.initialPosition();

        PieceType[] backRank = {
                PieceType.ROOK,
                PieceType.KNIGHT,
                PieceType.BISHOP,
                PieceType.QUEEN,
                PieceType.KING,
                PieceType.BISHOP,
                PieceType.KNIGHT,
                PieceType.ROOK
        };

        for (int file = 0; file < 8; file++) {
            assertEquals(
                    new Piece(PieceColor.WHITE, backRank[file]),
                    position.board().pieceAt(new Square(file, 0))
                            .orElseThrow()
            );
            assertEquals(
                    new Piece(PieceColor.BLACK, backRank[file]),
                    position.board().pieceAt(new Square(file, 7))
                            .orElseThrow()
            );

            for (int rank : new int[] {1, 6}) {
                var color = rank == 1
                        ? PieceColor.WHITE
                        : PieceColor.BLACK;

                assertEquals(
                        new Piece(color, PieceType.PAWN),
                        position.board().pieceAt(new Square(file, rank))
                                .orElseThrow()
                );
            }

            for (int rank = 2; rank <= 5; rank++) {
                assertTrue(
                        position.board().pieceAt(new Square(file, rank))
                                .isEmpty()
                );
            }
        }
    }

    @Test
    void initialPositionHasStandardStartingMetadata() {
        var position = rules.initialPosition();

        assertAll(
                () -> assertEquals(
                        PieceColor.WHITE,
                        position.sideToMove()
                ),
                () -> assertEquals(
                        new CastlingRights(true, true, true, true),
                        position.castlingRights()
                ),
                () -> assertTrue(
                        position.enPassantTarget().isEmpty()
                ),
                () -> assertEquals(
                        0,
                        position.halfmoveClock()
                ),
                () -> assertEquals(
                        1,
                        position.fullmoveNumber()
                )
        );
    }

    @Test
    void exposesCompleteImmutableOpeningMovesAndNormalAssessment() {
        var position = rules.initialPosition();
        var originalFen = serializer.serialize(position);

        var moves = rules.legalMoves(position);

        assertAll(
                () -> assertEquals(20, moves.size()),
                () -> assertTrue(moves.contains(move("e2", "e4"))),
                () -> assertTrue(moves.contains(move("g1", "f3"))),
                () -> assertEquals(
                        PositionStatus.NORMAL,
                        rules.evaluate(position)
                ),
                () -> assertThrows(
                        UnsupportedOperationException.class,
                        moves::clear
                ),
                () -> assertEquals(
                        originalFen,
                        serializer.serialize(position)
                )
        );
    }

    @Test
    void appliesLegalPawnAdvanceAndPreservesOriginalPosition() {
        var position = rules.initialPosition();
        var originalFen = serializer.serialize(position);

        var result = rules.applyMove(position, move("e2", "e4"));

        assertAll(
                () -> assertEquals(
                        "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR"
                                + " b KQkq e3 0 1",
                        serializer.serialize(result)
                ),
                () -> assertEquals(
                        originalFen,
                        serializer.serialize(position)
                )
        );
    }

    @Test
    void rejectsPseudoLegalMoveThatExposesOwnKing() {
        String fen = "4r2k/8/8/8/8/8/4N3/4K3 w - - 0 20";
        var position = parser.parse(fen);
        var proposal = move("e2", "c3");

        // The knight blocks the e-file rook. Moving it exposes White's king.
        var dispatcher = MovementTestFixtures.pseudoLegalGenerator();
        var legalMoves = rules.legalMoves(position);

        var visualization = BoardTestRenderer.render(
                position,
                proposal.from(),
                legalMoves.stream()
                        .filter(candidate ->
                                candidate.from().equals(proposal.from()))
                        .collect(java.util.stream.Collectors.toSet())
        );
        System.out.println(visualization);

        assertAll(
                () -> assertTrue(
                        dispatcher.generate(position, proposal.from())
                                .contains(proposal)
                ),
                () -> assertFalse(legalMoves.contains(proposal)),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> rules.applyMove(position, proposal),
                        visualization
                ),
                () -> assertEquals(fen, serializer.serialize(position))
        );
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            // White castling, both sides.
            "r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 4 20"
                    + " | e1 | g1 | -"
                    + " | r3k2r/8/8/8/8/8/8/R4RK1 b kq - 5 20",
            "r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 4 20"
                    + " | e1 | c1 | -"
                    + " | r3k2r/8/8/8/8/8/8/2KR3R b kq - 5 20",

            // Black castling also advances the fullmove number.
            "r3k2r/8/8/8/8/8/8/R3K2R b KQkq - 4 20"
                    + " | e8 | g8 | -"
                    + " | r4rk1/8/8/8/8/8/8/R3K2R w KQ - 5 21",
            "r3k2r/8/8/8/8/8/8/R3K2R b KQkq - 4 20"
                    + " | e8 | c8 | -"
                    + " | 2kr3r/8/8/8/8/8/8/R3K2R w KQ - 5 21",

            // En passant removes the pawn beside the source.
            "7k/8/8/3pP3/8/8/8/K7 w - d6 0 20"
                    + " | e5 | d6 | -"
                    + " | 7k/8/3P4/8/8/8/8/K7 b - - 0 20",
            "k7/8/8/8/3Pp3/8/8/7K b - d3 0 20"
                    + " | e4 | d3 | -"
                    + " | k7/8/8/8/8/3p4/8/7K w - - 0 21",

            // Quiet promotion and capture underpromotion, both colors.
            "7k/4P3/8/8/8/8/8/K7 w - - 0 20"
                    + " | e7 | e8 | QUEEN"
                    + " | 4Q2k/8/8/8/8/8/8/K7 b - - 0 20",
            "3r3k/4P3/8/8/8/8/8/K7 w - - 0 20"
                    + " | e7 | d8 | KNIGHT"
                    + " | 3N3k/8/8/8/8/8/8/K7 b - - 0 20",
            "k7/8/8/8/8/8/4p3/7K b - - 0 20"
                    + " | e2 | e1 | ROOK"
                    + " | k7/8/8/8/8/8/8/4r2K w - - 0 21",
            "k7/8/8/8/8/8/4p3/3R3K b - - 0 20"
                    + " | e2 | d1 | BISHOP"
                    + " | k7/8/8/8/8/8/8/3b3K w - - 0 21"
    })
    void appliesLegalSpecialMovesAndPreservesOriginalPosition(
            String fen,
            String from,
            String to,
            String promotion,
            String expectedFen
    ) {
        var position = parser.parse(fen);
        var proposal = move(from, to, promotion);

        final Set<Move> legalMoves = rules.legalMoves(position);
        assertTrue(legalMoves.contains(proposal));
        System.out.println("proposal move:");
        System.out.println(BoardTestRenderer.render(position, Square.fromAlgebraic(from), Set.of(proposal)));

        var result = rules.applyMove(position, proposal);
        System.out.println("result position:");
        System.out.println(BoardTestRenderer.render(result, Square.fromAlgebraic(from), Set.of(proposal)));

        System.out.println("================================");
        assertAll(
                () -> assertEquals(
                        expectedFen,
                        serializer.serialize(result)
                ),
                () -> assertEquals(fen, serializer.serialize(position))
        );
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            // Castling while the starting square is attacked.
            "k3r3/8/8/8/8/8/8/4K2R w K - 4 20"
                    + " | e1 | g1",

            // Castling through an attacked transit square, f1.
            "k4r2/8/8/8/8/8/8/4K2R w K - 4 20"
                    + " | e1 | g1",

            // Castling onto an attacked destination, g1.
            "k5r1/8/8/8/8/8/8/4K2R w K - 4 20"
                    + " | e1 | g1",

            // En passant vacates both blockers on the king's rank.
            "7k/8/8/r4pPK/8/8/8/8 w - f6 0 20"
                    + " | g5 | f6",
            "8/8/8/8/R4Ppk/8/8/7K b - f3 0 20"
                    + " | g4 | f3"
    })
    void rejectsPseudoLegalSpecialMovesThatViolateKingSafety(
            String fen,
            String from,
            String to
    ) {
        var position = parser.parse(fen);
        var proposal = move(from, to);
        var dispatcher = MovementTestFixtures.pseudoLegalGenerator();

        assertAll(
                () -> assertTrue(
                        dispatcher.generate(position, proposal.from())
                                .contains(proposal)
                ),
                () -> assertFalse(
                        rules.legalMoves(position).contains(proposal)
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> rules.applyMove(position, proposal)
                ),
                () -> assertEquals(fen, serializer.serialize(position))
        );
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            // Missing choice: quiet and capture promotions, both colors.
            "7k/4P3/8/8/8/8/8/K7 w - - 0 20"
                    + " | e7 | e8 | -",
            "3r3k/4P3/8/8/8/8/8/K7 w - - 0 20"
                    + " | e7 | d8 | -",
            "k7/8/8/8/8/8/4p3/7K b - - 0 20"
                    + " | e2 | e1 | -",
            "k7/8/8/8/8/8/4p3/3R3K b - - 0 20"
                    + " | e2 | d1 | -",

            // A promotion choice attached to an ordinary pawn advance.
            "7k/8/8/8/8/8/4P3/K7 w - - 0 20"
                    + " | e2 | e4 | QUEEN",

            // A promotion choice attached to en passant.
            "7k/8/8/3pP3/8/8/8/K7 w - d6 0 20"
                    + " | e5 | d6 | KNIGHT",

            // A promotion choice attached to castling.
            "k7/8/8/8/8/8/8/4K2R w K - 4 20"
                    + " | e1 | g1 | ROOK"
    })
    void rejectsMissingOrInapplicablePromotionChoices(
            String fen,
            String from,
            String to,
            String promotion
    ) {
        var position = parser.parse(fen);
        var proposal = move(from, to, promotion);

        assertAll(
                () -> assertFalse(
                        rules.legalMoves(position).contains(proposal)
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> rules.applyMove(position, proposal)
                ),
                () -> assertEquals(fen, serializer.serialize(position))
        );
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR"
                    + " w KQkq - 0 1 | NORMAL",
            "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR"
                    + " b KQkq e3 0 1 | NORMAL",

            "4r2k/8/8/8/8/8/8/4K3 w - - 0 20 | CHECK",
            "4k3/8/8/8/8/8/8/K3R3 b - - 0 20 | CHECK",

            "7k/6Q1/5K2/8/8/8/8/8 b - - 0 20 | CHECKMATE",
            "8/8/8/8/8/5k2/6q1/7K w - - 0 20 | CHECKMATE",

            "7k/5Q2/6K1/8/8/8/8/8 b - - 0 20 | STALEMATE",
            "8/8/8/8/8/6k1/5q2/7K w - - 0 20 | STALEMATE"
    })
    void exposesPositionAssessmentAndPreservesOriginalPosition(
            String fen,
            PositionStatus expectedStatus
    ) {
        var position = parser.parse(fen);

        assertAll(
                () -> assertEquals(
                        expectedStatus,
                        rules.evaluate(position)
                ),
                () -> assertEquals(fen, serializer.serialize(position))
        );
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            // Deliberately invalid defensive fixtures: Black king missing,
            // White king missing, then two White kings.
            "8/8/8/8/8/8/4N3/4K3 w - - 0 20",
            "7k/8/8/8/8/8/4N3/8 w - - 0 20",
            "7k/8/8/8/8/8/4N3/K3K3 w - - 0 20"
    })
    void rejectsInvalidKingCountsAcrossFacadeOperations(String fen) {
        var position = parser.parse(fen);
        var proposal = move("e2", "c3");

        assertAll(
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> rules.legalMoves(position)
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> rules.applyMove(position, proposal)
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> rules.evaluate(position)
                ),
                () -> assertEquals(fen, serializer.serialize(position))
        );
    }

    private static Move move(String from, String to) {
        return new Move(
                Square.fromAlgebraic(from),
                Square.fromAlgebraic(to)
        );
    }

    private static Move move(
            String from,
            String to,
            String promotion
    ) {
        return new Move(
                Square.fromAlgebraic(from),
                Square.fromAlgebraic(to),
                promotion.equals("-")
                        ? Optional.empty()
                        : Optional.of(PieceType.valueOf(promotion))
        );
    }

    private static ChessRules createRules() {
        var dispatcher = MovementTestFixtures.pseudoLegalGenerator();
        var applier = new MoveApplier(dispatcher);
        var detector = new SquareAttackDetector();

        var legalGenerator = new LegalMoveGenerator(
                dispatcher,
                applier,
                detector
        );

        return new StandardChessRules(
                new FenParser(),
                legalGenerator,
                applier,
                new PositionEvaluator(legalGenerator, detector)
        );
    }
}