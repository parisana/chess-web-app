package com.chessapp.game.rules.movement;

import com.chessapp.game.rules.model.ChessPosition;
import com.chessapp.game.rules.model.Move;
import com.chessapp.game.rules.model.PieceType;
import com.chessapp.game.rules.model.Square;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
/**
 * @author pari on 10/25/26
 * starting implementation details:
 * - Add empty squares.
 * - At the first occupied square, include an opposing non-king piece as a capture.
 * - Stop at that occupied square regardless of color.
 */
public final class RookMoveGenerator implements PiecePseudoLegalMoveGenerator {

    @Override
    public PieceType pieceType() {
        return PieceType.ROOK;
    }

    @Override
    public Set<Move> generate(ChessPosition position, Square from) {
        Objects.requireNonNull(position, "Position is required");
        Objects.requireNonNull(from, "Source square is required");

        var source = position.board().pieceAt(from);

        if (source.isEmpty()) {
            return Set.of();
        }

        var rook = source.orElseThrow();

        if (rook.type() != PieceType.ROOK) {
            throw new IllegalArgumentException(
                    "Source square must contain a rook"
            );
        }

        if (rook.color() != position.sideToMove()) {
            return Set.of();
        }

        Set<Move> moves = new HashSet<>();

        for (int direction : new int[] {-1, 1}) {
            // horizontal left and right
            SlidingRayTraversal.addMoves(
                    position, from, direction, 0, moves
            );
            // vertical up and down
            SlidingRayTraversal.addMoves(
                    position, from, 0, direction, moves
            );
        }

        return Set.copyOf(moves);
    }

}
