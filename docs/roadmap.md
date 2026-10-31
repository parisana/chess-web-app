# Roadmap

## Current position

Working Days 1–22 identify actual sessions, not a rewritten version of the original 30-working-day estimate. The [progress document](development-progress.md) lists delivered sessions. Day 20 implements authenticated real-time hints plus HTTP recovery. Day 21 adds persisted history, SAN and the synchronized move list. Day 22 adds explicit resignation and draw negotiation with atomic completion and shared Angular command coordination.

The custom board is the MVP choice. Home automatically discovers pairing and offers Open game; automatic navigation is not a missing requirement.

## Next milestone selection

Day 22 is complete in inspected source. Review and commit documentation, then select the next milestone in a new chat. No next feature is implicitly authorized.

A focused backend reliability milestone is a reasonable candidate: consider configuration binding, real socket security/delivery or an actual old-database upgrade fixture. The user reports the two previously missing draw HTTP precondition tests added and all tests green; inspect them with the next source checkpoint. Replay controls/PGN export or Player statistics/ratings are separate feature choices. Minimal frontend testing remains the user's preference.

## Remaining capabilities

| Capability | Separate decisions needed |
| --- | --- |
| History extensions | Optional pre-SAN backfill, pagination and retention if needed; missing pre-V7 moves remain unavailable |
| Replay/export | Replay controls and PGN generation/export; SAN is already implemented |
| Clocks | Time controls, authoritative timing and timeout results |
| Extra outcomes | Repetition, move-count and dead-position draws |
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

The original estimate and earlier milestone proposals are preserved in [historical main-document checkpoints](history/main-documents-through-day19.md) and existing Day documents. Historical pending features must not override the implemented Day 22 state.
