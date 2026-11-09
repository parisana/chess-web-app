# Day 29 - WebSocket delivery load testing

## Scope and checkpoint

The user chose to retain Board's defensive HashMap snapshot, wrapped in
Collections.unmodifiableMap, and revisit it if future measurements justify it.
Earlier comparisons showed faster move means/medians with mixed tail latency.
No observed memory pressure was reported; this does not establish equal allocation
or memory cost. Further Board confirmation runs are deferred by decision.
Hikari pool size 10 remains the local reference. No broker migration, sharding,
production capacity claim or additional source optimization is part of this increment.

Earlier WebSocket integration tests cover correctness; the HTTP-only k6 gameplay
batches did not connect sockets or measure notification delivery. This increment
adds a separate workload rather than changing those baselines.

## What one VU does

Setup registers and logs in two new Players, pairs them sequentially, discovers
colors and returns their session fixtures. Sequential setup avoids cross-pairing;
it does not load-test concurrent matchmaking. Each VU controls one pre-paired game
and two independent WebSocket connections, one authenticated as each Player.

1. Upgrade both connections with CHESS_SESSION and XSRF-TOKEN cookies and the
   configured allowed Origin. Send STOMP CONNECT with X-XSRF-TOKEN.
2. Receive CONNECTED, send SUBSCRIBE to /user/queue/updates, and allow the stated
   subscription settling interval to elapse on both connections.
3. Before each HTTP command, arm both sockets for the exact game ID/version.
4. Play e4, e5, Nf3, Nc6. After each accepted move, require both Players to receive
   GAME_UPDATED for the expected version. Then fetch the opponent's authoritative
   game state and check version, FEN and status against the command response.
5. Pause one second after each move. After four moves, White resigns; require the
   final notification on both sockets and reconcile the opponent's final state.
6. Close sockets in finally. Teardown logs out both Players for all fixtures.

A successful game records two STOMP connections and ten matched notifications:
(4 moves + 1 resignation) x 2 recipients. It has four intentional one-second
pauses. This differs deliberately from the earlier 20-ply/19-pause HTTP workload;
request timings are not a controlled before/after comparison of those scripts.
One VU is an orchestrator for two Players, not one browser or one socket.

The script uses k6/websockets and asynchronous HTTP/timers so socket callbacks
can run while HTTP requests are in flight. A notification may arrive before its
HTTP response; expectations are therefore registered before sending the command.
There is no silent command retry, socket reconnect or HTTP polling fallback that
could hide a delivery failure. The observer's HTTP read happens only after both
notifications have matched, rather than immediately after the command response.

## Subscription readiness limit

The current Spring simple broker does not provide STOMP receipt support for a
subscription acknowledgement barrier. The script waits 500 ms after sending each
SUBSCRIBE; CONNECTED alone acknowledges the STOMP connection, not a processed
subscription. This settling interval is a pragmatic test-harness assumption,
not a guarantee that subscriptions are ready. If the first update is missing,
inspect this race as well as application delivery. Do not automatically retry
mutations or silently increase this delay until a run passes. A deterministic
server/test readiness mechanism would be a separately reviewed change.

The minimal parser handles the text STOMP frames and JSON notification bodies
used here, including heartbeat newlines and multiple/partial text frames. It is
not a general STOMP library for arbitrary binary message bodies. Client heartbeats
are sent every ten seconds; this short test does not certify idle heartbeat
liveness or implement the browser's reconnect lifecycle.

## Metrics and definitions

| Metric/term | Meaning |
| --- | --- |
| ws_stomp_connected | Connections that received STOMP CONNECTED; expected 2 per completed game |
| ws_expected_notifications | First valid notification matching an armed game/version; expected 10 per game |
| ws_delivery_failures | Fraction of armed deliveries that timed out or were cancelled by failure; partial runs may never arm later deliveries |
| ws_command_to_notification | Milliseconds from immediately before starting the HTTP command until the matching socket callback is processed, measured for each recipient |
| ws_command_to_reconciled_state | Milliseconds from that same start through both matched notifications and the opponent's validated HTTP read; one sample per change |
| ws_gameplay_move_duration | k6 HTTP request duration for the four move commands |
| ws_connection_errors | Connection, STOMP, parsing or unexpected-close failures observed before deliberate cleanup |
| ws_unexpected_notifications | Invalid type/payload, another game's update, or a new version that was not currently expected; zero required |
| ws_duplicate_notifications | Additional copies of an already observed version; reported separately and not counted as successful additional deliveries |
| ws_games_completed / ws_gameplay_failures | Complete journeys and fraction of attempted VU journeys that did not finish successfully |
| p50 / median | Half the measured samples are at or below this value |
| p95 / p99 | 95% / 99% of measured samples are at or below this value; these describe the slower tail |
| Tail latency | The slow minority of observations, rather than the average request |
| Reconciliation | Fetching authoritative state after a notification hint and checking that state against the accepted command |
| Subscription | A request to receive messages for a destination over an established STOMP connection |

