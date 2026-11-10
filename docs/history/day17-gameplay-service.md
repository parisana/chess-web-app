# Day 17 — persisted gameplay service and source review

Reviewed on 28 October 2026 against the user's actual `chess-web-app-day17-end(1).zip`, compared with `chess-web-app-day16-end.zip`. No blocking defect was found in the reviewed Day 17 production/test changes. This review did not execute tests, builds or migrations. Tests are treated as green under the user's standing instruction to report failures; no detailed per-command log or separate frontend build result was supplied.

## Selected milestone and final organization

The selected larger milestone combines service move application, concurrency, terminal lifecycle and active-reservation release. The user implemented it through inline guided increments. The final source has one GameService for creation, participant-protected reads and move commands; there is no separate production GameMoveService.

GameMoveResult.from(game, assessment) performs mechanical mapping inside the record. Game.finish() calls its extracted switch-based validateResult() after null checks and before changing completion fields. These choices fit the conventional feature layers; no DDD model, extra mapper layer or per-use-case service interface is introduced.

## Compared source changes

Compared with Day 16, the changed production files are Game, GameStatus, GameService and ActiveGamePlayerRepository. Added production files are GameMoveResult, GameResult, GameTerminationReason and four command exceptions. V6 adds completion columns and constraints. New tests are GameTest, GameMoveServiceTest and GameMoveIntegrationTest.

The comparison confirms that all pure rules implementations, the ChessRules interface, GameRulesConfig, existing backend tests, V1–V5, build/runtime configuration and frontend implementation are unchanged. Existing creation/read behavior is extended through the mutable persisted state, without changing GameController or the GameResponse field list. This documentation follow-up changes no production/test source.

## Internal command contract

```java
GameMoveResult submitMove(
        Long gameId,
        Long requestingPlayerId,
        long expectedVersion,
        Move move
)
```

This method is transactional on GameService. A future HTTP caller must derive the requesting player from the authenticated principal. No production operation accepts a client-supplied FEN, color, winner or completion result. Internal callers submit the version they observed; initial games have version zero and successful command results provide the updated version.

Basic null/negative-version checks run first. For well-formed arguments, the service checks missing game, participant membership, lifecycle, expected version, turn ownership and exact legal membership in that order. An outsider cannot use command failures to inspect a game's lifecycle/version/turn.

| Condition | Service behavior |
| --- | --- |
| Game absent | GameNotFoundException |
| Requester not a participant | GameAccessDeniedException |
| Finished game | GameAlreadyFinishedException |
| Expected version is old | StaleGameVersionException |
| Participant does not own the turn | NotPlayersTurnException |
| Proposal absent from the complete legal set | IllegalGameMoveException |
| Parsing, generation, application or assessment fails | Original failure propagates |
| Optimistic flush loses a concurrent update | StaleGameVersionException retaining the original cause |
| Completion releases a count other than two | IllegalStateException; transaction rolls back |

The new command exceptions are not assigned HTTP statuses or codes in this milestone. Existing read exceptions retain their existing mappings.

## Command algorithm

1. Load the managed entity and authorize against the stored White/Black player IDs.
2. Require IN_PROGRESS and the observed version. Parse currentFen; compare its side to move with the requester's derived color.
3. Precheck complete legal membership, including the promotion choice. Distinguish an ordinary rejected proposal without catching all IllegalArgumentExceptions from the rules pipeline.
4. Still apply through ChessRules.applyMove(), then separately evaluate the returned position and serialize it. Finish rules processing before mutating the entity.
5. Call Game.updatePosition(). If assessment is CHECKMATE or STALEMATE, call Game.finish() with a service-derived result, reason and finish time.
6. Flush the managed update through GameRepository. JPA @Version rejects competing writes before reservation release.
7. For FINISHED, delete active reservations by game ID and require exactly two affected rows.
8. Return GameMoveResult.from(game, assessment). Flush is not commit: an enclosing transaction can still roll everything back.

Legal enumeration is repeated by the facade after the service's membership precheck. This is a deliberate correctness/error-classification tradeoff, preserving all existing validation boundaries. There is no direct pseudo-legal application shortcut or validation-skipping optimization.

## Concurrency and lifecycle

The expected-version check rejects old commands even if the same coordinate move becomes legal again after several turns. JPA @Version additionally handles transactions that both read the same current version before writing. Reads do not increment it; managed dirty updates increment it on flush. Direct SQL bypasses that mechanism, which is why test-only FEN seeding deliberately leaves version zero unchanged. Commands are not automatically retried against a newer position.

| Resulting assessment | Persisted status | Result | Termination reason | Active reservations |
| --- | --- | --- | --- | --- |
| NORMAL | IN_PROGRESS | null | null | Retained |
| CHECK | IN_PROGRESS | null | null | Retained |
| CHECKMATE after White moves | FINISHED | WHITE_WIN | CHECKMATE | Both released |
| CHECKMATE after Black moves | FINISHED | BLACK_WIN | CHECKMATE | Both released |
| STALEMATE after either color moves | FINISHED | DRAW | STALEMATE | Both released |

The resulting side to move is the opponent: checkmate awards the mover the win. Game.updatePosition() requires a nonblank FEN and an in-progress lifecycle, without duplicating chess rules. Game.finish() validates its supported field combinations and prevents later entity mutation. GameStatus remains separate from the rules PositionStatus.

