# Day 29 - Concurrent matchmaking increment

## Question

Earlier gameplay and socket scripts pair games sequentially in setup. They do not
exercise simultaneous queue entry across Players. This workload creates only
accounts/sessions in setup, then has independent Player VUs enter concurrently.
The inspected controller uses POST /api/v1/matchmaking/queue to join and
GET/DELETE /api/v1/matchmaking/queue/me to inspect/leave.

GAMES is the intended number of games; the scenario starts 2 x GAMES VUs. Here
one VU means one Player/session. It does not mean one game controlling both Players
as in the earlier gameplay workloads. Partners and colors are not predetermined.
No sockets connect in this workload; this isolates HTTP matchmaking behavior,
while production notifications can still be generated for committed matches.

Setup creates fresh Players sequentially with the existing registration/login
helper. Password hashing remains setup cost, not join latency. It returns only
session fixtures. Every VU joins once, polls at 250-ms intervals if still waiting,
and verifies its assigned game contains itself and another distinct cohort Player.
There is a 15-second observation deadline and 10-second HTTP deadline, not new
product SLOs. The observed-match duration includes polling and the game read, so
it is not pure server pairing time.

After all VUs finish, teardown checks each Player's assignment, verifies both
participants see the same game, expects exactly GAMES distinct observed games and
one appearance per cohort Player across those games. This verifies observed API
invariants, not an exhaustive SQL audit proving no hidden duplicate records.
It does not test admission fairness, race-with-leave, repeated join idempotency,
queue expiry or full production throughput. The existing integration tests retain
their separate concurrency/transaction coverage.

Teardown then resigns White in each observed cohort-only game, checks released
reservations via queue status and logs out every fixture Player. It attempts to
remove remaining WAITING entries after partial failures. It does not delete
persisted Players/games or automatically reset the database. A setup/transport
failure can leave fixtures; use the existing isolated reset procedure as needed.
Teardown assertions are check metrics and affect pass status, even if every VU
reports a matched game. Inspect checks/http failures as well as custom counters.

## Apply

Prerequisites: existing websocket-fixture.ts, run-k6.mjs and the existing noEmit
TypeScript configuration with allowImportingTsExtensions enabled. Add export to
these TWO existing helper declarations in websocket-fixture.ts:

```typescript
export function registerAndLogin(client: Client, username: string): void {
```

```typescript
export function serialize(client: Client): SessionFixture {
```

Keep their bodies unchanged. Copy concurrent-matchmaking.ts into load-tests and
merge package-scripts.json entries into package.json scripts. Do not replace the
whole package.json. No dependencies or backend source changes are needed.

Use only the isolated loadtest database/backend. Ensure no unrelated queue users,
manual clients or another load run can pair with this cohort. Do not guess partner
identity or allow foreign games to count as success. A contaminated fixture fails
and never resigns a game involving an unrelated Player. Keep Hikari pool 10,
notification workers 2, capacity 256 and JFR off. Do not tune settings in this run.

First run from load-tests:

```bash
npm run matchmaking:1
```

This includes type checking. Expected custom counters:

| Metric | Expected at GAMES=1 |
| --- | --- |
| matchmaking_players_matched | 2 |
| matchmaking_unique_games | 1 |
| matchmaking_player_failures | 0% |
| checks / http_req_failed | All checks pass / no HTTP failures |

Share this result before moving to matchmaking:10, then matchmaking:50. These
mean 20 and 100 Player VUs respectively; expected distinct games are 10 and 50.
The script permits GAMES through 100, but that is a cap, not an instruction to
skip the initial correctness check. There is no move workload during these games.
Reports with the same names are overwritten; preserve prior results before repeats.
Use the existing runner to avoid exporting setup session cookies. Do not log
fixture credentials or use raw summary exports.

## Terms and metrics

| Metric/term | Meaning |
| --- | --- |
| matchmaking_join_duration | HTTP duration of each Player's POST queue join |
| matchmaking_observed_match_duration | Time from starting the join through polling and validated game discovery |
| matchmaking_players_matched | VU journeys that successfully discover a valid game |
| matchmaking_unique_games | Distinct cohort games reconstructed and checked in teardown |
| matchmaking_player_failures | Fraction of VU journeys that do not complete successfully |
| Contention | Work waits or competes for a shared resource, such as the queue's database advisory lock |
| Polling | Periodically reading queue status to discover a match; adds observation delay |
| p50 / p95 / p99 | 50% / 95% / 99% of measured samples are at or below that duration |

Small runs have too few samples for stable tail estimates. Setup and teardown are
included in the whole-test request rate; that rate is not sustained pairing
throughput. A pass at one finite cohort does not establish a supported production
Player count. No results are yet recorded for this new workload. Scripts and docs
were prepared by inspection only; no assistant typecheck/build/test execution.
