# Post-MVP performance experiments and efficiency options

Selected 8 November 2026: investigate WebSocket move commands and authoritative
state delivery after MVP. This is an experiment, not an approved switch now.
Day 29 still ends with one bounded stability check and final source/doc review.
The operational post-MVP milestones remain sustained capacity, connection budgets,
and overload/slow-client/reconnect hardening.

## Latency expectations

End-to-end response includes client scheduling, network/transport, server queueing,
security/session work, chess rules, database work, commit and result delivery.
Changing HTTP to WebSocket mainly affects transport/routing; it does not remove
rule evaluation, database transactions or locks. HTTP can reuse connections, so
there is not necessarily a new TCP/TLS handshake for each move.

Pushing a full authoritative snapshot can remove the opponent's notification-
triggered HTTP read and associated round trip. This is independent of whether the
move command itself arrived through HTTP or WebSocket. Full snapshots cost more
bytes/serialization than a small hint. A complete snapshot must include the
appropriate version, outcome and authoritative clock basis; history consistency
and reconnect reconciliation remain concerns.

Hypothetical only: if 80 ms of a 100-ms path is backend work and 20 ms is other
transport work, halving that 20-ms part yields 90 ms total, not 50 ms. No measured
percentage saving is claimed for this application. The observed queue-lock and
notification pool waits require backend fixes irrespective of transport.

## Controlled comparison

1. Record fresh profiles/resource observations for the retained HashMap and bounded
   notification-executor implementation. The earlier JFR predates those changes.
2. Compare current HTTP commands plus hints/GET recovery against HTTP commands plus
   authoritative push snapshots, preserving security, pacing and game positions.
3. Separately compare HTTP versus WebSocket commands with equivalent result/state
   delivery. Require application-level accepted/rejected replies after commit,
   request correlation and the same expectedVersion safeguards.
4. Test lost replies/disconnects, auth expiry, concurrent tabs, stale versions and
   overload. A transport acknowledgement is not a committed-move acknowledgement.
   Do not automatically replay commands whose outcome is uncertain.
5. Retain initial/reconnect recovery and compare correctness, tail latency,
   database accesses, resource use and implementation/operational complexity.
   Adoption follows evidence rather than the assumption that all-WebSocket is faster.

## Candidate backend improvements

The source inspected is the user Day 29 mid-session archive plus subsequent
instructed/user-reported changes. A final later source archive remains pending.
The options below are hypotheses; no new algorithm has been implemented or tested.
Public rules remain pure Java and immutable unless a separately reviewed design
explicitly preserves those boundaries through internal implementation changes.

| Option | Mechanism | Potential benefit | Constraints |
| --- | --- | --- | --- |
| Reuse validated king locations | Find/validate kings once per generation context; derive the king square for each candidate, including king moves/castling | Avoid the observed repeated 64-square king scans | Preserve malformed-position rejection and safe candidate application |
| Existence-only legal reply query | For outcome assessment, stop after the first legal reply rather than building every legal reply | Less work in ordinary nonterminal positions | Current full generation deliberately exposes invalid/unsupported cases; explicit validation must preserve those failure contracts before short-circuiting |
| Compact 64-square board | Map each square to an integer and use a private array with defensive ownership/copies | Avoid map hashing, some wrapper/object allocation and pointer traversal | Candidate data-structure experiment; array copying still costs work; never expose mutable storage |
| Precomputed attacks/rays | Tables for knight/king targets and directional rays | Reuse fixed geometry rather than repeatedly calculating it | Sliding attacks still depend on blockers; preserve attack versus legal-move distinctions |
| Bitboards | Represent square sets using 64-bit words and combine occupancy/attacks with bit operations | Efficient piece/occupancy traversal and attack tests | Substantial rules-internal rewrite and complex special-move correctness; not an MVP requirement |
| Request-local reuse | Reuse pure legality/analysis results for identical full position inputs within a request | Avoid proven duplicate calculation | Measure actual duplication; side-to-move, castling and en-passant matter; do not cache clocks/auth or assume two different positions share results |
| Internal make/unmake | Temporarily apply/undo candidates in private request-local state | Reduce per-candidate board copies | Higher restoration/concurrency risk, especially en passant/castling/promotion; much later experiment, never shared mutable board state |

