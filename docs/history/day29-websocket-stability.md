# Day 29 - Bounded WebSocket stability check

Prepared 8 November 2026. This is the final selected local load-test increment,
followed by final source/document review and milestone closure. It is a three-
minute moderate-load check, not a sustained production-capacity or long-soak study.
No backend changes or assistant execution are part of the increment.

## Purpose and workload

The successful 100-game socket batches kept connections alive for roughly five
seconds, less than the configured ten-second STOMP heartbeat interval. This check
holds both Player connections open for at least three minutes after readiness,
with a quiet interval and occasional HTTP move commands. Each VU controls one
pre-paired game and two independent session-authenticated sockets. Setup remains
sequential and is not another concurrent matchmaking experiment.

Use the retained HashMap implementation, pool size 10, notification workers 2,
queue capacity 256, default 10-second STOMP heartbeat settings and JFR off. Use
only the isolated loadtest backend/database. The script caps GAMES at 20. No
1,000-game run, broker migration or production configuration tuning is selected.

Relative to each VU's ready/settled time, the intended schedule is:

| Offset | Activity |
| --- | --- |
| 0, 1, 2, 3 seconds | Opening plies e4, e5, Nf3, Nc6 |
| After the fourth move until 45 seconds | No game commands; observe idle connection heartbeats |
| 45, 53, ..., 165 seconds | Four reversible knight cycles, sixteen plies total |
| At or after 180 seconds | White resigns; validate both notifications and opponent's final state |

There are 20 plies plus one resignation per game, with two matching notification
receipts per command. The current repetition/move-count draw deferrals permit the
reversible fixture as in the established HTTP gameplay test. Absolute scheduling
is not an arrival-rate executor: slow operations delay subsequent work rather
than creating overlapping commands. Clock time never pauses. The normal ten-minute
initial game clock comfortably exceeds the intended hold plus setup; a changed
short control would invalidate this fixture and must not be silently ignored.

The hold duration begins after both STOMP connections settle. Each connection
therefore remains open at least that long on a passing run. Actual duration can
be slightly longer for commands, reads and cleanup. Setup/teardown add to overall
wall time, which should normally be a little over three minutes at twenty games.
A five-minute VU scenario cap bounds unexpectedly stalled work; setup and teardown
have their separate four-/two-minute caps. These are harness bounds, not UX SLOs.

## Heartbeat and delivery checks

The script sends STOMP newline heartbeats every ten seconds. Received standalone
newlines are consumed between protocol frames rather than mistaken for JSON or
MESSAGE frames. STOMP permits EOL padding after frames; the aggregate received-
newline metric can therefore include padding. The dedicated quiet window, without
new game messages, requires each socket to observe such incoming idle activity
before the remaining moves start. Do not claim an exact heartbeat count or period
from this aggregate alone. This is STOMP activity, not WebSocket PING/PONG frames.

A five-second watchdog checks each connected socket's last incoming data time.
If no data arrives for over thirty seconds, it records a liveness timeout, closes
the socket and fails the game. This is a generous diagnostic allowance for the
inspected ten-second heartbeat settings, not a new production timeout policy.
It measures application-observed data, not TCP peer reachability independently.

Both exact game/version expectations are armed before HTTP requests. Moves and
resignation must succeed, both Players must receive the corresponding GAME_UPDATED,
and the opponent must read matching authoritative version/FEN/status. Earlier
five-second notification and ten-second HTTP deadlines remain unchanged. The script
includes the two previously supplied k6 TypeScript fixes (null protocols argument
and message-event data narrowing).

No automatic reconnect, notification fallback polling, silent command retry or
background browser rendering is introduced. Failures remain visible. A broken
socket is checked during the hold at roughly one-second intervals. The same
500-ms subscription-settling assumption remains; it is not an acknowledged broker
subscription barrier. This workload is not an Angular recovery acceptance test.

## Custom metrics and expected counts

