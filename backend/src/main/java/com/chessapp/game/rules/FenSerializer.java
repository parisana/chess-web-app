package com.chessapp.game.rules;

import com.chessapp.game.rules.model.*;

import java.util.Objects;

/**
 * @author pari on 10/24/26
 */
public final class FenSerializer {

    public String serialize(ChessPosition position) {
        Objects.requireNonNull(position, "Chess position is required");

        return String.join(
                " ",
                serializeBoard(position.board()),
                position.sideToMove() == PieceColor.WHITE ? "w" : "b",
                serializeCastlingRights(position.castlingRights()),
                position.enPassantTarget()
                        .map(Square::toAlgebraic)
                        .orElse("-"),
                Integer.toString(position.halfmoveClock()),
                Integer.toString(position.fullmoveNumber())
        );
    }

    private String serializeBoard(Board board) {
        var placement = new StringBuilder();

        // FEN visits rank 8 first, while our internal ranks start at zero.
        for (int rank = 7; rank >= 0; rank--) {
            int emptySquares = 0;

            for (int file = 0; file < 8; file++) {
                var piece = board.pieceAt(new Square(file, rank));

                if (piece.isEmpty()) {
                    emptySquares++;
                    continue;
                }

                // Flush the preceding empty run before writing an occupied square.
                if (emptySquares > 0) {
                    placement.append(emptySquares);
                    emptySquares = 0;
                }

                placement.append(serializePiece(piece.orElseThrow()));
            }

            // An empty run may also end at the right edge of a rank.
            if (emptySquares > 0) {
                placement.append(emptySquares);
            }

            if (rank > 0) {
                placement.append('/');
            }
        }

        return placement.toString();
    }

    private char serializePiece(Piece piece) {
        char symbol = switch (piece.type()) {
            case KING -> 'k';
            case QUEEN -> 'q';
            case ROOK -> 'r';
            case BISHOP -> 'b';
            case KNIGHT -> 'n';
            case PAWN -> 'p';
        };

        return piece.color() == PieceColor.WHITE
                ? Character.toUpperCase(symbol)
                : symbol;
    }

    private String serializeCastlingRights(CastlingRights rights) {
        var value = new StringBuilder();

        if (rights.whiteKingSide()) {
            value.append('K');
        }
        if (rights.whiteQueenSide()) {
            value.append('Q');
        }
        if (rights.blackKingSide()) {
            value.append('k');
        }
        if (rights.blackQueenSide()) {
            value.append('q');
        }

        return value.isEmpty() ? "-" : value.toString();
    }
}
