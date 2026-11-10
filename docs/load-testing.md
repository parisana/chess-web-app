# Local load testing

Use the TypeScript k6 suite in `load-tests/`. Each stability VU controls one game with two authenticated players and two WebSocket connections. The scenario pairs games in setup, then plays 20 half-moves and resigns each game. It validates both snapshots against each committed command and observes heartbeats during an idle interval. Match setup and cleanup are outside command latency.

## Run

Create the separate `chess_loadtest` PostgreSQL database and configure its credentials. From `backend/`, run `SPRING_PROFILES_ACTIVE=loadtest ./gradlew bootRun`. This profile uses port 8081. From `load-tests/`, run:

```bash
npm ci
npm run typecheck
GAMES=20 WS_READ_AFTER_NOTIFICATION=0 node run-k6.mjs --report results/ws-stability-20-snapshot.json websocket-stability.ts
```

Use a distinct report filename when changing `GAMES` or repeating a run. Set `WS_READ_AFTER_NOTIFICATION=1` to add one game GET per command after the snapshots arrive. That mode provides a comparison with the extra read. Keep heartbeats, executor settings, pacing and timeouts fixed when comparing loads. The snapshot runs used a 10-second heartbeat interval, 2-second subscription settling delay and 5-second delivery timeout. The reported outbound executor size was 12.

For resource capture, start `vmstat -w -t 1` and `pidstat -u -r -p ALL -C '^(java|k6|postgres)$' 1` before k6. Record the setup-complete timestamp and the JVM PID listening on 8081. Keep raw logs, reports and recordings out of Git.

## Results

Command to validated game state, in milliseconds. The backend, PostgreSQL and k6 shared one Ryzen 3 3200G desktop with four logical CPUs.

| Mode | Games | Sockets | Validated deliveries | p50 | p90 | p95 | p99 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| Snapshot + GET | 20 | 40 | 840 | 128.5 | 172 | 202.1 | 237 |
| Snapshot only | 20 | 40 | 840 | 54 | 94 | 109.1 | 130.7 |
| Snapshot only | 250 | 500 | 10,500 | 220 | 521 | 625.6 | 798.5 |
| Snapshot only, first run | 500 | 1,000 | 21,000 | 308 | 1,574 | 1,703 | 2,122 |
| Snapshot only, repeat | 500 | 1,000 | 21,000 | 39 | 1,171 | 1,455 | 2,066 |

All runs passed their configured thresholds. At 20 games, snapshot-only made 700 HTTP requests versus 1,120 with the extra read. The 420-request difference matches one GET per command. Move POST times differed between the two runs, so the latency gap measures the two paths under their observed load.

Both 500-game runs held connections for at least three minutes and recorded zero HTTP failures, delivery failures, duplicates or heartbeat timeouts. Host CPU idle dropped to 0–5% during the opening burst in both captures. The first `pidstat` missed that burst. In the repeat, measured peaks were 258% backend CPU on a per-core scale and 789 MiB backend RSS. The later-starting repeat `vmstat` captured the burst and showed zero swap activity in its sampling window.

This is a finite local stability workload. Browser rendering, separate move-history reads, reconnect surges and sustained production capacity require separate measurements. A user-run two-browser reconnect check confirmed board, clock and history recovery and continued play.

## Earlier investigations

- [HTTP load-testing baseline](history/http-load-testing-baseline.md)
- [WebSocket notification pool starvation](performance-investigations/README.md)
- [100-game profiling](history/day29-diagnostic-review.md)
- [Hikari pool-size comparison](history/day29-pool-comparison.md)
- [Board lookup experiment](history/day29-board-map-experiment.md)
- [Concurrent matchmaking](history/day29-concurrent-matchmaking.md)
