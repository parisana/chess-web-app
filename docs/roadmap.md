# Roadmap

## Current position — Day 24

Working Days 1–24 identify actual sessions, not a revised promise about the original 30-working-day estimate. Day 20 adds live hints/recovery, Day 21 persisted history/SAN, Day 22 resignation/draw negotiation, Day 23 reliability fixtures, and Day 24 recorded-position replay plus backend PGN export. See [progress](development-progress.md) and [Day 24 review](day24-replay-pgn-review.md).

The custom board remains the MVP choice. Home discovers pairing and offers explicit Open game. Replay Start/Previous/Next/history entries remain read-only until Latest is selected; live state continues updating.

## Deferred: PGN download UI

Backend PGN export and focused formatter/HTTP tests are implemented. Connecting an Angular download button is deferred by user decision. Replay uses the existing history API and does not depend on PGN export. Future integration should include authenticated Blob retrieval, availability feedback and cancellation on route changes. Do not remove or rebuild the existing backend endpoint merely because the UI is deferred.

## Deferred: promotion chooser legality gating

The chooser can open for a promotion-shaped proposal that does not resolve check or exposes the king. The backend still owns legal membership and must reject that move without changing state. The user intentionally keeps this UI limitation to fix later. A moderate future extension can expose version-associated legal moves and gate the chooser accordingly; no implementation is authorized now. See [tracking details](frontend-gameplay-flow.md#deferred-improvement-promotion-chooser-before-legality-validation).

## Optional replay interaction

Automatically resuming live mode when Next reaches the synchronized final move was discussed only. Current behavior requires Latest explicitly. Any future change should distinguish an outdated history list from the true latest position and account for commands becoming available again. No change is required for Day 24 completion.

## Next milestone selection

Day 24 source review found no blocking issue in inspected changes. Tests follow the user's standing green-unless-reported agreement; replay is reported working apart from the unverified route-switch scenario. No assistant builds/tests/browser checks were run. Select the next milestone in a new chat before implementation. Ratings/statistics, clocks, additional rule-based draws, acceptance and abandonment are separate choices, not automatically authorized work.

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
| Player profile growth | Statistics, ELO, event-driven updates and account actions |
| Delivery reliability | Whether durable outbox/replay or multi-instance routing is actually needed |
| Board polish | Optional accessibility/interaction work or a justified library adapter |

These are candidates rather than promises to implement every item in the MVP. Redis/Kafka, spectators, tournaments, chat, engine analysis, AI and distributed scaling remain outside current scope. Existing coverage limits do not imply another reliability milestone.

## Engineering constraints

Preserve the modular monolith and conventional feature layers, cross-feature services/events, pure Java rules, Player naming, versioned HTTP commands, atomic reservation/lifecycle changes, YAML configuration and separate component HTML/CSS. User implements inline coherent increments; no assistant tests/builds without explicit request. Keep frontend tests minimal and provide a short what-to-understand note with test increments.

Notifications are best-effort hints, not a durable move log or command channel. Closing a page does not finish a game. Login/token state is in memory; games/history are persisted in PostgreSQL. Session persistence/refresh tokens remain a separate decision. Board storage changes remain deferred until justified.

## Historical plan

Earlier estimates and checkpoint narratives are preserved in [history](history/main-documents-through-day23.md) and existing Day documents. Historical pending-feature lists do not override the implemented Day 24 state.
