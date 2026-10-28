# Post-Day-16 source review and integration proposal

Historical proposal: the user subsequently selected the larger service option (move application, concurrency, completion and reservation release together), implemented it through inline guided increments, and uploaded the actual Day 17 source for review. The selected implementation uses GameService and GameMoveResult.from; see [Day 17 review](day17-gameplay-service.md). The original proposal below is preserved as historical planning.

28 October 2026. Baseline: `chess-web-app-day16-end.zip`. This document records source inspection and a proposed scope. No gameplay implementation is authorized by this proposal.

## Reviewed baseline and evidence

The seven requested documents already contain the source-reviewed Day 16 replacements: README, architecture, development progress, roadmap, API contracts, frontend README and movement pipeline architecture. Reapplying the replacements is unnecessary. Historical sections describe earlier boundaries; their initial-position-only statements do not override the Day 16 sections.

Source inspection confirms the four-operation `ChessRules` interface, exact legal membership in `StandardChessRules.applyMove()`, separate assessment, the Lombok four-dependency constructor and all six production strategy registrations. No blocking defect was identified in the inspected Day 16 facade/configuration code. This is not an exhaustive rules-engine correctness claim or a comparison against a separately supplied Day 15 archive.

| Source location, relative to the project root | Inspected finding |
| --- | --- |
| `backend/src/main/java/com/chessapp/game/rules/StandardChessRules.java` | Complete legal lookup precedes application; promotion choice participates in membership; assessment remains separate |
| `backend/src/main/java/com/chessapp/game/config/GameRulesConfig.java` | One complete internal rules graph; parser and serializer remain beans |
| `backend/src/test/java/com/chessapp/game/rules/StandardChessRulesTest.java` | Opening, application, special-move, rejection and assessment regressions; missing/duplicate king checks across all three position-taking operations |
| `backend/src/test/java/com/chessapp/game/config/GameRulesConfigTest.java` | Actual production construction without Boot/PostgreSQL, including legal sources for all six piece types |
| `backend/src/main/java/com/chessapp/game/service/GameService.java` | Creation uses initialPosition and serialization; reads enforce participant access; no move command |
| `backend/src/main/java/com/chessapp/game/entity/Game.java` | Current FEN and @Version already exist; no operation changes the FEN |
| `backend/src/main/resources/db/migration/V4__create_games_table.sql` | Version column exists; status constraint permits only IN_PROGRESS |
| `backend/src/main/java/com/chessapp/game/repository/ActiveGamePlayerRepository.java` | Reservations and active-game lookup exist; release does not |
| `backend/src/test/java/com/chessapp/game/GameCreationIntegrationTest.java` | Initial FEN persistence, version zero and invalid/missing participant assertions |
| `backend/src/test/java/com/chessapp/game/GameReadIntegrationTest.java` | Both participants, nonparticipant, missing-game and unauthenticated HTTP cases |
| `backend/src/test/java/com/chessapp/game/ActiveGameIntegrationTest.java` | Existing committed-transaction/concurrent-creation patterns to reuse for future move integration |
| `frontend/src/app/app.routes.ts` | Authentication/Home routes; no game route |

The supplied handoff reports GameService and GameCreationIntegrationTest unchanged from Day 15. This session inspected their current content; it did not independently establish a cross-archive diff.

The user reports tests green through Day 16 increments 1–2. Both closing tests are present, with no subsequent explicit passing report supplied. No tests or builds were run in this session; no detailed test log or separate frontend build result is available. The closing tests should not be implemented again.

## Recommended milestone split — awaiting scope selection

The next milestone should demonstrate one trusted application caller submitting a move against the persisted position, with service-owned access, turn and concurrent-write checks.

| Capability | Recommended placement | Reason |
| --- | --- | --- |
| Service move use case and current-FEN persistence | Next milestone | Connects the existing pure rules boundary to the application |
| Concurrent-command protection | Same milestone as persistence | The first mutable game use case must prevent lost updates and stale proposals |
| Terminal lifecycle | Following milestone, before HTTP move exposure | Requires persisted status/result decisions and a new migration for the existing status constraint |
| Active-reservation release | Same transaction and milestone as terminal lifecycle | A completed game and its eligibility reservations must agree |
| HTTP move endpoint | Separate milestone after those service behaviors | Adds JWT extraction, request validation, response and error contracts |
| Move history | Separate milestone | Current FEN persistence alone can support this bounded integration; replay and repetition cannot be claimed |

