# Day 29 — Load testing

## Purpose

Establish a reproducible local performance baseline for the chess backend.
Separate workload correctness from performance measurement.

## Environment

- OS: Ubuntu 22.04.5 LTS
- Backend profile: loadtest
- Backend address: http://localhost:8081
- Database: chess_loadtest
- Load generator, backend and PostgreSQL share one computer.
- Record CPU, RAM, Java, PostgreSQL and k6 versions before measured runs.

## Terminology

| Term | Definition |
| --- | --- |
| System under test (SUT) | The application and dependencies being exercised |
| Virtual user (VU) | A simulated client executing a script; not a VM |
| Iteration | One execution of the scenario function |
| Check | An assertion recorded as passing or failing |
| Threshold | A metric condition determining whether the run passes |
| Smoke test | A minimal workload verifying the script and application flow |
| Throughput | Work completed per unit of time, such as requests per second |
| p50 | Median: approximately 50% of observations are at or below this value |
| p95 | Approximately 95% of observations are at or below this value |
| p99 | Approximately 99% of observations are at or below this value |
| Warm-up | Activity before measurement to let JVM compilation and caches settle |

## Increment 1 — Authentication smoke test

Command: `npx tsc && k6 run smoke-auth.ts`
or
Command: `npm run test:auth` [check the package for json for command integrity]

One VU performs one iteration:
CSRF initialization, registration, login, CSRF renewal, profile read,
statistics read, logout and a protected read expecting 401.

Each run creates one test Player. Logout invalidates its session.
No automatic Player deletion is performed.

The script requires all checks to pass and zero unexpected HTTP failures.
The final 401 is explicitly expected.

Execution status: Authentication smoke passed; one VU, one iteration, approximately 0.7 seconds.

## Interpretation limits

This smoke test verifies the client flow; it does not establish capacity.
Its aggregate timings mix authentication and ordinary reads.
No performance latency threshold has been selected yet.
Local results include competition for resources on the shared computer.

## Increment 1 result — Authentication smoke

The user reports the authentication smoke test passed, with approximately
0.7 seconds for its single iteration. This is a functional result, not a
performance capacity measurement. It includes eight sequential requests.

## Increment 2 — Authenticated statistics-read baseline

Script: `load-tests/read-statistics.ts`.
Execution status: pending user execution.

### Question and workload

How does authenticated Player-statistics read latency behave with 1 versus
5 simulated clients on this local machine?

Each VU uses a separate newly registered Player and authenticated session.
Setup registers and logs in the Players before the scenario begins.
There is one scenario: 20 seconds of warm-up and 60 seconds of measurement.
Each iteration makes one statistics GET, validates the Player ID and zero
games played, then waits one second. Timers use the shared scenario start.
A request is assigned to a phase when dispatched; a slow warm-up request
finishing during measurement remains excluded from measured metrics.

This is a closed workload: each VU waits for its request and pause before
starting another iteration. Slower responses reduce achieved request rate.
One VU should generate slightly less than one request/second; five VUs,
slightly less than five. These are expectations, not fixed arrival rates.

No browser, WebSocket client, active game or substantial history dataset is
included. This measures an authenticated HTTP read with session/database
work; it does not measure chess rules, matchmaking or full game capacity.

### Metrics and terms

| Term / metric | Interpretation |
| --- | --- |
| Setup | Fixture creation/authentication before the scenario |
| Teardown | Session logout after the scenario; Players remain in the database |
| Think time | Intentional pause between a simulated client's actions |
| Closed workload | A client begins its next iteration only after finishing its current iteration |
| Graceful stop | Time allowed for an in-progress iteration to finish after scheduling stops; configured here as 12 seconds |
| measured_read_requests | Completed requests dispatched during the measurement window |
| measured_read_failures | Fraction of measured requests with wrong status, invalid JSON or unexpected Player data |
| measured_read_duration | Sending + waiting + receiving time for measured reads, in milliseconds; excludes initial connection establishment |
| med / p50 | Approximately half the observations are at or below this value |
| p95 / p99 | Approximately 95% / 99% of observations are at or below this value |
| Tail latency | The slower part of the response-time distribution, often discussed using p95/p99 |

Use the custom measured metrics to compare runs. The ordinary HTTP totals
and checks include setup, warm-up and teardown. k6's displayed Counter rate
can use the overall run duration: estimate measured RPS separately as
`measured_read_requests count / 60 seconds`. In-flight measured requests may
finish during graceful stop; requests still unfinished when it ends are
not a valid completed measurement. Investigate interruption/error output.

Thresholds require all checks to pass, zero unexpected HTTP failures, at
least one measured read and zero measured read failures. There is no
latency target yet. A passing run is not evidence of acceptable capacity.
The initial approximately 60 samples from one VU provide only a rough tail
estimate; longer/repeated runs are needed before drawing strong conclusions.

### Run procedure

1. Start only the loadtest-profile backend on 8081 against chess_loadtest.
   Avoid running another development backend during measured runs.
2. Close unrelated heavy applications. Keep the same launch method,
   application settings and database for both runs. Use no debugger or
   verbose SQL logging. Observe CPU/memory with top or htop.
3. From load-tests/, run `npm run typecheck`.
4. Create the output folder: `mkdir -p results`.
5. Run `npm run baseline:stats -- --summary-export=results/stats-1vu.json`.
6. Once finished, run `VUS=5 npm run baseline:stats -- --summary-export=results/stats-5vu.json`.
7. Record run order, environment versions, CPU/RAM, timing, failures,
   measured count and visible resource behaviour. Share both summaries.

Add this npm script without replacing other existing scripts:
`"baseline:stats": "k6 run read-statistics.ts"`.

Each run creates VUS test Players with zero games. No Player deletion or
database reset is performed. Sessions are logged out in normal teardown.
An interrupted or failed setup may leave sessions until expiry/cleanup.
Do not share raw setup data or HTTP debug output containing session cookies.
The summary JSON contains aggregate metrics, not the setup session data.

### Results to fill after execution

| Run | Measured requests | Approx. measured RPS | p50 ms | p95 ms | p99 ms | Measured failure rate | Resource observations |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 1 VU | pending | pending | pending | pending | pending | pending | pending |
| 5 VUs | pending | pending | pending | pending | pending | pending | pending |

In user-executed local runs with 1, 5 and 20 paced virtual users, authenticated statistics reads recorded zero measured failures. Measured p95 ranged from 14.78–16.87 ms. The 20-VU run achieved approximately 20 measured reads/second. These short runs do not establish maximum throughput or production capacity.

Record `k6 version`, `java -version`, `psql --version`, CPU model and RAM.
The backend JVM, PostgreSQL and generator share hardware, and localhost
excludes realistic remote network delay. These initial paced reads do not
establish maximum throughput or a production concurrent-user limit.
Warm-up reduces some startup effects; it does not prove full JVM/cache
stability. Run order may affect results. Subsequent comparisons should be
repeated under controlled conditions.

### References

- https://grafana.com/docs/k6/latest/using-k6/metrics/reference/
- https://grafana.com/docs/k6/latest/using-k6/scenarios/concepts/open-vs-closed/
- https://grafana.com/docs/k6/latest/using-k6/test-lifecycle/
