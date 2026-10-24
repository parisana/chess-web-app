package com.chessapp.game.rules.movement;

import com.chessapp.game.rules.attack.SquareAttackDetector;
import com.chessapp.game.rules.model.Board;
import com.chessapp.game.rules.model.ChessPosition;
import com.chessapp.game.rules.model.Move;
import com.chessapp.game.rules.model.PieceColor;
import com.chessapp.game.rules.model.PieceType;
import com.chessapp.game.rules.model.Square;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * @author pari on 10/24/26
 * Generates legal moves for one knight, not a complete position-wide move list.
 * Assumes a valid standard-chess position; validates king counts defensively.
 */
public final class KnightLegalMoveGenerator {

    private final KnightMoveGenerator moveGenerator;
    private final KnightMoveApplier moveApplier;
    private final SquareAttackDetector attackDetector;

    public KnightLegalMoveGenerator(
            KnightMoveGenerator moveGenerator,
            KnightMoveApplier moveApplier,
            SquareAttackDetector attackDetector
    ) {
        this.moveGenerator = Objects.requireNonNull(
                moveGenerator,
                "Knight move generator is required"
        );
        this.moveApplier = Objects.requireNonNull(
                moveApplier,
                "Knight move applier is required"
        );
        this.attackDetector = Objects.requireNonNull(
                attackDetector,
                "Attack detector is required"
        );
    }

    /**
     * The algorithm is:
     * 1. Locate the moving side’s king.
     * 2. Generate knight candidates.
     * 3. Apply each candidate to a temporary position.
     * 4. Check whether the opponent attacks the king in that position.
     * 5. Keep only candidates where the king is safe.
     * */
    public Set<Move> generate(ChessPosition position, Square from) {
        Objects.requireNonNull(position, "Position is required");
        Objects.requireNonNull(from, "Source square is required");

        var movingColor = position.sideToMove();
        var opposingColor = movingColor == PieceColor.WHITE
                ? PieceColor.BLACK
                : PieceColor.WHITE;

        var kingSquare = requireSingleKing(position.board(), movingColor);
        requireSingleKing(position.board(), opposingColor);

        Set<Move> legalMoves = new HashSet<>();

        for (var move : moveGenerator.generate(position, from)) {
            var candidate = moveApplier.apply(position, move);

            // The candidate's sideToMove has switched. Check the king
            // of the side that made the move, not the next side to move.
            if (!attackDetector.isAttacked(
                    candidate.board(),
                    kingSquare,
                    opposingColor
            )) {
                legalMoves.add(move);
            }
        }

        return Set.copyOf(legalMoves);
    }

    /**
     * The king-count checks prevent incomplete representation fixtures from accidentally being treated as positions suitable for legal-move filtering
     * */
    private Square requireSingleKing(Board board, PieceColor color) {
        Square kingSquare = null;

        for (int file = 0; file < 8; file++) {
            for (int rank = 0; rank < 8; rank++) {
                var square = new Square(file, rank);
                var piece = board.pieceAt(square);

                boolean isKing = piece
                        .filter(value -> value.color() == color
                                && value.type() == PieceType.KING)
                        .isPresent();

                if (!isKing) {
                    continue;
                }

                if (kingSquare != null) {
                    throw invalidKingCount(color);
                }

                kingSquare = square;
            }
        }

        if (kingSquare == null) {
            throw invalidKingCount(color);
        }

        return kingSquare;
    }

    private IllegalArgumentException invalidKingCount(PieceColor color) {
        return new IllegalArgumentException(
                "Position must contain exactly one " + color + " king"
        );
    }
}
