package com.chessapp.game.rules.movement;

import com.chessapp.game.rules.model.*;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
/**
 * @author pari on 10/26/26
 * Generates ordinary king and standard castling candidates.
 * King safety, including castling path restrictions, is checked separately.
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
        addCastlingCandidates(position, from, king.color(), moves);
        return Set.copyOf(moves);
    }

    private void addCastlingCandidates(
            ChessPosition position,
            Square from,
            PieceColor color,
            Set<Move> moves
    ) {
        int homeRank = color == PieceColor.WHITE ? 0 : 7;

        // Rights alone do not establish the required king placement.
        if (!from.equals(new Square(4, homeRank))) {
            return;
        }

        var rights = position.castlingRights();

        boolean kingSide = color == PieceColor.WHITE
                ? rights.whiteKingSide()
                : rights.blackKingSide();

        boolean queenSide = color == PieceColor.WHITE
                ? rights.whiteQueenSide()
                : rights.blackQueenSide();

        if (kingSide
                && hasRook(position, new Square(7, homeRank), color)
                && isEmptyPath(position, homeRank, 5, 6)) {
            moves.add(new Move(from, new Square(6, homeRank)));
        }

        // b-file must also be empty, although the king never crosses it.
        if (queenSide
                && hasRook(position, new Square(0, homeRank), color)
                && isEmptyPath(position, homeRank, 1, 3)) {
            moves.add(new Move(from, new Square(2, homeRank)));
        }
    }

    private boolean hasRook(
            ChessPosition position,
            Square square,
            PieceColor color
    ) {
        return position.board().pieceAt(square)
                .filter(piece -> piece.type() == PieceType.ROOK
                        && piece.color() == color)
                .isPresent();
    }

    private boolean isEmptyPath(
            ChessPosition position,
            int rank,
            int firstFile,
            int lastFile
    ) {
        for (int file = firstFile; file <= lastFile; file++) {
            if (position.board().pieceAt(new Square(file, rank)).isPresent()) {
                return false;
            }
        }

        return true;
    }
}
