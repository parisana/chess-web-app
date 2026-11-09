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
Subsequent user uploads supplied game-smoke.json, games-1/5/20/50.json and a
50-game system log in results.zip, then games-100.json, its system log and
local-environment.txt separately. Those game JSON summaries have now been
inspected. No newer full source archive or assistant execution is claimed.
Console values below retain their originally displayed precision where noted.
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
| Generator | Captured k6 v2.3.0, linux/amd64 |
| Tooling in supplied lockfile | TypeScript 7.0.2; `@types/k6` 2.3.1 |
| Hardware arrangement | Generator, backend and PostgreSQL share one computer |
| Current inventory | Ryzen 3 3200G, 4 cores / 4 logical CPUs; 15 GiB OS-visible RAM; shell Java 21.0.1; psql client 14.24 |
| Still unconfirmed | Actual backend JVM/toolchain, PostgreSQL server version, JVM options and per-process resource/GC/pool attribution |

The npm type definitions are not the k6 executable and do not establish its
version. The new inventory confirms the versions listed above. Shell Java may
differ from the backend toolchain, and psql is a client version, not a server
version. Keep launch method and configuration consistent; do not replace
missing process/runtime evidence with an older machine description.

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
| `npm run baseline:stats` | Checks types, runs reads through the aggregate-report runner and writes stats.json; defaults to 1 VU unless VUS is set |
| `npm run baseline:stats:1vu` | Checks types, explicitly sets VUS=1 and writes aggregate stats-1vu.json |
| `npm run baseline:stats:5vu` | Checks types, explicitly sets VUS=5 and writes aggregate stats-5vu.json |
| `npm run baseline:stats:20vu` | Checks types, explicitly sets VUS=20 and writes aggregate stats-20vu.json |
| `npm run smoke:game` | Runs one game smoke through the aggregate-report runner; no preceding type check |
| `npm run test:game` | Checks types, then runs game smoke and writes results/game-smoke.json |

`tsc` is the TypeScript compiler; `--noEmit` requests type checking without
JavaScript output. The same setting is in tsconfig.json. `npx tsc` resolves the
local compiler; npm scripts automatically put local executables on PATH. k6
runs TypeScript but does not replace a separate type check. The npm script
`test:auth` does not exist; use `test:smoke`.

Run the existing shortcuts on Ubuntu:

```bash
npm run test:smoke
mkdir -p results
npm run baseline:stats:1vu
npm run baseline:stats:5vu
npm run baseline:stats:20vu
```

These are separate sequential runs. The named 1/5/20-VU scripts explicitly set
their loads and overwrite their named aggregate reports. The runner creates
output directories automatically. The old --summary-export shortcut commands
are superseded: use the existing npm entry points, or
`node run-k6.mjs --report results/custom.json read-statistics.ts`. `VUS=5` is
an environment setting; the current shortcuts use Ubuntu/POSIX shell syntax.
The npm -- separator forwards arguments but does not select the VU count.

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
Setup and teardown are sequential. The statistics script accepts VUS from 1 through 20; the game script uses the separate GAMES parameter.

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

## Increment 3 — One-game smoke and aggregate reports

The supplied increment adds smoke-game.ts and run-k6.mjs, and updates package.json
scripts without changing dependencies. Assistant execution status: not run.
User execution status: passed; result recorded below. The earlier statistics measurements are historical
results; they are not measurements of this game workload or revised runner.

Run `npm run test:game` from load-tests/ with only the loadtest backend active.
The command checks types and runs one VU/one iteration, orchestrating two Players
using two explicit local cookie jars. A VU represents the game orchestrator here,
not one human Player. Normal requests are sequential. This is a functional smoke,
not simultaneous client load or proof of WebSocket delivery.

