package com.chessapp.game.rules;

import com.chessapp.game.rules.evaluation.PositionEvaluator;
import com.chessapp.game.rules.evaluation.PositionStatus;
import com.chessapp.game.rules.fen.FenParser;
import com.chessapp.game.rules.model.ChessPosition;
import com.chessapp.game.rules.model.Move;
import com.chessapp.game.rules.movement.LegalMoveGenerator;
import com.chessapp.game.rules.movement.MoveApplier;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import java.util.Objects;
import java.util.Set;

/**
 * @author pari on 10/24/26
 * Coordinates the existing standard-chess rules pipeline.
 */
@RequiredArgsConstructor
public final class StandardChessRules implements ChessRules {

    private static final String INITIAL_FEN =
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR"
                    + " w KQkq - 0 1";

    @NonNull
    private final FenParser fenParser;

    @NonNull
    private final LegalMoveGenerator legalMoveGenerator;

    @NonNull
    private final MoveApplier moveApplier;

    @NonNull
    private final PositionEvaluator positionEvaluator;

    @Override
    public ChessPosition initialPosition() {
        // Keep one starting-position definition, interpreted by our parser.
        return fenParser.parse(INITIAL_FEN);
    }

    @Override
    public Set<Move> legalMoves(ChessPosition position) {
        return legalMoveGenerator.generate(position);
    }

    /**
     * Algorithm:
     * 1. Generate the complete legal set from the original position.
     * 2. Require exact membership, including promotion choice.
     * 3. Delegate immutable placement and metadata updates.
     */
    @Override
    public ChessPosition applyMove(
            ChessPosition position,
            Move move
    ) {
        Objects.requireNonNull(position, "Position is required");
        Objects.requireNonNull(move, "Move is required");

        if (!legalMoves(position).contains(move)) {
            throw new IllegalArgumentException(
                    "Move must be legal for the side to move"
            );
        }

        // Direct application checks pseudo-legality only. The legal-set
        // check above also establishes king safety and castling path safety.
        return moveApplier.apply(position, move);
    }

    @Override
    public PositionStatus evaluate(ChessPosition position) {
        return positionEvaluator.evaluate(position);
    }
}
