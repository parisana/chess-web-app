package com.chessapp.game.rules.attack;

import com.chessapp.game.rules.model.Board;
import com.chessapp.game.rules.model.PieceColor;
import com.chessapp.game.rules.model.PieceType;
import com.chessapp.game.rules.model.Square;

import java.util.Objects;
/**
 * @author pari on 10/24/26
 */
public final class SquareAttackDetector {

    // Does any piece of the specified color attack this square?
    public boolean isAttacked(
            Board board,
            Square target,
            PieceColor attackingColor
    ) {
        Objects.requireNonNull(board, "Board is required");
        Objects.requireNonNull(target, "Target square is required");
        Objects.requireNonNull(attackingColor, "Attacking color is required");

        int pawnDirection = attackingColor == PieceColor.WHITE ? 1 : -1;

        // Work backward from the target to possible pawn source squares.
        int pawnRank = target.rank() - pawnDirection;

        for (int fileOffset : new int[] {-1, 1}) {
            if (hasPiece(
                    board,
                    target.file() + fileOffset,
                    pawnRank,
                    attackingColor,
                    PieceType.PAWN
            )) {
                return true;
            }
        }

        for (int fileOffset : new int[] {-2, -1, 1, 2}) {
            int rankDistance = 3 - Math.abs(fileOffset);

            for (int direction : new int[] {-1, 1}) {
                if (hasPiece(
                        board,
                        target.file() + fileOffset,
                        target.rank() + direction * rankDistance,
                        attackingColor,
                        PieceType.KNIGHT
                )) {
                    return true;
                }
            }
        }

        for (int fileStep = -1; fileStep <= 1; fileStep++) {
            for (int rankStep = -1; rankStep <= 1; rankStep++) {
                if (fileStep == 0 && rankStep == 0) {
                    continue;
                }

                if (hasPiece(
                        board,
                        target.file() + fileStep,
                        target.rank() + rankStep,
                        attackingColor,
                        PieceType.KING
                )) {
                    return true;
                }

                boolean diagonal = fileStep != 0 && rankStep != 0;

                if (hasSlidingAttacker(
                        board,
                        target,
                        attackingColor,
                        fileStep,
                        rankStep,
                        diagonal ? PieceType.BISHOP : PieceType.ROOK
                )) {
                    return true;
                }
            }
        }

        return false;
    }

    private boolean hasSlidingAttacker(
            Board board,
            Square target,
            PieceColor attackingColor,
            int fileStep,
            int rankStep,
            PieceType directionalType
    ) {
        int file = target.file() + fileStep;
        int rank = target.rank() + rankStep;

        while (isInsideBoard(file, rank)) {
            var occupant = board.pieceAt(new Square(file, rank));

            if (occupant.isPresent()) {
                var piece = occupant.orElseThrow();

                // Any occupied square blocks the rest of this ray.
                return piece.color() == attackingColor
                        && (piece.type() == directionalType
                        || piece.type() == PieceType.QUEEN);
            }

            file += fileStep;
            rank += rankStep;
        }

        return false;
    }

    private boolean hasPiece(
            Board board,
            int file,
            int rank,
            PieceColor color,
            PieceType type
    ) {
        if (!isInsideBoard(file, rank)) {
            return false;
        }

        return board.pieceAt(new Square(file, rank))
                .filter(piece -> piece.color() == color
                        && piece.type() == type)
                .isPresent();
    }

    private boolean isInsideBoard(int file, int rank) {
        return file >= 0 && file < 8 && rank >= 0 && rank < 8;
    }
}