These generally reduce repeated work, allocations or constant factors on a fixed
64-square board. An elaborate asymptotic claim about board size is less useful
than realistic profiles. Implementing minimax/alpha-beta would add AI search,
which is a different feature from validating human moves and is not selected.

## Database and concurrency candidates

Investigate query counts/plans and transaction/pool waiting before changing data
structures in unrelated code. Inspect actual growing-data queries before adding
indexes; avoid redundant indexes and preserve transactional ratings/reservations.

Matchmaking currently acquires the same advisory lock for join, leave and status.
Assess whether status needs the same serialization and whether the locked section
can safely shrink. Neither removing that lock nor moving calculations outside
transactions is a drop-in change: consistency, clock timing, optimistic version
rechecks and race guarantees must be specified and verified first.

If measured demand later justifies it, a dedicated pairing worker, multiple
independent matching pools, or a row-claiming design can be investigated. PostgreSQL
SKIP LOCKED is useful for some queue consumers, but it changes observation/fairness
and is not a replacement flag to add while retaining the same global lock. Pair
formation, active-game reservations and join/leave races require a coherent design.
No such redesign is selected for this milestone.

## Recommended investigation order

Fresh profile -> eliminate demonstrated repeated work -> benchmark a small change
with correctness checks -> confirm end-to-end impact. Cached king locations and
an existence query are smaller candidates than bitboards; a compact board is a
separate measured representation experiment. Do not combine changes so their
individual effects become unknown. Preserve the existing HashMap decision and
working implementation until a separately selected increment changes them.

## References and evidence limits

- Original local code: LegalMoveGenerator.generateForSource()/requireSingleKing(),
  PositionEvaluator.evaluate(), Board.movePiece()/removePiece(), MatchmakingService
  join()/leave()/status() and MatchmakingQueueRepository.
- The earlier profile sampled repeated board lookups/king scans, but does not
  establish the current post-change CPU distribution or a performance guarantee.
- Stockfish's bitboard implementation is a primary example of the technique, not
  an integration/reuse decision: https://github.com/official-stockfish/Stockfish/blob/master/src/bitboard.h
- PostgreSQL queue-locking behavior and caveats:
  https://www.postgresql.org/docs/14/sql-select.html

No assistant compilation, application, benchmark or test execution occurred.


## Would a graph help?

A square graph has 64 vertices. Fixed knight targets and king-neighbor relations
can be represented as precomputed adjacency lists or attack masks. Such a lookup
may reduce repeated coordinate calculations; it is the same precomputation option
already listed, not a reason to add a general graph framework.

The full legal-move graph on squares is position-dependent. Rook/bishop/queen
reach changes with blockers, pawn behavior depends on occupancy and special state,
and legal edges must account for king safety, castling and en passant. Updating or
rebuilding a graph for every candidate can cost more than querying the existing
board and traversing short rays. The inspected SlidingRayTraversal already provides
a direct way to walk a direction until a blocker/boundary.

BFS or Dijkstra solves reachability/shortest-path questions. Validating a chess
move and testing attacks is not generally a shortest-path problem. Inference for
this project: reusable fixed geometry could be useful if profiling shows its cost;
a generic dynamic graph is unlikely to be the first effective optimization.

A different graph uses complete chess positions as vertices and legal moves as
edges. Traversing that graph searches future moves for AI/analysis. Our human-game
rules service needs current legality and outcomes, not the best future move.
Materializing a position graph would expand state/memory and is not selected.

For this application, prioritize demonstrated repeated king scans and unnecessary
full move enumeration before choosing a larger representation rewrite. No speedup
is asserted without equivalent correctness and end-to-end measurements.
