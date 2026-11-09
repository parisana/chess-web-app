# Day 29 remaining tasks and selected post-MVP milestones

Updated 8 November 2026. This list records the user's selected scope, not authority
to run the application/tests on the assistant's side. The user applies increments
and executes checks. Final source inspection follows a new user archive.

## Finish Day 29 locally

- [x] HTTP baselines, concurrent independent-game batches through 100 games.
- [x] JVM/system diagnostics and fixed-pool comparison; retain pool 10 locally.
- [x] Retain HashMap Board snapshot provisionally by user decision; mixed tail
  evidence remains, and further storage confirmation is deferred.
- [x] Diagnose synchronous notification/session-read pool starvation using runtime
  stacks; add bounded asynchronous delivery and the scoped integration-test override.
  User reports tests working. Keep real socket tests/production asynchronous.
- [x] Post-fix WebSocket delivery through 100 games, with a resource-observed repeat.
  No sustained production-capacity claim or long-lived heartbeat coverage.
- [x] Concurrent matchmaking journeys reported successful at 1/10/50 games. The
  50-game output reports 100 matched Players, 50 observed games and no HTTP failures
  across 1,100 requests. The full validation/cleanup check summary was not supplied;
  retain that evidence boundary until final report/source review.
- [ ] One bounded moderate-load stability check beyond heartbeat intervals. Scope
  exact workload/duration after matchmaking results. Include resource observations.
  No long soak, indefinite tuning or 1,000-game test is selected.
- [ ] Review latest user archive; synchronize final docs, measured results and
  limitations; commit and hand off to Day 30 in a new chat.

## Day 30 - MVP closure

- [ ] Final user-run acceptance and only the small fixes resulting from it.
- [ ] Demo/screenshots, portfolio wording, run instructions and release packaging.
- [ ] Record what was actually tested; no supported-production-user-count claim.

## Post-MVP milestone 1 - sustained capacity

- [ ] Longer-lived connections, repeated heartbeats and sustained game traffic on
  the actual deployment target, using a separate load-generator host.
- [ ] Include TLS/proxy; measure resource/queue/pool/latency/failure trends and leave
  headroom. Distinguish idle sockets, active games, connection churn and auth load.
- [ ] Publish a reproducible tested operating range and limitations.

## Post-MVP milestone 2 - connection budgets and graceful admission

- [ ] Audit open-file limits and server/proxy/service/container connection budgets.
- [ ] Choose measured per-instance/per-Player limits and preserve HTTP/recovery room.
- [ ] Test rejection and recovery at/above the boundary; avoid uncontrolled exhaustion.

## Post-MVP milestone 3 - overload and reconnect hardening

- [ ] Observe/extend existing bounded delivery and rejection logging as needed.
- [ ] Bound slow-client buffering and test cleanup without blocking other Players.
- [ ] Backoff with jitter, reconnect surges and authoritative catch-up; preserve
  session authorization and never silently replay uncertain commands.
- [ ] Confirm resources stay bounded during overload and recovery.

The three milestones are selected follow-up work, not features to implement during
MVP closure. Single-VM deployment scheduling remains separate; the provisional
sizing TODO remains in roadmap.md. Broker migration, microservices, distributed
routing and database sharding are separately scoped options.


## Post-MVP experiment - WebSocket commands and state delivery

- [ ] Profile the retained implementation before assigning current bottlenecks.
- [ ] Compare HTTP commands + WebSocket hints against HTTP commands + authoritative
  WebSocket snapshots, holding workload, security, versions and resources fixed.
- [ ] Separately compare WebSocket move commands with the same state-delivery model,
  so transport and removed read round trips are not conflated.
- [ ] Define correlated accepted/rejected results after commit, expectedVersion,
  authorization, bounded incoming/outgoing work and recovery for uncertain results.
  Do not treat transport receipt as business success or silently replay commands.
- [ ] Retain a reliable initial/reconnect reconciliation path; evaluate latency,
  failures, database load and operational complexity before choosing adoption.

This selects investigation after MVP, not automatic replacement of HTTP or changes
for Day 29. See [performance experiment options](post-mvp-performance-experiments.md).
Algorithms/data structures below are a candidate catalog, not authorization to
implement all of them or a new rules/state-management architecture.


## Post-MVP investigation - rules algorithms and data structures

- [ ] Obtain a fresh profile of the retained HashMap/executor implementation.
- [ ] Evaluate reusing validated king locations and an existence-only legal-reply
  query while preserving invalid-position/unsupported-case failure contracts.
- [ ] If justified by the profile, compare a compact 64-square array and fixed
  attack/movement tables. Benchmark one change at a time and preserve immutability.
- [ ] Assess graph-style precomputed square adjacency/rays as an implementation of
  those fixed tables; do not assume a general graph library or BFS speeds legality.
- [ ] Treat bitboards and private make/unmake as larger optional candidates, selected
  separately only if expected benefit justifies special-move/restoration complexity.

These tasks select investigation after MVP, not implementation of every candidate.
AI search over a graph of complete positions is a different feature and remains
outside this plan. Current MVP work remains the bounded stability check and closure.