The script registers/logs in two new Players, refreshes their CSRF tokens, checks
that they are not queued, queues them and verifies matching to the same game.
The first join must WAIT and the second must MATCH; use an otherwise empty test
queue. Colors are selected from the returned game, not assumed from join order.
It submits e4/e5/Nf3/Nc6 using each accepted version, and the opponent performs a
GET after each move. It checks persisted coordinate/SAN history, then White
resigns. Both game views must show BLACK_WIN/RESIGNATION, the same board and an
advanced version. History must still have four moves at the new captured version.
Both queue statuses must be NOT_QUEUED and statistics must count one loss/win.
This checks outcome counts, not the precise numerical Elo adjustment.

The smoke_games_completed threshold requires exactly one fully checked game,
alongside all checks passing and zero unexpected HTTP failures. Normal success
makes 33 HTTP requests including authentication, reads and logout. There is no
latency threshold. Report output: results/game-smoke.json.

The finally block attempts logout for successfully logged-in clients, even if
an assertion fails. It does not retry game commands, silently resign after an
uncertain command, delete fixtures or cancel a match. A failed smoke may leave
an active game or queued Player; inspect it before rerunning. The usual timed
game can expire, but queue entries have no automatic expiry. Do not run it beside
another matchmaking workload; it intentionally asserts an isolated pair.

The Node runner uses the locally installed k6 executable, keeps its normal
console output and requests a raw legacy summary in a private temporary directory.
It writes only metrics and root_group to the requested report and removes the
raw temporary directory in normal/failure-finally handling. It preserves k6's
nonzero exit status. A missing/unexpected summary is an error, not a passing
result. There is no remote summary-library import or new npm dependency.
An abrupt process kill can prevent cleanup of a private temporary directory.

Do not pass --summary-export to the runner; it rejects that option to prevent
a second raw output. The runner's --report selects the shareable output path.
Existing raw files remain unchanged until overwritten or explicitly sanitized;
the earlier sanitizer is still useful for archived exports. Both old sanitized
legacy reports and new reports expose metrics/root_group; this runner does not
switch to k6's optional new machine-readable summary format.

### Recorded game-smoke result

User-run game smoke passed: one iteration, one checked game, 33 HTTP requests,
zero unexpected HTTP failures and an iteration duration of 844.53 ms.
Reaching smoke_games_completed=1 means the preceding workflow checks passed.
This verifies the scripted HTTP lifecycle; it does not establish concurrent-game
capacity or WebSocket delivery. The average HTTP duration mixes auth/reads/writes.

## Increment 4 — Concurrent independent games

Script: load-tests/concurrent-games.ts. User reports completed batches at 1, 5
and 20 games; console results recorded below. No assistant compiler, test,
application or database execution. Apply this load-test increment
after the game-smoke increment, which provides run-k6.mjs.

### Workload and exclusions

Setup sequentially creates two distinct Players/sessions per game, joins them to
an otherwise empty queue and records their actual random colors. Each game is
paired completely before setting up the next. This prevents cross-pairing between
VUs and excludes queue/authentication work from the custom gameplay timings.
Fixtures hold session data in memory; only the aggregate runner output should
be shared. No browser or WebSocket client connects.

Each local VU receives one pre-paired game and drives both Player sessions.
One VU does not mean one Player: 5 VUs means 5 games and 10 Player sessions.
VUs run concurrently, while commands and reads within a game are sequential.
Each VU performs exactly one iteration, not a repeating arrival stream.

The game has 20 plies (individual moves): e4/e5/Nf3/Nc6, then four cycles of
Ng1/Nb8/Nf3/Nc6. After each accepted move the opponent reads the game by HTTP;
there is a one-second pause before the next move except after the final move.
Moves use each accepted version. The current rules deliberately lack repetition
and move-count draws, so these reversible cycles remain legal. Update the fixture
if those policies are implemented. These repetitive positions do not represent
a broad game corpus or realistic move selection.

