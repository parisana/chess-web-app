# Roadmap — Day 29 in progress

Day 29 performance-baseline work is selected and in progress. Reviewed against actual user chess-web-app-day29-mid.zip on 6 November 2026. Authentication smoke and paced statistics reads are implemented; user-reported and saved results are in [load testing](load-testing.md). The Day 28 small readability cleanup is applied. No assistant application execution or capacity claim. See [mid-session handoff](day29-mid-handoff.md).

## Current scope

The modular monolith has authentication/profile, queue/pairing/reservations, pure Java chess rules, versioned commands, live hints/recovery, history/SAN, read-only replay, backend PGN export, resignation/agreed draws, transactional Elo/statistics and server-authoritative clocks/timeouts. The custom board remains the MVP choice, now with legal hints, destination promotion icons and rejected-move feedback. Games played and immediate search/live-game navigation are implemented. Warm amber is the default; blue/green are code-selectable alternatives. Sessions and clocks remain persisted across page closure; closing/disconnecting does not directly finish a game, although clocks continue toward TIMEOUT.

The shared test-profile timeout scanner follow-up from Day 26 is now applied: false. Session cleanup is disabled in tests and configured every minute in production. Session continuity is no longer pending, and refresh tokens are not an active implementation plan.

## Deferred observation: game-action clicks

Some Offer draw/Accept draw/other buttons reportedly need another attempt. No runtime cause or fix is established. Intentional confirmation, blocking synchronization/needsRefresh, newer-snapshot confirmation clearing and HTTP 403/409 are distinct hypotheses. Investigate no-POST versus rejected POST first. User explicitly deferred changes; no silent command retry is authorized.

## Selected remaining work

Continue Day 29 with a single-pair game smoke flow: authenticate, pair through matchmaking, read state, make alternating legal moves and resign. Only after that passes should independent concurrent games be measured. This game workload is not yet implemented or executed. Explain measurements and document the environment; make an optimization only if supported by an observed bottleneck. Fix export handling so shareable results omit setup session data.

The initial read runs do not establish production capacity. The supplied 5-VU JSON has a different and higher tail than the pasted 5-VU run; preserve both observations rather than asserting a monotonic scaling result. Prometheus/Grafana instrumentation remains optional; a broader observability stack has not been added.

Day 30 is planned for a final user-run acceptance pass, small resulting fixes, screenshots/demo material, portfolio wording and release packaging. Deployment remains a separate scope decision. A larger Game state-service refactor remains deferred. No chat, rematch, public-history or extra-draw work is authorized by these plans.

## Separately scoped candidates

| Capability | Remaining decision |
| --- | --- |
| Chat/rematch | Placeholder only; messaging delivery and targeted rematch invitations require a separately selected milestone |
| Other-Player history | Choose visibility and individual-game access policy; current list is self-only |
| Portfolio | Demo/run instructions, deployment scope, architecture presentation and interview narratives |
| Extra outcomes | Repetition, move-count and dead-position draws; full timeout mating-possibility exception |
| Match lifecycle | Acceptance, grace, abandonment, cancellation and queue expiry |
| PGN UI | Optional authenticated download; backend export already exists |
| Promotion chooser | Legal hints now exist; using them to gate chooser opening remains intentionally deferred |
| Replay polish | Square-click return, stronger banner/Return-to-live UI, sounds, new-live-move indicators and Next-to-live are post-MVP |
| Clock extensions | Selectable controls, increment, lag compensation and scheduler scaling |
| Session extensions | OIDC/provider linking, all-device logout, session UI, absolute cap/renewal policy or stronger auth-race coordination only if selected |
| Reliability/scaling | Outbox, distributed routing, retry/quarantine and database-access optimization require concrete scope |
| Ratings | Leaderboard, rating-aware matching, rated modes, ledger/repair and asynchronous redesign remain separate |

No Redis/Kafka, spectators, tournaments, chat, engine/AI analysis, new draw policy, audit ledger or distributed deployment is silently introduced. Missing coverage does not automatically require another reliability milestone. Current known session/tab/socket limits are documented rather than asserted solved.

## Working constraints

User implements coherent inline increments. Preserve conventional feature layers, Player naming, cross-feature services/events, pure rules, useful comments, YAML/user-managed configuration and separate Angular templates/styles. Minimal frontend tests; explain selected important tests. No assistant tests/builds without explicit request. Existing historical checkpoint documents retain their original scope; current companions supersede pending-session claims.
