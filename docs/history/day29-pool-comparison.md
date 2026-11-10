# Day 29 — Controlled connection-pool comparison

## Question and status

Does changing the fixed Hikari pool capacity from 10 to 20 improve the current
100-game workload on the same local machine without breaking correctness?
JFR established acquisition waiting, but not a universally correct pool size.
This increment supplies configuration/scripts/procedure. User supplied four labelled JSON reports; results are recorded below.
The user subsequently confirmed restarting the backend with the corresponding
10/20 pool specifications. Startup snippets and warm-up evidence were not included. No assistant application, compiler, build or test was run.

This establishes an explicit 10-connection reference; it does not retroactively
prove the effective pool setting in older runs. The original YAML had no
explicit Hikari capacity and the previous recording did not expose its value.

## Applied files and controlled variables

Merge the supplied application-loadtest.yml into the matching backend profile,
retaining any subsequent local settings. The supplied file preserves the known
8081/chess_loadtest/local HTTP configuration and adds only Hikari settings and
startup configuration logging. Ordinary application.yaml is unchanged.

maximum-pool-size and minimum-idle use the same LOADTEST_POOL_SIZE value, giving
a fixed-size target rather than adding a pool-growth policy as another variable.
Capacity is the experimental factor; both properties are printed at initialization.
Hikari normally defaults minimumIdle to maximumPoolSize. Explicit values avoid
relying on that default in this comparison. External Spring overrides can still
win, so confirm the actual printed values for each launch.

The code, positions, pauses, 100-game fixture, ten-minute clocks, connection/request
timeouts, database instance, JVM launch method and type-check/aggregate runner
behavior remain unchanged. No database connection limit, entity/rules algorithm,
HTTP route or WebSocket transport is changed. This applies to the shared datasource,
including JDBC sessions, not just game repositories.

package.json adds pool:warmup and pool:10:a/:b/pool:20:a/:b. Dependencies and
existing scripts are retained; no npm install/package-lock change is required
for script-only edits. Keep any extra custom scripts added after the supplied
source. The client script label selects a report filename only; it DOES NOT
change the backend pool. Restart the backend with the intended environment value.

## Procedure — explicit capacity 10

Stop the existing loadtest backend, and any JFR recording for it, normally.
From backend/, start:

```bash
SPRING_PROFILES_ACTIVE=loadtest LOADTEST_POOL_SIZE=10 ./gradlew bootRun
```

Wait for startup and initialization. Find HikariConfig DEBUG entries for
maximumPoolSize and minimumIdle; both must show 10. Save just these two lines
as results/pool-10-config.txt in load-tests/ or include them with the run report.
Do not label a result from its filename if the actual backend setting differs.
If startup fails or the values differ, resolve that before running load.

Keep this backend running. In another terminal, from load-tests/:

```bash
npm run pool:warmup
npm run pool:10:a
npm run pool:10:b
```

Run commands one after another. Inspect failures before proceeding. The warm-up
is five complete game journeys, with twenty moves each; it exercises rules and
completion after a fresh JVM start. Its report is pool-warmup.json and is excluded
from the comparison. Warm-up reduces startup effects but does not prove full
JIT/cache equilibrium. Each measured run uses new Players/games.

## Procedure — capacity 20

Stop the capacity-10 backend normally and restart from backend/:

```bash
SPRING_PROFILES_ACTIVE=loadtest LOADTEST_POOL_SIZE=20 ./gradlew bootRun
```

Confirm maximumPoolSize and minimumIdle both show 20; save those lines as
results/pool-20-config.txt. Then, from load-tests/:

```bash
npm run pool:warmup
npm run pool:20:a
npm run pool:20:b
```

The second warm-up replaces pool-warmup.json; it is not a measured result.
Do not restart the backend between a/b runs of the same setting. Do not run
both backend instances or workloads concurrently. Keep background work consistent,
SQL logging disabled and JFR off for all four measured runs. The HikariConfig
DEBUG logger prints configuration during initialization, not every SQL request.
Mission Control's previous recording can be inspected after the measurements.

If any run fails, retain its evidence and inspect remaining queue/active fixtures
before repeating; do not replay commands or change the workload to hide failures.
Do not raise PostgreSQL max_connections as part of this comparison. If an
external database limit prevents a 20-connection pool, record it as a constraint.

## Outputs and interpretation

Every measured run still expects 100 completed games, 2,000 move attempts,
6,200 HTTP requests, 16,400 passing checks and no HTTP/gameplay failures.
Reports are aggregate-only, written by run-k6.mjs with no setup_data:

- results/pool-10-a.json
- results/pool-10-b.json
- results/pool-20-a.json
- results/pool-20-b.json

Share those reports and the two effective-configuration snippets. Do not replace
existing unprofiled/diagnostic reports or combine their samples into these runs.

## Recorded comparison results

