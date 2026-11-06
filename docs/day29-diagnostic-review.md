# Day 29 — 100-game diagnostic review

## Evidence and scope

Reviewed on 6 November 2026 from the user-supplied diagnosticFiles.zip. The
archive contains the k6 JSON/console report, backend JVM identity, pidstat log,
JFR summary/filtered event text and the binary recording. Analysis here parsed
the provided JSON and text exports; the binary JFR was not independently decoded.
No assistant application, load test, build, compiler or database operation ran.
No new full implementation archive was supplied. Code comparisons refer to the
actual day29-mid archive plus the known supplied/user-applied test increments.

This is a diagnostic run with JFR profile settings and process sampling enabled.
It does not replace the unprofiled 100-game baseline. Instrumentation overhead,
run-to-run variation and accumulated fixtures can affect comparisons.

## Result

| Metric | Prior unprofiled 100-game run | Diagnostic 100-game run |
| --- | --- | --- |
| Games completed | 100 | 100 |
| Move attempts | 2,000 | 2,000 |
| HTTP requests | 6,200 | 6,200 |
| Checks passed / failed | 16,400 / 0 | 16,400 / 0 |
| HTTP / gameplay failures | 0 / 0 | 0 / 0 |
| Move average | 119.00 ms | 148.09 ms |
| Move p50 | 78.77 ms | 101.63 ms |
| Move p95 | 399.17 ms | 452.19 ms |
| Move p99 | 780.80 ms | 858.51 ms |
| Move maximum | 1,059.50 ms | 1,112.60 ms |
| Game-read p95 | 155.20 ms | 187.94 ms |
| Resignation p95 | 225.06 ms | 251.47 ms |
| Average game journey, including pauses | 22.61 s | 23.45 s |

The diagnostic run passed correctness checks but is slower in these metrics.
Do not attribute the entire difference to JFR or use it as evidence of a code
regression: no application optimization/change was made or controlled comparison
established. There is still no selected latency SLO or sustainable-capacity claim.

## Time alignment

Console timestamps explicitly identify:

| Marker | UTC | IST |
| --- | --- | --- |
| Setup complete | 10:59:28.574 | 16:29:28.574 |
| VU 1 gameplay entry | 10:59:28.595 | 16:29:28.595 |
| Teardown entry | 10:59:53.645 | 16:29:53.645 |

The analysis uses VU 1 entry to teardown entry as an approximately 25.05-second
workload window. VU 1's entry is an approximate marker, not proof of the exact
first request time across all VUs. JFR event text uses local IST, matching
pidstat's PM clock; the JFR summary start is UTC. Events are assigned by start
time. Long events may extend past the chosen boundary. The pidstat calculation
uses one-second reports timestamped inside this window; edge intervals may
partly straddle a boundary. No per-request latency trace is supplied.

## Finding 1 — Recorded connection-pool acquisition waiting

During the gameplay window, 6,201 jdk.ThreadPark events include the stack:

```text
SynchronousQueue.poll
ConcurrentBag.borrow
HikariPool.getConnection
HikariDataSource.getConnection
Hibernate connection acquisition / transaction begin
```

| Recorded acquisition-park metric | Value |
| --- | --- |
| Event count | 6,201 |
| Median duration | 28.2 ms |
| Mean duration | 40.54 ms |
| Maximum duration | 682 ms |
| Sum across threads | Approximately 251.37 seconds |

This is direct evidence that request threads waited while borrowing connections.
These are recorded park events, not 6,201 unique HTTP requests or complete
connection-acquisition measurements. One acquisition can park more than once,
short parks may fall below recording thresholds, and waits overlap across
threads. The aggregate sum exceeding wall time is expected; do not interpret
it as 251 seconds added to each request or divide it into HTTP latency as an
exact causal percentage.

The inspected YAML does not explicitly set Hikari maximum-pool-size, but the
running effective value is not established by this capture. Hikari's documented
default is 10; external overrides are possible. Confirm actual configuration
before treating 10 as measured fact. A larger pool is an experiment, not a
universal fix: it can move waiting into CPU/database contention.

Database socket waits also exist: 632 recorded gameplay SocketRead events target
port 5432. The maximum database socket-read event in the exported window is
606 ms. Such events do not identify an individual SQL query, distinguish server
execution from transport/scheduling delay or prove the database is otherwise
healthy. Pool acquisition is one observed waiting location, not the sole possible
source of latency.