These are proposed boundaries, not accepted decisions or delivery dates. A larger alternative is one complete gameplay milestone covering service, concurrency, terminal lifecycle and release; HTTP and history can still remain separate. The smaller split better supports guided increments, but leaves a deliberately incomplete internal checkpoint between the first two milestones.

At that intermediate checkpoint a move may produce checkmate or stalemate while persisted status remains IN_PROGRESS and both reservations remain held. The rules provide no legal continuation from such a position, but that does not complete the application lifecycle. Document this limitation explicitly and complete terminal lifecycle/release before exposing HTTP moves. Do not prohibit a legal mating move merely to hide the limitation.

## Proposed service algorithm

An illustrative future signature is `submitMove(gameId, requestingPlayerId, expectedVersion, move)`. It is not added to source by this update.

1. Begin the service transaction; load the game or report its absence.
2. Verify participant membership before revealing move-related state. Derive White/Black from stored participant IDs, never from a supplied color.
3. Reject an outdated expected version. Parse the stored FEN, then require that the requester owns its side to move.
4. Call `ChessRules.applyMove(position, move)`. Keep king safety, castling and promotion semantics inside the rules boundary.
5. Serialize the returned position and change the managed entity through an explicit mutation method. Flush so a concurrent optimistic-lock conflict surfaces before constructing the successful result, including its new version.
6. Return normally through the transaction boundary; a successful flush is not itself a commit. All failed commands must leave durable state unchanged.

An expected-version check rejects proposals created from an older state even if a later position again makes the same coordinate move legal. JPA @Version also protects two transactions that both read the same version before either writes. Use both checks; do not automatically retry a rejected move against a different board. Select application exception/result shapes during implementation; this proposal assigns no HTTP statuses or new API codes.

Do not wrap every IllegalArgumentException from parsing, generation and application as an illegal user move. Invalid stored state or broken configuration must remain distinguishable from an ordinary rejected proposal. The existing facade propagates these failures; any translation needs a deliberately narrow boundary.

The service may use a separate application result containing the current FEN/version without changing the pure rules interface or the existing HTTP response prematurely. A version token for external callers becomes part of the later HTTP contract.

## Guided implementation increments, if selected

1. **Define the service contract and mutation:** settle failure precedence, result/version shape and an explicit Game FEN mutation; add focused service tests for access, ownership and accepted/rejected proposals.
2. **Verify PostgreSQL persistence:** ordinary White/Black moves, selected promotion/castling/en passant paths, and unchanged state after rejection. Reload after flush/clear. Reuse pure rules coverage rather than duplicating every geometry matrix.
3. **Verify competing transactions:** two legal proposals against the same version allow exactly one commit; an old expected version is rejected even without overlapping transactions. Use independent transactions and deterministic coordination rather than timing sleeps. Preserve existing creation/read/reservation regressions.

Select complete FEN fixtures and compare canonical FEN or board contents rather than Board identity. Label deliberately invalid defensive fixtures. Keep explanations and selected BoardTestRenderer output focused on the behavior under test.

For the following lifecycle milestone, evaluate the resulting position, map CHECKMATE/STALEMATE into an explicitly chosen persisted result model, and atomically update FEN/status/result and delete reservations scoped to that game ID. A failure must roll back the whole transition. Existing applied migrations stay unchanged; V4's status constraint requires a new migration. NORMAL and CHECK continue as nonterminal states, and NORMAL says nothing about other draw rules.

History, repetition, move-count draws, dead-position detection, clocks, resignation, agreement, AI and Board storage changes remain outside this proposal. Cross-feature calls remain service calls. Spring/JPA/HTTP dependencies stay outside the rules subsystem; secrets and runtime configuration remain user-managed.

## Local verification commands — not executed

From `backend/`, rules and configuration require no PostgreSQL:

```bash
./gradlew test --tests 'com.chessapp.game.rules.*' --tests 'com.chessapp.game.config.GameRulesConfigTest'
```

Existing game creation requires the configured PostgreSQL test database; its test class already selects the test profile:

```bash
./gradlew test --tests 'com.chessapp.game.GameCreationIntegrationTest'
```

No future test class is claimed to exist or pass. Add the implementation-specific command when the selected milestone creates those tests. Continue guided work without routine test-pass confirmation pauses, while recording any subsequently supplied results accurately.