| Metric | Expected / meaning at 20 games |
| --- | --- |
| ws_stomp_connected | 40 |
| ws_games_completed | 20 |
| ws_expected_notifications | 840 = 20 games x (20 plies + resignation) x 2 recipients |
| ws_idle_heartbeat_connections | 40 connections passing the idle-window heartbeat observation |
| ws_heartbeats_sent | Positive; diagnostic count, not a fixed expected total |
| ws_heartbeats_received | Positive; received newline activity, with padding caveat above |
| ws_liveness_timeouts | 0 |
| ws_stability_hold_duration | Each recorded hold at least 180,000 ms; failed partial holds can be shorter |
| ws_delivery_failures / ws_gameplay_failures | 0%; all expected journeys/deliveries succeed |
| ws_connection_errors / ws_unexpected_notifications | 0 |
| ws_duplicate_notifications | Reported separately; not counted as extra successful deliveries |
| ws_command_to_notification | Command start to matching callback; includes backend/transport/client scheduling |
| ws_command_to_reconciled_state | Command start through both matched notifications and validated opponent GET |
| ws_gameplay_move_duration | HTTP duration of move commands only; no intentional hold time |

All checks and HTTP thresholds must also pass. Total built-in WebSocket message
counts now include heartbeat frames and are not fixed at the earlier short-run
values. The game count and exact notification thresholds prevent a partial run
from appearing successful because its few recorded latency samples are fast.

A VU holds one game, rather than creating replacement games over a fixed-duration
executor. This bounds persisted fixture growth and avoids mixing repeated auth,
queue setup and reconnect churn into the measurement. CPU/latency rates include
setup/teardown unless explicitly separated. The complete move-history and rating
invariants remain covered by earlier tests; this script adds no claim of exhaustive
history/statistics verification or timeout-outcome coverage.

## Apply and execute

Copy websocket-stability.ts into load-tests. Keep existing websocket-fixture.ts,
run-k6.mjs and other workloads. Merge package-scripts.json into package.json's
scripts object, preserving dependencies and current settings. No new npm package
is needed. Keep noEmit/allowImportingTsExtensions and the current console typing
configuration. Do not replace package.json with the entries-only JSON.

Keep the isolated backend running on port 8081. With no other load workload active,
from load-tests start resource capture in terminal 1:

```bash
vmstat -t 1 > results/ws-stability-20-vmstat.txt
```

In terminal 2:

```bash
set -o pipefail
npm run ws:stability:20 2>&1 | tee results/ws-stability-20-console.txt
```

The npm command includes type checking and writes the sanitized report through
run-k6.mjs to results/ws-stability-20.json. pipefail preserves a nonzero status
when k6/typecheck fails despite tee succeeding. Retain phase timestamps: console
messages use ISO/UTC, whereas vmstat prints its local timezone header. Align those
zones before selecting the hold window. Console/frame debug remains disabled for
credentials; do not add raw STOMP frame or setup-fixture logging.

Stop vmstat with Ctrl+C only after k6 has finished. Share the JSON, vmstat and
console files, plus any backend warnings for Hikari acquisition, notification queue
rejection or socket failures. The first vmstat row is a since-boot average; ignore
it for interval analysis. Swapped memory already present is different from active
si/so traffic. Whole-machine memory/CPU is not process attribution or proof of a
leak; three minutes cannot exclude slow leaks.

The one-game script entry is available for debugging the harness if a type/protocol
issue appears. The selected execution is one twenty-game bounded run, not automatic
escalation or repeated tuning. A failure needs diagnosis before repeating or changing
timeouts. Preserve reports before repeats because the same filenames are overwritten.
Cleanup closes sockets and logs out fixtures. Failed games may remain in progress
until clocks expire; use the existing isolated fixture-reset procedure if needed.
Do not resend uncertain moves or disable session checks to make this run pass.

## Completion criteria and next step

If all checks pass, the observed hold/heartbeat counts match and no resource/log
concern remains, record this as a successful bounded local stability check. It does
not certify remote TLS/proxy behavior, many hours of sessions, connection admission,
slow receivers, reconnect surges, production sizing or distributed routing.

After reviewing results, request the final user project ZIP and synchronize all
Day 29 documents/evidence, including the executor test override and user-reported
matchmaking runs. Close this milestone with a commit/handoff before Day 30. Larger
transport/algorithm experiments and the three operational milestones stay post-MVP.

No result is recorded yet. The assistant prepared and inspected scripts/docs only;
no typecheck, compiler, build, application, database or load test was executed.

Reference: https://stomp.github.io/stomp-specification-1.2.html#Heart-beating
