package com.chessapp.game.rules.movement;

import com.chessapp.game.rules.model.ChessPosition;
import com.chessapp.game.rules.model.Move;
import com.chessapp.game.rules.model.PieceColor;
import com.chessapp.game.rules.model.PieceType;
import com.chessapp.game.rules.model.Square;

import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
/**
 * @author pari on 10/26/26
 * Generates ordinary pawn, promotion and en passant candidates.
 * King safety is checked separately.
 */
public final class PawnMoveGenerator
        implements PiecePseudoLegalMoveGenerator {

    @Override
    public PieceType pieceType() {
        return PieceType.PAWN;
    }

    /**
     * Advances: require empty squares. The double advance is considered only after the first square is confirmed empty, and only from the starting rank.
     * Captures: inspect the two forward diagonals and accept opposing non-king occupants.
     * Direction: White increases the internal rank; Black decreases it.
     * Special cases: expand promotions into four choices;
     * en passant requires matching metadata, the correct source rank,
     * an empty destination and an adjacent opposing pawn.
     * */
    @Override
    public Set<Move> generate(ChessPosition position, Square from) {
        Objects.requireNonNull(position, "Position is required");
        Objects.requireNonNull(from, "Source square is required");

        var sourcePiece = position.board().pieceAt(from);

        if (sourcePiece.isEmpty()) {
            return Set.of();
        }

        var pawn = sourcePiece.orElseThrow();

        if (pawn.type() != PieceType.PAWN) {
            throw new IllegalArgumentException(
                    "Source square must contain a pawn"
            );
        }

        if (pawn.color() != position.sideToMove()) {
            return Set.of();
        }

        // Unpromoted pawns cannot occupy either terminal rank.
        if (from.rank() == 0 || from.rank() == 7) {
            throw new IllegalArgumentException(
                    "Pawn source must not be on rank 1 or 8"
            );
        }

        int direction = pawn.color() == PieceColor.WHITE ? 1 : -1;
        int startingRank = pawn.color() == PieceColor.WHITE ? 1 : 6;
        int nextRank = from.rank() + direction;

        Set<Move> moves = new HashSet<>();

        var oneStep = new Square(from.file(), nextRank);

        if (position.board().pieceAt(oneStep).isEmpty()) {
            addPawnMove(moves, from, oneStep);

            // Reaching this branch also establishes a clear intermediate square.
            if (from.rank() == startingRank) {
                var twoSteps = new Square(
                        from.file(),
                        from.rank() + 2 * direction
                );

                if (position.board().pieceAt(twoSteps).isEmpty()) {
                    addPawnMove(moves, from, twoSteps);
                }
            }
        }

        for (int fileOffset : new int[] {-1, 1}) {
            int file = from.file() + fileOffset;

            if (file < 0 || file > 7) {
                continue;
            }

            var to = new Square(file, nextRank);
            var target = position.board().pieceAt(to);

            if (target.isEmpty()) {
                int enPassantSourceRank =
                        pawn.color() == PieceColor.WHITE ? 4 : 3;

                // The diagonal loop already establishes one file sideways
                // and one rank forward. Metadata alone is insufficient.
                if (from.rank() == enPassantSourceRank
                        && position.enPassantTarget()
                        .filter(to::equals)
                        .isPresent()) {

                    // The target is the empty landing square. The captured pawn sits
                    // on that file, beside the capturing pawn on its original rank.
                    // Example: e5 -> d6 captures the pawn on d5.
                    var capturedSquare = new Square(to.file(), from.rank());

                    boolean hasOpposingPawn = position.board()
                            .pieceAt(capturedSquare)
                            .filter(piece -> piece.type() == PieceType.PAWN
                                    && piece.color() != pawn.color())
                            .isPresent();

                    if (hasOpposingPawn) {
                        moves.add(new Move(from, to));
                    }
                }

                continue;
            }

            var occupant = target.orElseThrow();

            if (occupant.color() != pawn.color()
                    && occupant.type() != PieceType.KING) {
                addPawnMove(moves, from, to);
            }
        }

        return Set.copyOf(moves);
    }

    private void addPawnMove(
            Set<Move> moves,
            Square from,
            Square to
    ) {
        if (to.rank() == 0 || to.rank() == 7) {
            // Each choice is a separate candidate, including underpromotions.
            for (var type : new PieceType[] {
                    PieceType.QUEEN,
                    PieceType.ROOK,
                    PieceType.BISHOP,
                    PieceType.KNIGHT
            }) {
                moves.add(new Move(from, to, Optional.of(type)));
            }

            return;
        }

        moves.add(new Move(from, to));
    }
}