V6 adds result, termination_reason and finished_at; updates the status constraint; and requires complete, supported combinations. Explicit nonnull checks avoid SQL CHECK's acceptance of UNKNOWN allowing incomplete records. Existing games remain IN_PROGRESS with null completion fields. Applied V1–V5 remain unchanged.

Finish time uses the existing injected Clock and microsecond precision matching PostgreSQL timestamps. These timestamps do not implement chess clocks.

Both the JPA update and JDBC release participate in the same datasource/transaction manager. Flushing first prevents a losing command from deleting reservations. Release by game ID also prevents an old game's command from deleting a newer game's reservations. A missing reservation or later enclosing failure rolls back FEN/version/status/result/time and the deletion together.

## Source-confirmed test coverage

Paths below are relative to backend/src/test/java/com/chessapp/game/.

| Test source | Inspected assertions |
| --- | --- |
| entity/GameTest.java | Invalid mate/draw and stalemate/winner combinations do not partially change state; completed games reject further updates and completion attempts |
| service/GameMoveServiceTest.java | Nine unit methods testing failure precedence, turn ownership, absent legal proposals, original generation/assessment failure propagation and no release after optimistic flush failure; the injected target is GameService |
| GameMoveIntegrationTest.java | Ordinary White/Black persistence and versions; selected castling/en passant/promotions for both colors; both-color mate/stalemate results; check remains active; stale/access/turn/illegal failures; invalid stored king counts; later-game reservations survive old-game retries; matchmaking status no longer reports matched |
| GameMoveIntegrationTest.java, transaction cases | Rejection after a full knight return sequence; later enclosing rollback after successful release; deliberately missing reservation causing partial-delete rollback; migration constraints reject incomplete/inconsistent completion |
| GameMoveIntegrationTest.java, race cases | Two independent contexts preload version zero, then latches release both commands; ordinary proposals commit exactly one new FEN/version and duplicate terminal commands commit one completion/release |

The integration class has no class-level transaction. Setup commits fixtures, normal service calls commit independently, reloads use fresh persistence contexts, and cleanup removes fixture-owned rows. The rollback/race cases deliberately create explicit transaction boundaries. There are no timing sleeps. These are inspected test-source properties, not an independently reproduced result.

The existing GameCreationIntegrationTest, GameReadIntegrationTest, ActiveGameIntegrationTest and MatchmakingIntegrationTest remain unchanged. Service-level reads of updated FEN/FINISHED are asserted in the new integration class; a new terminal HTTP read test is not claimed. Lower-level rules tests retain the broader promotion/special-move geometry matrix.

The labeled missing-king and missing-reservation inputs are deliberately invalid defensive fixtures. Other sparse FEN fixtures are controlled rules inputs, not proof that historical reachability is validated. Selected terminal inputs use BoardTestRenderer. Board content assertions use FEN rather than identity equality.

Example White mate transition, Qg6–g7:

```text
Before: 7k/8/5KQ1/8/8/8/8/8 w - - 4 20
After:  7k/6Q1/5K2/8/8/8/8/8 b - - 5 20
```

## Local commands — not executed by the assistant

From backend/, no PostgreSQL needed:

```bash
./gradlew test --tests 'com.chessapp.game.entity.GameTest' --tests 'com.chessapp.game.service.GameMoveServiceTest' --tests 'com.chessapp.game.rules.*' --tests 'com.chessapp.game.config.GameRulesConfigTest'
```

The unit class is actually named GameMoveServiceTest, despite testing GameService. Use that source-confirmed name, not the earlier suggested GameServiceMoveTest name.

With the configured PostgreSQL test database:

```bash
./gradlew test --tests 'com.chessapp.game.GameMoveIntegrationTest' --tests 'com.chessapp.game.GameCreationIntegrationTest' --tests 'com.chessapp.game.GameReadIntegrationTest' --tests 'com.chessapp.game.ActiveGameIntegrationTest' --tests 'com.chessapp.matchmaking.MatchmakingIntegrationTest'
```

The integration classes select the test profile. V6 is applied through normal Flyway startup; schema validation remains enabled. Runtime configuration and secrets remain user-managed. Current test status follows the user's standing green-unless-failure-reported instruction; there is no detailed test report, independent execution, or separate frontend build claim.

## Remaining boundary and next decision

Game GET retains id, whitePlayerId, blackPlayerId, status, currentFen and createdAt. It can observe an updated FEN or FINISHED, but does not expose version/result/reason/time. GameMoveResult remains internal. No HTTP move submission, history, frontend gameplay or real-time notification is implemented.

The candidate next larger milestone is HTTP gameplay: authenticated move submission, version/result-bearing reads, request validation, feature error contracts and relevant HTTP tests. Select it separately. Continue with inline solutions that the user implements incrementally; do not replace that workflow with a generated implementation archive. Documentation artifacts may be supplied as requested.

Disconnects presently leave the game and reservations active. Grace periods, persisted deadlines, expiry workers, reconnection handling and any abandonment/forfeit policy are deferred. Abandonment need not mean a draw; choose the policy and extend lifecycle/schema explicitly later.

History, repetition, move-count draws, dead positions, clocks, resignation/agreement, AI and Board storage changes remain outside this checkpoint. NORMAL does not exclude additional draw conditions; FEN alone cannot establish repetition. Preserve the modular monolith, Player terminology, service-level cross-feature calls, pure rules subsystem, Lombok usage without @Data on JPA entities, complete FEN fixtures and minimal frontend tests.
