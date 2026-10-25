package com.chessapp.game.rules.movement;

import com.chessapp.game.rules.model.ChessPosition;
import com.chessapp.game.rules.model.Move;
import com.chessapp.game.rules.model.PieceType;
import com.chessapp.game.rules.model.Square;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
/**
 * @author pari on 10/25/26
 */
public final class PseudoLegalMoveGenerator {

    private final Map<PieceType, PiecePseudoLegalMoveGenerator> strategies;

    public PseudoLegalMoveGenerator(PiecePseudoLegalMoveGenerator... generators) {
        Objects.requireNonNull(generators, "Generators are required");

        var registered =
                new EnumMap<PieceType, PiecePseudoLegalMoveGenerator>(PieceType.class);

        for (var generator : generators) {
            Objects.requireNonNull(generator, "Generator is required");

            var type = Objects.requireNonNull(
                    generator.pieceType(),
                    "Generator piece type is required"
            );

            if (registered.putIfAbsent(type, generator) != null) {
                throw new IllegalArgumentException(
                        "Duplicate move generator for " + type
                );
            }
        }

        // Copy registrations so the dispatcher remains immutable.
        this.strategies = Map.copyOf(registered);
    }

    public Set<Move> generate(ChessPosition position, Square from) {
        Objects.requireNonNull(position, "Position is required");
        Objects.requireNonNull(from, "Source square is required");

        var source = position.board().pieceAt(from);

        if (source.isEmpty()) {
            return Set.of();
        }

        var piece = source.orElseThrow();

        if (piece.color() != position.sideToMove()) {
            return Set.of();
        }

        var strategy = strategies.get(piece.type());

        if (strategy == null) {
            // Unsupported movement must not look like zero available moves.
            throw new UnsupportedOperationException(
                    "Movement is not supported for " + piece.type()
            );
        }

        return strategy.generate(position, from);
    }
}
