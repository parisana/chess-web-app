package com.chessapp.game.rules;

import com.chessapp.game.rules.model.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
/**
 * @author pari on 10/24/26
 */
public final class FenCodec {

    public ChessPosition parse(String fen) {
        if (fen == null) {
            throw new IllegalArgumentException("FEN is required");
        }

        // Preserve empty fields so missing fields and repeated spaces
        // are rejected instead of silently normalized.
        String[] fields = fen.split(" ", -1);

        if (fields.length != 6) {
            throw new IllegalArgumentException(
                    "FEN must contain six space-separated fields"
            );
        }

        return new ChessPosition(
                parseBoard(fields[0]),
                parseSideToMove(fields[1]),
                parseCastlingRights(fields[2]),
                parseEnPassantTarget(fields[3]),
                parseCounter(fields[4], 0, "Halfmove clock"),
                parseCounter(fields[5], 1, "Fullmove number")
        );
    }

    private Board parseBoard(String placement) {
        String[] ranks = placement.split("/", -1);

        if (ranks.length != 8) {
            throw new IllegalArgumentException(
                    "FEN placement must contain eight ranks"
            );
        }

        Map<Square, Piece> pieces = new HashMap<>();

        for (int rankIndex = 0; rankIndex < 8; rankIndex++) {
            int file = 0;
            boolean previousWasDigit = false;

            for (char symbol : ranks[rankIndex].toCharArray()) {
                if (symbol >= '1' && symbol <= '8') {
                    // A run of empty squares uses one digit, such as 8,
                    // rather than adjacent digits such as 44.
                    if (previousWasDigit) {
                        throw new IllegalArgumentException(
                                "FEN empty-square counts must not be adjacent"
                        );
                    }

                    file += symbol - '0';
                    previousWasDigit = true;
                } else {
                    Piece piece = parsePiece(symbol);

                    if (file == 8) {
                        throw invalidRankWidth();
                    }

                    // FEN starts at rank 8; our internal rank zero is rank 1.
                    pieces.put(
                            new Square(file, 7 - rankIndex),
                            piece
                    );

                    file++;
                    previousWasDigit = false;
                }

                if (file > 8) {
                    throw invalidRankWidth();
                }
            }

            if (file != 8) {
                throw invalidRankWidth();
            }
        }

        return new Board(pieces);
    }

    private Piece parsePiece(char symbol) {
        PieceType type = switch (symbol) {
            case 'K', 'k' -> PieceType.KING;
            case 'Q', 'q' -> PieceType.QUEEN;
            case 'R', 'r' -> PieceType.ROOK;
            case 'B', 'b' -> PieceType.BISHOP;
            case 'N', 'n' -> PieceType.KNIGHT;
            case 'P', 'p' -> PieceType.PAWN;
            default -> throw new IllegalArgumentException(
                    "Unrecognized FEN piece: " + symbol
            );
        };

        PieceColor color = Character.isUpperCase(symbol)
                ? PieceColor.WHITE
                : PieceColor.BLACK;

        return new Piece(color, type);
    }

    private PieceColor parseSideToMove(String value) {
        return switch (value) {
            case "w" -> PieceColor.WHITE;
            case "b" -> PieceColor.BLACK;
            default -> throw new IllegalArgumentException(
                    "FEN side to move must be w or b"
            );
        };
    }

    private CastlingRights parseCastlingRights(String value) {
        if (value.isEmpty()
                || (!value.equals("-") && !value.matches("K?Q?k?q?"))) {
            throw new IllegalArgumentException(
                    "FEN castling rights must be - or an ordered subset of KQkq"
            );
        }

        return new CastlingRights(
                value.contains("K"),
                value.contains("Q"),
                value.contains("k"),
                value.contains("q")
        );
    }

    private Optional<Square> parseEnPassantTarget(String value) {
        return value.equals("-")
                ? Optional.empty()
                : Optional.of(Square.fromAlgebraic(value));
    }

    private int parseCounter(String value, int minimum, String name) {
        if (!value.matches("[0-9]+")) {
            throw new IllegalArgumentException(
                    name + " must contain decimal digits"
            );
        }

        final int number;

        try {
            number = Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(
                    name + " exceeds the supported integer range",
                    exception
            );
        }

        if (number < minimum) {
            throw new IllegalArgumentException(
                    name + " must be at least " + minimum
            );
        }

        return number;
    }

    private IllegalArgumentException invalidRankWidth() {
        return new IllegalArgumentException(
                "Each FEN rank must describe exactly eight squares"
        );
    }
}
