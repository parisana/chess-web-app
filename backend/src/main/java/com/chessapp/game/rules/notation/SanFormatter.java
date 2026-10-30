package com.chessapp.game.rules.notation;

import com.chessapp.game.rules.evaluation.PositionStatus;
import com.chessapp.game.rules.model.ChessPosition;
import com.chessapp.game.rules.model.Move;
import com.chessapp.game.rules.model.Piece;
import com.chessapp.game.rules.model.PieceType;

import java.util.Objects;
import java.util.Set;
/**
 * @author pari on 10/30/26
 * Formats an accepted standard-chess move as SAN.

 * The caller supplies the complete legal-move set for the original
 * position and the assessment of the resulting position.
 * This formatter does not generate moves or establish their legality.
 */
public final class SanFormatter {

    public String format(
            ChessPosition before,
            Move move,
            Set<Move> legalMoves,
            PositionStatus resultingStatus
    ) {
        Objects.requireNonNull(before, "Original position is required");
        Objects.requireNonNull(move, "Move is required");
        Objects.requireNonNull(legalMoves, "Legal moves are required");
        Objects.requireNonNull(
                resultingStatus,
                "Resulting position status is required"
        );

        if (!legalMoves.contains(move)) {
            throw new IllegalArgumentException(
                    "SAN requires a move from the supplied legal set"
            );
        }

        var piece = before.board().pieceAt(move.from())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Source square must contain a piece"
                ));

        if (isCastling(piece, move)) {
            String notation = move.to().file() > move.from().file()
                    ? "O-O"
                    : "O-O-O";

            return notation + checkSuffix(resultingStatus);
        }

        // A legal diagonal pawn move is a capture, including en passant,
        // whose destination square is empty in the original position.
        boolean capture =
                before.board().pieceAt(move.to()).isPresent()
                        || (
                        piece.type() == PieceType.PAWN
                                && move.from().file() != move.to().file()
                );

        var notation = new StringBuilder();

        if (piece.type() == PieceType.PAWN) {
            if (capture) {
                notation.append(fileLetter(move));
            }
        } else {
            notation.append(pieceLetter(piece.type()));
            notation.append(disambiguation(
                    before,
                    move,
                    piece,
                    legalMoves
            ));
        }

        if (capture) {
            notation.append('x');
        }

        notation.append(move.to().toAlgebraic());

        move.promotion().ifPresent(type ->
                notation.append('=').append(pieceLetter(type))
        );

        notation.append(checkSuffix(resultingStatus));

        return notation.toString();
    }

    private boolean isCastling(Piece piece, Move move) {
        return piece.type() == PieceType.KING
                && move.from().rank() == move.to().rank()
                && Math.abs(move.to().file() - move.from().file()) == 2;
    }

    /**
     * Returns the source information needed to distinguish this move from
     * other legal moves by pieces of the same type and color to the same
     * destination.

     * Uses no qualifier when there are no alternatives; otherwise prefers
     * the source file if unique, then the source rank if unique, and finally
     * the full source square.

     * Examples for a knight moving from b1 to d2:
     * - No alternative knight: returns ""    -> SAN "Nd2".
     * - Alternative on f1:    returns "b"   -> SAN "Nbd2".
     * - Alternative on b3:    returns "1"   -> SAN "N1d2".
     * - Alternatives on both f1 and b3:
     *                         returns "b1"  -> SAN "Nb1d2".

     * Only legal alternatives count, so a pinned piece that cannot legally
     * reach the destination does not cause disambiguation.
     * The caller adds any capture marker and check/checkmate suffix.
     */
    private String disambiguation(
            ChessPosition before,
            Move move,
            Piece movingPiece,
            Set<Move> legalMoves
    ) {
        var alternativeSources = legalMoves.stream()
                .filter(candidate -> candidate.to().equals(move.to()))
                .map(Move::from)
                .filter(from -> !from.equals(move.from()))
                .filter(from ->
                        before.board().pieceAt(from)
                                .filter(movingPiece::equals)
                                .isPresent()
                )
                .distinct()
                .toList();

        if (alternativeSources.isEmpty()) {
            return "";
        }

        boolean sharesFile = alternativeSources.stream()
                .anyMatch(source ->
                        source.file() == move.from().file()
                );

        boolean sharesRank = alternativeSources.stream()
                .anyMatch(source ->
                        source.rank() == move.from().rank()
                );

        // Prefer the file when it uniquely identifies the moving piece.
        if (!sharesFile) {
            return String.valueOf(fileLetter(move));
        }

        if (!sharesRank) {
            return Integer.toString(move.from().rank() + 1);
        }

        // Different alternatives can require both file and rank.
        return move.from().toAlgebraic();
    }

    private char fileLetter(Move move) {
        return (char) ('a' + move.from().file());
    }

    private String pieceLetter(PieceType type) {
        return switch (type) {
            case KING -> "K";
            case QUEEN -> "Q";
            case ROOK -> "R";
            case BISHOP -> "B";
            case KNIGHT -> "N";
            case PAWN -> throw new IllegalArgumentException(
                    "Pawns have no SAN piece letter"
            );
        };
    }

    private String checkSuffix(PositionStatus status) {
        return switch (status) {
            case CHECK -> "+";
            case CHECKMATE -> "#";
            case NORMAL, STALEMATE -> "";
        };
    }
}
