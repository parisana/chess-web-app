# Roadmap — Day 29 in progress

Day 29 performance-baseline work is selected and in progress. Reviewed against actual user chess-web-app-day29-mid.zip on 6 November 2026. Authentication smoke and paced statistics reads are implemented; user-reported and saved results are in [load testing](load-testing.md). The Day 28 small readability cleanup is applied. No assistant application execution or capacity claim. See [mid-session handoff](day29-mid-handoff.md).

## Current scope

The modular monolith has authentication/profile, queue/pairing/reservations, pure Java chess rules, versioned commands, live hints/recovery, history/SAN, read-only replay, backend PGN export, resignation/agreed draws, transactional Elo/statistics and server-authoritative clocks/timeouts. The custom board remains the MVP choice, now with legal hints, destination promotion icons and rejected-move feedback. Games played and immediate search/live-game navigation are implemented. Warm amber is the default; blue/green are code-selectable alternatives. Sessions and clocks remain persisted across page closure; closing/disconnecting does not directly finish a game, although clocks continue toward TIMEOUT.

The shared test-profile timeout scanner follow-up from Day 26 is now applied: false. Session cleanup is disabled in tests and configured every minute in production. Session continuity is no longer pending, and refresh tokens are not an active implementation plan.

## Deferred observation: game-action clicks

Some Offer draw/Accept draw/other buttons reportedly need another attempt. No runtime cause or fix is established. Intentional confirmation, blocking synchronization/needsRefresh, newer-snapshot confirmation clearing and HTTP 403/409 are distinct hypotheses. Investigate no-POST versus rejected POST first. User explicitly deferred changes; no silent command retry is authorized.

## Selected checkpoint and remaining work

The initial Day 29 HTTP baseline is recorded: auth smoke, paced statistics reads, one-game smoke and 1/5/20 pre-paired independent game batches. User game reports show 20/100/400 move attempts, all game journeys completed and zero game-journey failures. Move p95 increased across these batches, but no causal bottleneck is established. The saved 5-VU statistics run differs from the pasted one; retain both. See [load testing](load-testing.md) for exact provenance, tables and limits.

The aggregate-report runner omits setup session data from newly written reports; historical raw JSON still needs sanitization. Retain result artifacts and record current CPU/RAM/runtime configuration for reproducibility. No rerun or optimization is required solely for this documentation synchronization. The finite local workload does not establish maximum/sustainable throughput or production-user capacity.

Further matchmaking contention, socket delivery/connection load, substantial history fixtures, longer sustained/stress/soak runs and observability instrumentation remain separate choices. They have not been implemented or measured as part of this checkpoint. The gameplay batch pairs sequentially in setup and connects no WebSocket clients.

The selected local 50-/100-game extension is now recorded. Both user-run batches passed all checks with 1,000/2,000 move attempts and zero HTTP/gameplay failures. Move p95 was 171.17/399.17 ms; the 100-game maximum move was 1.06 seconds. System captures show brief CPU pressure and no swap traffic, but no exact phase/process/request attribution or causal bottleneck. Inventory confirms Ryzen 3 3200G, four logical CPUs, 15 GiB OS-visible RAM and k6 2.3.0. Preserve results; improve resource attribution before further escalation rather than treating pass status as a latency/capacity guarantee. See load-testing.md. The game cap remains 100 and no 1,000-game run is selected.

Day 30 remains planned for final user-run acceptance, small resulting fixes, screenshots/demo material, portfolio wording and release packaging. Deployment is a separate scope decision. Larger state-management changes, chat, rematch, public history and extra draws remain deferred.

## Current diagnostic follow-up

The profiled 100-game run is reviewed separately from the unprofiled baseline.
Hikari acquisition waiting and repeated rules/board lookups are observed; exact
causal contribution to individual tail requests remains unproven. The smallest
proposed next experiment confirms effective pool configuration and compares a
moderate alternative in loadtest only, holding rules/pacing/hardware fixed.
Board/rule optimization is a separate possible follow-up. No optimization or
configuration change has been implemented by the review. See [diagnostic review](day29-diagnostic-review.md).

## TODO — Future cloud VM deployment and separate-generator tests

Deferred by user decision: stay local for the current milestone. No VM is
provisioned or deployment authorized by this TODO.

- [ ] Choose a provider/region and deploy the single-instance backend, PostgreSQL,
  reverse proxy and built Angular assets. Provisional practical minimum:
  **2 vCPU, 4 GB RAM, 40–60 GB SSD**. More comfortable initial option:
  **4 vCPU, 8 GB RAM, 60–100 GB SSD**. These are planning estimates for a
  portfolio/light-traffic deployment, not measured capacity guarantees.
- [ ] Confirm storage growth, backups/log retention and JVM/database memory
  budgets against the actual workload. Reserve disk/OS headroom.
- [ ] Record shared versus dedicated CPU and actual provider limits. Prefer
  predictable CPU for repeatable sustained-performance comparisons; choose
  economy based on the real demo workload, not an unmeasured user-count claim.
- [ ] Run k6 from a separate physical/cloud host to separate generator resource
  use from backend/database resource use. A VM on the same desktop still shares
  its physical resources and is not that separation.
- [ ] Measure the deployed protocol path, including TLS/reverse proxy and,
  if separately selected, WebSocket/STOMP clients. Current local game scripts
  have no connected WebSocket clients.
- [ ] Reassess sizing from deployment measurements. Support for 1,000 active
  games, 2,000 sockets or 1,000 moves/second is not established or promised.

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

## Pool-comparison update

Four user-supplied labelled pool-10/pool-20 reports pass all checks. Both
pool-20 move averages, medians and p95s exceed both pool-10 results, so these
reports do not support adopting 20 for this local workload. The user subsequently confirmed restarting the backend with the corresponding
10/20 pool settings. This is user-reported verification; logs were not inspected.
Keep 10 as the explicit local loadtest reference, not a production optimum.
No code optimization or assistant test execution is claimed. See [pool-comparison evidence](day29-pool-comparison.md).

Configuration evidence update (6 November 2026): the user confirmed restarting
the backend for each corresponding 10/20 pool specification. This resolves the
comparison-setting confirmation request; no raw startup-log inspection or causal
performance proof is implied. Keep 10 as the local reference and preserve all
four reports. No repeat run is requested solely for this confirmation.
