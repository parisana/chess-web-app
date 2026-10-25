package com.chessapp.game.rules.movement;

import com.chessapp.game.rules.model.ChessPosition;
import com.chessapp.game.rules.model.Move;
import com.chessapp.game.rules.model.PieceType;
import com.chessapp.game.rules.model.Square;

import java.util.Set;

/**
 * @author pari on 10/25/26
 * Generates pseudo-legal candidates for one piece type.
 * King safety is checked separately.
 */
public interface PiecePseudoLegalMoveGenerator {
    PieceType pieceType();

    Set<Move> generate(ChessPosition position, Square from);
}
