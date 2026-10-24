package com.chessapp.game.rules.movement;

import com.chessapp.game.rules.model.CastlingRights;
import com.chessapp.game.rules.model.ChessPosition;
import com.chessapp.game.rules.model.Move;
import com.chessapp.game.rules.model.PieceColor;
import com.chessapp.game.rules.model.PieceType;
import com.chessapp.game.rules.model.Square;

import java.util.Objects;
import java.util.Optional;
/**
 * @author pari on 10/24/26
 */
public final class KnightMoveApplier {

    private final KnightMoveGenerator moveGenerator;

    public KnightMoveApplier(KnightMoveGenerator moveGenerator) {
        this.moveGenerator = Objects.requireNonNull(
                moveGenerator,
                "Knight move generator is required"
        );
    }

    /**
     * Applies a pseudo-legal knight move without checking king safety.
     * The algorithm is:
     * 1. Confirm the move appears in the knight’s pseudo-legal candidates.
     * 2. Inspect the destination before moving, to identify any capture.
     * 3. Create the new board.
     * 4. Switch the side to move.
     * 5. Clear the previous en passant target.
     * 6. Reset the halfmove clock on capture; otherwise increment it.
     * 7. Increment the fullmove number after Black moves.
     * 8. Remove castling rights if a rook is captured on its original corner.
     */
    public ChessPosition apply(ChessPosition position, Move move) {
        Objects.requireNonNull(position, "Position is required");
        Objects.requireNonNull(move, "Move is required");

        if (!moveGenerator.generate(position, move.from()).contains(move)) {
            throw new IllegalArgumentException(
                    "Move must be a pseudo-legal knight move"
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
                castlingRightsAfterCapture(position, move.to()),
                // An en passant opportunity lasts for only the next move.
                Optional.empty(),
                halfmoveClock,
                fullmoveNumber
        );
    }

    private CastlingRights castlingRightsAfterCapture(
            ChessPosition position,
            Square destination
    ) {
        var rights = position.castlingRights();
        var captured = position.board().pieceAt(destination);

        if (captured.isEmpty()
                || captured.orElseThrow().type() != PieceType.ROOK) {
            return rights;
        }

        if (captured.orElseThrow().color() == PieceColor.WHITE) {
            return new CastlingRights(
                    rights.whiteKingSide()
                            && !destination.equals(Square.fromAlgebraic("h1")),
                    rights.whiteQueenSide()
                            && !destination.equals(Square.fromAlgebraic("a1")),
                    rights.blackKingSide(),
                    rights.blackQueenSide()
            );
        }

        return new CastlingRights(
                rights.whiteKingSide(),
                rights.whiteQueenSide(),
                rights.blackKingSide()
                        && !destination.equals(Square.fromAlgebraic("h8")),
                rights.blackQueenSide()
                        && !destination.equals(Square.fromAlgebraic("a8"))
        );
    }
}
