# 04 - Verification and follow-up

[Issue overview](README.md) | [Board recovery](03-board-synchronization.md)

## Runtime status

Original failure evidence has been inspected. The bounded executor correction
has been supplied, but the user has not yet reported its application or results.
Do not label the correction verified, or infer application from receipt of the
package. No assistant build, compiler, typecheck, backend, database or load test
has been executed.

## Focused checks

GameNotificationListenerTest protects the handoff boundary, rejected-task behavior
and attempting the second participant after the first recipient fails.
NotificationDeliveryConfigTest saturates the actual configured executor and
requires rejection rather than caller-thread execution. This is a behavioral
regression guard, not a claim that a unit test reproduces the whole pool stall.
Existing real-socket integration tests retain after-commit/rollback/private-session
coverage. User-run command from backend:

```bash
./gradlew test --tests 'com.chessapp.notification.*'
```

The assistant has not run tests, compilation, a backend, a load generator or a
database. Review supplied files and apply as a coherent increment. Restart the
loadtest backend after applying; retain the pool at 10 and JFR off. Preserve the
failed reports before rerunning. From load-tests, run each separately and inspect
for failures before proceeding:

```bash
npm run ws:games:1
npm run ws:games:5
npm run ws:games:20
```

Expected successful 20-game counts are 40 connected sessions, 200 matched
notifications and 20 completed games, with no delivery/socket/HTTP/check failures.
Watch backend logs for queue rejections and Hikari acquisition timeouts. The first
1/5 runs establish that asynchronous delivery still follows the expected protocol;
the 20-game run tests the previously failing concurrency level. This targeted
retesting is justified by the source correction and observed failure.

Do not call the correction runtime-verified until user results arrive. If stalls
remain, capture the same thread stacks during failure and inspect other connection
holders. Do not increase pool size, change deadlines or disable security checks
in the same verification run. The test's 500-ms subscription settling assumption
remains disclosed; it is separate from the observed database-pool blockage.

## Board recovery verification boundary

The k6 WebSocket workload requires exact notifications and deliberately has no
polling/reconnect fallback. That makes delivery failures visible. It does not run
Angular, prove its recovery timing or test its replay UI. The frontend recovery
companion describes inspected source behavior, not a new runtime acceptance test.

A later targeted browser/integration check can deliberately omit a notification
while HTTP remains available, then observe focus/periodic/manual reconciliation.
A separate interruption check can observe reconnect and catch-up. Controlled
fault injection should be explicit; do not disable session checks or retry move
commands to manufacture a successful run. These checks have not been executed or
added as silently completed work.

When results arrive, record source/configuration identity, pool/worker/queue
settings, report names, completed-game/notification counts, all failures and timing
sample counts. Preserve before/after results. No favorable percentile should hide
dropped hints or failed games. Keep diagnostic captures distinct from clean timing
runs because the capture process can affect results.

## Remaining milestone

After the socket workload is healthy, resume bounded local increments: consider
50/100 games with resource observations, then concurrent matchmaking and an
optional moderate-duration stability run. Longer runs, idle/reconnect tests,
history data volume, login bursts and deployment VM testing remain separately
scoped. Retain HashMap provisionally with its earlier mixed tail-latency evidence.

## How to extend this record

The directory's 001 is a stable issue number, not a count of passing tests. The
01-04 files are a reading order, not four separate implemented corrections. If
this issue needs another correction, record the failed verification and add a
clearly dated revision here. Give a different independently diagnosed issue a new
sibling such as 002-short-problem-name. Preserve evidence and superseded decisions;
do not silently rewrite an unverified proposal as an applied successful fix.