The VU checks complete ordered coordinate/SAN history, resigns White, confirms
the opponent's final read and unchanged board/history, checks both reservation
releases and verifies one win/loss in Player statistics. It checks outcome counts,
not the precise Elo formula. Every pair has separate statistics rows; this does
not measure contention on the same Player's rating row. Resignation does exercise
the synchronous completion/statistics transaction for independent games.

### Metrics and terms

| Metric / term | Meaning |
| --- | --- |
| Batch workload | A fixed set of game journeys, not a sustained fixed-duration throughput test |
| Ply | One Player's move; 20 plies are 10 full move pairs |
| games_completed | Game journeys that reached every final verification |
| gameplay_move_requests | Move POST attempts during the game phase; expect 20 per game |
| gameplay_failures | Fraction of game iterations that entered the journey but did not complete all its checks |
| gameplay_move_duration | Move POST sending/waiting/receiving time; excludes setup and intentional pauses |
| gameplay_game_read_duration | Gameplay game-detail GET durations, including opponent reads after moves and one final read |
| gameplay_resignation_duration | Resignation POST duration, including server-side completion/statistics processing |
| gameplay_game_duration | Whole per-VU journey duration including its 19 one-second pauses and checks |

All durations are milliseconds. No warm-up is excluded within this game batch;
first moves and later moves share the same per-operation distribution. A response
time includes network/client aspects and is not a pure rules-engine CPU timing.
Only one resignation sample per game exists; do not give its p95/p99 strong
statistical meaning with 1/5/20 samples. An isolated one-game journey should take
at least approximately 19 seconds because of its deliberate pauses.

Global HTTP metrics also include setup/auth/queue and teardown. Counter rates
printed by k6 use overall elapsed run time; there is no 60-second measurement
window here. Do not divide game counters by 60 or equate displayed games/second
with sustainable capacity. This first batch checks independent concurrent game
behavior; a longer repeated/arrival-rate workload would be separate work.

Expected normal-success counts:

| Games / VUs | Player sessions | Move POSTs | Gameplay game-detail GETs | Resignations | Total HTTP requests |
| --- | --- | --- | --- | --- | --- |
| 1 | 2 | 20 | 21 | 1 | 62 |
| 5 | 10 | 100 | 105 | 5 | 310 |
| 20 | 40 | 400 | 420 | 20 | 1,240 |

Thresholds require every check to pass, zero unexpected HTTP failures, exactly
GAMES completions, exactly 20*GAMES move attempts and zero game-journey failures.
A caught assertion/error is not retried. Missing/completely unstarted iterations
are caught by completion counts. The scheduler allows up to three minutes for
the batch. Setup originally allowed three minutes for the 1/5/20 runs; Increment
5 raises it to four minutes. Teardown remains two minutes.
Neither a latency target nor a capacity claim is introduced.

### Apply and run

Add concurrent-games.ts and replace package.json with the supplied version,
retaining any subsequent custom scripts. Dependencies are unchanged; no npm
install or package-lock update is needed for script-only changes. run-k6.mjs
from the previous increment must remain present. The new commands run type
checking first and write aggregate reports without setup_data.

From load-tests/, with only the loadtest-profile backend running on 8081:

```bash
npm run baseline:games:1
npm run baseline:games:5
```

Wait for the first run to finish and inspect any failure before running the next.
Share games_completed, gameplay_move_requests, gameplay_failures, the three
per-operation duration metrics, total HTTP errors and resource observations.
Once these pass, `npm run baseline:games:20` is available as the next step.
Reports are results/games-1.json, games-5.json and games-20.json; reruns overwrite
the corresponding file. The runner creates directories automatically.

Teardown attempts logout for all successfully returned fixtures. It does not
retry moves, resign after an uncertain command, delete Players/games or clear
the queue. Failed setup may leave queued/active fixtures and sessions because
setup did not return; interrupted journeys may leave timed active games. Inspect
these before rerunning. Normal success leaves 2*GAMES Players and GAMES finished
games with 20 history rows each. Clocks start at pairing during setup and continue
while later fixtures are prepared; the ordinary 10-minute clock is unchanged.