## Finding 2 — Chess-board lookups are an execution-sample hotspot

There are 732 jdk.ExecutionSample events whose start timestamps fall in the
chosen gameplay window. 357 (approximately 49%) contain a com.chessapp.game.rules
frame within the exported depth. This is a fraction of sampled stacks, not an
exact percentage of request duration, all JVM CPU or an exclusive cost category.

A recurring stack is:

```text
Square.equals
ImmutableCollections.MapN.probe / get
Board.pieceAt
LegalMoveGenerator.requireSingleKing
LegalMoveGenerator.generateForSource / generate
StandardChessRules / PositionEvaluator
GameCommandService.submitMove
```

Square.equals appears in 278 sampled gameplay stacks, and is the top frame in
187. Source inspection confirms Board uses Map.copyOf and pieceAt performs map
lookup. LegalMoveGenerator scans all 64 squares to locate the moving king on
each candidate board. The service generates a legal set, calls the legality-
enforcing applyMove facade and evaluates the resulting position, so multiple
legal-generation traversals occur in a command.

This identifies a useful optimization candidate. It does not prove that equals
itself is incorrectly implemented, quantify map collisions or justify removing
king-count/king-safety validation. A compact indexed board or more efficient
validated king lookup could be evaluated later, preserving public behavior and
rules tests. Do not rewrite board storage solely from a single sample profile.

## Finding 3 — Long stop-the-world GC pauses are not the leading signal

The selected gameplay window contains 46 jdk.GCPhasePause events: approximately
210.79 ms summed, median 3.98 ms and maximum 18.5 ms. These pauses are much
shorter than the slowest approximately 1.11-second move request. A single long
stop-the-world pause is not supported as its explanation by these events.

Do not substitute jdk.GarbageCollection total event duration for pause time:
some collection work is concurrent. Allocation and concurrent GC can still
consume CPU, so GC is not ruled out as a secondary contributor. This review
has no per-request correlation proving how much any pause affected a request.
JFR's summary reports zero jdk.DataLoss events, which is not a guarantee of
complete profiling coverage or that below-threshold events were recorded.

## Finding 4 — Backend CPU exceeds generator CPU in this window

backend-jvm.txt confirms PID 20731, OpenJDK 21.0.1+12-29. The pidstat samples
inside the chosen gameplay window give:

| Process grouping | Mean CPU | Peak one-second CPU |
| --- | --- | --- |
| Backend PID 20731 | 148.16% | 247% |
| k6 | 12.28% | 40% |
| PostgreSQL processes summed per timestamp | 24.96% | 65% |

Without pidstat -I, 100% represents approximately one logical CPU, so the
backend mean is approximately 1.48 CPU cores, not 148% of this four-core host.
The peaks are independent and must not simply be added as simultaneous load.
Background desktop/system processes are outside these selected groups. Average
headroom does not rule out short bursts, waiting or limited runnable concurrency.

Backend sampled RSS peaks around 439 MiB; k6 around 145 MiB. PostgreSQL RSS
must not be naively summed because processes can share mapped memory. These
are resident-memory observations, not JVM heap sizes. PostgreSQL process I/O
columns are -1 in this log, so no per-process database I/O conclusion follows.

Before gameplay, 4,083 of 4,151 execution samples contain BCrypt frames. That
matches sequential registration/login work in setup. It must not be mistaken
for password hashing on every move. Setup and gameplay are distinct workloads.

## What this establishes, and next experiment

Observed: pool-acquisition waiting, repeated rule/board lookup execution samples,
short GC pauses and materially more backend CPU than generator CPU in this run.
The cause of each individual tail request remains unproven.

A plausible interaction is that rules computation inside a database transaction
holds a borrowed connection longer, amplifying acquisition waiting in other
requests. Source shows the game is loaded before rules calculation and the whole
command is transactional. This is an inference linking two observed signals,
not a measured connection-hold-time attribution.

The smallest next experiment is to confirm effective pool size and test one
moderate alternative in the loadtest profile only, keeping rules code, fixture,
pacing and hardware unchanged. Repeat comparable unprofiled runs to distinguish
run variation from a pool-size effect. A larger pool can help or hurt; do not
raise it to match 100 VUs or change PostgreSQL limits blindly. Do not modify the
pool and board representation in the same comparison.