Evidence: user-supplied hikari-pool-test.zip, reviewed on 6 November 2026.
It contains only pool-10-a/b.json and pool-20-a/b.json. All have aggregate
metrics/root_group and no setup_data. The labels are supported by the user's subsequent confirmation that the backend
was restarted with the corresponding pool specifications. This is user-reported
configuration verification; the assistant did not inspect startup logs. Warm-up
completion, JFR status and background-resource consistency are not established
by the aggregate JSON alone.
No new source archive was supplied. No assistant test/application was run.

Each report records 100 completed games, 2,000 move attempts, 6,200 HTTP requests,
16,400 passing checks, zero failed checks and zero HTTP/gameplay failures.
The four reports together describe 400 completed game journeys, but their
percentiles must remain separate; no combined percentile was calculated.

| Label | Repeat | Move average ms | Move p50 ms | Move p95 ms | Move p99 ms | Read p95 ms | Resignation p95 ms | Average journey s |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| pool-10 | a | 94.62 | 77.62 | 233.61 | 374.93 | 149.97 | 266.48 | 22.26 |
| pool-10 | b | 88.02 | 66.70 | 224.86 | 273.27 | 122.95 | 149.04 | 21.71 |
| pool-20 | a | 156.15 | 147.60 | 357.44 | 538.31 | 156.17 | 210.15 | 23.37 |
| pool-20 | b | 146.41 | 157.63 | 293.84 | 342.29 | 138.27 | 173.43 | 23.08 |

Within these reports, both pool-20 move averages, medians and p95 values exceed
both pool-10 values. Reported move p95 ranges are 224.86–233.61 ms for pool-10
versus 293.84–357.44 ms for pool-20. Game-read p95 and resignation results are
less uniformly separated; do not claim every metric is worse with pool-20.
Both pool-20 average game journeys are longer, including the same nominal
19-second pause budget.

The evidence does not favor selecting 20 for this local workload. Retain the
explicit loadtest reference of 10 provisionally rather than adopting 20 merely
because earlier JFR showed acquisition waiting. The user has confirmed the corresponding backend restarts/settings; retain
that as user-reported verification rather than an assistant log inspection. No normal
deployment configuration change is justified by these local reports.

One plausible mechanism is that more available connections permit more game
transactions to perform CPU-heavy rule work concurrently, shifting waiting into
scheduling/database contention. That is an explanation to test, not an observed
cause: the ZIP has no matched process/JFR/pool-utilization recordings for the
four runs. A reduced pool wait has not been measured here either. The earlier
connection-wait observation does not guarantee that increasing capacity improves
end-to-end performance.

The b runs are faster than a in some metrics, and there is notable within-setting
variation (for example, pool-10 resignation p95). The sequence groups capacity
10 before 20, so run order and accumulated fixtures remain confounders. Do not
average the four p95 values, compare only the fastest run, or claim that 10 is
globally optimal. A reverse-order confirmation is useful if selecting a tuning
change, but no additional run is required to reject an unsupported promotion
to 20 for this checkpoint.

The user subsequently confirmed the backend was restarted with each corresponding
pool specification. No further confirmation or rerun is required solely for this
point. Describe this as a small local 10-versus-20 comparison with user-confirmed
settings that did not show benefit from 20, not a universal rule or independently
inspected configuration log. The earlier baseline's effective pool value remains
unverified; the confirmation applies to these comparison launches.

Compare both repetitions, correctness, tail timings and the other endpoints,
not only the single fastest run. Do not average percentile values and call that
the combined percentile. If results are noisy or improvement is borderline,
repeat in reverse configuration order before adopting a setting; run order,
new fixture accumulation and JVM/database caches remain potential confounders.

A larger pool can reduce acquisition waiting but increase runnable work and
pressure on PostgreSQL/CPU. Improvement is not assumed. Even a clear latency
improvement would not by itself prove that acquisition waiting was the entire
cause; a later similarly profiled comparison can test the waiting mechanism.
Keep board/rules changes out of this experiment. Do not change normal deployment
configuration merely because a local synthetic workload improves.

After analysis, choose or revert the loadtest setting explicitly and record why.
At this checkpoint 20 is not recommended from the labelled results; 10 remains
the explicit loadtest reference for this workload, with settings confirmed by
the user. This is not a claim of a globally optimal pool size.
No measured code optimization or deployment-capacity claim is made.

## References

- https://github.com/brettwooldridge/HikariCP#configuration-knobs-baby
- https://docs.spring.io/spring-boot/reference/data/sql.html

Configuration evidence update (6 November 2026): the user confirmed restarting
the backend for each corresponding 10/20 pool specification. This resolves the
comparison-setting confirmation request; no raw startup-log inspection or causal
performance proof is implied. Keep 10 as the local reference and preserve all
four reports. No repeat run is requested solely for this confirmation.