### Recorded concurrent-game results

The initial rows were user-pasted console reports in the sequence 1, then 5,
then 20 games. The later results.zip now supplies all three JSON summaries;
their measurements agree apart from console truncation/rounding. Values in
these tables preserve original console precision. All three JSON summaries
record zero unexpected HTTP failures, zero failed checks and HTTP totals of
62/310/1,240 respectively. Check totals are 164/820/3,280. No system logs were
supplied for those first three batches.

| Games / VUs | Player sessions by design | Completed | Move attempts | Game failures | Average journey duration |
| --- | --- | --- | --- | --- | --- |
| 1 | 2 | 1 | 20 | 0 / 1 | 19.60 s |
| 5 | 10 | 5 | 100 | 0 / 5 | 19.81 s |
| 20 | 40 | 20 | 400 | 0 / 20 | 20.07 s |

Each game had 20 plies: four opening plies plus four knight cycles of four plies
(4 + 4*4 = 20). There were 19 one-second pauses, because the final move skips
sleep. Pauses overlap across VUs, so they do not add to 19*GAMES wall-clock seconds.
Journey duration includes pauses and request/check overhead, but excludes setup
and teardown. Subtracting pauses does not isolate backend CPU processing time.

Move POST timings, milliseconds:

| Games | Samples / attempts | Average | p50 | p95 | p99 | Maximum |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | 20 | 19.69 | 16.56 | 31.08 | 33.09 | 33.59 |
| 5 | 100 | 27.91 | 26.43 | 43.80 | 50.56 | 67.08 |
| 20 | 400 | 36.65 | 23.51 | 98.09 | 125.36 | 157.28 |

Game-detail GET timings, milliseconds (opponent reads after moves plus final read):

| Games | Average | p50 | p95 | p99 | Maximum |
| --- | --- | --- | --- | --- | --- |
| 1 | 5.59 | 5.57 | 7.32 | 7.80 | 7.91 |
| 5 | 7.26 | 6.20 | 12.82 | 26.80 | 28.96 |
| 20 | 9.42 | 5.68 | 26.94 | 48.77 | 89.39 |

Resignation POST timings, milliseconds:

| Games | Samples by design | Average | p50 | Maximum |
| --- | --- | --- | --- | --- |
| 1 | 1 | 17.04 | 17.04 | 17.04 |
| 5 | 5 | 16.25 | 16.95 | 17.93 |
| 20 | 20 | 27.08 | 20.55 | 72.70 |

Resignation has too few samples for strong tail claims. Move/read tail estimates
also remain preliminary. Each condition has one short reported batch, no excluded
in-game warm-up, a small repeating position set and no correlated resource data.

### Interpretation

All requested game journeys reached their completion checks: 1/5/20 games,
20/100/400 move attempts and zero recorded game-journey failures. This gives
functional evidence for independent concurrent game commands, committed HTTP
reads, history and outcome-count verification under the supplied fixture.
The later JSON summaries also confirm zero failed checks/unexpected HTTP
failures across the whole first-three runs, including their cleanup requests.

Move p95 increased from 31.08 to 43.80 to 98.09 ms across these runs. Game-read
p95 increased from 7.32 to 12.82 to 26.94 ms. Thus the slower response tail was
higher in the higher-concurrency batches. Median timings were not monotonic:
the 20-game move median was lower than the 5-game median. Do not describe all
latencies as uniformly increasing or claim that higher concurrency improved them.

The average 20-game journey was 20.07 seconds, including 19 seconds of intended
pauses. The printed 13.684671 move attempts/second and 0.684234 games/second in
that run span the overall duration including sequential setup and teardown;
neither is a measured sustainable gameplay rate or production capacity.

No causal bottleneck is identified. CPU scheduling, JVM/GC, database/pool work,
generator activity and run order have not been correlated with slow requests.
No code optimization is justified solely by these summaries. Preserve this
baseline and collect environment/resource evidence if investigating further.

