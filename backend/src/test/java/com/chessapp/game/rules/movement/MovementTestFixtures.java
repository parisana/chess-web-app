package com.chessapp.game.rules.movement;

import com.chessapp.game.rules.attack.SquareAttackDetector;

/**
 * @author pari on 10/25/26
 */
final class MovementTestFixtures {

    private MovementTestFixtures() {
    }

    static PseudoLegalMoveGenerator pseudoLegalGenerator() {
        return new PseudoLegalMoveGenerator(
                new KnightMoveGenerator(),
                new RookMoveGenerator(),
                new BishopMoveGenerator(),
                new QueenMoveGenerator(),
                new KingMoveGenerator()
        );
    }

    static LegalMoveGenerator legalGenerator(
            PseudoLegalMoveGenerator generator
    ) {
        return new LegalMoveGenerator(
                generator,
                new MoveApplier(generator),
                new SquareAttackDetector()
        );
    }
}
