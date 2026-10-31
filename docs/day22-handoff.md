# After Day 22 — next working session

Continue from actual user chess-web-app-day22-end.zip plus the Day 22 documentation replacements and any subsequently applied inline changes. No assistant-generated implementation archive replaces the user source.

## Working agreement

User implements coherent inline increments. Keep modular monolith/conventional layers, Player naming, cross-feature services/events, pure Java rules, useful comments, separate Angular HTML/CSS, minimal frontend tests and user-managed configuration. Do not run tests/builds without explicit request. Treat tests as green unless a failure is reported. Documentation replacements are welcome.

## Reviewed state

Source-reviewed on 31 October 2026 against the attached user archive, compared with the Day 21 checkpoint. No blocking defect found in inspected changes. The user reports all tests green after adding the two HTTP tests noted below. No assistant tests/builds/migrations/browser checks were run.

V9 supports resignation with opponent win; V10 adds one nullable participant draw sender on active games and DRAW_AGREEMENT. Either participant can offer/resign regardless of turn. Recipient accepts/declines; sender withdraws. Any accepted move and every finish clears an offer; rejected moves preserve it. Negotiation does not pause play and has no timeout/throttle/minimum move rule.

GameService owns the five JWT-derived versioned lifecycle commands. loadActiveGameForCommand checks existence, membership, lifecycle and version; offer presence/role follows. persistLifecycleChange flushes the versioned game, requires two reservation releases if finished and publishes GAME_UPDATED. Public methods own transactions; private helpers do not commit. Failed completion/release rolls back all state and suppresses hints.

GameResponse adds drawOfferedByPlayerId. GameMoveResult stays unchanged. Lifecycle mutations advance version without changing FEN/count/baseline or inserting move rows. History version must catch up even when the list is unchanged. Day 21 SAN/bounded-history and Day 20 security/expiry/recovery behavior remain.

Angular executeGameCommand shares one POST lock across moves and lifecycle actions, cancels either safety GET stage, ignores replaced-snapshot callbacks and queues reconciliation on success. Conflicts/uncertain outcomes reconcile without replay. General command permission is turn-independent; move permission adds turn. Promotion must resolve before lifecycle actions. Resign/accept use inline confirmation; route changes, blocking synchronization and accepted newer snapshots clear it. Same-version nonblocking reads preserve it.

Home still requires Open game; closing/disconnecting never finishes a game or releases reservations. Initial failed game GET without a snapshot still has no timer retry. Finished games recover missing/outdated/failed history while visible.

## Read first

README.md, frontend/README.md, docs/architecture.md, docs/development-progress.md, docs/roadmap.md, docs/api-contracts.md, docs/game-lifecycle-flow.md, docs/frontend-gameplay-flow.md, docs/realtime-updates-flow.md, docs/move-history-flow.md and docs/day22-lifecycle-review.md. For pure rules consult movement-pipeline-architecture.md. Older checkpoint documents retain historical scope.

## Coverage and cleanup

Actual source includes resignation/draw persistence, HTTP workflow/error mapping, rollback/release failure, resignation after-commit delivery and controlled move/resignation plus move/acceptance races. The inspected upload lacks drawCommandsRequireAuthentication and drawCommandsValidateVersionAndPreserveFailurePrecedence, but the user subsequently reports both added and all tests green. Inspect their post-upload source at the next review; do not ask the user to add them again. No new frontend coordinator suite, full socket suite, WebSocketPropertiesTest or actual old-database upgrade fixture is present. Do not claim independent test execution.

APPLY-DOCS.md is a stale instruction sheet from the prior package and may be removed. This documentation package does not remove it or change production. Optional unused-import/comment/format cleanup is listed in the review.

## Next milestone

Select before implementing in a new chat. A focused backend reliability milestone is a reasonable candidate, covering configuration binding, real socket security/delivery or prior-database upgrades. Replay/PGN, ratings/statistics, clocks, extra rule-based draws, acceptance and abandonment remain separate choices. No next feature is authorized by this handoff.
