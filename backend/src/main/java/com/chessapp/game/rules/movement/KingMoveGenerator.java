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
 * Generates ordinary one-square king candidates.
 * King safety is checked separately; castling is not included.
 */
public final class KingMoveGenerator
        implements PiecePseudoLegalMoveGenerator {

    @Override
    public PieceType pieceType() {
        return PieceType.KING;
    }

    @Override
    public Set<Move> generate(ChessPosition position, Square from) {
        Objects.requireNonNull(position, "Position is required");
        Objects.requireNonNull(from, "Source square is required");

        var source = position.board().pieceAt(from);

        if (source.isEmpty()) {
            return Set.of();
        }

        var king = source.orElseThrow();

        if (king.type() != PieceType.KING) {
            throw new IllegalArgumentException(
                    "Source square must contain a king"
            );
        }

        if (king.color() != position.sideToMove()) {
            return Set.of();
        }

        Set<Move> moves = new HashSet<>();

        for (int fileStep = -1; fileStep <= 1; fileStep++) {
            for (int rankStep = -1; rankStep <= 1; rankStep++) {
                if (fileStep == 0 && rankStep == 0) {
                    continue;
                }

                int file = from.file() + fileStep;
                int rank = from.rank() + rankStep;

                // Validate coordinates before constructing Square.
                if (file < 0 || file > 7 || rank < 0 || rank > 7) {
                    continue;
                }

                var to = new Square(file, rank);
                var target = position.board().pieceAt(to);

                if (target.isPresent()) {
                    var occupant = target.orElseThrow();

                    if (occupant.color() == king.color()
                            || occupant.type() == PieceType.KING) { // excludes capturing the opposing king when given an invalid position.
                        continue;
                    }
                }

                moves.add(new Move(from, to));
            }
        }

        return Set.copyOf(moves);
    }
}
