package com.chessapp.game.rules.movement;

import com.chessapp.game.rules.model.*;

import java.util.Objects;
import java.util.Optional;
/**
 * @author pari on 10/24/26
 */
public final class MoveApplier {

    private final PseudoLegalMoveGenerator moveGenerator;

    public MoveApplier(PseudoLegalMoveGenerator moveGenerator) {
        this.moveGenerator = Objects.requireNonNull(
                moveGenerator,
                "Pseudo-legal move generator is required"
        );
    }

    /**
     * Applies supported pseudo-legal candidates without checking king safety.
     * Updates placement, turns, counters, en passant metadata and castling rights.
     * Unsupported special moves remain the registered strategies' responsibility.
     */
    public ChessPosition apply(ChessPosition position, Move move) {
        Objects.requireNonNull(position, "Position is required");
        Objects.requireNonNull(move, "Move is required");

        if (!moveGenerator.generate(position, move.from()).contains(move)) {
            throw new IllegalArgumentException(
                    "Move must be a supported pseudo-legal move"
            );
        }

        var movingPiece = position.board()
                .pieceAt(move.from())
                .orElseThrow();

        boolean isPawnMove = movingPiece.type() == PieceType.PAWN;

        boolean destinationOccupied = position.board()
                .pieceAt(move.to())
                .isPresent();

        // Exact candidate membership has already validated the target,
        // geometry and adjacent opposing pawn. A supported pawn move
        // diagonally into an empty square must therefore be en passant.
        boolean isEnPassant = isPawnMove
                && move.from().file() != move.to().file()
                && !destinationOccupied;

        boolean isCapture = destinationOccupied || isEnPassant;

        var nextSide = position.sideToMove() == PieceColor.WHITE
                ? PieceColor.BLACK
                : PieceColor.WHITE;

        int halfmoveClock = isPawnMove || isCapture
                ? 0
                : Math.incrementExact(position.halfmoveClock());

        int fullmoveNumber = position.sideToMove() == PieceColor.BLACK
                ? Math.incrementExact(position.fullmoveNumber())
                : position.fullmoveNumber();

        // Candidate membership has established whether promotion is required and whether this exact replacement choice is supported.
        var destinationPiece = move.promotion()
                .map(type -> new Piece(movingPiece.color(), type))
                .orElse(movingPiece);

        var nextBoard = position.board().movePiece(
                move.from(),
                move.to(),
                destinationPiece
        );

        if (isEnPassant) {
            // En passant captures beside the source, not on the destination:
            // e5 -> d6 removes d5; e4 -> d3 removes d4.
            var capturedSquare = new Square(
                    move.to().file(),
                    move.from().rank()
            );

            nextBoard = nextBoard.removePiece(capturedSquare);
        }

        return new ChessPosition(
                nextBoard,
                nextSide,
                castlingRightsAfterMove(position, move),
                enPassantTargetAfterMove(move, movingPiece),
                halfmoveClock,
                fullmoveNumber
        );
    }

    private CastlingRights castlingRightsAfterMove(
            ChessPosition position,
            Move move
    ) {
        var rights = position.castlingRights();
        var movingPiece = position.board()
                .pieceAt(move.from())
                .orElseThrow();

        if (movingPiece.type() == PieceType.KING) {
            rights = removeKingRights(rights, movingPiece.color());
        } else if (movingPiece.type() == PieceType.ROOK) {
            rights = removeCornerRookRight(
                    rights,
                    movingPiece.color(),
                    move.from()
            );
        }

        var captured = position.board().pieceAt(move.to());

        if (captured.isPresent()
                && captured.orElseThrow().type() == PieceType.ROOK) {
            rights = removeCornerRookRight(
                    rights,
                    captured.orElseThrow().color(),
                    move.to()
            );
        }

        return rights;
    }

    private CastlingRights removeCornerRookRight(
            CastlingRights rights,
            PieceColor color,
            Square square
    ) {
        if (color == PieceColor.WHITE) {
            return new CastlingRights(
                    rights.whiteKingSide()
                            && !square.equals(Square.fromAlgebraic("h1")),
                    rights.whiteQueenSide()
                            && !square.equals(Square.fromAlgebraic("a1")),
                    rights.blackKingSide(),
                    rights.blackQueenSide()
            );
        }

        return new CastlingRights(
                rights.whiteKingSide(),
                rights.whiteQueenSide(),
                rights.blackKingSide()
                        && !square.equals(Square.fromAlgebraic("h8")),
                rights.blackQueenSide()
                        && !square.equals(Square.fromAlgebraic("a8"))
        );
    }

    private CastlingRights removeKingRights(
            CastlingRights rights,
            PieceColor color
    ) {
        if (color == PieceColor.WHITE) {
            return new CastlingRights(
                    false,
                    false,
                    rights.blackKingSide(),
                    rights.blackQueenSide()
            );
        }

        return new CastlingRights(
                rights.whiteKingSide(),
                rights.whiteQueenSide(),
                false,
                false
        );
    }

    private Optional<Square> enPassantTargetAfterMove(
            Move move,
            Piece movingPiece
    ) {
        // Membership validation has already established a supported pawn advance.
        if (movingPiece.type() == PieceType.PAWN
                && Math.abs(move.to().rank() - move.from().rank()) == 2) {
            int passedOverRank =
                    (move.from().rank() + move.to().rank()) / 2;

            return Optional.of(
                    new Square(move.from().file(), passedOverRank)
            );
        }

        // Every other supported move expires the previous opportunity.
        return Optional.empty();
    }
}
