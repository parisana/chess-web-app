package com.chessapp.game.rules.model;

import java.util.Objects;
import java.util.Optional;

/**
 * @author pari on 10/24/26
 * Immutable board state and position metadata.
 *
 * @param enPassantTarget the square passed over by the preceding double
 *                        pawn advance, and the destination of a potential
 *                        en passant capture; not the captured pawn's square.
 *                        Its presence does not guarantee a capture is available.
 */
public record ChessPosition(
        Board board,
        PieceColor sideToMove,
        CastlingRights castlingRights,
        Optional<Square> enPassantTarget,
        int halfmoveClock,
        int fullmoveNumber
) {

    public ChessPosition {
        Objects.requireNonNull(board, "Board is required");
        Objects.requireNonNull(sideToMove, "Side to move is required");
        Objects.requireNonNull(
                castlingRights,
                "Castling rights are required"
        );
        Objects.requireNonNull(
                enPassantTarget,
                "En passant target must use Optional.empty() when absent"
        );

        // In standard chess, the target square can only be on rank 3 or 6.
        // This validates its shape, not whether a capture is actually legal.
        enPassantTarget.ifPresent(square -> {
            if (square.rank() != 2 && square.rank() != 5) {
                throw new IllegalArgumentException(
                        "En passant target must be on rank 3 or 6"
                );
            }
        });

        if (halfmoveClock < 0) {
            throw new IllegalArgumentException(
                    "Halfmove clock must not be negative"
            );
        }

        if (fullmoveNumber < 1) {
            throw new IllegalArgumentException(
                    "Fullmove number must be at least 1"
            );
        }
    }
}