## Increment 5 — Larger local batches with system observations

Scope selected by the user: remain on the local machine, extend game batches
beyond 20 and record a future deployment-VM TODO. Run 50 first; review its
results/resource observations before 100. Both larger batches have now been user-executed; JSON summaries and
system samples are inspected below. The assistant did not execute them. No 1,000-game run is enabled or claimed.

The script now accepts GAMES from 1 through 100. New npm entry points
baseline:games:50 and baseline:games:100 preserve the existing type-check-first,
aggregate-report behavior. No dependencies change. Setup timeout increases
from three to four minutes for sequential creation/authentication of more
fixtures. The gameplay limit remains three minutes and teardown two minutes.
The ordinary 10-minute clocks are unchanged. Clocks start during setup; a longer
setup allowance is not a reason to ignore an expiry. If the current clock
configuration differs from 10 minutes, reassess this timing budget first.

Moves, pause count, per-VU game count, custom metrics, assertions and request
timeouts are otherwise unchanged. There is still no excluded in-game warm-up.
The new timestamps mark setup completion, VU 1's gameplay entry and teardown
start. They carry no fixture/session data. These are approximate phase markers,
not proof that every VU starts simultaneously or an exact response-time trace.

| Games / VUs | Player sessions | Move attempts | Gameplay game-detail GETs | Resignations | Expected total HTTP requests |
| --- | --- | --- | --- | --- | --- |
| 50 | 100 | 1,000 | 1,050 | 50 | 3,100 |
| 100 | 200 | 2,000 | 2,100 | 100 | 6,200 |

This remains a finite 20-ply game journey with 19 one-second pauses per VU.
Its concurrency parameter is active game orchestrators, not requests in flight
or connected WebSocket clients. VUs control both sessions. The script connects
no WebSocket clients and pairs fixtures sequentially before gameplay.

### Capture the environment once

From load-tests/, before the new batches:

```bash
mkdir -p results
lscpu > results/local-environment.txt
free -h >> results/local-environment.txt
k6 version >> results/local-environment.txt
java -version >> results/local-environment.txt 2>&1
psql --version >> results/local-environment.txt
```

The Java executable in this shell might differ from the Gradle toolchain; record
the backend's actual JVM from its startup output as well. psql --version records
the client, not the server: record the actual database server version separately
when making reproducibility claims. The user's last reported hardware is a
Ryzen 3 3200G APU/16 GB RAM; use the current inventory for authoritative details.
No GPU acceleration is used by these scripts.

### Record system activity during the 50-game batch

In terminal A, from load-tests/, start a one-second system sampler:

```bash
vmstat -w -t 1 | tee results/games-50-system.txt
```

In terminal B, also from load-tests/, run:

```bash
npm run baseline:games:50
```

Once k6 and report writing finish, stop only the sampler in terminal A with
Ctrl+C. Leave the backend running. The aggregate report is results/games-50.json.
The VM-statistics command does not create a VM; vmstat is an OS resource sampler.

The first vmstat data row reports CPU/rate averages since boot. Exclude it from
this run's interval interpretation; later rows reflect one-second samples.
Memory/process fields are snapshots. Use timestamps and k6 phase messages to
distinguish authentication/setup activity from gameplay and teardown. These
samples cover the whole machine, including the generator, backend, database,
desktop and unrelated applications. They do not identify which process caused
an observed saturation or a slow request. An interactive top/htop view can add
process-level observations; save concrete observations rather than inferring
which component is responsible from an aggregate graph.

| vmstat field | Meaning and useful interpretation |
| --- | --- |
| r | Runnable tasks, including tasks waiting for CPU; compare sustained values with the machine's logical CPU count |
| b | Tasks blocked waiting for I/O |
| us / sy | CPU time in user / kernel execution |
| id | Idle CPU percentage; sustained values near zero suggest little aggregate CPU headroom |
| wa | CPU accounting for I/O wait; not a direct disk-utilization percentage or proof of a database bottleneck |
| si / so | Swap-in / swap-out per second; sustained activity is evidence to investigate memory pressure |
| free / buff / cache | Memory accounting; low free memory alone is not exhaustion because cache can be reclaimable |

