# After Day 21 — next working session

Continue from the actual user chess-web-app-day21-end.zip plus the Day 21 documentation replacements and subsequently applied inline changes. Review actual source first; no assistant-generated implementation archive replaces it.

## Working agreement

User implements inline coherent increments. Preserve modular monolith/conventional layers, Player naming, cross-feature services/events, pure Java rules, useful comments, separate component HTML/CSS, minimal frontend tests and user-managed configuration. Do not execute tests/builds without explicit request. Treat tests as green unless a failure is reported. Documentation replacement packages are welcome.

## Reviewed state

Reviewed on 30 October 2026; no blocking defect in inspected changes. User reports all looks good. No assistant tests/builds/migrations/browser checks were run.

V7 establishes baseline FEN, completeness and recorded count; existing games record forward with historyComplete=false, new games start complete. GameMove records coordinates/promotion/resulting FEN/time/SAN with independent sequence relative to the baseline. Version remains a concurrency token. V8 preserves null SAN for pre-SAN entries with no backfill.

GameService validates/applies/evaluates/formats SAN before managed mutation, advances count/position/completion, flushes the versioned game, inserts/flushes history, releases terminal reservations and publishes the immutable event. All share one transaction. Private extracted helpers do not create new transactions. getHistory authorizes first and bounds records by captured count.

Pure SanFormatter uses original position, accepted move, complete legal set and resulting assessment. Legal-source disambiguation excludes pinned alternatives. GameMoveResult remains unchanged; SAN is read via GET /api/v1/games/{gameId}/moves.

Angular reads board then history in one coordinator. Matching versions accept history; older history is ignored; newer history queues blocking board reconciliation. A stale accepted list may remain while catching up. History-only failures have separate messages and do not independently block proposals. Downstream takeUntil cancels either GET stage before POST. Accepted POST explicitly queues reconciliation. Visible finished games recover missing/failed/outdated history. Initial game GET failure without a snapshot is not timer-retried.

User retained separate Move and SAN columns with matching headers; null SAN displays “—”. Home still requires Open game; no automatic board navigation. Day 20 security/expiry/notification/recovery behavior remains.

## Read first

README.md, frontend/README.md, docs/architecture.md, docs/development-progress.md, docs/roadmap.md, docs/api-contracts.md, docs/frontend-gameplay-flow.md, docs/realtime-updates-flow.md, docs/move-history-flow.md and docs/day21-history-review.md. Use movement-pipeline-architecture.md for pure-rules changes. Older documents and docs/history retain historical scope.

## Test evidence and gaps

Actual source contains history persistence/access/ordering/rollback/concurrency additions, bounded-query fixture, simulated legacy/null-SAN compatibility, entity guards and SanFormatter fixtures. No new frontend coordinator suite, full socket suite, WebSocketPropertiesTest or actual old-database migration-upgrade test. Bounded-query verification is sequential. Do not claim independent test execution.

## Next milestone

Discuss and select before implementation in a new chat. Resignation/agreed draws is a reasonable lifecycle candidate; focused reliability tests are another. Replay controls, PGN export, pre-SAN backfill, clocks, extra draws, acceptance, ratings and abandonment remain separate choices. No next feature has been authorized by this handoff. Closing a page does not finish a game or release reservations.
