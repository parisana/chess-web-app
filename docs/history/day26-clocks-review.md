# Day 26 — clocks and timeout source review

## Evidence and checkpoint

Reviewed on 3 November 2026 against actual user `chess-web-app-day26-end.zip`, compared byte-for-byte with actual `chess-web-app-day25-end(1).zip`, which contains the Day 25 documentation replacements. Twenty-one files added, twenty-two changed, none removed. Forty-three source/test/configuration paths changed; uploaded main documentation was still at the preceding checkpoint. The user's archive remains the source baseline.

No production blocker was found in inspected changes. The shared test profile still enables the timeout scanner; disabling it is a recommended configuration follow-up, not an applied change. User reported green tests after fixture/order/cleanup fixes; later increments follow the standing green-unless-reported agreement. No assistant tests, builds, migrations, database calls or browser checks ran. User says the frontend looks good; no scenario-by-scenario manual acceptance or restart recovery verification is established by this review.

## Inspected implementation

| Area | Source evidence |
| --- | --- |
| Pure arithmetic | game.clock.GameClockState / GameClockCalculator; whole-millisecond remaining time, exact-zero expiry, immutable turn transitions |
| Persistence | Game nullable balances/running color/start timestamp; V12 shape constraint; V13 winning TIMEOUT completion |
| Configuration | GameClockProperties initialTime; GameClockConfig enables binding/scheduling |
| Creation | New standard games start White's clock before save/flush; old games remain untimed |
| Commands | Access/lifecycle/version before expiry; second move check at acceptance timestamp; timing remains outside pure chess rules |
| Completion | finish freezes before setting FINISHED; expireGame rechecks and reuses normal flush/release/statistics/event pipeline |
| Selection/scan | Repository selects up to 100 candidates by deadline and ID; actual method is scanAndExpireGames(), not scan() |
| API | Nullable clock on GameResponse and GameMoveResult; projected balances, runningColor and calculatedAt |
| Frontend | Separate GameClocks TS/HTML/CSS; independent clockSnapshot signal; same-version refresh; 100ms display tick |
| Explanation | User's frontend components/game-clocks/pseudocode.txt is present; expanded companion below |

Production YAML sets initial-time=10m, timeout-scan-enabled=true and timeout-scan-interval=1s. The scanner defaults to enabled and 1s if those two settings are absent. Initial time is required, limited to whole milliseconds from 1ms through 24h. Batch size is a code constant, not YAML. Scanner scheduling interval does not have the same constructor-bound range validation as initial time.

The shared `backend/src/test/resources/application-test.yml` also has enabled=true. Only GameTimeoutIntegrationTest overrides this to false explicitly. Recommend setting the shared test profile to false for isolation. The documentation package contains no configuration edits.

## Behavioral policy

White starts when the new game is initialized, before transaction commit and before a Player opens the board. There is no acceptance or first-move grace stage. No increment, pause, lag compensation or selectable time-control queue is added. Time passes through draw negotiation, logout, disconnect, replay and application downtime. A running application's next scan can detect overdue persisted games after restart; no restart test was added. A long creation transaction consumes White's time under the current timestamp policy.

Balances are captured at turn boundaries. The running balance at time t is max(0, stored minus elapsed whole milliseconds). Opponent time is unchanged. Exactly zero is expired. Backward timestamps do not add balance or rewind the next turn boundary; this does not make wall-clock adjustments harmless. Flooring elapsed durations can forgive a fractional millisecond at a turn transition. No nanosecond timing guarantee is made.

For well-formed moves, existence/access/lifecycle/version checks precede the clock read. A first check rejects already expired games before rules work; a second check captures playedAt after application/assessment/FEN serialization and before SAN generation/entity mutation. That captured instant is the acceptance boundary, not HTTP arrival or commit time. Clock transition, move history and any terminal completion use it. Negotiation does not reset the turn timestamp. Terminal lifecycle commands recheck before finish; nonterminal changes recheck before flush.

An expired command returns 409 GAME_CLOCK_EXPIRED without completing the game. Scanner completion runs in a separate public service transaction. It reloads/rechecks eligibility, awards the opponent a win, records finishedAt at the computed deadline and freezes the expired balance at zero. Timeout inserts no move record. Current policy does not implement the possible-mating-sequence draw exception; it is not complete tournament timeout adjudication.

Game flush arbitrates optimistic conflicts before reservation release. Completion, frozen balances, exactly two reservation deletions, the processing claim and both ratings share the transaction. Statistics failure restores active state and clock values; subsequent processing can retry. AFTER_COMMIT notifications remain best-effort. Concurrent pre-expiry move/expiry processing has no privileged request-arrival ordering: one versioned update wins.

## API and UI details

`clock` is null for untimed games. Otherwise whiteRemainingMillis/blackRemainingMillis are projected for calculatedAt, runningColor is WHITE/BLACK for active games and null for finished games. A read may show zero with IN_PROGRESS while scanner completion is pending. Reading clocks does not write the row or advance version. calculatedAt is response metadata, distinct from persisted finishedAt/turn-start time; frozen-state comparisons ignore only clock.calculatedAt.

