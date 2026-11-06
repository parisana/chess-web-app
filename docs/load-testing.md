# Load testing — Day 29 checkpoint

## Purpose and evidence

Establish a reproducible local performance baseline for the chess backend.
Separate script correctness, measurements and explanations of those measurements.
Reviewed on 6 November 2026 against the actual user `chess-web-app-day29-mid.zip`.
The assistant inspected source, documentation and the supplied aggregate JSON;
no application, build, type checker, load test or database operation was run.

The user reports the authentication smoke passing in approximately 0.7 seconds
and supplied console measurements for 1, 5 and 20 VUs. The archive contains
`stats-5vu.json` and `stats-20vu.json`, but no `stats-1vu.json`. The saved 5-VU
summary differs from the earlier pasted run; both are recorded separately below.
The 20-VU saved summary agrees with its pasted output apart from display rounding.
These are initial paced-read measurements, not a maximum-capacity benchmark.

## Environment and actual source

| Item | Inspected configuration or known evidence |
| --- | --- |
| OS | User reports Ubuntu 22.04.5 LTS |
| Backend profile | `loadtest` |
| Profile file | `backend/src/main/resources/application-loadtest.yml` |
| Backend address | `http://localhost:8081` |
| Database | `chess_loadtest` on local PostgreSQL |
| Credentials and other backend settings | Inherited from the ordinary application configuration unless overridden externally |
| Cookie | Local profile sets `secure: false`; session/CSRF protection remains configured |
| Generator | Locally installed k6 CLI; exact version not recorded |
| Tooling in supplied lockfile | TypeScript 7.0.2; `@types/k6` 2.3.1 |
| Hardware arrangement | Generator, backend and PostgreSQL share one computer |
| Not yet recorded | CPU/RAM inventory, actual Java/PostgreSQL/k6 versions, JVM options and resource observations |

The npm type definitions are not the k6 executable and do not establish its
version. Record `k6 version`, `java -version`, `psql --version`, CPU model and
RAM before stronger comparisons. Do not infer current hardware from an older
machine description. Keep launch method and configuration consistent.

The application excludes Spring Boot's `UserDetailsServiceAutoConfiguration`.
The inspected login flow authenticates through AuthService and explicitly saves
the session context in LoginService. The exclusion removes the unused default
in-memory user; it does not remove the configured SecurityFilterChain or CSRF.

## Terminology

| Term | Meaning in these tests |
| --- | --- |
| System under test (SUT) | Backend and dependencies being exercised |
| Virtual user (VU) | An independent simulated client; not a VM or necessarily a browser |
| Concurrency | Clients whose lifetimes overlap; not a promise that all have an HTTP request in flight simultaneously |
| Iteration | One execution of the workload function |
| Check | A recorded assertion about status or data |
| Threshold | A metric condition controlling whether the run passes |
| Smoke test | Minimal workload verifying the script/application flow |
| Setup | Fixture creation/authentication before the workload |
| Teardown | Cleanup after the workload; here, normal session logout |
| Think time | Intentional pause between a client's actions |
| Closed workload | A client completes its current iteration before starting its next; slower responses reduce achieved request rate |
| Throughput / RPS | Completed work per unit time / requests per second |
| Warm-up | Initial activity excluded from measured metrics to reduce startup effects; not proof that JVM/cache state has stabilized |
| Average | Sum of observations divided by their count |
| p50 / med | Median; approximately 50% of observations are at or below this value |
| p95 | Approximately 95% are at or below this value; the slowest 5% took longer |
| p99 | Approximately 99% are at or below this value; estimates are weak with small samples |
| Tail latency | Slower end of the response-time distribution, often described using p95/p99 |
| Graceful stop | Allowance for in-progress iterations to finish after scheduling stops |

A p95 of 200 ms does not mean an average of 200 ms. Percentiles from multiple
runs cannot be averaged to obtain the combined run's percentile.

## Install and run commands

Start the configured backend from `backend/`:

```bash
SPRING_PROFILES_ACTIVE=loadtest ./gradlew bootRun
```

Flyway initializes the separate database, which must already exist and be
accessible to the configured role. Avoid another development backend, a debugger,
heavy unrelated workloads and verbose SQL logging during measurements.
Angular is unnecessary for these HTTP scripts.

