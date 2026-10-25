package com.chessapp.game.rules.movement;

import com.chessapp.game.rules.model.ChessPosition;
import com.chessapp.game.rules.model.Move;
import com.chessapp.game.rules.model.PieceType;
import com.chessapp.game.rules.model.Square;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
/**
 * @author pari on 10/24/26
 * A knight moves two squares along one axis and one along the other.
 * Enumerate those eight offsets, discard destinations outside the board, then exclude friendly-occupied squares and the opposing king’s square.
 * Each remaining destination becomes a candidate move. King safety is checked in a later stage.
 */
public final class KnightMoveGenerator implements PiecePseudoLegalMoveGenerator {

    @Override
    public PieceType pieceType() {
        return PieceType.KNIGHT;
    }

    @Override
    public Set<Move> generate(ChessPosition position, Square from) {
        Objects.requireNonNull(position, "Position is required");
        Objects.requireNonNull(from, "Source square is required");

        var source = position.board().pieceAt(from);

        if (source.isEmpty()) {
            return Set.of();
        }

        var knight = source.orElseThrow();

        if (knight.type() != PieceType.KNIGHT) {
            throw new IllegalArgumentException(
                    "Source square must contain a knight"
            );
        }

        if (knight.color() != position.sideToMove()) {
            return Set.of();
        }

        Set<Move> moves = new HashSet<>();

        // A knight changes one coordinate by two and the other by one.
        for (int fileOffset : new int[] {-2, -1, 1, 2}) {
            int rankDistance = 3 - Math.abs(fileOffset);

            for (int direction : new int[] {-1, 1}) {
                int file = from.file() + fileOffset;
                int rank = from.rank() + direction * rankDistance;

                // Check before constructing Square, which rejects invalid coordinates.
                if (file < 0 || file > 7 || rank < 0 || rank > 7) {
                    continue;
                }

                var to = new Square(file, rank);
                var target = position.board().pieceAt(to);

                if (target.isPresent()) {
                    var occupant = target.orElseThrow();

                    // Kings are checked or checkmated; they are never captured.
                    if (occupant.color() == knight.color()
                            || occupant.type() == PieceType.KING) {
                        continue;
                    }
                }

                moves.add(new Move(from, to));
            }
        }

        // prevents callers from modifying the result
        return Set.copyOf(moves);
    }
}
