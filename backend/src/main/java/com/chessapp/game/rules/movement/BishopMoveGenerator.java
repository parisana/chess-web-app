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
 */
public final class BishopMoveGenerator
        implements PiecePseudoLegalMoveGenerator {

    @Override
    public PieceType pieceType() {
        return PieceType.BISHOP;
    }

    @Override
    public Set<Move> generate(ChessPosition position, Square from) {
        Objects.requireNonNull(position, "Position is required");
        Objects.requireNonNull(from, "Source square is required");

        var source = position.board().pieceAt(from);

        if (source.isEmpty()) {
            return Set.of();
        }

        var bishop = source.orElseThrow();

        if (bishop.type() != PieceType.BISHOP) {
            throw new IllegalArgumentException(
                    "Source square must contain a bishop"
            );
        }

        if (bishop.color() != position.sideToMove()) {
            return Set.of();
        }

        Set<Move> moves = new HashSet<>();

        // Each combination selects one of the four diagonals.
        for (int fileStep : new int[] {-1, 1}) {
            for (int rankStep : new int[] {-1, 1}) {
                SlidingRayTraversal.addMoves(
                        position, from, fileStep, rankStep, moves
                );
            }
        }

        return Set.copyOf(moves);
    }
}
