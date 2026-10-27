package com.chessapp.game.rules.evaluation;

import com.chessapp.game.rules.attack.SquareAttackDetector;
import com.chessapp.game.rules.model.ChessPosition;
import com.chessapp.game.rules.model.PieceColor;
import com.chessapp.game.rules.model.PieceType;
import com.chessapp.game.rules.model.Square;
import com.chessapp.game.rules.movement.LegalMoveGenerator;

import java.util.Objects;
/**
 * @author pari on 10/27/26
 * LegalMoveGenerator validates king counts and enumerates legal moves; SquareAttackDetector establishes current check; PositionEvaluator combines those results.
 */
public final class PositionEvaluator {

    private final LegalMoveGenerator legalMoveGenerator;
    private final SquareAttackDetector attackDetector;

    public PositionEvaluator(
            LegalMoveGenerator legalMoveGenerator,
            SquareAttackDetector attackDetector
    ) {
        this.legalMoveGenerator = Objects.requireNonNull(
                legalMoveGenerator,
                "Legal move generator is required"
        );
        this.attackDetector = Objects.requireNonNull(
                attackDetector,
                "Attack detector is required"
        );
    }

    /**
     * Assesses the side to move.

     * Algorithm:
     * 1. Enumerate all legal replies, validating king counts through the generator.
     * 2. Check whether the current moving king is attacked.
     * 3. Combine check state and reply availability.

     * Assumes an otherwise valid standard-chess position; this does not
     * establish historical reachability or evaluate other draw rules.
     */
    public PositionStatus evaluate(ChessPosition position) {
        Objects.requireNonNull(position, "Position is required");

        // Generate the complete set first. Invalid inputs or unsupported
        // movement must fail rather than resemble a position with no replies.
        var legalMoves = legalMoveGenerator.generate(position);

        var kingSquare = findMovingKing(position);
        var opposingColor = position.sideToMove() == PieceColor.WHITE
                ? PieceColor.BLACK
                : PieceColor.WHITE;

        // Inspect the current board, before any reply is applied.
        boolean inCheck = attackDetector.isAttacked(
                position.board(),
                kingSquare,
                opposingColor
        );

        if (legalMoves.isEmpty()) {
            return inCheck
                    ? PositionStatus.CHECKMATE
                    : PositionStatus.STALEMATE;
        }

        return inCheck
                ? PositionStatus.CHECK
                : PositionStatus.NORMAL;
    }

    /**
     * Locates the moving king after legal generation has established
     * that exactly one king of each color exists.
     */
    private Square findMovingKing(ChessPosition position) {
        for (int file = 0; file < 8; file++) {
            for (int rank = 0; rank < 8; rank++) {
                var square = new Square(file, rank);

                boolean isMovingKing = position.board()
                        .pieceAt(square)
                        .filter(piece ->
                                piece.color() == position.sideToMove()
                                        && piece.type() == PieceType.KING)
                        .isPresent();

                if (isMovingKing) {
                    return square;
                }
            }
        }

        // represents a broken internal assumption because generation already validated both kings.
        throw new IllegalStateException(
                "Moving king is missing after legal-move validation"
        );
    }
}
