package com.chessapp.game.rules.movement;

import com.chessapp.game.rules.attack.SquareAttackDetector;

/**
 * @author pari on 10/25/26
 */
public final class MovementTestFixtures {

    private MovementTestFixtures() {
    }

    public static PseudoLegalMoveGenerator pseudoLegalGenerator() {
        return new PseudoLegalMoveGenerator(
                new KnightMoveGenerator(),
                new RookMoveGenerator(),
                new BishopMoveGenerator(),
                new QueenMoveGenerator(),
                new KingMoveGenerator(),
                new PawnMoveGenerator()
        );
    }

    public static LegalMoveGenerator legalGenerator(
            PseudoLegalMoveGenerator generator
    ) {
        return new LegalMoveGenerator(
                generator,
                new MoveApplier(generator),
                new SquareAttackDetector()
        );
    }
}
