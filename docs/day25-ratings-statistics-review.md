# Day 25 — ratings/statistics source review

## Evidence and scope

Reviewed on 2 November 2026 against actual user chess-web-app-day25-end.zip, compared by file bytes with actual Day 24 source including its documentation replacements. Eighteen files are added, seventeen changed and none removed: thirty-four source/test paths plus roadmap.md. No blocking issue was found in inspected changes. The user's implementation archive remains the source baseline; this documentation package contains no replacement application source.

The user reports the test suite green after correcting Move fixtures and foreign-key cleanup. Later increments follow the standing green-unless-reported agreement. No assistant tests, builds, migrations, database commands or browser checks ran. Archive/diff/document inspection is not independent application verification. No manual statistics-card acceptance is established by this review. Retain Day 24's unverified replay route-switch scenario without requesting it again as a blocker.

## Inspected changes

| Area | Source evidence |
| --- | --- |
| Pure rating policy | EloCalculator, RatingOutcome, RatingChange in player.rating |
| Persistence | Separate versioned PlayerStatistics; V11 statistics and processing/exclusion tables |
| Registration | Transactional PlayerService.createPlayer; parent flush then statistics saveAndFlush |
| Completion | GameFinishedEvent and two GameService publication sites after writes/release |
| Consumer | player.event.PlayerStatisticsGameFinishedEventListener, synchronous @EventListener |
| Processing | MANDATORY service; consolidated PlayerStatisticsRepository; boolean claim wrapper; ordered pessimistic locks |
| Read API | JWT-derived /players/me/statistics, DTO, ordinary read and no-store |
| Frontend | Typed API/model, Home statistics component with separate HTML/CSS and final tap pipeline |
| Tests | Four new statistics/rating suites, V10-to-V11 fixture and dependency-aware existing cleanup |

Rules, existing entities other than the new statistics entity, V1–V10, configuration, dependencies and socket implementation are unchanged. GameResponse/GameMoveResult/PlayerResponse/PlayerUpdate contracts remain unchanged. No separate processed-game repository exists. The listener's actual package is player.event, not the alternative player.listener discussed in conversation.

## Inspected behavior

Elo starts at 1200, fixed K=32 and scale=400; actual scores are 1/0.5/0. One Math.round delta is applied to White and its opposite to Black, preserving pair total. Pure rating code has no Spring/game/persistence dependencies. There is no rating floor, provisional algorithm or rating-mode split. Exact arithmetic detects integer/counter overflow. gamesPlayed is derived from wins+draws+losses.

V11 initializes existing Players with zero counts and initial rating. Already finished games get statistics_applied=false markers; active games are unmarked and count on later completion. Registration creates statistics in its enclosing credentials transaction. Scalar foreign keys preserve database integrity without ORM relationships/cascades. Missing statistics for an existing Player are internal invariant failures, not an invitation to recreate data silently.

Both terminal moves and lifecycle finishes publish GameFinishedEvent only after optimistic game/history writes and exact reservation release. The regular synchronous listener maps GameResult and calls the MANDATORY statistics service. Its native claim inserts statistics_applied=true in the same transaction, returning false for duplicates or historical exclusion. The service locks lower Player ID first, then higher, maps colors, calculates from old ratings and flushes both changes. Runtime failures propagate; a later outer failure also undoes successful processing. GameChangedEvent follows; its notification listener remains best-effort AFTER_COMMIT. No asynchronous dispatcher was introduced.

The claim record is not an audit ledger, outbox or recovery queue. The service trusts the internal event's game IDs/participants/result instead of querying another feature's repository. The primary key prevents duplicate counting under that trusted contract; it does not independently prove event authenticity or a historical rating sequence.

Self-statistics comes from JWT identity and ordinary findById, without writer locks or email/version exposure. Deleted account returns 401; response has no-store. Home requests on creation, manual refresh, connection and any private update hint. switchMap cancels older reads; tap guards token/Player identity; inner catchError returns EMPTY so future triggers survive. finalize handles cancellation too; downstream takeUntilDestroyed cleans the active request. No statistics polling/focus retry, frontend test suite, public leaderboard or rating-history API is added.

## Test evidence and limits

Counts below are inspected source methods/parameter cases, not assistant execution results.

| Source | Methods | Invocations/cases | Focus |
| --- | ---: | ---: | --- |
| EloCalculatorTest | 5 | 9 | Equal/unequal ratings, reversed fixture, total preservation, null outcome |
| PlayerStatisticsTest | 5 | 8 | Initial state, counters, accumulation, null rejection, nonpositive IDs |
| PlayerStatisticsIntegrationTest | 9 | 14 | Registration, three lifecycle results, four terminal positions, duplicates, failures and outer rollback |
| PlayerStatisticsHttpIntegrationTest | 4 | 4 | DTO/no-store, both identities after resignation, missing auth and deleted account |
| GameMigrationIntegrationTest | 1 added | 1 added | Actual isolated V10-to-V11 schema upgrade |

The concurrent test seeds a finished-but-unprocessed row with SQL and gates two event submissions in independent transactions. It checks one marker and one applied result, without forcing/observing a specific database wait schedule. It does not test two distinct games concurrently changing one Player, nor independently prove every deadlock pattern is excluded. Service source orders locks correctly. Existing one-active-game reservations further constrain normal overlap.

Failure tests inject counter overflow and inspect game/version/history/count, statistics, marker and reservations after rollback. The outer rollback fixture additionally proves successfully flushed statistics are not an independent commit. Nonterminal move/offer/decline/withdraw leave statistics unchanged. HTTP fixtures intentionally roll back; they establish read/transport behavior, not after-commit hints. Migration tests establish SQL backfill/exclusion and unchanged games/reservations, not application event consumption against that isolated schema. No dedicated historical-false-marker event replay or no-transaction MANDATORY test was added. Existing registration-failure tests remain unchanged; no new statistics-specific rollback assertion exists there.

Move fixtures now use the two-argument constructor and Square.fromAlgebraic, avoiding null Optional promotion. Existing cleanup changes remove markers/moves before games and statistics before Players. The emergency cleanup in AuthRegistrationRollbackIntegrationTest remains an optional future hardening point if a transaction regression leaves new records committed. No normal-path failure is inferred from it.

## Deliberate limits

Ratings count all supported finishes, including zero-move resignation/agreement. No minimum-move or abuse policy exists. No backfill of historical results, rating adjustment audit, admin repair, leaderboard, rated/unrated distinction or rating-aware pairing is implemented. Do not replay historical completion events to reconstruct Elo as if processing order were irrelevant. An asynchronous redesign would need durable work, idempotency, ordering and recovery policy; discussion of banking scenarios did not authorize it.

PGN download UI, promotion legality gating and automatic Next-to-live remain deferred. Access-token duration/refresh-token/session persistence is now tracked but unchanged. Browser reload still requires login. Earlier socket, replay and migration limits remain where not superseded.

## Documentation package

Replaces ten current documents, adds this review, player-statistics-flow.md and day25-handoff.md, and archives the ten uploaded originals in history/main-documents-through-day24.md. Historical Day reviews/handoffs remain untouched. Extract at the project root. No production code, tests, migrations, configuration or APPLY-DOCS.md is included.

Suggested documentation commit:

```text
docs: record Day 25 transactional ratings and statistics checkpoint
```
