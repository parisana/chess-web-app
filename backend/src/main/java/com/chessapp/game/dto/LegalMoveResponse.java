package com.chessapp.game.dto;

import com.chessapp.game.rules.model.ChessPosition;
import com.chessapp.game.rules.model.Move;
import com.chessapp.game.rules.model.PieceType;
/**
 * @author pari on 11/4/26
 * Display information for a move already established as legal.
 */
public record LegalMoveResponse(
        String from,
        String to,
        PieceType promotion,
        boolean capture
) {
    public static LegalMoveResponse fromLegalMove(
            ChessPosition position,
            Move move
    ) {
        var movingPiece = position.board().pieceAt(move.from())
                .orElseThrow(() -> new IllegalStateException(
                        "A legal move requires a source piece"
                ));

        boolean occupiedDestination =
                position.board().pieceAt(move.to()).isPresent();

        // A legal diagonal pawn move to an empty square is en passant.
        // This class describes legal moves; it does not validate proposals.
        boolean enPassant =
                movingPiece.type() == PieceType.PAWN
                        && move.from().file() != move.to().file()
                        && !occupiedDestination;

        return new LegalMoveResponse(
                move.from().toAlgebraic(),
                move.to().toAlgebraic(),
                move.promotion().orElse(null),
                occupiedDestination || enPassant
        );
    }
}
