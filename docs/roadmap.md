# Roadmap — after Day 27

Day 27 delivers persistent browser sessions, CSRF-protected HTTP, session-authenticated WebSockets, startup recovery and cross-tab hints. See [review](day27-sessions-review.md), [authentication](session-authentication-flow.md), [tab diagrams](tab-synchronization-flow.md) and [handoff](day27-handoff.md). User reports the observed failures resolved and browser login/flow working; no assistant execution. The original roughly 30-working-day estimate is not a promise to implement every candidate below.

## Current scope

The modular monolith has authentication/profile, queue/pairing/reservations, pure Java chess rules, versioned commands, live hints/recovery, history/SAN, read-only replay, backend PGN export, resignation/agreed draws, transactional Elo/statistics and server-authoritative clocks/timeouts. The custom board remains the MVP choice. Sessions and clocks remain persisted across page closure; closing/disconnecting does not directly finish a game, although clocks continue toward TIMEOUT.

The shared test-profile timeout scanner follow-up from Day 26 is now applied: false. Session cleanup is disabled in tests and configured every minute in production. Session continuity is no longer pending, and refresh tokens are not an active implementation plan.

## Deferred observation: game-action clicks

Some Offer draw/Accept draw/other buttons reportedly need another attempt. No runtime cause or fix is established. Intentional confirmation, blocking synchronization/needsRefresh, newer-snapshot confirmation clearing and HTTP 403/409 are distinct hypotheses. Investigate no-POST versus rejected POST first. User explicitly deferred changes; no silent command retry is authorized.

## Choose the next milestone separately

Portfolio preparation is a useful candidate now that the main end-to-end path is implemented. Other options include additional chess draws or match acceptance/abandonment. These remain choices, not authorization. Start the next major milestone in a new chat using the handoff and actual user source.

| Capability | Remaining decision |
| --- | --- |
| Portfolio | Demo/run instructions, deployment scope, architecture presentation and interview narratives |
| Extra outcomes | Repetition, move-count and dead-position draws; full timeout mating-possibility exception |
| Match lifecycle | Acceptance, grace, abandonment, cancellation and queue expiry |
| PGN UI | Optional authenticated download; backend export already exists |
| Promotion chooser | Version-associated legal candidates before opening; intentionally deferred |
| Replay polish | Square-click return, stronger banner/Return-to-live UI, sounds, new-live-move indicators and Next-to-live are post-MVP |
| Clock extensions | Selectable controls, increment, lag compensation and scheduler scaling |
| Session extensions | OIDC/provider linking, all-device logout, session UI, absolute cap/renewal policy or stronger auth-race coordination only if selected |
| Reliability/scaling | Outbox, distributed routing, retry/quarantine and database-access optimization require concrete scope |
| Ratings | Leaderboard, rating-aware matching, rated modes, ledger/repair and asynchronous redesign remain separate |

No Redis/Kafka, spectators, tournaments, chat, engine/AI analysis, new draw policy, audit ledger or distributed deployment is silently introduced. Missing coverage does not automatically require another reliability milestone. Current known session/tab/socket limits are documented rather than asserted solved.

## Working constraints

User implements coherent inline increments. Preserve conventional feature layers, Player naming, cross-feature services/events, pure rules, useful comments, YAML/user-managed configuration and separate Angular templates/styles. Minimal frontend tests; explain selected important tests. No assistant tests/builds without explicit request. Existing historical checkpoint documents retain their original scope; current companions supersede pending-session claims.
