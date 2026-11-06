# Day 29 — mid-session handoff

Continue from the actual user chess-web-app-day29-mid.zip plus these documentation replacements and subsequent user-applied changes. Reviewed on 6 November 2026. Do not substitute a generated implementation archive. The earlier mid-session package contained documentation only. The subsequent game-smoke increment adds load-test files and npm script changes; no backend/frontend production change.

## Working agreement

User applies coherent inline increments. Preserve conventional modular-monolith feature layers, Player naming, cross-feature services/events, pure Java rules, YAML/user-managed configuration, useful comments, readable one-line signatures/calls and separate Angular HTML/CSS. Minimal frontend tests. No assistant builds/tests without explicit request; user reports green unless a failure is supplied. New major milestones begin in a new chat.

## Actual source

Day 28 gameplay remains the baseline, including V15 starting Player details, game read/command separation, self-only history discovery and recovery through authoritative HTTP state. Small cleanup is applied: unused Game.moveTokens removed, setup streams named, and external #gameActions receives current through the outlet's implicit context. Historical Day 28 companion proposals are not a reason to reapply those changes.

Day 29 includes application-loadtest.yml (8081/chess_loadtest/local HTTP cookie), TypeScript k6 authentication smoke/statistics baseline, strict noEmit compiler config, npm shortcuts and lockfile. Lockfile contains TypeScript 7.0.2 and @types/k6 2.3.1; k6 CLI version is not established. The application excludes default UserDetailsServiceAutoConfiguration while retaining custom authentication/session/CSRF security.

## Evidence and results

User reports smoke passed in approximately 0.7 seconds. Console reports describe 59/296/1,200 measured reads for 1/5/20 VUs. The archive contains no 1-VU summary; its saved 5-VU JSON is a different 290-read run with p95 66.72 ms/p99 639.65 ms. Keep it separate from the pasted 296-read run with p95 16.87 ms. Saved 20-VU JSON has 1,200 reads, zero measured failures and p95 16.31 ms. Both saved summaries have zero failed checks/unexpected HTTP failures. Full provenance, glossary and limits are in load-testing.md.

The assistant inspected files/JSON only. No application/test/build/type-check/database execution or independent benchmark was performed. Hardware/runtime inventory and resource observations remain unrecorded. No maximum throughput, production-user capacity, causal bottleneck or optimization claim is justified.

## Subsequent increment — one-game smoke and aggregate reports

smoke-game.ts adds one VU orchestrating two independent Player cookie jars,
random-color discovery, e4/e5/Nf3/Nc6 with accepted versions, opponent HTTP reads,
SAN/history checks, White resignation, reservation release and outcome-count
checks. The user reports it passed: one game, 33 HTTP requests, zero unexpected failures, 844.53 ms iteration. The assistant has not executed it.
Use npm run test:game, which type-checks first. It creates results/game-smoke.json.
It requires an otherwise empty test queue and does not test WebSocket delivery.

Raw mid-session JSON includes setup_data with session cookies. run-k6.mjs now
keeps k6's console output while saving only metrics/root_group from a private
temporary summary; it preserves the test's failure exit status and normally
removes the raw temporary directory. Npm baseline scripts use the runner and
explicit 1/5/20 VUS values. Old reports are not retroactively sanitized. No new
npm dependency is added. The full procedures and limits are in load-testing.md.

## Concurrent-game increment — user reports 1/5/20 completed batches

concurrent-games.ts pre-pairs distinct Player/session pairs sequentially in setup,
then assigns one game to each local VU. Each VU makes 20 legal plies (four opening
plies followed by four reversible knight cycles), with one-second pauses, opponent
HTTP reads, SAN/history checks and resignation/completion/statistics verification.
No queue/auth work enters custom gameplay timings, and no in-game warm-up is
excluded. This is one finite journey per VU, not sustained capacity measurement.
The repetition fixture relies on currently deferred repetition/move-count draws.

Npm commands baseline:games:1 / :5 / :20 type-check first and use run-k6.mjs to
save aggregate metrics/checks. Dependencies are unchanged. Expected move counts
are 20/100/400, completions 1/5/20 and total HTTP counts 62/310/1,240. No assistant
compiler/build/test execution. User has supplied console results for 1, then 5,
then 20 games; no later full-source or game-result JSON review is claimed. Setup assumes an otherwise empty test queue; failed fixtures are not silently
cancelled or commands retried. Inspect leftovers before rerun. Gameplay is
concurrent across games and sequential within each game; each VU controls both
Player sessions. No WebSocket client or delivery assertion is present.

## Recorded game measurements

One-game smoke: one completed game, 33 HTTP requests, zero unexpected HTTP
failures, 844.53 ms iteration. Concurrent batches: 1/5/20 games completed,
20/100/400 move attempts and zero recorded game-journey failures. Move p95 was
31.08/43.80/98.09 ms; game-read p95 7.32/12.82/26.94 ms. Average journeys were
19.60/19.81/20.07 seconds, including 19 one-second pauses. The subsequent results.zip supplies the 1/5/20 JSON summaries and confirms
HTTP counts 62/310/1,240, zero HTTP failures and all checks passing. It also
supplies the 50-game JSON/system samples; separate uploads supply the 100-game
JSON/system samples and current inventory. Full provenance and limitations remain in load-testing.md.

## Completed local extension — 50 and 100 games

User explicitly chose to stay local and extend concurrency in Day 29.
concurrent-games.ts accepts GAMES 1..100; baseline:games:50 and :100 type-check
first and use aggregate-only reporting. Sequential setup timeout is four minutes;
gameplay max three minutes, teardown two minutes, ordinary clocks ten minutes.
The supplied k6-globals.d.ts declaration fixes console.log typing only; it is
user-applied inline and adds no runtime code/dependency.

Inspected user JSON: 50/100 completions, 1,000/2,000 move attempts,
3,100/6,200 HTTP requests, 8,200/16,400 checks passed, zero failed checks and
zero HTTP/gameplay failures. Move p95 171.17/399.17 ms, p99 208.23/780.80 ms;
100-game maximum move 1,059.50 ms. Game-read p95 81.20/155.20 ms. Journeys average
21.21/22.61 seconds including 19 seconds of deliberate pauses.

Whole-machine captures: 50-game idle minimum 19%; 100-game idle minimum 3%,
with two consecutive near-saturated samples. Runnable-task peaks 18/34, maximum
I/O wait 3%/11%, no swap traffic. Phase logs were not supplied, so these cannot
be attributed to exact gameplay intervals or individual requests/processes.
Current inventory confirms Ryzen 3 3200G / four logical CPUs / 15 GiB OS-visible
RAM, k6 2.3.0, shell Java 21.0.1 and psql client 14.24. A free snapshot had
6.3 GiB available memory; it is not a during-run minimum. Actual backend JVM and
PostgreSQL server versions remain unconfirmed. No cause or optimization is proven.

No assistant test/build/compiler/application execution. The selected local
extension is recorded; there is no need for another run just to sync docs.
Further escalation should first improve resource attribution, not assume that
passing correctness thresholds establishes acceptable tail latency or capacity.

Future VM deployment remains a TODO: provisional 2 vCPU/4 GB RAM/40–60 GB SSD
minimum, or comfortable 4 vCPU/8 GB RAM/60–100 GB SSD, with a separate generator
host for deployment load tests. No VM or 1,000-game test is selected here.

Keep PGN UI, promotion gating, replay polish, full timeout draw adjudication, rating extensions, chat/rematch, public history and acceptance/abandonment deferred. Day 30 remains planned for final acceptance/presentation/release; deployment is a separate choice.