From `load-tests/`, use `npm ci` when reproducing the supplied lockfile.
Use `npm install` when intentionally changing dependencies, then retain the updated
lockfile. Neither is required before every test run.

| Command from load-tests/ | Actual behavior in the supplied package.json |
| --- | --- |
| `npm run typecheck` | Runs `tsc --noEmit` |
| `npm run smoke:auth` | Runs k6 only; no preceding type check |
| `npm run test:smoke` | Checks types, then runs authentication smoke |
| `npm run baseline:stats` | Checks types, then runs the read workload; defaults to 1 VU unless VUS is set |
| `npm run baseline:stats:1vu` | Checks types and exports stats-1vu.json; relies on default VUS=1, so an inherited VUS changes its load |
| `npm run baseline:stats:5vu` | Checks types, explicitly sets VUS=5 and exports stats-5vu.json |
| `npm run baseline:stats:20vu` | Checks types, explicitly sets VUS=20 and exports stats-20vu.json |

`tsc` is the TypeScript compiler; `--noEmit` requests type checking without
JavaScript output. The same setting is in tsconfig.json. `npx tsc` resolves the
local compiler; npm scripts automatically put local executables on PATH. k6
runs TypeScript but does not replace a separate type check. The npm script
`test:auth` does not exist in this archive; use `test:smoke`.

Run the existing shortcuts on Ubuntu:

```bash
npm run test:smoke
mkdir -p results
VUS=1 npm run baseline:stats:1vu
npm run baseline:stats:5vu
npm run baseline:stats:20vu
```

These are separate sequential runs. The explicit VUS=1 prevents an inherited
variable from changing the first run. Each shortcut overwrites its named JSON.
For a custom filename: `npm run baseline:stats -- --summary-export=results/custom.json`.
The `--` forwards arguments to k6; it does not select a VU count. `VUS=5` is
an environment setting, and the current shortcuts use Ubuntu/POSIX shell syntax.

## Authentication smoke — completed by user

Script: `load-tests/smoke-auth.ts`. One VU performs one iteration with eight
sequential HTTP requests: CSRF initialization, registration, login, CSRF renewal,
profile read, statistics read, logout and a protected read expecting 401.
Login resets CSRF, so the script obtains the current token before further writes.
The intentional final 401 is explicitly classified as expected.

All checks and zero unexpected HTTP failures are required. The user reports a
successful iteration taking approximately 0.7 seconds. That duration includes the
whole sequence; it is not a single-endpoint latency or a capacity claim. Each run
leaves one new test Player. Normal logout invalidates its session.

## Statistics-read baseline — completed initial runs

Script: `load-tests/read-statistics.ts`. Setup sequentially creates/logs in one
new Player per VU and captures its session cookie. Each VU receives its own
session. The script explicitly transfers cookies from setup data to VU cookie
jars and retains them between iterations using noCookiesReset.

There is one constant-VU scenario: 20 seconds of warm-up and a nominal 60-second
measurement window, with up to 12 seconds of graceful stop. Each VU sends one
GET `/api/v1/players/me/statistics`, checks status/JSON/Player ID/zero games,
then sleeps one second. Requests are sequential within a VU; VUs run concurrently.
Setup and teardown are sequential. The script accepts VUS from 1 through 20.

A request's phase is assigned when dispatched using shared scenario start time.
A slow warm-up request finishing during measurement stays excluded. Measured
requests may finish during graceful stop; interrupted iterations must be inspected.
Checks and ordinary HTTP metrics span the whole run. The custom metrics below
exclude setup, warm-up and teardown.

| Metric | Interpretation |
| --- | --- |
| measured_read_requests | Count of completed measured-window requests |
| measured_read_failures | Fraction with wrong status, invalid JSON or unexpected Player data |
| measured_read_duration | Measured reads' sending + waiting + receiving time, in ms; excludes initial connection establishment |
| http_req_failed | Unexpected HTTP failures across all phases; does not independently validate response JSON |

For a Rate metric, zero measured_read_failures means none of the added boolean
failure samples were true. The legacy JSON fields passes/fails count true/false
samples; they must not be read as application assertions succeeding/failing.

Estimate measured RPS as `measured_read_requests / 60`. The Counter rate printed
by k6 in these exports uses the longer overall run duration, so it is not the
measurement-window RPS. Think time makes 1/5/20 VUs roughly 1/5/20 RPS under quick
responses; exact finite-window counts vary with scheduling and boundaries.

