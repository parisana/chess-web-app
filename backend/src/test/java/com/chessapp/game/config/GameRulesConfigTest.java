package com.chessapp.game.config;

import com.chessapp.game.rules.ChessRules;
import com.chessapp.game.rules.evaluation.PositionStatus;
import com.chessapp.game.rules.fen.FenParser;
import com.chessapp.game.rules.fen.FenSerializer;
import com.chessapp.game.rules.model.Move;
import com.chessapp.game.rules.model.Square;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class GameRulesConfigTest {

    @Test
    void constructsCompleteRulesFacadeWithoutApplicationInfrastructure() {
        // Load only rules configuration: no Boot application or database.
        try (var context = new AnnotationConfigApplicationContext(
                GameRulesConfig.class
        )) {
            var rules = context.getBean(ChessRules.class);
            var parser = context.getBean(FenParser.class);
            var serializer = context.getBean(FenSerializer.class);
            var initial = rules.initialPosition();
            var initialFen = serializer.serialize(initial);

            var advanced = rules.applyMove(initial, new Move(
                    Square.fromAlgebraic("e2"),
                    Square.fromAlgebraic("e4")
            ));

            // Each of the six White piece types has a legal move here.
            // This catches an omitted strategy in production registration.
            var allTypes = parser.parse(
                    "4k3/8/8/8/8/8/4P3/RNBQK3 w Q - 0 20"
            );
            var sources = rules.legalMoves(allTypes).stream()
                    .map(candidate -> candidate.from().toAlgebraic())
                    .collect(Collectors.toSet());

            assertAll(
                    () -> assertEquals(
                            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR"
                                    + " w KQkq - 0 1",
                            initialFen
                    ),
                    () -> assertEquals(20, rules.legalMoves(initial).size()),
                    () -> assertEquals(
                            PositionStatus.NORMAL,
                            rules.evaluate(initial)
                    ),
                    () -> assertEquals(
                            "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR"
                                    + " b KQkq e3 0 1",
                            serializer.serialize(advanced)
                    ),
                    () -> assertEquals(
                            Set.of("a1", "b1", "c1", "d1", "e1", "e2"),
                            sources
                    ),
                    () -> assertEquals(initialFen, serializer.serialize(initial))
            );
        }
    }
}