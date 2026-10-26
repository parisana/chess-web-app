package com.chessapp.game.rules.movement;

import com.chessapp.game.rules.model.ChessPosition;
import com.chessapp.game.rules.model.Move;
import com.chessapp.game.rules.model.PieceType;
import com.chessapp.game.rules.model.Square;

import java.util.Set;
/**
 * @author pari on 10/25/26
 * Shared movement-ray traversal for sliding-piece strategies. [rook, bishop, queen]
 */
final class SlidingRayTraversal {

    private SlidingRayTraversal() {
    }

    /**
     * Adds candidates along one ray.
     * Callers supply a nonzero direction with steps between -1 and 1,
     * after validating the source piece and side to move.
     */
    static void addMoves(
            ChessPosition position,
            Square from,
            int fileStep,
            int rankStep,
            Set<Move> moves
    ) {
        int file = from.file() + fileStep;
        int rank = from.rank() + rankStep;

        while (file >= 0 && file < 8 && rank >= 0 && rank < 8) {
            var to = new Square(file, rank);
            var target = position.board().pieceAt(to);

            if (target.isPresent()) {
                var occupant = target.orElseThrow();

                if (occupant.color() != position.sideToMove()
                        && occupant.type() != PieceType.KING) {
                    moves.add(new Move(from, to));
                }

                // Every occupant ends the ray, including a capture.
                break;
            }

            moves.add(new Move(from, to));

            file += fileStep;
            rank += rankStep;
        }
    }
}
