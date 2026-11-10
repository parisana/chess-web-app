# Performance and reliability investigations

Use numbered directories for independently diagnosed issues. Keep the evidence,
proposed/applied correction, verification status and operational recovery together.
A number identifies an issue; it is not an optimization ranking or certification.

| Issue | Finding | Status |
| --- | --- | --- |
| [001 - WebSocket notification pool starvation](001-websocket-notification-pool-starvation/README.md) | Synchronous after-commit delivery waits on an independent JDBC session transaction while the game connection can remain held | Correction applied. Two later 500-game local snapshot runs passed. See [results](../load-testing.md). |

The earlier Board map and pool-size experiments remain in their existing
experiment documents. This index begins with the diagnosed notification issue;
it does not retroactively renumber historical milestones or imply those other
experiments were correctness defects.