An independent later experiment can optimize a demonstrated rule traversal or
board lookup while preserving pure Java rules, immutable behavior, king-count
checks, SAN/disambiguation and error precedence. Existing rule tests are needed
for a semantic change; no build/test execution has occurred during this review.

The current workload remains a finite local batch, not 100 connected browser
sessions, WebSocket delivery load or a sustainable production-capacity test.
The future VM/separate-generator TODO remains deferred.

## Reproduce the diagnostic recording

These are user-run commands for the existing local loadtest backend and supplied
scripts. They do not require an application-source change. Run from load-tests/,
with the backend already serving port 8081 against chess_loadtest and no other
matchmaking workload active. The provided concurrent-games.ts already prints
phase timestamps and the supplied package contains the aggregate runner.

### 1. Select the actual backend JVM

```bash
jcmd -l
```

Find com.chessapp.ChessBackendApplication, not GradleDaemon or another Java app.
Use the same OS user as the backend. Replace 12345 with its current PID; PIDs
change after restart. Prefer diagnostic tools from the backend's JDK version.

```bash
chess_backend_pid=12345
mkdir -p results
jcmd "$chess_backend_pid" VM.version > results/backend-jvm.txt
```

### 2. Start per-process sampling in a second terminal

Install sysstat once if pidstat is unavailable:

```bash
sudo apt install sysstat
```

From load-tests/ in terminal B:

```bash
pidstat -h -u -r -d -C 'java|k6|postgres' -p ALL 1 \
  | tee results/games-100-processes.txt
```

-u reports CPU, -r memory/page faults, -d I/O, -C filters command names, -p ALL
includes matching processes, and 1 requests one-second intervals. -h gives
single-line records. Without -I, 100% CPU means approximately one logical core.
Do not add -l when command arguments are unnecessary. Non-owned PostgreSQL I/O
fields can be unavailable (-1); do not interpret those as zero I/O.

### 3. Start JFR and run a separately named diagnostic workload

In terminal A, which holds chess_backend_pid:

```bash
jcmd "$chess_backend_pid" JFR.start \
  name=chess100 settings=profile duration=5m maxsize=128m \
  filename="$PWD/results/games-100-diagnostic.jfr"
```

profile is a built-in detailed JFR configuration. duration bounds recording time;
maxsize limits retained recording data. The absolute filename is important
because the backend and terminal can have different working directories.
Profile recording has overhead; do not replace the original unprofiled baseline.

```bash
set -o pipefail

npm run typecheck && \
GAMES=100 node run-k6.mjs \
  --report results/games-100-diagnostic.json concurrent-games.ts \
  2>&1 | tee results/games-100-diagnostic-console.txt
```

The named report preserves the original games-100.json. tee saves phase timestamps
and the k6 result. pipefail prevents tee from masking a failed workload exit code.
The timestamps separate setup (registration/BCrypt/pairing), gameplay and teardown.
These commands reuse filenames; use a distinct suffix to retain later repeats.

### 4. Stop and export

After the test and summary write finish:

```bash
jcmd "$chess_backend_pid" JFR.stop name=chess100
```

If the five-minute limit already stopped recording, the configured file should
already have been written; check the diagnostic output instead of starting a
new recording. Stop only pidstat in terminal B with Ctrl+C. Keep the backend
running. Interrupted/failed game journeys may leave fixtures; inspect those
before repeating as described in load-testing.md.

```bash
jfr summary results/games-100-diagnostic.jfr \
  > results/games-100-jfr-summary.txt

jfr print --stack-depth 32 \
  --events jdk.ExecutionSample,jdk.GarbageCollection,jdk.GCPhasePause,jdk.JavaMonitorEnter,jdk.ThreadPark,jdk.SocketRead \
  results/games-100-diagnostic.jfr \
  > results/games-100-jfr-events.txt
```

These exports do not execute application tests. summary shows event inventory;
print expands selected events and stack traces. Stack depth limits how much of
a call chain is visible. Keep the raw recording local by default and inspect
exports before publication; a complete recording can contain runtime metadata.
For review, retain the JSON, console, process log, backend version and both text
exports together so timing evidence can be aligned.

## Where to see the evidence yourself

