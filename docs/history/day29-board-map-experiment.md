# Day 29 — Board map lookup experiment

## Change and question

The preceding JFR profile repeatedly sampled Square.equals / MapN.probe /
Board.pieceAt during legal generation. The proposed candidate replaces only
Board's Map.copyOf snapshot with a defensive HashMap copy wrapped using
Collections.unmodifiableMap. Explicit null-key/value checks retain constructor
rejections. Public methods, immutable move/capture/removal behavior, king-count
validation and rule semantics are intended to remain unchanged.

This is an implementation/storage experiment within the pure Java model,
not a new rules architecture. HashMap can increase allocation/memory overhead,
so faster lookups must not be assumed to improve every request or workload.
The suggested BoardTest additions protect null key/value rejection; existing
Board tests cover snapshot isolation, moves/captures/removal. User verification
command supplied was ./gradlew test --tests 'com.chessapp.game.rules.*'.
No assistant build, compiler, application or test execution occurred.
No updated Board source or test execution log was supplied with these results;
this review describes the instructed candidate and the user's report artifacts,
not an independently inspected post-edit implementation archive.

## Comparison procedure and evidence

The earlier pool-10-a/b reports provide an unprofiled Map.copyOf reference.
The user confirmed backend restarts with the corresponding settings during the
pool comparison. For this candidate, instructions specify restarting with
LOADTEST_POOL_SIZE=10, a five-game warm-up and two 100-game measurements with
JFR off, preserving the workload/pacing/hardware. Candidate reports are
board-hashmap-a.json and board-hashmap-b.json, supplied on 6 November 2026.
Their JSON does not independently record the effective pool, warm-up or JVM flags.

All four inspected reports contain metrics/root_group, no setup_data, and each
records 100 completed games, 2,000 move attempts, 6,200 HTTP requests, 16,400
passing checks, zero failed checks and zero HTTP/gameplay failures. No new
profile or process/system trace accompanies the candidate reports.

## Recorded results

Timings are milliseconds, rounded to two decimals from supplied JSON.
A/b are repetitions, not statistically matched pairs; retain each distribution
rather than averaging their percentiles or selecting only the fastest run.

| Storage / report | Move average | Move p50 | Move p95 | Move p99 | Move maximum |
| --- | --- | --- | --- | --- | --- |
| Map.copyOf, pool-10-a | 94.62 | 77.62 | 233.61 | 374.93 | 465.70 |
| Map.copyOf, pool-10-b | 88.02 | 66.70 | 224.86 | 273.27 | 358.66 |
| HashMap snapshot, a | 39.05 | 17.32 | 143.90 | 349.67 | 511.63 |
| HashMap snapshot, b | 68.01 | 28.74 | 271.59 | 606.79 | 770.13 |

| Storage / report | Game-read average | Game-read p50 | Game-read p95 | Resignation p95 | Average journey s |
| --- | --- | --- | --- | --- | --- |
| Map.copyOf, pool-10-a | 39.64 | 16.09 | 149.97 | 266.48 | 22.26 |
| Map.copyOf, pool-10-b | 31.65 | 11.49 | 122.95 | 149.04 | 21.71 |
| HashMap snapshot, a | 20.72 | 6.28 | 85.50 | 170.37 | 20.62 |
| HashMap snapshot, b | 28.08 | 9.61 | 121.88 | 208.84 | 21.30 |

Every journey still includes 19 seconds of intentional pauses. The k6 printed
request/game rates span setup and teardown as before; they are not sustained
capacity measurements. All reports are finite, repetitive local game batches
without WebSocket clients or an excluded in-game warm-up.

## Interpretation

Both HashMap move means and medians are below both reference values, as are
both candidate game-read means/medians. Candidate journey means are also lower.
This supports a promising typical-latency improvement in this local fixture.

Tail improvement is not consistent. Candidate a has move p95 143.90 ms, below
both references, but candidate b has p95 271.59 ms, above both. Candidate b p99
606.79 ms also exceeds both references, and both candidate maxima exceed both
reference maxima. Do not report this as a universal or stable tail reduction.
Resignation remains variable and is not uniformly faster across settings.

The illustrative local targets discussed in chat (move median below 50 ms,
p95 below 150 ms and p99 below 300 ms; game-read p95 below 75 ms) are provisional
engineering goals, not selected automated SLOs. Both candidate medians are below
50 ms, but only candidate a meets the example move-p95 goal, neither meets the
example move-p99 goal, and neither meets the example read-p95 goal. These
comparisons must not retroactively mark previously passing correctness runs as
failures or establish production suitability.

The cause of candidate b's larger tail is unknown. There are no corresponding
CPU/process, GC, pool-wait or slow-request traces in these attachments. Increased
allocation, run order, JVM/cache state, background work or database effects are
hypotheses, not observed diagnoses. The reference and candidate are sequential
batches in different JVM lifetimes; no reverse-order confirmation exists yet.
The old JFR profile does not prove the new MapN sample count or pool waits fell.

## Next bounded verification

Keep the pool at 10 and treat the HashMap implementation as a candidate. Before
calling it a retained performance win, perform one reverse-order confirmation:
restore the original constructor, restart/warm up and save two new original
reference reports, then restore the candidate, restart/warm up and save two new
candidate reports. Preserve distinct filenames. Keep all other settings fixed
and capture phase-aligned process/system observations for the comparison.

This is additional testing justified by the unresolved tail/variation, not a
request to rerun passing tests merely for documentation. Compare both pairs;
if results remain unstable, stop tuning for this milestone and record the
mixed outcome instead of chasing a favorable run. A similarly configured JFR
capture can investigate mechanism separately from unprofiled timing, but does
not replace the timing comparison. Avoid adding another source optimization,
changing pool size or increasing VUs in the same experiment.

No production adoption or tail-latency improvement is claimed at this checkpoint.
The board candidate can be reverted by restoring the original constructor:

```java
public Board(Map<Square, Piece> pieces) {
    Objects.requireNonNull(pieces, "Board pieces are required");
    this.pieces = Map.copyOf(pieces);
}
```

Both null-rejection tests remain valid for the original constructor. Remove an
unused Collections import on the original variant. Preserve the intended final
implementation explicitly after comparisons; do not leave its identity dependent
on which test happened to run last.
