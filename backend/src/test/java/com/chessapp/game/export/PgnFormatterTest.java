package com.chessapp.game.export;

import com.chessapp.game.dto.GameHistoryResponse;
import com.chessapp.game.dto.GameMoveResponse;
import com.chessapp.game.dto.GameResponse;
import com.chessapp.game.entity.GameResult;
import com.chessapp.game.entity.GameStatus;
import com.chessapp.game.exception.PgnExportUnavailableException;
import com.chessapp.game.rules.fen.FenParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;


/**
 * @author pari on 10/31/26
 */
class PgnFormatterTest {

    private static final String INITIAL =
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR"
                    + " w KQkq - 0 1";

    private final PgnFormatter formatter =
            new PgnFormatter(new FenParser());

    @Test
    void formatsStandardHeadersAndActiveMovetext() {
        String pgn = formatter.format(
                game(null),
                history(INITIAL, true, "e4", "e5", "Nf3"),
                "Alice",
                "Bob"
        );

        assertEquals("""
                [Event "Casual Game"]
                [Site "?"]
                [Date "2026.10.31"]
                [Round "-"]
                [White "Alice"]
                [Black "Bob"]
                [Result "*"]

                1. e4 e5 2. Nf3 *

                """, pgn);
    }

    @Test
    void customBlackBaselineControlsNumberingRegardlessOfVersion() {
        String baseline = "7k/8/5KQ1/8/8/8/8/8 b - - 0 20";

        String pgn = formatter.format(
                game(null),
                history(baseline, true, "Kh7", "Qg7#"),
                "Alice",
                "Bob"
        );

        assertTrue(pgn.contains("[FEN \"" + baseline + "\"]\n"));
        assertTrue(pgn.contains("[SetUp \"1\"]\n"));
        assertTrue(pgn.endsWith("20... Kh7 21. Qg7# *\n\n"));
    }

    @ParameterizedTest
    @EnumSource(GameResult.class)
    void finishedResultMatchesHeaderAndFinalMarker(GameResult result) {
        String marker = switch (result) {
            case WHITE_WIN -> "1-0";
            case BLACK_WIN -> "0-1";
            case DRAW -> "1/2-1/2";
        };

        String pgn = formatter.format(
                game(result),
                history(INITIAL, true),
                "Alice",
                "Bob"
        );

        // Resignation/agreement can finish a game without any moves.
        assertTrue(pgn.contains("[Result \"" + marker + "\"]\n"));
        assertTrue(pgn.endsWith("\n\n" + marker + "\n\n"));
    }

    @Test
    void rejectsIncompleteLegacyHistoryEvenWithNoMoves() {
        assertThrows(
                PgnExportUnavailableException.class,
                () -> formatter.format(
                        game(null),
                        history(INITIAL, false),
                        "Alice",
                        "Bob"
                )
        );
    }

    @Test
    void rejectsMissingSan() {
        assertThrows(
                PgnExportUnavailableException.class,
                () -> formatter.format(
                        game(null),
                        history(INITIAL, true, (String) null),
                        "Alice",
                        "Bob"
                )
        );
    }

    @Test
    void rejectsMissingRowsAndSequenceGaps() {
        var missingRow = new GameHistoryResponse(
                10L, 42L, INITIAL, true, 1L, List.of()
        );

        var sequenceGap = new GameHistoryResponse(
                10L,
                42L,
                INITIAL,
                true,
                1L,
                List.of(move(2, "e4"))
        );

        for (var history : List.of(missingRow, sequenceGap)) {
            assertThrows(
                    PgnExportUnavailableException.class,
                    () -> formatter.format(
                            game(null), history, "Alice", "Bob"
                    )
            );
        }
    }

    @Test
    void escapesNamesAndPreventsTagLineInjection() {
        String pgn = formatter.format(
                game(null),
                history(INITIAL, true),
                "A\"B\\C\nD",
                "Bob"
        );

        assertTrue(pgn.contains("[White \"A\\\"B\\\\C D\"]\n"));
    }

    @Test
    void wrapsMovetextBetweenTokens() {
        String[] sans = IntStream.range(0, 80)
                .mapToObj(index -> index % 2 == 0 ? "Nf3" : "Nf6")
                .toArray(String[]::new);

        String pgn = formatter.format(
                game(null),
                history(INITIAL, true, sans),
                "Alice",
                "Bob"
        );

        String movetext = pgn.substring(pgn.indexOf("\n\n") + 2);

        assertTrue(movetext.lines().allMatch(line ->
                line.length() < 80
                        && line.equals(line.strip())
        ));

        assertTrue(movetext.endsWith("40. Nf3 Nf6 *\n\n"));
    }

    private static GameResponse game(GameResult result) {
        // Version 42 intentionally has no relationship to the move count.
        return new GameResponse(
                10L,
                1L,
                2L,
                result == null
                        ? GameStatus.IN_PROGRESS
                        : GameStatus.FINISHED,
                INITIAL,
                Instant.parse("2026-10-31T09:00:00Z"),
                42L,
                result,
                null,
                null,
                null
        );
    }

    private static GameHistoryResponse history(
            String baseline,
            boolean complete,
            String... sans
    ) {
        var moves = IntStream.range(0, sans.length)
                .mapToObj(index -> move(index + 1L, sans[index]))
                .toList();

        return new GameHistoryResponse(
                10L, 42L, baseline, complete, moves.size(), moves
        );
    }

    private static GameMoveResponse move(long sequence, String san) {
        // Formatting consumes only sequence and SAN from each record.
        return new GameMoveResponse(
                sequence,
                1L,
                "e2",
                "e4",
                null,
                INITIAL,
                Instant.parse("2026-10-31T09:00:00Z"),
                san
        );
    }
}