### Recorded results

Values below are milliseconds, rounded to two decimals. Console-only rows retain
the user's displayed values. Saved JSON rows are rounded from stored precision.

| Run and evidence | Measured reads | Approx. RPS | p50 | p95 | p99 | Maximum | Measured failures |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 1 VU — user console, no JSON supplied | 59 | 0.98 | 13.28 | 14.78 | 18.58 | 20.82 | 0 / 59 |
| 5 VUs — earlier user console | 296 | 4.93 | 9.55 | 16.87 | 33.00 | 47.88 | 0 / 296 |
| 5 VUs — supplied stats-5vu.json, different run | 290 | 4.83 | 10.53 | 66.72 | 639.65 | 768.51 | 0 / 290 |
| 20 VUs — supplied stats-20vu.json | 1,200 | 20.00 | 8.62 | 16.31 | 27.15 | 37.37 | 0 / 1,200 |

The saved 5-VU export contains 415 total HTTP requests; the saved 20-VU export
contains 1,700. Both record zero unexpected HTTP failures and zero failed checks.
Their unfiltered HTTP distributions include authentication and warm-up, so they
are not substitutes for measured_read_duration.

### Findings and limits

The paced read workload completed without measured failures in the reported runs.
The saved 5-VU run has a much higher latency tail than the earlier console run
and the 20-VU run. The cause is unestablished: no correlated CPU, GC, database,
connection-pool or generator observations were supplied. Do not attribute it to
load, GC or warm-up without evidence, omit it, or combine it with another run.
The archive does not establish the saved 5-VU run's order relative to other runs.

The 20-VU run achieved approximately 20 measured reads/second. That does not
establish the maximum sustainable rate, a production user limit, scalability,
or a lower latency caused by higher concurrency. One short run per condition
and small samples give weak evidence for tail comparisons. No latency SLO or
performance threshold has been selected. Existing thresholds require all checks,
zero unexpected HTTP failures, at least one measured read and zero measured
failures; passing them is a correctness condition for this baseline.

All measured Players have zero games and the statistics path returns a small
response. No substantial history, pairing, moves, rating writes, legal generation,
WebSocket clients, notification receipt, browser rendering, remote network or
multi-instance setup is measured. Local services compete for shared hardware.

## Export handling — correction based on the supplied files

The supplied `--summary-export` JSON includes a top-level `setup_data` containing
fixture Player IDs and sessionCookie values. The earlier statement that these
exports contained aggregate metrics only was incorrect for this setup. Normal
teardown logs these sessions out, but raw summaries should still remain local.
Do not publish raw session data or HTTP debug logs.

Before sharing or committing a summary, write a copy excluding setup_data.
From load-tests/, for example:

```bash
python3 - <<'PY'
import json
from pathlib import Path

source = Path('results/stats-20vu.json')
data = json.loads(source.read_text())
data.pop('setup_data', None)
destination = source.with_name(source.stem + '-sanitized.json')
destination.write_text(json.dumps(data, indent=2) + '\n')
print(destination)
PY
```

Change the source filename for the 5-VU export. Inspect that the sanitized output
has no setup_data before sharing it. Keep the raw source files out of the commit;
if already staged, unstage them. This documentation replacement does not alter
or remove the user's JSON files, source or git staging. A future custom summary
writer can prevent setup data from entering shareable reports in the first place.

## Next increment

Implement and smoke-test one pair of Players through matchmaking, game reads,
alternating legal moves and resignation. Then exercise multiple independent
games. That workload has not yet been implemented or executed. Preserve existing
PGN UI, promotion, replay, timeout, rating and session deferrals. No optimization
is claimed from these initial reads; investigate a demonstrated bottleneck only
when the evidence supports it.

## References

- [k6 metrics](https://grafana.com/docs/k6/latest/using-k6/metrics/reference/)
- [Open and closed workloads](https://grafana.com/docs/k6/latest/using-k6/scenarios/concepts/open-vs-closed/)
- [Test lifecycle](https://grafana.com/docs/k6/latest/using-k6/test-lifecycle/)
- [TypeScript support](https://grafana.com/docs/k6/latest/using-k6/javascript-typescript-compatibility-mode/)
