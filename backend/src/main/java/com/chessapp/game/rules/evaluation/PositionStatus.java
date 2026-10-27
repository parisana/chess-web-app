package com.chessapp.game.rules.evaluation;

/**
 * @author pari on 10/27/26
 * Assessment of the side to move.
 * Does not include history-dependent or other draw rules.
 */
public enum PositionStatus {
    NORMAL,
    CHECK,
    CHECKMATE,
    STALEMATE
}