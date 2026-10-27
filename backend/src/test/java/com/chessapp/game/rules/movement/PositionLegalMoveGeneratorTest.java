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

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class PositionLegalMoveGeneratorTest {

    private final FenParser parser = new FenParser();
    private final FenSerializer serializer = new FenSerializer();

    private final LegalMoveGenerator legalGenerator =
            MovementTestFixtures.legalGenerator(
                    MovementTestFixtures.pseudoLegalGenerator()
            );

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR"
                    + " w KQkq - 0 1 | e2 | e4 | b1 | c3",

            // Black replies after White's e2-e4.
            "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR"
                    + " b KQkq e3 0 1 | e7 | e5 | b8 | c6"
    })
    void aggregatesTwentyOpeningMovesForEitherColor(
            String fen,
            String pawnFrom,
            String pawnTo,
            String knightFrom,
            String knightTo
    ) {
        var position = parser.parse(fen);
        var moves = legalGenerator.generate(position);
        final List<Square> fromSquares = moves.stream().map(Move::from).collect(Collectors.toUnmodifiableSet()).stream()
                .sorted((sq1, sq2)-> {
                    final int compareFile = Integer.compare(sq1.file(), sq2.file());
                    if (compareFile != 0) return compareFile;
                    if (sq1.rank() < 3) {
                        // Reversed order
                        return Integer.compare(sq2.rank(), sq1.rank());
                    } else {
                        // Natural order
                        return Integer.compare(sq1.rank(), sq2.rank());
                    }
                })
                .toList();
        System.out.println("fen: " + fen);
        for (Square fromSquare : fromSquares) {
            System.out.println("From square: " + fromSquare.toAlgebraic());
            System.out.println(BoardTestRenderer.render(position, fromSquare, moves.stream().filter(move-> move.from().equals(fromSquare)).collect(Collectors.toUnmodifiableSet())));
        }

        assertAll(
                () -> assertEquals(20, moves.size()),
                () -> assertTrue(moves.contains(move(pawnFrom, pawnTo))),
                () -> assertTrue(moves.contains(move(knightFrom, knightTo))),
                () -> assertTrue(moves.stream().allMatch(candidate ->
                        position.board()
                                .pieceAt(candidate.from())
                                .orElseThrow()
                                .color() == position.sideToMove()
                )),
                () -> assertEquals(fen, serializer.serialize(position)),
                () -> assertThrows(
                        UnsupportedOperationException.class,
                        moves::clear
                )
        );
    }

    @Test
    void emptyPinnedPieceResultDoesNotHideOtherLegalMoves() {
        String fen = "4r2k/8/8/8/8/8/4N3/4K3 w - - 0 20";
        var position = parser.parse(fen);
        var knightSource = Square.fromAlgebraic("e2");

        var knightMoves = legalGenerator.generate(
                position, knightSource
        );
        var allMoves = legalGenerator.generate(position);

        // Render the king's moves; the pinned knight has no legal moves.
        var visualization = BoardTestRenderer.render(
                position,
                Square.fromAlgebraic("e1"),
                allMoves
        );
        System.out.println(visualization);

        assertAll(
                () -> assertTrue(knightMoves.isEmpty()),
                () -> assertEquals(
                        Set.of(
                                move("e1", "d1"),
                                move("e1", "f1"),
                                move("e1", "d2"),
                                move("e1", "f2")
                        ),
                        allMoves,
                        visualization
                ),
                () -> assertEquals(fen, serializer.serialize(position))
        );
    }

    @Test
    void rejectsMissingKingsBeforeReturningAnEmptyResult() {
        // Deliberately invalid defensive fixture: both kings are absent.
        var position = parser.parse(
                "8/8/8/8/8/8/8/8 w - - 0 1"
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> legalGenerator.generate(position)
        );
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "r3k2r/8/8/8/8/8/8/R3K2R"
                    + " w KQkq - 4 20 | e1 | g1 | c1",
            "r3k2r/8/8/8/8/8/8/R3K2R"
                    + " b KQkq - 4 20 | e8 | g8 | c8"
    })
    void includesBothLegalCastlingMoves(
            String fen,
            String from,
            String kingSideTo,
            String queenSideTo
    ) {
        var position = parser.parse(fen);
        var moves = legalGenerator.generate(position);

        assertAll(
                () -> assertTrue(moves.contains(move(from, kingSideTo))),
                () -> assertTrue(moves.contains(move(from, queenSideTo))),
                () -> assertEquals(fen, serializer.serialize(position))
        );
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            // En passant removes the pawn currently checking the moving king.
            "7k/8/8/3pP3/4K3/8/8/8"
                    + " w - d6 0 20 | e5 | d6 | true",
            "8/8/8/4k3/3Pp3/8/8/7K"
                    + " b - d3 0 20 | e4 | d3 | true",

            // Removing both pawns from the rank would expose a rook attack.
            "7k/8/8/r4pPK/8/8/8/8"
                    + " w - f6 0 20 | g5 | f6 | false",
            "8/8/8/8/R4Ppk/8/8/7K"
                    + " b - f3 0 20 | g4 | f3 | false"
    })
    void includesEnPassantOnlyWhenKingRemainsSafe(
            String fen,
            String from,
            String to,
            boolean expected
    ) {
        var position = parser.parse(fen);
        var moves = legalGenerator.generate(position);

        var visualization = BoardTestRenderer.render(
                position,
                Square.fromAlgebraic(from),
                moves
        );
        System.out.println(fen);
        System.out.println(visualization);

        assertAll(
                () -> assertEquals(
                        expected,
                        moves.contains(move(from, to)),
                        visualization
                ),
                () -> assertEquals(fen, serializer.serialize(position))
        );
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "3r3k/4P3/8/8/8/8/8/K7"
                    + " w - - 0 20 | e7 | e8 | d8",
            "k7/8/8/8/8/8/4p3/3R3K"
                    + " b - - 0 20 | e2 | e1 | d1"
    })
    void retainsAllQuietAndCapturePromotionChoices(
            String fen,
            String from,
            String quietTo,
            String captureTo
    ) {
        var position = parser.parse(fen);
        var source = Square.fromAlgebraic(from);
        var moves = legalGenerator.generate(position);

        Set<Move> expected = new HashSet<>();

        // Two destinations, each requiring four distinct replacement choices.
        for (var type : Set.of(
                PieceType.QUEEN,
                PieceType.ROOK,
                PieceType.BISHOP,
                PieceType.KNIGHT
        )) {
            expected.add(new Move(
                    source,
                    Square.fromAlgebraic(quietTo),
                    Optional.of(type)
            ));
            expected.add(new Move(
                    source,
                    Square.fromAlgebraic(captureTo),
                    Optional.of(type)
            ));
        }

        var pawnMoves = moves.stream()
                .filter(candidate -> candidate.from().equals(source))
                .collect(Collectors.toSet());
        System.out.println(fen);
        System.out.println(BoardTestRenderer.render(position, Square.fromAlgebraic(from), pawnMoves));

        assertAll(
                () -> assertEquals(expected, pawnMoves),
                () -> assertEquals(fen, serializer.serialize(position))
        );
    }

    private static Move move(String from, String to) {
        return new Move(
                Square.fromAlgebraic(from),
                Square.fromAlgebraic(to)
        );
    }
}