# 001 - WebSocket notification connection-pool starvation

This investigation connects a load-test failure to its transaction/thread cause,
the supplied correction and the existing browser recovery behavior.

The original failure was captured on 7 November 2026. The current implementation
hands off delivery to a bounded executor and checks outbound session access on the
outbound worker. See [verification](04-verification-and-follow-up.md) for the
later local load runs and browser reconnect check.

| Read in order | Purpose |
| --- | --- |
| [01 - Problem and evidence](01-problem-and-evidence.md) | Captured stacks, transaction/connection dependency, original flow and why longer timeouts do not resolve it |
| [02 - Resolution](02-resolution.md) | Bounded background delivery, before/after independence, queue rejection and security guarantees |
| [03 - Board synchronization](03-board-synchronization.md) | Normal delivery, missed hints, HTTP reconciliation, uncertain commands, version/history/clock handling and diagrams |
| [04 - Verification and follow-up](04-verification-and-follow-up.md) | Focused test commands, expected counts, unverified boundaries and future corrections |

The directory number is a stable issue identifier. The 01-04 filenames are reading
order, not four separate corrections or four passing verification stages. Add a
new sibling issue only for another independently diagnosed problem. Keep later
corrections to this problem in this issue's record with dates and evidence.

This folder is the canonical explanatory record and supersedes the former flat
docs/day29-websocket-pool-starvation.md explanation.
