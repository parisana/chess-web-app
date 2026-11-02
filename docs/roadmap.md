# Roadmap

## Current position — Day 25

Working Days 1–25 identify actual sessions, not a revised promise about the original 30-working-day estimate. Day 20 adds live hints/recovery, Day 21 persisted history/SAN, Day 22 resignation/draw negotiation, Day 23 reliability fixtures, and Day 24 recorded-position replay plus backend PGN export, and Day 25 transactional Elo/statistics plus the self-statistics API/Home card. See [progress](development-progress.md) and [Day 25 review](day25-ratings-statistics-review.md).

The custom board remains the MVP choice. Home discovers pairing and offers explicit Open game. Replay Start/Previous/Next/history entries remain read-only until Latest is selected; live state continues updating.

## Deferred: authentication session continuity

Access-token expiry currently clears the frontend session and requires login.
Review the configured access-token lifetime for normal gameplay sessions.

A longer lifetime is a configuration/policy option. Refresh tokens are a
separate feature requiring secure storage, rotation, revocation, logout behavior
and handling of concurrent refresh attempts. Neither change is implemented
as part of the ratings/statistics milestone.

Frontend token/Player state remains in memory; increasing token lifetime alone
does not preserve login across a browser reload.

## Deferred: PGN download UI

Backend PGN export and focused formatter/HTTP tests are implemented. Connecting an Angular download button is deferred by user decision. Replay uses the existing history API and does not depend on PGN export. Future integration should include authenticated Blob retrieval, availability feedback and cancellation on route changes. Do not remove or rebuild the existing backend endpoint merely because the UI is deferred.

## Deferred: promotion chooser legality gating

The chooser can open for a promotion-shaped proposal that does not resolve check or exposes the king. The backend still owns legal membership and must reject that move without changing state. The user intentionally keeps this UI limitation to fix later. A moderate future extension can expose version-associated legal moves and gate the chooser accordingly; no implementation is authorized now. See [tracking details](frontend-gameplay-flow.md#deferred-improvement-promotion-chooser-before-legality-validation).

## Optional replay interaction

Automatically resuming live mode when Next reaches the synchronized final move was discussed only. Current behavior requires Latest explicitly. Any future change should distinguish an outdated history list from the true latest position and account for commands becoming available again. No change is required for Day 24 completion.

## Next milestone selection

Day 25 source review found no blocking issue in inspected changes. User reports tests green after fixture corrections; later increments follow the standing agreement. No assistant tests/builds/migrations/browser checks ran, and manual statistics-card acceptance is not established. Select the next milestone in a new chat before implementation. Clocks, additional rule-based draws, acceptance/abandonment, session continuity and portfolio preparation are separate choices. Ratings/statistics are implemented; extensions are not automatically authorized.

## Remaining capabilities

| Capability | Separate decisions needed |
| --- | --- |
| PGN UI | Optional authenticated download integration; backend already implemented |
| History extensions | Optional pre-SAN backfill, pagination and retention; missing pre-V7 moves stay unavailable |
| Promotion interaction | Version-associated legal candidates before opening the chooser |
| Replay polish | Optional Next-to-live behavior and later justified UI refinements |
| Clocks | Time controls, authoritative timing and timeout results |
| Extra outcomes | Repetition, move-count and dead-position draws |
| Disconnect policy | Grace periods, abandonment, reconnect behavior and reservation release |
| Match lifecycle | Acceptance, cancellation, queue expiry and rating filters |
| Rating/profile extensions | Optional leaderboard, rating-aware pairing, rated modes, audit/repair, account actions; Elo/statistics already implemented |
| Delivery reliability | Whether durable outbox/replay or multi-instance routing is actually needed |
| Board polish | Optional accessibility/interaction work or a justified library adapter |

These are candidates rather than promises to implement every item in the MVP. Redis/Kafka, spectators, tournaments, chat, engine analysis, AI and distributed scaling remain outside current scope. Existing coverage limits do not imply another reliability milestone.

## Engineering constraints

Preserve the modular monolith and conventional feature layers, cross-feature services/events, pure Java rules, Player naming, versioned HTTP commands, atomic reservation/lifecycle changes, YAML configuration and separate component HTML/CSS. User implements inline coherent increments; no assistant tests/builds without explicit request. Keep frontend tests minimal and provide a short what-to-understand note with test increments.

Notifications are best-effort hints, not a durable move log or command channel. Closing a page does not finish a game. Login/token state is in memory; games/history are persisted in PostgreSQL. Session persistence/refresh tokens remain a separate decision. Board storage changes remain deferred until justified.

## Historical plan

Earlier estimates and checkpoint narratives are preserved in [history](history/main-documents-through-day23.md) and existing Day documents. Historical pending-feature lists do not override the implemented Day 25 state. Current prior-document copies are in [Day 24 document archive](history/main-documents-through-day24.md).

## Day 25 rating boundary

All supported completions count from V11 onward, including active games that finish after migration and zero-move resignation/agreement. Already finished games are excluded. Initial rating 1200, fixed K=32, no floor or backfill. Synchronous listener processing shares the completion transaction; no async worker, outbox, full rating ledger or repair API exists. These may be considered only for a concrete requirement. A future async redesign must explicitly address durable work, per-Player ordering and correction propagation. Banking discussions were explanatory, not feature authorization.