| File | What it shows | What it does not show |
| --- | --- | --- |
| games-100-diagnostic.json | Counts, failures and request/journey distributions | Which method caused a slow request |
| games-100-diagnostic-console.txt | Same run's result and setup/gameplay/teardown markers | A per-request timing trace |
| games-100-jfr-summary.txt | Event types, counts and recording duration | Ranked expensive application methods |
| games-100-jfr-events.txt | Individual execution samples, waits, GC pauses and stacks | Exact CPU time of each method or every wait below recording thresholds |
| games-100-processes.txt | Per-process interval CPU/memory/I/O observations | Internal application method timings |
| backend-jvm.txt | Recorded backend PID and runtime identity | Effective pool size or PostgreSQL server configuration |

### Example A — Connection acquisition wait

In the supplied events text, jump near line 227357 and locate the ThreadPark
event at 16:29:30.286. It records a 13.6 ms park with this call chain:

```text
Unsafe.park / LockSupport.parkNanos
SynchronousQueue.poll
ConcurrentBag.borrow
HikariPool.getConnection
HikariDataSource.getConnection
Hibernate transaction/connection acquisition
```

The event's duration directly records a wait. The stack tells us it occurs
while borrowing a connection, rather than simply showing an idle executor thread.
Do not add every ThreadPark duration: idle workers also park normally, and
concurrent thread waits overlap. The 6,201 count in this review includes only
recorded gameplay-start events whose stacks contain ConcurrentBag.borrow.

If ripgrep is installed, inspect a few matching events from load-tests/:

```bash
rg -n -m 3 -B 16 -A 8 'ConcurrentBag.borrow' results/games-100-jfr-events.txt
```

Early matches can belong to setup; compare their startTime with the gameplay
markers. Use the supplied gameplay time window, not the whole recording.

### Example B — Sampled board-lookup work

Jump near line 225032 of the supplied events text. The ExecutionSample at
16:29:30.001 shows:

```text
Square.equals
ImmutableCollections.MapN.probe / get
Board.pieceAt
LegalMoveGenerator.requireSingleKing
LegalMoveGenerator.generateForSource / generate
PositionEvaluator / StandardChessRules
GameCommandService.submitMove
```

An ExecutionSample is a sampled stack, not a timed method call. Seeing this
chain once proves only that execution was sampled there. Repetition across
many gameplay samples identifies a hotspot candidate. It does not establish
that equals is faulty or that 49% of each request is spent in rules.

```bash
rg -n -m 3 -B 8 -A 16 'ImmutableCollections\$MapN.probe' results/games-100-jfr-events.txt
```

The code shape is consistent with the sampled work: Board.pieceAt does a map
lookup and requireSingleKing scans candidate boards. Keep validation semantics
when exploring faster representations/traversals; a hotspot is not permission
to remove a correctness check.

### Example C — GC and process CPU

Search for jdk.GCPhasePause and inspect duration/startTime. The gameplay maximum
of 18.5 ms is near line 218501 in the supplied export. Use pause events for
stop-the-world delay, not the full GarbageCollection event duration, which can
include concurrent work. A pause longer than an individual request would be
stronger evidence than the short pauses seen here, but per-request correlation
would still be needed.

In the process log, find backend PID 20731 from backend-jvm.txt. For example,
at 04:29:30 PM it reports 93% CPU; k6 at the same timestamp reports 8%. Averages
in this review aggregate only gameplay-window samples, not the whole file.
PIDs will differ on another run. Compare PostgreSQL processes as a timestamped
group for CPU, but do not sum their RSS as independent private memory.

For visual exploration, the raw .jfr can also be opened locally in JDK Mission
Control. Select the gameplay interval before looking at method profiling, thread
waiting and GC views. The text exports above remain sufficient to inspect the
specific events cited in this review.

The exact line numbers refer to the supplied export, not future recordings.

## References

- [Hikari configuration](https://github.com/brettwooldridge/HikariCP#configuration-knobs-baby)
- [JFR performance analysis](https://docs.oracle.com/en/java/javase/21/troubleshoot/troubleshooting-guide.pdf)
- [pidstat manual](https://man7.org/linux/man-pages/man1/pidstat.1.html)

- [jcmd recording commands](https://docs.oracle.com/en/java/javase/21/docs/specs/man/jcmd.html)

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