Command-to-notification includes command/network/server work and client callback
scheduling. It is not isolated broker latency or server-commit-to-delivery time.
Reconciled-state timing waits for both recipients; it does not model each browser's
independent refresh pipeline or Angular rendering. The shared local load generator
can contribute to timing. No latency SLO is introduced: five seconds is a bounded
delivery deadline for this diagnostic harness, not an acceptable UX target.
A one-game run gives only ten notification samples; its p99 is not a stable tail
estimate. Report individual runs and sample counts, not averaged percentiles.

Wrong-game events fail the workload during observation, but this does not replace
the earlier dedicated third-Player privacy/security tests. Connections close after
completion; the script does not observe indefinite late delivery.

## Apply and run

Copy the two new .ts files into load-tests. Keep the existing run-k6.mjs and other
scripts unchanged. Merge the supplied package-scripts.json entries into the
existing package.json scripts object; do not replace package.json with that file.
Add "allowImportingTsExtensions": true to compilerOptions in tsconfig.json,
preserving the existing noEmit/types/lib/include configuration. No new npm
package is needed with the existing k6 2.3.0 and @types/k6 2.3.1 setup.

Use only the isolated loadtest profile/database on localhost:8081. Keep the
HashMap implementation and pool size 10. Start with JFR off. Run from load-tests:

```bash
npm run ws:games:1
```

The npm command runs the TypeScript check before k6 and saves a sanitized report
with the existing runner. Expected custom counts are 2 connections, 10 matched
notifications and 1 completed game, with zero errors/failures. If it fails, share
the failure and sanitized report before escalation. No application authentication
or socket security needs to be disabled for this workload.

After reviewing a passing one-game run, progress separately to:

```bash
npm run ws:games:5
npm run ws:games:20
```

Five games means ten sockets and fifty matched notifications. Twenty games means
forty sockets and two hundred matched notifications. Review one run before
starting the next. Later 50/100-game runs are possible within the script's cap,
but are not the initial execution step and need resource observation as before.
Do not run this and another load workload concurrently on the local machine.
Reports with the same filename are overwritten; copy/rename results before repeats.

Defaults can be overridden explicitly, for example:

```bash
WS_ORIGIN=http://localhost:4200 npm run ws:games:1
```

BASE_URL defaults to http://localhost:8081. WS_URL defaults to the corresponding
ws:// host plus /api/v1/ws. WS_DESTINATION defaults to /user/queue/updates.
WS_ORIGIN must equal the backend's configured allowed-origin. WS_DELIVERY_TIMEOUT_MS
(default 5000) and WS_SUBSCRIBE_SETTLE_MS (default 500) are disclosed harness
settings, not tuning knobs to conceal failures. Record overrides with results.
The script's configured cookies/CSRF headers match the inspected Day 29 mid-session
source; preserve any later user-managed routing/security changes explicitly.

Do not use raw --summary-export, frame debug logging or log setup fixtures:
setup contains authenticated session cookies. The existing runner exports only
metrics/root_group and removes its temporary raw summary. Reports contain aggregate
metrics/checks, not saved socket frames. Setup failures can leave partial fixtures;
use the existing isolated-database reset procedure when needed. Successful test
games remain as persisted fixtures, as in earlier increments.

## Other useful workloads

| Candidate | Question answered | Priority and boundary |
| --- | --- | --- |
| Concurrent matchmaking | Does simultaneous queue entry pair each Player at most once and release queue/reservation state correctly under contention? | Useful next bounded increment; separate from sequential gameplay fixture setup |
| Longer moderate-load run | Do memory, threads, sessions, pool waits or latency drift over time? | Useful after socket batches pass; a genuine repeated/duration workload needs fixture/clock/data-growth planning |
| Larger history/list fixtures | Do pagination and move-history reads remain efficient as persisted data grows? | Later data-volume experiment, rather than just adding VUs to tiny fixtures |
| Reconnect and catch-up | Does a disconnected Player recover the latest authoritative state without retrying commands? | Reliability increment; socket delivery test here deliberately has no reconnect |
| Idle connections | What resources do many connected, mostly idle Players consume, including heartbeats? | Separate workload; short active games do not answer it |
| Login bursts | How do BCrypt and session writes behave when many Players log in together? | Optional separate auth workload; do not mix into gameplay latency conclusions |

For the remainder of Day 29, prioritize socket delivery and then a bounded
matchmaking contention test. Choose one moderate-duration stability check if
resources/time permit; defer the other workloads rather than making the milestone
an unlimited capacity study. Distributed deployment, 1,000-game claims, RabbitMQ,
Kafka and database sharding remain outside this local milestone.

## Evidence and results

No WebSocket load results are recorded yet. The assistant inspected source and
prepared scripts/documentation only; no compiler, typecheck, tests, backend,
load generator or database was executed. User execution/review will supply the
runtime evidence. Broader source/doc synchronization follows the final user archive.

Primary API references:
- https://grafana.com/docs/k6/latest/javascript-api/k6-websockets/websocket/
- https://grafana.com/docs/k6/latest/javascript-api/k6-websockets/params/
- https://grafana.com/docs/k6/latest/javascript-api/k6-http/asyncrequest/
- https://docs.spring.io/spring-framework/reference/web/websocket/stomp/handle-broker-relay.html
