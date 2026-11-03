# After Day 26 — next working session

Continue from actual user chess-web-app-day26-end.zip plus the Day 26 documentation replacements and subsequent inline changes. Never substitute an assistant-generated implementation archive.

## Working agreement

User implements coherent inline increments. Preserve modular monolith/conventional feature layers, Player naming, cross-feature services/events, pure Java rules, useful comments, separate Angular HTML/CSS, minimal frontend tests and user-managed configuration. Do not run tests/builds without explicit request. Treat tests as green unless a failure is reported. Include a short what-to-understand note and identify important tests for deep reading. Documentation replacements are welcome. New major milestones start in a new chat.

## Evidence

Source-reviewed on 3 November 2026 against actual user Day 26 archive, compared with actual Day 25 archive including reviewed documentation. Twenty-one added files, twenty-two changed, none removed; forty-three source/test/config paths. No production blocker found in inspected changes. Shared test scanner configuration follow-up below remains unapplied. User reports green after fixture/order/cleanup fixes; later increments follow standing agreement. No assistant tests/builds/migrations/database/browser runs. User says frontend looks good, without separately established scenario-by-scenario acceptance. Historical Day 24 route-switch manual verification is still not established.

## Implemented

Pure game.clock calculator/state; V12 nullable balances/running color/turn timestamp and shape constraint; V13 TIMEOUT winner constraint. Old games remain untimed. New games initialize White with YAML initial-time=10m, without increment. Clock starts before creation commit/opening; no acceptance or first-move grace. Configuration permits whole milliseconds 1ms..24h. Server acceptance timestamps use microseconds; arithmetic deducts whole elapsed milliseconds. Exact zero expires. Backward timestamps clamp without adding time; no strong wall-clock-jump guarantee.

Game.startClock/runningClockState/advanceClockAfterAcceptedMove and finish freezing are implemented. finish freezes before setting FINISHED. Access/lifecycle/version precede expiry in submitMove; running color must match FEN side. First guard precedes rules; captureCommandTimeAndCheckExpiry supplies playedAt after assessment/FEN serialization, before SAN and mutation. Negotiation does not reset clocks. Expired commands return 409 GAME_CLOCK_EXPIRED without completion; frontend reconciles without replaying commands.

Public transactional GameService.expireGame reloads/rechecks, skips untimed/finished/unexpired games, freezes at computed deadline, awards opponent win and uses normal optimistic flush/reservation/statistics/events. Timeout records no move. Full impossible-mate timeout draw adjudication is deferred; current policy always awards opponent win. GameFinishedEvent synchronously applies ratings/marker in the same transaction; AFTER_COMMIT notifications remain best-effort. A later outer failure still rolls back completion.

GameTimeoutScanner actual method is scanAndExpireGames(). Enabled by default, 1s fixed-delay default, hardcoded batch 100. Native candidate query orders deadline/ID without retained locks; each completion service call has its own transaction. Stale/missing candidates are skipped; other runtime service failures are logged and batch continues. Candidate-query failure is outside per-game catches. No dedicated deadline index/backoff/quarantine/lease/outbox. Repeated earliest failures can fill a batch and starve later work. No strict one-second SLA.

Both response DTOs contain nullable clock with projected remaining times, runningColor and calculatedAt. Do not revert to capturedAt/asOf. Response mapping accepts explicit Clock-derived timestamp, including GameExportService. Same-version reads can yield newer remaining times without a game write. Frozen-state equality ignores clock.calculatedAt, not persisted finishedAt or balances.

Angular GameClocks has separate TS/HTML/CSS and a 100ms display interval. It anchors received balances to performance.now, subtracts elapsed only for runningColor, rounds seconds upward, clamps zero and shows waiting for server result. calculatedAt is metadata, not used to estimate network age. Display may lag by delivery time and adjust on reconciliation. Local zero neither declares a result nor independently disables commands. Parent clockSnapshot refreshes at equal versions without replacing board identity, preserving selection/promotion for nonblocking reads. POSTs map clocks. Route initialization clears clockSnapshot; 403/404 clears game/hides clock but does not explicitly clear the separate clock signal. Replay retains historical board while clocks/status are live; Latest is still required. Revised pseudocode is in component pseudocode.txt and docs/game-clocks-flow.md.

## Configuration follow-up

IMPORTANT: uploaded backend/src/test/resources/application-test.yml has timeout-scan-enabled=true, as does production. Only GameTimeoutIntegrationTest explicitly overrides false. Recommend setting the shared test profile to false to avoid background interference; no configuration change was made in the docs package. Do not claim this follow-up was applied. Production remains enabled=true/10m/1s.

## Tests and fixes

Seven new suites: 33 methods/39 cases. Existing suites add five service-unit, two HTTP and two migration methods. Counts are source inspection, not assistant execution results. Integration coverage includes both-color committed timeout, duplicate skip, exact-deadline candidate query/stale candidate recheck, expired-move rejection, rating-overflow rollback/retry and controlled move-versus-timeout version race. Race preloads same version and uses per-thread test times; either update may win, without request-arrival priority or proof of all schedules. Candidate test rewinds test time only for fixture construction. Persistence flush-clear-reload tests roll back. SQL migrations test V11-to-V12 preservation/shape and V12-to-V13 outcome support. Scanner mocks invoke method directly; no scheduled-trigger, process-restart or real-socket timeout integration test. HTTP tests cover switch/freeze/null clocks, not a dedicated expired-command HTTP case. Frontend tests remain minimal/unchanged.

Synthetic FEN/legacy SQL and finished-unprocessed event fixtures clear four clock fields. Post-completion move failure was fixed by placing expiry after lifecycle/version. Bodyless STOMP ERROR rejection remains required; rejectionObserved skips graceful disconnect on rejected sockets while still stopping clients. This addresses the observed cleanup race, not all socket failures. The earlier optional emergency auth-rollback cleanup hardening remains deferred.

Deep-read ratingFailureRollsBackTimeoutAndAllowsSuccessfulRetry, moveAndTimeoutCannotBothCommitFromTheSameVersion and clockResponsesSwitchAfterMoveAndFreezeAfterResignation.

## Read first and remaining scope

README.md, docs/day26-clocks-review.md, docs/game-clocks-flow.md, docs/development-progress.md, docs/roadmap.md and docs/architecture.md. Consult API/frontend/lifecycle/realtime/testing companions for selected work. Current checkpoints supersede historical pending-clock claims.

Day 26 is complete in inspected scope with documented limits and test-profile follow-up. Select the next major milestone in a new chat. Session continuity, extra draws, acceptance/abandonment and portfolio preparation are options, not authorization. Replay square-click return, stronger banner/Return-to-live UI, sounds and new-live-move indicators are explicitly deferred until after MVP. Existing Latest behavior and live-clock note remain.

PGN Angular download, promotion chooser legality gating and automatic Next-to-live remain deferred. Session duration/refresh tokens/login persistence unchanged; in-memory auth is separate from durable games/history/statistics/clocks. Logout/disconnect does not itself finish a game, but timed games keep counting and may finish later by TIMEOUT. No ledger, leaderboard, rating-aware matching, async ratings, outbox, distributed routing or new draw policy is authorized.
