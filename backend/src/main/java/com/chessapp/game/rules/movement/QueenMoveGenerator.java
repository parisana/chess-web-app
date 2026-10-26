package com.chessapp.game.rules.movement;

import com.chessapp.game.rules.model.ChessPosition;
import com.chessapp.game.rules.model.Move;
import com.chessapp.game.rules.model.PieceType;
import com.chessapp.game.rules.model.Square;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * @author pari on 10/26/26
 * a queen follows all four rook directions and all four bishop directions.
 */
public final class QueenMoveGenerator
        implements PiecePseudoLegalMoveGenerator {

    @Override
    public PieceType pieceType() {
        return PieceType.QUEEN;
    }

    @Override
    public Set<Move> generate(ChessPosition position, Square from) {
        Objects.requireNonNull(position, "Position is required");
        Objects.requireNonNull(from, "Source square is required");

        var sourcePiece = position.board().pieceAt(from);

        if (sourcePiece.isEmpty()) {
            return Set.of();
        }

        var queen = sourcePiece.orElseThrow();

        if (queen.type() != PieceType.QUEEN) {
            throw new IllegalArgumentException(
                    "Source square must contain a queen"
            );
        }

        if (queen.color() != position.sideToMove()) {
            return Set.of();
        }

        Set<Move> moves = new HashSet<>();

        // The two loops produce nine combinations.
        // We skip (0, 0) because it represents no movement, leaving exactly eight valid ray directions.
        for (int fileStep = -1; fileStep <= 1; fileStep++) {
            for (int rankStep = -1; rankStep <= 1; rankStep++) {
                // Zero displacement is not a ray.
                if (fileStep == 0 && rankStep == 0) {
                    continue;
                }

                SlidingRayTraversal.addMoves(
                        position, from, fileStep, rankStep, moves
                );
            }
        }

        return Set.copyOf(moves);
    }
}