Keep the same backend launch method/configuration and avoid a debugger or verbose
SQL logs. If there are failed checks, timeouts, interrupted iterations, a setup
timeout, heavy sustained swapping or an unresponsive desktop, stop escalating
and share the evidence. A brief CPU spike or one nonzero swap sample alone is
not an established bottleneck. An aborted/failed run may leave queued/active
fixtures and sessions; inspect them before rerunning, as with previous batches.
No automatic command retry or cleanup resignation is introduced.

### Then consider 100 games

After reviewing the 50-game run, use the same two-terminal procedure with
results/games-100-system.txt and npm run baseline:games:100. Do not start both
batches concurrently. Existing result files are overwritten if the same command
is repeated; keep separately named copies if retaining multiple observations.
The setup dataset is copied to each VU, which adds generator overhead as GAMES
grows. The 100 cap bounds this increment; it is not a certified local capacity.
Redesign fixture distribution/setup before considering much larger runs.

### Larger-batch recorded results

Evidence: games-50.json and games-50-system.txt from results.zip; separately
uploaded games-100.json, games-100-system.txt and local-environment.txt.
New summaries contain metrics/root_group and omit setup_data. Earlier raw
statistics summaries in results.zip still include setup_data; sanitize those
historical files before committing or sharing them.

JSON-derived timings below are rounded to two decimals. Each batch ran once.
No application run, compiler, test or database operation was executed by the
assistant while reviewing these artifacts.

| Games | Completed | Move attempts | HTTP requests | Checks passed | Checks failed | HTTP / gameplay failures |
| --- | --- | --- | --- | --- | --- | --- |
| 50 | 50 | 1,000 | 3,100 | 8,200 | 0 | 0 / 0 |
| 100 | 100 | 2,000 | 6,200 | 16,400 | 0 | 0 / 0 |

| Metric | 50 games | 100 games |
| --- | --- | --- |
| Move average | 75.88 ms | 119.00 ms |
| Move p50 | 76.13 ms | 78.77 ms |
| Move p95 | 171.17 ms | 399.17 ms |
| Move p99 | 208.23 ms | 780.80 ms |
| Move maximum | 250.13 ms | 1,059.50 ms |
| Game-read average | 21.81 ms | 39.48 ms |
| Game-read p50 | 9.86 ms | 15.36 ms |
| Game-read p95 | 81.20 ms | 155.20 ms |
| Game-read p99 | 134.87 ms | 302.08 ms |
| Game-read maximum | 181.89 ms | 673.24 ms |
| Resignation average | 50.51 ms | 87.04 ms |
| Resignation p95 | 140.34 ms | 225.06 ms |
| Average game journey, including 19 seconds of pauses | 21.21 s | 22.61 s |

The 100-game summary reports 28.173504 move attempts/second and 1.408675
completed games/second across the overall run, including sequential setup and
teardown. Those are not sustainable gameplay-capacity measurements. They must
not be compared directly with the earlier 60-second statistics-read window.

### Captured system activity and inventory

The 50-game capture has 62 interval rows after excluding the initial since-boot
row. The 100-game capture has 81. Statistics below describe their entire captures,
which include lead-in/setup/gameplay/cleanup activity. Phase timestamps were not
included in the uploaded console logs, so an exact gameplay-only system interval
cannot be recovered from the supplied artifacts alone. One-second samples also
cannot capture every short spike or link a request to a process.

