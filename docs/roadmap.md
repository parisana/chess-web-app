# Roadmap

## Current position

Working Days 1–20 identify actual sessions, not a rewritten version of the original 30-working-day estimate. The [progress document](development-progress.md) lists delivered sessions. Day 20 implements authenticated real-time hints plus HTTP state recovery for matchmaking and gameplay.

The custom board is the MVP choice. Home automatically discovers pairing and offers Open game; automatic navigation is not a missing requirement.

## Next milestone selection

Start a new major milestone in a new chat after closing the Day 20 review/documentation commit. Recheck the latest source before implementation. Do not silently combine unrelated features.

Persisted move history is a reasonable next candidate: record accepted moves atomically, provide ordered participant-protected reads and later add a minimal move list. Existing games cannot recover earlier moves from their current FEN. History continuity/backfill policy must be explicit. Replay controls and SAN/PGN notation are separate scope choices.

A focused verification/reliability milestone is another candidate if desired: configuration-binding tests, actual STOMP security/delivery tests and selected frontend coordination tests. No broad frontend suite is required by the current agreement.

## Remaining capabilities

| Capability | Separate decisions needed |
| --- | --- |
| Move history | Stored fields, ordering, legacy games, read contract and minimal display |
| Replay/notation | Replay controls, SAN/PGN format and export |
| Clocks | Time controls, authoritative timing and timeout results |
| Extra outcomes | Repetition, move-count and dead-position draws; resignation/agreement |
| Disconnect policy | Grace periods, abandonment rules, reconnect behavior and reservation release |
| Match lifecycle | Acceptance stage, cancellation, queue expiry and rating filters |
| Player profile growth | Statistics, ELO and event-driven updates; account email/password actions |
| Delivery reliability | Whether durable outbox/replay or multi-instance routing is actually needed |
| Board polish | Optional styling, accessibility/interaction improvements or a justified library adapter |

These are candidates rather than promises to implement every item in the MVP. Redis/Kafka, spectators, tournaments, chat, engine analysis, AI and distributed scaling remain outside current scope.

## Engineering constraints

Preserve the modular monolith and conventional feature layers. Cross-feature calls use services/events; repositories stay owned by their feature. Pure rules remain free of transport/persistence dependencies. Keep Player terminology, versioned HTTP moves, atomic reservation/lifecycle updates, YAML configuration and separate component templates/styles.

Notifications are best-effort invalidation hints, not a command or durable move log. Closing a page does not finish the game. Browser session persistence/refresh tokens are separate decisions. Map-versus-array board evaluation remains deferred until a demonstrated benefit justifies it while preserving Board API.

## Historical plan

The original estimate and earlier milestone proposals are preserved in [historical main-document checkpoints](history/main-documents-through-day19.md) and existing Day documents. Historical pending features must not override the implemented Day 20 state.
