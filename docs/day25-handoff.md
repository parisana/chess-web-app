# After Day 25 — next working session

Continue from actual user chess-web-app-day25-end.zip plus Day 25 documentation replacements and subsequent inline changes. Never replace the baseline with an assistant-generated implementation archive.

## Working agreement

User implements coherent inline increments. Preserve modular monolith/conventional feature layers, Player naming, cross-feature services/events, pure Java rules, useful comments, separate Angular HTML/CSS, minimal frontend tests and user-managed configuration. Do not run tests/builds without explicit request. Treat tests as green unless a failure is reported. Include a brief what-to-understand note and identify important tests for deep reading. Documentation replacements are welcome. New major milestones start in a new chat.

## Reviewed checkpoint

Source-reviewed on 2 November 2026 against the actual Day 25 ZIP, compared with Day 24 including documentation replacements. Eighteen files added, seventeen changed, none removed; thirty-four source/test paths plus roadmap.md. No blocking issue found in inspected changes. User reports suite green after fixture fixes; later increments follow the standing agreement. No assistant tests/builds/migrations/database/browser checks ran. Manual statistics-card acceptance is not established. Day 24 replay route-switch reset remains source-confirmed but not manually verified.

## Implemented Day 25

V11 adds player_statistics (scalar Player-ID PK/FK, rating, wins/draws/losses, optimistic version, timestamps) and player_statistics_processed_games (game-ID PK/FK, statistics_applied, timestamp). Existing Players start at 1200/zero. Already FINISHED games are marked false/excluded; active games count on later completion. New registration creates statistics inside the enclosing credentials transaction. Missing statistics are invariant failures, not silently recreated.

EloCalculator is pure Java in player.rating: initial 1200, K=32, scale 400; scores 1/0.5/0; one rounded delta applied oppositely. No floor, rating mode split, provisional policy or audit ledger. gamesPlayed is derived. All supported completion reasons count, including zero-move resignation/agreement.

GameService publishes GameFinishedEvent after game/history flush and reservation release at both terminal-move and lifecycle paths. Listener is named PlayerStatisticsGameFinishedEventListener in player.event (not player.listener). It uses ordinary synchronous @EventListener and maps to RatingOutcome. PlayerStatisticsService.recordFinishedGame requires MANDATORY and claims/locks/calculates/flushes in the same transaction. No async dispatcher is introduced. Any failure or later outer failure rolls back game/history/statistics/claim/reservation release. GameChangedEvent follows; existing AFTER_COMMIT notification behavior remains best-effort.

Only PlayerStatisticsRepository is used. Native int insertFinishedGameClaim has a default boolean claimFinishedGame wrapper. Unique game ID suppresses duplicate/excluded processing. Lock lower Player ID then higher with PESSIMISTIC_WRITE, map colors afterward, calculate from both old ratings before mutation. Service trusts internal event details; the marker is not an audit ledger/outbox or arbitrary-event validator.

GET /api/v1/players/me/statistics derives identity from JWT, uses ordinary read without writer lock, returns playerId/rating/wins/draws/losses/gamesPlayed with no-store. Deleted account is 401; missing statistics for existing account is internal failure. PlayerResponse/JWT/game/notification DTOs remain unchanged.

Home embeds a separate TS/HTML/CSS statistics component. merge(manual Subject, connections$, updates$) plus startWith triggers reads; switchMap cancels older GETs. Final code uses inner tap with captured token/Player guards, inner catchError returning EMPTY, finalize and downstream takeUntilDestroyed. No nested subscribe or dependent rating-history request exists. Error feedback preserves older values. No card polling/focus retry or new frontend tests. Missed hints recover through manual refresh, connection or reopening Home.

## Test evidence and limits

EloCalculatorTest: 5 methods/9 cases. PlayerStatisticsTest: 5/8. PlayerStatisticsIntegrationTest: 9/14. PlayerStatisticsHttpIntegrationTest: 4/4. GameMigrationIntegrationTest adds one V10-to-V11 fixture and retains older V10-target cases. These are source counts, not assistant test results.

Statistics integration tests cover actual lifecycle commits for three results, terminal mate/stalemate for both colors, sequential duplicate event, concurrent submissions, counter-overflow rollback including move/history, successful retry, nonterminal negotiation unchanged, and outer rollback after successful rating flush. Concurrent fixture uses SQL to create finished-but-unprocessed state; start gate does not force a specific DB lock schedule or test distinct overlapping games. HTTP rollback fixtures assert reads, not commit delivery. SQL migration fixture verifies backfill, historical exclusions and unchanged games/reservations, not service execution against that isolated schema. No dedicated historical-false-marker event replay, MANDATORY-without-transaction or registration-failure statistics assertion was added.

User encountered null promotion in generated Move test constructors and foreign-key fixture failures; uploaded source uses two-argument Move/Square.fromAlgebraic and dependency-aware cleanup. Remove processed markers and game_moves before games, statistics before Players; explicit JPA child flush precedes parent removal. Failed transactional cleanup can leave committed fixtures. Do not claim all earlier WebSocket failures were proven caused by residue. AuthRegistrationRollbackIntegrationTest's emergency cleanup still lacks statistics-first handling if a future regression leaves a Player committed; optional hardening, not a normal-path blocker.

## Retained scope and deferrals

Day 24 replay and backend PGN export remain implemented; Angular PGN button is deferred. Next-to-live automatic behavior and promotion chooser legality gating are intentionally unimplemented. Premature chooser is a UI limitation, not evidence backend accepts self-check. In-memory authentication is separate from durable PostgreSQL games/history/statistics. Session-duration/refresh-token/session persistence improvement is now explicitly deferred and no config changed. Logout/disconnect never completes games. No clocks, additional draw rules, acceptance/abandonment, leaderboard, rating-aware matchmaking, async ratings, ledger or outbox is authorized by discussions.

## Read first

README.md, docs/day25-ratings-statistics-review.md, docs/player-statistics-flow.md, docs/development-progress.md, docs/roadmap.md and docs/architecture.md. Consult API/frontend/lifecycle/realtime/testing companions for the selected work. Day 24 and Day 23 review documents retain historical evidence; current checkpoints supersede old pending-feature claims. GameMigrationIntegrationTest.java filename is correct; no rename remains.

## Next milestone

Day 25 is complete in reviewed scope with documented limits. Choose the next major milestone before implementation, in a new chat. Clocks, extra rule-based draws, acceptance/abandonment, session continuity or portfolio preparation are options, not authorized additions. Do not silently expand ratings with a ledger, leaderboard or rating-aware matchmaking.