| Whole-capture observation | 50 games | 100 games |
| --- | --- | --- |
| Minimum CPU idle | 19% | 3% |
| Median CPU idle | 60.5% | 56% |
| Interval samples with idle below 10% | 0 / 62 | 2 / 81 |
| Maximum runnable-task count r | 18 | 34 |
| Maximum I/O wait wa | 3% | 11% |
| Swap-in / swap-out | Zero throughout | Zero throughout |
| Existing swapped memory | Stable 1,024 KiB | Stable 1,024 KiB |

The 100-game CPU-idle lows occurred in consecutive samples at 15:15:22 and
15:15:23 IST, at 3% and 4% idle. This is evidence of brief near-saturation on the
shared machine, not sustained saturation throughout gameplay. The maximum
runnable count of 34 is a snapshot; the median was 1. An 11% I/O-wait sample is
not a disk-utilization percentage or proof of database storage contention.
No swap traffic was observed in either capture.

The new inventory confirms Ryzen 3 3200G, 4 cores with one thread each, and
15 GiB OS-visible RAM. The free -h snapshot reports 6.3 GiB available RAM and
1 MiB of swap occupied, consistent with cache rather than an exhaustion claim
based on low free memory alone. That snapshot is not the minimum available
memory during the workload. It also records k6 2.3.0 and shell Java 21.0.1, plus
psql client 14.24. The actual PostgreSQL server/backend JVM configuration remains
to be recorded before claiming fully reproducible deployment comparisons.

### Interpretation and checkpoint

Both batches completed all configured correctness checks, but latency increased.
The 100-game move p50 was only slightly above the 50-game value, while p95 rose
from 171.17 to 399.17 ms and p99 from 208.23 to 780.80 ms. The slower tail grew
more than the median. The maximum move exceeded one second. No latency threshold
had been selected, so a passing test must not be described as a latency-SLO pass.

Brief CPU pressure and I/O wait are observed; there is no correlated per-process,
JVM/GC, connection-pool or database-lock trace identifying their causes or the
cause of the slowest requests. Generator, backend, PostgreSQL and desktop share
hardware. Do not infer linear scaling, a production-user limit, a 1,000-game
capacity or a particular required optimization from these finite batches.

This completes the selected local 50/100 extension. Preserve the baseline and
review resource attribution before considering further escalation. A future
focused investigation can collect process/JVM/database evidence; no further
load increase, instrumentation stack or optimization is claimed completed here.
The deferred VM sizing/separate-generator TODO remains in roadmap.md.

## Increment 6 — 100-game diagnostic recording

User supplied diagnosticFiles.zip with k6 JSON/console, pidstat log, backend JVM
identity and JFR exports/recording. The assistant parsed supplied JSON/text;
no application/test/compiler run or independent binary JFR decoding occurred.
The profiled run passed 16,400 checks across 6,200 HTTP requests, 100 games and
2,000 moves, with no HTTP/gameplay failures. Move p95 452.19 ms, p99 858.51 ms,
maximum 1,112.60 ms; game-read p95 187.94 ms. This instrumented run is kept
separate from the unprofiled 100-game baseline (move p95 399.17 ms).

The phase-aligned gameplay window is approximately 16:29:28.595–16:29:53.645 IST.
Recorded gameplay evidence includes 6,201 Hikari acquisition ThreadPark events
(maximum 682 ms), and rule/board lookup frames in 357 of 732 execution samples.
GCPhasePause totals approximately 210.79 ms, maximum 18.5 ms. Backend CPU averages
148.16% in pidstat units (about 1.48 cores), versus k6 12.28% and summed PostgreSQL
24.96%. These observations support pool-wait and rule-computation investigation,
not a complete attribution of every slow request. See [the diagnostic review](day29-diagnostic-review.md)
for methods, glossary, overlapping waits, sampling limitations and next experiment.
Its Reproduce the diagnostic recording section contains the complete jcmd,
pidstat, k6 and export commands; Where to see the evidence yourself maps each
finding to files, example events and search commands.

The next proposed bounded experiment confirms the effective pool configuration
and compares one moderate alternative in loadtest only, without simultaneous
rule/storage changes. No pool change or measured optimization is implemented
in this documentation checkpoint. Do not interpret BCrypt-dominated setup
samples as password hashing during moves, or total GC event duration as pauses.

