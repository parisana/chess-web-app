package com.chessapp.game.export;

import com.chessapp.game.dto.GameHistoryResponse;
import com.chessapp.game.dto.GameResponse;
import com.chessapp.game.entity.GameStatus;
import com.chessapp.game.exception.PgnExportUnavailableException;
import com.chessapp.game.rules.fen.FenParser;
import com.chessapp.game.rules.model.ChessPosition;
import com.chessapp.game.rules.model.PieceColor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
/**
 * @author pari on 10/31/26
 */
@Component
@RequiredArgsConstructor
public class PgnFormatter {

    private static final String INITIAL_FEN =
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR"
                    + " w KQkq - 0 1";

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("uuuu.MM.dd")
                    .withZone(ZoneOffset.UTC);

    private final FenParser fenParser;

    /**
     * Formats one captured game/history snapshot.
     * Stored SAN is reused rather than reconstructed from old positions.
     */
    public String format(
            GameResponse game,
            GameHistoryResponse history,
            String whiteUsername,
            String blackUsername
    ) {
        requireMatchingSnapshot(game, history);
        requireExportableHistory(history);

        var baseline = fenParser.parse(history.historyStartFen());
        String result = resultMarker(game);

        var pgn = new StringBuilder();

        appendTag(pgn, "Event", "Casual Game");
        appendTag(pgn, "Site", "?");
        appendTag(pgn, "Date", DATE_FORMAT.format(game.createdAt()));
        appendTag(pgn, "Round", "-");
        appendTag(pgn, "White", whiteUsername);
        appendTag(pgn, "Black", blackUsername);
        appendTag(pgn, "Result", result);

        if (!INITIAL_FEN.equals(history.historyStartFen())) {
            // Supplemental tags follow the standard roster alphabetically.
            appendTag(pgn, "FEN", history.historyStartFen());
            appendTag(pgn, "SetUp", "1");
        }

        pgn.append('\n');

        final List<String> tokens = generateTokens(history, baseline, result);
        appendMovetext(pgn, tokens);

        // End the game with one empty line.
        return pgn.append('\n').toString();
    }

    private static List<String> generateTokens(GameHistoryResponse history, ChessPosition baseline, String result) {
        List<String> tokens = new ArrayList<>();

        boolean whiteToMove =
                baseline.sideToMove() == PieceColor.WHITE;

        // A long avoids overflow when advancing a large FEN move number.
        long moveNumber = baseline.fullmoveNumber();

        for (int index = 0; index < history.moves().size(); index++) {
            if (whiteToMove) {
                tokens.add(moveNumber + ".");
            } else if (index == 0) {
                // A custom baseline may begin with Black, e.g. 20... Kh7.
                tokens.add(moveNumber + "...");
            }

            tokens.add(history.moves().get(index).san());

            // A full move finishes only after Black has moved.
            if (!whiteToMove) {
                moveNumber++;
            }

            whiteToMove = !whiteToMove;
        }

        tokens.add(result);
        return tokens;
    }

    private static void requireMatchingSnapshot(
            GameResponse game,
            GameHistoryResponse history
    ) {
        if (!game.id().equals(history.gameId())
                || game.version() != history.version()) {
            throw new IllegalArgumentException(
                    "PGN requires matching game and history snapshots"
            );
        }
    }

    private static void requireExportableHistory(
            GameHistoryResponse history
    ) {
        if (!history.historyComplete()) {
            throw new PgnExportUnavailableException(
                    "Earlier moves are unavailable for this legacy game"
            );
        }

        if (history.recordedMoveCount() != history.moves().size()) {
            throw new PgnExportUnavailableException(
                    "The recorded move history is incomplete"
            );
        }

        long expectedSequence = 1;

        for (var move : history.moves()) {
            if (move.sequenceNumber() != expectedSequence) {
                throw new PgnExportUnavailableException(
                        "The recorded move history has a sequence gap"
                );
            }

            if (move.san() == null || move.san().isBlank()) {
                throw new PgnExportUnavailableException(
                        "SAN notation is unavailable for an older move"
                );
            }

            expectedSequence++;
        }
    }

    private static String resultMarker(GameResponse game) {
        if (game.status() == GameStatus.IN_PROGRESS) {
            return "*";
        }

        if (game.result() == null) {
            throw new IllegalStateException(
                    "A finished game requires a result"
            );
        }

        return switch (game.result()) {
            case WHITE_WIN -> "1-0";
            case BLACK_WIN -> "0-1";
            case DRAW -> "1/2-1/2";
        };
    }

    private static void appendTag(
            StringBuilder output,
            String name,
            String value
    ) {
        output.append('[')
                .append(name)
                .append(" \"")
                .append(escapeTagValue(value))
                .append("\"]\n");
    }

    private static String escapeTagValue(String value) {
        // Keep user-controlled names inside a single quoted tag value.
        return value.replaceAll("\\p{Cntrl}", " ")
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }

    /**
     * Wraps only between tokens, keeping movetext lines below 80 columns.
     */
    private static void appendMovetext(
            StringBuilder output,
            List<String> tokens
    ) {
        int lineLength = 0;

        for (String token : tokens) {
            if (lineLength > 0) {
                if (lineLength + 1 + token.length() > 79) {
                    output.append('\n');
                    lineLength = 0;
                } else {
                    output.append(' ');
                    lineLength++;
                }
            }

            output.append(token);
            lineLength += token.length();
        }

        output.append('\n');
    }
}
