# 04 - Verification and follow-up

[Issue overview](README.md) | [Board recovery](03-board-synchronization.md)

## Evidence

The [original failure](01-problem-and-evidence.md) recorded no completed games
in the diagnostic 20-game run. Its thread dumps show servlet workers waiting
for session database connections from inside synchronous notification delivery.

After the bounded handoff and subsequent delivery changes, two local 500-game
snapshot-only runs each completed 500 games and validated all 21,000 expected
snapshots over 1,000 sockets. Both passed their thresholds. Command-to-state
p99 was 2.07–2.12 seconds with Spring Boot, PostgreSQL and k6 on the same host.
See [local load testing](../../load-testing.md) for run settings and limits.

The user also confirmed a two-browser reconnect check. After reconnect, the
board, clock and move history caught up and play continued. The k6 workload
validates delivery on healthy sockets. It leaves browser rendering, focus and
periodic recovery, and deliberate notification loss outside its measurements.

## Regression checks

`GameNotificationListenerTest` covers handoff, rejected submission and delivery
to the second participant after the first send fails.
`NotificationDeliveryConfigTest` checks rejection under executor saturation.
Real-socket integration tests cover after-commit delivery, rollback suppression
and private session access. Run from `backend/` with the test database configured:

```bash
./gradlew test --tests 'com.chessapp.notification.*'
```

If the stall recurs, compare Hikari acquisition waits with thread stacks from
the same interval. Check executor saturation and outbound queue timing
separately. Keep workload and timeout settings fixed while collecting evidence.
