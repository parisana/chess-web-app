# Backend reliability testing

Source companion updated for Day 25 on 2 November 2026. The user reports tests green after fixture corrections; no assistant application execution is claimed. See [Day 25 review](day25-ratings-statistics-review.md) and the historical [Day 23 review](day23-reliability-review.md).

## Pick the boundary being tested

| Question | Fixture | Reason |
| --- | --- | --- |
| Do invalid settings prevent binding/startup? | WebSocketPropertiesTest | Small binding context, without database or server |
| Can an authenticated socket receive only its private updates? | WebSocketIntegrationTest | Real transport, broker, security and listener |
| Does a hint wait for successful transaction completion? | Explicit TransactionTemplate and ready socket clients | Check before commit, after commit and after rollback |
| Do old stored rows survive new migrations? | GameMigrationIntegrationTest | Create old schema, insert data, then migrate forward |

## Reading a test

Read every test's name, action and main assertion; explain the behavior in one sentence. Deep-read one important test per increment, prioritizing authorization, transactions, concurrency and recovery. Before reading generated tests, list the successful result, expected rejection and state that must remain unchanged. Compare that checklist with the assertions.

For resignationReachesOnlyParticipantsAfterOuterCommit, trace who creates the transaction, when GameService joins it and when the listener can run. Check why the outsider needs its own successful probe. Ask which assertions distinguish an authorization defect from a broken receiving fixture.

## Transactions

| Approach | Default behavior and tradeoff |
| --- | --- |
| Application @Transactional | Starts a transaction if needed; otherwise joins by default. Successful owned transactions commit; unchecked exceptions normally trigger rollback. |
| Spring test @Transactional | Normally rolls back after the test; service calls join it. Useful for automatic cleanup, unsuitable by itself for after-commit assertions. |
| Test @Transactional plus @Commit | Commits at test completion, normally after assertions inside the test method. |
| TestTransaction | Explicitly end/commit a test-managed transaction mid-test; committed fixture data needs cleanup. |
| TransactionTemplate | Explicit block boundaries. It also joins an existing transaction by default, so these fixtures omit test-level @Transactional. |

Keep @Transactional on application service methods. Avoiding it on these tests lets templates own completion. Calls on different threads need their own transactions; a template alone does not make execution concurrent. Flush executes writes but does not make them committed. AFTER_COMMIT delivery failure cannot roll back the already committed game; the listener catches recipient send failures independently.

## Socket readiness and errors

CONNECTED is not a subscription acknowledgement. The fixture repeatedly sends a unique private byte probe until that client receives it. Later probe replies are ignored. This makes both participants and the outsider demonstrably able to receive before testing application isolation.

The test client preserves raw bytes with SimpleMessageConverter, then explicitly parses PlayerUpdate JSON. ERROR frames can have no body, so text handles null. A callback exception can otherwise leave the error queue empty and make a rejection assertion fail even when the server rejected correctly. Inspect protocol errors, callback errors and transport errors separately.

No-update polling for 300 ms is bounded evidence, not proof of permanent absence. Executor/network delays can matter locally. Combine these socket checks with narrower listener/interceptor tests instead of inferring durable delivery or exhaustive security from one fixture.

## Migration isolation

Flyway uses a unique default schema and target version for each fixture. A retained JDBC connection points fixture SQL at that schema; its schema is restored before release. Each rejected SQL statement runs with auto-commit, so subsequent assertions can proceed. Cleanup drops only that generated schema; Flyway.clean remains disabled.

Older rows are inserted before advancing migrations. V6-to-V10 verifies legacy baseline metadata; V7-to-V10 preserves pre-SAN history and V9 resignation. These fixtures test SQL schema behavior rather than service orchestration. Do not edit already-applied migrations to change production behavior.

## Suggested user-run commands

From backend/, without PostgreSQL:

```bash
./gradlew test --tests 'com.chessapp.notification.config.WebSocketPropertiesTest' --tests 'com.chessapp.notification.security.*'
```

With the configured PostgreSQL test database:

```bash
./gradlew test --tests 'com.chessapp.notification.WebSocketIntegrationTest' --tests 'com.chessapp.game.GameMigrationIntegrationTest'
SPRING_PROFILES_ACTIVE=test ./gradlew test
```

The broad notification.* pattern now includes the database-backed socket test. The current source filename and class are both GameMigrationIntegrationTest; no naming cleanup remains. Schema creation permission is required. These commands are suggestions, not assistant execution evidence.

## Day 25 ratings and cleanup lessons

PlayerStatisticsIntegrationTest omits test-level @Transactional: committed setup/service results are inspected by later JDBC reads, while explicit outer blocks establish rollback boundaries. Overflow is injected into a counter to fail rating processing after game flush/history insertion and reservation deletion. The tests check complete rollback, including marker removal. outerRollbackUndoesSuccessfulCompletionAndStatistics checks the reverse direction: a later outer failure undoes successful rating processing.

concurrentCompletionEventsApplyStatisticsOnce constructs a finished-but-unprocessed game using controlled SQL, then submits the same event on two workers with separate transactions. This isolates duplicate processing rather than replaying an actual HTTP completion race. The gate permits concurrent submissions; it does not force/observe a specific PostgreSQL lock-wait schedule or test distinct games sharing a Player. HTTP statistics fixtures use normal rollback-managed @Transactional tests because they assert reads/formatting rather than commit delivery. GameMigrationIntegrationTest adds a V10-to-V11 isolated SQL migration fixture; its V6/V7-to-V10 cases intentionally remain targeted at V10.

Foreign-key cleanup must delete processed markers and game_moves before games, and statistics before Players (also respect credentials, queue and reservations where present). Explicitly flush JPA child deletion before parent deletion. A failed @AfterEach marks the test failed even if assertions passed. Since cleanup itself is transactional, its failure rolls back earlier cleanup statements; previously committed setup/actions remain. Such residue can affect a later shared-database test. The earlier WebSocket failures' exact indirect cause is not established merely because a later suite passes. Never infer all failures share one cause from abbreviated stack traces; inspect the violated constraint.

The auth rollback fixture's emergency finally-path was not extended in Day 25: if a future transaction regression leaves a Player committed, its best-effort deletion may itself need statistics-first cleanup. Normal successful rollback remains covered by the existing assertions; no explicit new registration-failure statistics assertion was added.

Deep-read ratingFailureRollsBackTerminalMoveAndItsHistory, outerRollbackUndoesSuccessfulCompletionAndStatistics, concurrentCompletionEventsApplyStatisticsOnce, and upgradesV10WithInitialStatisticsAndHistoricalExclusions. No tests/builds are run by the assistant under the standing agreement.
