# Day 16 closing checks

Both checks below are present in chess-web-app-day16-end.zip, inspected on 28 October 2026. The user's green-test report predates their addition; no later explicit passing report is recorded. No tests or builds were run independently. The code below is retained as implementation reference; do not add duplicate tests. This file adds no production scope.

## 1. Facade propagation of king-count validation

Present in `backend/src/test/java/com/chessapp/game/rules/StandardChessRulesTest.java`. The parameterized imports and two-argument move helper already exist from the guided increments.

```java
@ParameterizedTest
@CsvSource(delimiter = '|', value = {
        // Deliberately invalid defensive fixtures: Black king missing,
        // White king missing, then two White kings.
        "8/8/8/8/8/8/4N3/4K3 w - - 0 20",
        "7k/8/8/8/8/8/4N3/8 w - - 0 20",
        "7k/8/8/8/8/8/4N3/K3K3 w - - 0 20"
})
void rejectsInvalidKingCountsAcrossFacadeOperations(String fen) {
    var position = parser.parse(fen);
    var proposal = move("e2", "c3");

    assertAll(
            () -> assertThrows(
                    IllegalArgumentException.class,
                    () -> rules.legalMoves(position)
            ),
            () -> assertThrows(
                    IllegalArgumentException.class,
                    () -> rules.applyMove(position, proposal)
            ),
            () -> assertThrows(
                    IllegalArgumentException.class,
                    () -> rules.evaluate(position)
            ),
            () -> assertEquals(fen, serializer.serialize(position))
    );
}
```

The knight proposal is ordinary in shape. Each operation must propagate king-count failure rather than accept it or report an outcome. This checks validation precedence for these inputs; it does not prove complete position legality/reachability.

## 2. Exercise production construction without PostgreSQL

Present in `backend/src/test/java/com/chessapp/game/config/GameRulesConfigTest.java`:

```java
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
```

Algorithm: load the production rules configuration in a small Spring context; obtain its actual facade; check initial placement, lookup, legal application and assessment; enumerate a position with all six moving-side piece types to detect missing strategy registration. The sparse fixture is a coherent rules input with one king per color; it is not a claim that the rules validate its historical reachability.

## Local verification

From `backend/`:

```bash
./gradlew test --tests 'com.chessapp.game.rules.*' --tests 'com.chessapp.game.config.GameRulesConfigTest'
```

The configuration test is outside `com.chessapp.game.rules.*`, so the original rules-only command does not select it.

With the configured PostgreSQL test database available, preserve the existing game-creation check:

```bash
./gradlew test --tests 'com.chessapp.game.GameCreationIntegrationTest'
```

Apply the seven accompanying document replacements at their project-relative paths. The handoff is `docs/day17-handoff.md`. The final Day 16 archive has now been source-reviewed; the later closing-test passing status remains unreported. Source inspection does not establish test execution.
