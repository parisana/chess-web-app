package com.chessapp.game.config;

import com.chessapp.game.rules.ChessRules;
import com.chessapp.game.rules.StandardChessRules;
import com.chessapp.game.rules.attack.SquareAttackDetector;
import com.chessapp.game.rules.evaluation.PositionEvaluator;
import com.chessapp.game.rules.fen.FenParser;
import com.chessapp.game.rules.fen.FenSerializer;
import com.chessapp.game.rules.movement.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
/**
 * @author pari on 10/24/26
 */
@Configuration(proxyBeanMethods = false)
public class GameRulesConfig {

    @Bean
    FenParser fenParser() {
        return new FenParser();
    }

    @Bean
    FenSerializer fenSerializer() {
        return new FenSerializer();
    }

    @Bean
    ChessRules chessRules(FenParser fenParser) {
        var pseudoLegalGenerator = new PseudoLegalMoveGenerator(
                new KnightMoveGenerator(),
                new RookMoveGenerator(),
                new BishopMoveGenerator(),
                new QueenMoveGenerator(),
                new KingMoveGenerator(),
                new PawnMoveGenerator()
        );

        var moveApplier = new MoveApplier(pseudoLegalGenerator);
        var attackDetector = new SquareAttackDetector();

        var legalMoveGenerator = new LegalMoveGenerator(
                pseudoLegalGenerator,
                moveApplier,
                attackDetector
        );

        var positionEvaluator = new PositionEvaluator(
                legalMoveGenerator,
                attackDetector
        );

        // Internal collaborators share one graph behind the rules boundary.
        return new StandardChessRules(
                fenParser,
                legalMoveGenerator,
                moveApplier,
                positionEvaluator
        );
    }
}