Same-version GETs can carry newer remaining times. Angular accepts their clock data while retaining board snapshot identity, which protects selection/promotion during nonblocking reads. Older versions do not replace the clock. POST mappers also replace clock data. Route initialization clears clockSnapshot. On a 403/404 the board/game is cleared and clocks are hidden; the separate clock signal is not explicitly cleared in that error branch, and the next route reset clears it.

GameClocks anchors received server-calculated remaining times to performance.now(), subtracts local elapsed time only for runningColor, rounds display seconds upward and cleans its interval on destruction. It does not use calculatedAt to estimate network age. Response latency and background/sleep timer behavior can affect display; focus/reconnect/visible-page safety reads provide reconciliation. Same-version reanchoring can cause a small correction. Local zero displays waiting for the server result and does not determine a winner or independently disable commands. Existing backend checks remain authoritative.

Replay keeps the chosen historical board while clocks and live status continue updating. Latest is still required to return to play. Square-click return, stronger replay banners, move sounds and new-live-move indicators are deferred until after MVP. The existing live-clock replay note is implemented; the newly discussed replay polish is not.

## Test evidence and limits

Counts are inspected source, not assistant-run results.

| New suite | Methods | Invocations/cases |
| --- | ---: | ---: |
| GameClockCalculatorTest | 8 | 8 |
| GameClockEntityTest | 7 | 7 |
| GameClockPropertiesTest | 4 | 9 |
| GameClockResponseTest | 3 | 3 |
| GameClockPersistenceIntegrationTest | 3 | 3 |
| GameTimeoutIntegrationTest | 5 | 6 |
| GameTimeoutScannerTest | 3 | 3 |
| Total | 33 | 39 |

Existing suites add five clock/timeout service-unit methods, two HTTP methods and two migration methods: nine additional methods/cases. Other changes adapt DTO fixtures, exact comparison metadata, synthetic legacy/finished fixtures and socket cleanup. No new frontend test suite is introduced.

The timeout integration suite uses real commits and a mocked Clock, with explicit per-thread times for the race. Both racing transactions preload the original game/version before release. One move or timeout must commit; the test does not prove every possible database schedule or give early-arriving commands priority. The candidate-recheck fixture deliberately rewinds test time to model an old selection followed by changed state; it is not a production wall-clock recommendation. Rating overflow rollback/retry verifies clock, outcome, marker, ratings and reservations.

Persistence tests flush/clear/reload inside rollback-managed transactions: mapping evidence, not commit or restart evidence. V11-to-V12 verifies old rows/reservations and clock constraints; V12-to-V13 verifies winning TIMEOUT support and rejection of DRAW. These isolated-schema SQL tests do not run application services against intermediate schemas. Scanner tests invoke scanAndExpireGames directly with mocks; they do not exercise the scheduled trigger. HTTP tests verify clock serialization, switching, frozen reads and untimed null clocks, not a dedicated expired-command HTTP or scheduled-timeout socket workflow.

Deep-read ratingFailureRollsBackTimeoutAndAllowsSuccessfulRetry, moveAndTimeoutCannotBothCommitFromTheSameVersion, clockResponsesSwitchAfterMoveAndFreezeAfterResignation and upgradesV11GamesWithoutStartingClocksAndEnforcesClockShape.

## Fixture and review findings

Direct custom-FEN/legacy SQL now clears all four clock fields, including legacy tests that bypass seedFen. The finished-but-unprocessed statistics fixture also clears them before concurrent event delivery. This avoids fabricating timed states inconsistent with the synthetic board/lifecycle. Command ordering is source-confirmed corrected; runningClockState is not read before access/lifecycle/version guards.

WebSocket rejection tests still require a STOMP ERROR. After observed rejection they skip graceful disconnect while still stopping clients. This addresses the identified closed-session cleanup race, not every possible transport failure. No claim is made that historical socket failures were caused by database residue. AuthRegistrationRollbackIntegrationTest emergency cleanup remains the earlier optional hardening point.

The 100-row scan batch bounds attempted completions, not the cost of candidate selection. There is no dedicated deadline index, scheduler lease, durable job queue or multi-instance routing design. Repeatedly failing earliest candidates remain eligible; enough failures to fill the batch can starve later games. A slow/failed scan or lock wait can delay visible completion beyond 1s. No strict one-second SLA, periodic clock broadcast or per-game scheduled task exists.

## Package

Twelve current documents are replaced; this review, game-clocks-flow.md and day26-handoff.md are added. The twelve uploaded originals are archived in history/main-documents-through-day25.md. Historical Day reviews and handoffs are untouched. No production code, test, migration, configuration or APPLY-DOCS.md is included. Extract at project root.

Suggested documentation commit:

```text
docs: record Day 26 clocks and timeout checkpoint
```
