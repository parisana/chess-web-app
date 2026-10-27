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
     * Generates legal moves for one source square.
     * An empty source-specific result does not establish a position outcome.
     */
    public Set<Move> generate(ChessPosition position, Square from) {
        Objects.requireNonNull(position, "Position is required");
        Objects.requireNonNull(from, "Source square is required");

        requireKings(position.board());

        return generateForSource(position, from);
    }

    /**
     * Generates every legal move for the side to move.

     * Algorithm:
     * 1. Require exactly one king per color.
     * 2. Inspect every square for a moving-side piece.
     * 3. Combine its moves using the shared legal filtering pipeline.
     * 4. Return an immutable set.
     */
    public Set<Move> generate(ChessPosition position) {
        Objects.requireNonNull(position, "Position is required");

        // Validate before traversal so an empty board cannot look like zero replies.
        requireKings(position.board());

        Set<Move> legalMoves = new HashSet<>();

        for (int file = 0; file < 8; file++) {
            for (int rank = 0; rank < 8; rank++) {
                var from = new Square(file, rank);

                boolean belongsToMovingSide = position.board()
                        .pieceAt(from)
                        .filter(piece ->
                                piece.color() == position.sideToMove())
                        .isPresent();

                if (belongsToMovingSide) {
                    // Each source is evaluated against the same original position.
                    legalMoves.addAll(generateForSource(position, from));
                }
            }
        }

        return Set.copyOf(legalMoves);
    }

    /**
     * Generates moves after the public entry point has validated the input.
     */
    private Set<Move> generateForSource(
            ChessPosition position,
            Square from
    ) {
        var movingColor = position.sideToMove();
        var opposingColor = movingColor == PieceColor.WHITE
                ? PieceColor.BLACK
                : PieceColor.WHITE;

        Set<Move> legalMoves = new HashSet<>();

        for (var move : moveGenerator.generate(position, from)) {
            if (isCastlingMove(position, move)
                    && !isCastlingPathSafe(position, move, opposingColor)) {
                continue;
            }

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

    private void requireKings(Board board) {
        requireSingleKing(board, PieceColor.WHITE);
        requireSingleKing(board, PieceColor.BLACK);
    }

    /**
     * Identifies castling among already validated pseudo-legal candidates.
     */
    private boolean isCastlingMove(ChessPosition position, Move move) {
        var movingPiece = position.board()
                .pieceAt(move.from())
                .orElseThrow();

        return movingPiece.type() == PieceType.KING
                && Math.abs(move.to().file() - move.from().file()) == 2;
    }

    /**
     * Checks the starting and transit squares of a castling candidate.
     * Called only for castling; destination safety uses the shared final-board check.
     */
    private boolean isCastlingPathSafe(
            ChessPosition position,
            Move move,
            PieceColor opposingColor
    ) {
        // Castling cannot be used to escape check.
        if (attackDetector.isAttacked(
                position.board(),
                move.from(),
                opposingColor
        )) {
            return false;
        }

        int direction = Integer.signum(
                move.to().file() - move.from().file()
        );

        var transitSquare = new Square(
                move.from().file() + direction,
                move.from().rank()
        );

        // Check transit with the king's source vacated and the rook unmoved.
        var transitBoard = position.board().movePiece(
                move.from(),
                transitSquare
        );

        return !attackDetector.isAttacked(
                transitBoard,
                transitSquare,
                opposingColor
        );
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