## Baseline checkpoint and remaining work

The planned initial HTTP baseline is recorded: authentication smoke, paced
statistics reads, one-game smoke and 1/5/20 independent concurrent game batches.
There is no need to repeat passing runs solely for this documentation update. The selected local 50-/100-game extension is also recorded above.
Retain the captured inventory and sanitized summaries; record the remaining
actual backend JVM/database server configuration for fuller reproducibility. These results do not replace the ordinary application tests.

Further matchmaking contention, WebSocket connection/delivery load, substantial
history fixtures, longer repeated/arrival-rate workloads, stress/soak tests and
observability instrumentation are separate choices, not claimed completed here.
No production capacity, end-to-end browser latency, distributed routing or
maximum sustainable rate is established. Preserve PGN UI, promotion, replay,
timeout, rating and session deferrals. Day 30 acceptance/presentation/release
remains planned; deployment is deferred to the VM TODO in roadmap.md, with provisional sizing estimates.

## References

- [k6 metrics](https://grafana.com/docs/k6/latest/using-k6/metrics/reference/)
- [Open and closed workloads](https://grafana.com/docs/k6/latest/using-k6/scenarios/concepts/open-vs-closed/)
- [Test lifecycle](https://grafana.com/docs/k6/latest/using-k6/test-lifecycle/)
- [TypeScript support](https://grafana.com/docs/k6/latest/using-k6/javascript-typescript-compatibility-mode/)

- [vmstat manual](https://man7.org/linux/man-pages/man8/vmstat.8.html)

## Pool comparison — labelled results reviewed

The reproducible 10-versus-20 fixed-capacity procedure, warm-up/repeat commands,
effective-setting verification and result template are in
[day29-pool-comparison.md](day29-pool-comparison.md). No measured improvement
or runtime-setting claim for the old baseline is implied.

### Pool-comparison result update

The user supplied four aggregate reports labelled pool-10-a/b and pool-20-a/b.
All four pass 16,400 checks with 100 completed games and zero HTTP/gameplay
failures. Both pool-20 move averages/medians/p95s are above both pool-10 values;
move p95 ranges 224.86–233.61 ms versus 293.84–357.44 ms. Other endpoint timings
are less uniformly separated. This does not support adopting 20 for the local
workload. The user subsequently confirmed backend restarts with the corresponding
10/20 pool specifications; startup logs were not independently inspected.
Keep 10 as the explicit local test reference, not an
optimal-production-capacity conclusion. Full provenance, tables and confounders
are in [day29-pool-comparison.md](day29-pool-comparison.md). No code optimization
or new assistant execution occurred.

Configuration evidence update (6 November 2026): the user confirmed restarting
the backend for each corresponding 10/20 pool specification. This resolves the
comparison-setting confirmation request; no raw startup-log inspection or causal
performance proof is implied. Keep 10 as the local reference and preserve all
four reports. No repeat run is requested solely for this confirmation.

## Board lookup candidate — mixed timing result

The user supplied two HashMap-snapshot candidate reports after the instructed
constructor-only change, with pool 10 and the same 100-game workload specified.
Each passes all 16,400 checks with 100 games, 2,000 moves and zero HTTP/gameplay
failures. Both move medians/means are lower than the earlier Map.copyOf pool-10
references, but tail improvement is inconsistent: candidate move p95 143.90 and
271.59 ms versus reference 233.61/224.86 ms; candidate b p99 606.79 ms. Do not
claim a stable tail reduction or production adoption. No updated implementation
archive or candidate resource profile was supplied. A bounded reverse-order
confirmation is proposed before retaining the candidate as a performance win;
stop and record a mixed outcome if variability remains unresolved. No assistant
application/compiler/build/test execution occurred. See [board-map experiment](day29-board-map-experiment.md).
