# Roadmap - Day 29 in progress

Updated 8 November 2026 from the inspected user Day 29 mid-session source,
subsequent supplied increments, user execution reports and inspected result files.
No later full source archive has yet been reviewed. No assistant application,
build, compiler or test execution. User implements coherent increments.

## Current MVP scope

The modular monolith has authentication/profile, queue/pairing/reservations, pure
Java chess rules, versioned commands, live hints/HTTP recovery, history/SAN,
read-only replay, backend PGN export, resignation/agreed draws, transactional
Elo/statistics and server-authoritative clocks/timeouts. Keep Player terminology,
the custom Angular board, warm amber shell and code-selectable blue/green themes.
The self-only game list, participant snapshots and immediate live-game/search
navigation remain current. PGN UI, promotion gating, replay polish and additional
outcomes remain deferred as listed below.

Closing/disconnecting does not finish or pause games; clocks continue. The shared
test timeout scanner is disabled. Session cleanup is disabled in tests and runs
every minute in production. Refresh tokens are not an active implementation plan.
Some action clicks reportedly need another attempt; investigate no-POST versus
rejected POST when separately selected. No silent command retry is authorized.

## Day 29 - complete the selected local milestone

- [x] HTTP authentication/statistics baselines, game smoke and independent-game
  batches through 100 games; retain fixture, setup/pacing and finite-batch limits.
- [x] Diagnostic profile and 10/20 Hikari comparison. Retain pool 10 as the local
  reference; increasing to 20 was not supported by that workload's timings.
- [x] Board lookup experiment. User chose to retain the defensive HashMap snapshot
  provisionally, with faster typical latency and mixed tails; further confirmation
  deferred. No observed memory pressure is not proof of equal allocation cost.
- [x] WebSocket/STOMP workload and original 20-game pool-starvation diagnosis.
- [x] Bounded notification-executor correction applied according to user report;
  user reports tests working after two mocked-delivery integration classes use a
  test-only direct executor. Production and real-socket tests retain asynchronous
  delivery. Final source bytes remain pending review.
- [x] Post-correction 1/20/100-game socket runs reported successful. The resource-
  observed 100-game JSON independently confirms 200 connections, 1,000 matched
  notifications, 100 completed games, 5,500 passing checks and no HTTP failures.
  Connections last roughly five seconds; sustained/heartbeat capacity is unmeasured.
- [x] Resource-observed repeat: inspected vmstat interval samples show CPU idle
  25-93%, runnable count peaking at 23, I/O wait 0-2%, no swap-in/out activity and
  unchanged pre-existing swapped memory. No console phase timestamps accompany
  the files, so exact setup/gameplay alignment and process attribution are unavailable.
- [x] Concurrent matchmaking journeys reported successful at 1/10/50 games; the
  largest output shows 100 matched Players, 50 observed games and zero HTTP failures
  out of 1,100 requests. Full validation/cleanup check output remains uninspected.
- [ ] One bounded moderate-load stability check lasting beyond heartbeat intervals.
  Choose duration/load explicitly, observe resources and delivery, then stop. This
  is a limited local check, not a long soak or production-capacity certification.
- [ ] Review the final user source archive, sync load-testing/results/investigation
  evidence and limitations, then close Day 29 with a commit and handoff.

See [task list](day29-task-list.md) for execution order and the exact scope of the
next load-test increment. Keep local cap at 100 games; no 1,000-game escalation.
Retain no application retries, no socket-security bypass and no combined tuning
changes during verification.

## Day 30 - MVP closure

Final user-run acceptance, small resulting fixes, screenshots/demo material,
portfolio wording and release packaging remain planned. Start the major milestone
in a new chat. Deployment remains a separate scope decision. Before public release,
check the actual connection/resource configuration and disclose the tested range;
do not advertise a supported user count from short local batches.

## Selected post-MVP sequence

These milestones are selected roadmap tasks, not changes to implement during
Day 29. The sequence is measurement, admission limits, then overload/reconnect
hardening. Iterate earlier if a concrete safety problem appears.

### 1. Sustained capacity on the deployment target

- [ ] Run longer-lived WebSockets through repeated heartbeat intervals and sustained
  game activity on the intended deployment, with a separate load-generator host.
- [ ] Include the real TLS/reverse-proxy path; measure delivery/reconciliation tails,
  failures, connection counts, queue depth, pool waits and resource trends.
- [ ] Establish a reproducible operating range with headroom. Separate idle socket
  count, active gameplay, connection churn and login bursts. A configured maximum
  is not demonstrated throughput or a production guarantee.

### 2. Connection limits and graceful admission

- [ ] Audit process/service/container open-file limits, server/proxy connection
  budgets and memory; base limits on the measured operating range.
- [ ] Define per-instance/per-Player limits and predictable rejection for new
  connections when capacity is reached. Preserve capacity for HTTP and recovery.
- [ ] Verify admission and recovery at/above the boundary, rather than relying on
  resource exhaustion. Record intended behavior and operational indicators.

### 3. Overload, slow clients and reconnect behavior

- [ ] Extend and observe the existing bounded notification queue, rejection logging
  and HTTP reconciliation; these foundations already exist and need no redesign
  merely to finish the MVP.
- [ ] Bound per-client outbound buffering, handle slow/stalled recipients and test
  cleanup without allowing one client to obstruct others.
- [ ] Add/verify reconnect backoff with jitter and catch-up after interruption,
  including a reconnect surge. Preserve session authorization and no command replay.
- [ ] Re-run the target workload and confirm bounded resource use under overload.

Distributed deployment, RabbitMQ/Kafka and sharding remain separately scoped
options. Multiple instances would require cross-instance notification routing;
none is silently added by these milestones.

## Post-MVP experiment - move transport and authoritative updates

- [ ] Investigate WebSocket move commands separately from full-state delivery.
  Compare hints versus snapshots first, then command transport with equivalent
  state payloads. Preserve authentication, expectedVersion, after-commit results,
  bounded work and reconciliation after uncertain outcomes.
- [ ] Profile the retained implementation and select a measured efficiency change
  if justified. Candidate algorithms/data structures are documented, not selected
  for immediate implementation or a replacement rules architecture.
- [ ] Investigate king-location reuse, existence-only legal replies, compact board
  storage and fixed attack/adjacency tables after profiling. Bitboards and internal
  make/unmake remain larger optional experiments, not automatic rewrite tasks.
  A general graph/AI position-search implementation is not selected.

See [experiment options](post-mvp-performance-experiments.md). The MVP retains
HTTP game commands and private version hints; this is post-MVP investigation scope.

## TODO - Future cloud VM and separate-generator tests

Stay local for Day 29. No VM is provisioned or deployment authorized by this TODO.

- [ ] Select provider/region and deploy the single-instance application/database,
  reverse proxy and built frontend when separately scheduled. Provisional minimum:
  2 vCPU, 4 GB RAM, 40-60 GB SSD; comfortable initial option: 4 vCPU, 8 GB RAM,
  60-100 GB SSD. These are portfolio/light-traffic estimates, not measured capacity.
- [ ] Confirm shared/dedicated CPU, backup/log/storage growth and JVM/database/OS
  memory budgets against the actual workload.
- [ ] Use a separate physical/cloud load generator. A VM on the same desktop still
  shares physical resources and does not provide that separation.
- [ ] Reassess sizing from measurements. No promise of 1,000 active games,
  2,000 sockets or 1,000 moves/second is established.

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

