package com.chessapp.game.rules.movement;

import com.chessapp.game.rules.model.*;

import java.util.Objects;
import java.util.Optional;
/**
 * @author pari on 10/24/26
 */
public final class MoveApplier {

    private final PseudoLegalMoveGenerator moveGenerator;

    public MoveApplier(PseudoLegalMoveGenerator moveGenerator) {
        this.moveGenerator = Objects.requireNonNull(
                moveGenerator,
                "Pseudo-legal move generator is required"
        );
    }

    /**
     * Applies pseudo-legal candidates without checking king safety.
     * Currently, supports knight, rook, bishop, queen and ordinary king moves through registered strategies.
     * The algorithm is:
     * 1. Confirm the move appears in the piece's pseudo-legal candidates.
     * 2. Inspect the destination before moving, to identify any capture.
     * 3. Create the new board.
     * 4. Switch the side to move.
     * 5. Clear the previous en passant target.
     * 6. Reset the halfmove clock on capture; otherwise increment it.
     * 7. Increment the fullmove number after Black moves.
     * 8. Remove both rights for a moving king, and rights affected by
     *  *    moving or capturing a rook on its original corner.
     */
    public ChessPosition apply(ChessPosition position, Move move) {
        Objects.requireNonNull(position, "Position is required");
        Objects.requireNonNull(move, "Move is required");

        if (!moveGenerator.generate(position, move.from()).contains(move)) {
            throw new IllegalArgumentException(
                    "Move must be a supported pseudo-legal move"
            );
        }

        boolean isCapture = position.board()
                .pieceAt(move.to())
                .isPresent();

        var nextSide = position.sideToMove() == PieceColor.WHITE
                ? PieceColor.BLACK
                : PieceColor.WHITE;

        int halfmoveClock = isCapture
                ? 0
                : Math.incrementExact(position.halfmoveClock());

        int fullmoveNumber = position.sideToMove() == PieceColor.BLACK
                ? Math.incrementExact(position.fullmoveNumber())
                : position.fullmoveNumber();

        return new ChessPosition(
                position.board().movePiece(move.from(), move.to()),
                nextSide,
                castlingRightsAfterMove(position, move),
                // An en passant opportunity lasts for only the next move.
                Optional.empty(),
                halfmoveClock,
                fullmoveNumber
        );
    }

    private CastlingRights castlingRightsAfterMove(
            ChessPosition position,
            Move move
    ) {
        var rights = position.castlingRights();
        var movingPiece = position.board()
                .pieceAt(move.from())
                .orElseThrow();

        if (movingPiece.type() == PieceType.KING) {
            rights = removeKingRights(rights, movingPiece.color());
        } else if (movingPiece.type() == PieceType.ROOK) {
            rights = removeCornerRookRight(
                    rights,
                    movingPiece.color(),
                    move.from()
            );
        }

        var captured = position.board().pieceAt(move.to());

        if (captured.isPresent()
                && captured.orElseThrow().type() == PieceType.ROOK) {
            rights = removeCornerRookRight(
                    rights,
                    captured.orElseThrow().color(),
                    move.to()
            );
        }

        return rights;
    }

    private CastlingRights removeCornerRookRight(
            CastlingRights rights,
            PieceColor color,
            Square square
    ) {
        if (color == PieceColor.WHITE) {
            return new CastlingRights(
                    rights.whiteKingSide()
                            && !square.equals(Square.fromAlgebraic("h1")),
                    rights.whiteQueenSide()
                            && !square.equals(Square.fromAlgebraic("a1")),
                    rights.blackKingSide(),
                    rights.blackQueenSide()
            );
        }

        return new CastlingRights(
                rights.whiteKingSide(),
                rights.whiteQueenSide(),
                rights.blackKingSide()
                        && !square.equals(Square.fromAlgebraic("h8")),
                rights.blackQueenSide()
                        && !square.equals(Square.fromAlgebraic("a8"))
        );
    }

    private CastlingRights removeKingRights(
            CastlingRights rights,
            PieceColor color
    ) {
        if (color == PieceColor.WHITE) {
            return new CastlingRights(
                    false,
                    false,
                    rights.blackKingSide(),
                    rights.blackQueenSide()
            );
        }

        return new CastlingRights(
                rights.whiteKingSide(),
                rights.whiteQueenSide(),
                false,
                false
        );
    }
}
