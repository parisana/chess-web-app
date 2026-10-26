package com.chessapp.game.rules.movement;

import com.chessapp.game.rules.attack.SquareAttackDetector;
import com.chessapp.game.rules.model.*;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * @author pari on 10/24/26
 */
public final class LegalMoveGenerator {

    private final PseudoLegalMoveGenerator moveGenerator;
    private final MoveApplier moveApplier;
    private final SquareAttackDetector attackDetector;

    public LegalMoveGenerator(
            PseudoLegalMoveGenerator moveGenerator,
            MoveApplier moveApplier,
            SquareAttackDetector attackDetector
    ) {
        this.moveGenerator = Objects.requireNonNull(
                moveGenerator,
                "Pseudo-legal move generator is required"
        );
        this.moveApplier = Objects.requireNonNull(
                moveApplier,
                "Move applier is required"
        );
        this.attackDetector = Objects.requireNonNull(
                attackDetector,
                "Attack detector is required"
        );
    }

    /**
     * Filters movement candidates using king safety.
     * Currently, generates moves for a single supported piece source square;
     * an empty result does not establish checkmate or stalemate.
     * The algorithm is:
     1. Generate candidates.
     2. Apply each candidate.
     3. Reject candidates that leave the moving side’s king attacked.
     * */
    public Set<Move> generate(ChessPosition position, Square from) {
        Objects.requireNonNull(position, "Position is required");
        Objects.requireNonNull(from, "Source square is required");

        var movingColor = position.sideToMove();
        var opposingColor = movingColor == PieceColor.WHITE
                ? PieceColor.BLACK
                : PieceColor.WHITE;

        // Reject incomplete or ambiguous positions before generating candidates.
        requireSingleKing(position.board(), movingColor);
        requireSingleKing(position.board(), opposingColor);

        Set<Move> legalMoves = new HashSet<>();

        for (var move : moveGenerator.generate(position, from)) {
            var candidate = moveApplier.apply(position, move);

            // King moves change its square; other moves preserve its location.
            var kingSquare = requireSingleKing(
                    candidate.board(),
                    movingColor
            );

            // Application switches turns, but we inspect the side that just moved.
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
