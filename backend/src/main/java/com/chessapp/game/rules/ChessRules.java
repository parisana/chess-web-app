package com.chessapp.game.rules;

import com.chessapp.game.rules.evaluation.PositionStatus;
import com.chessapp.game.rules.model.ChessPosition;
import com.chessapp.game.rules.model.Move;

import java.util.Set;

/**
 * @author pari on 10/24/26
 * Application-owned boundary for pure chess-rule operations.
 */
public interface ChessRules {
    ChessPosition initialPosition();
    /**
     * Returns every legal move for the side to move.
     */
    Set<Move> legalMoves(ChessPosition position);

    /**
     * Applies an exact legal move, including its promotion choice.
     * Returns a new position without changing the input.
     *
     * @throws IllegalArgumentException if the proposal is not legal
     */
    ChessPosition applyMove(ChessPosition position, Move move);

    /**
     * Assesses the side to move within the supported position statuses.
     * Does not evaluate history-dependent or other draw rules.
     */
    PositionStatus evaluate(ChessPosition position);
}
