# Day 29 — mid-session handoff

Continue from the actual user chess-web-app-day29-mid.zip plus these documentation replacements and subsequent user-applied changes. Reviewed on 6 November 2026. Do not substitute a generated implementation archive. This package contains documentation only.

## Working agreement

User applies coherent inline increments. Preserve conventional modular-monolith feature layers, Player naming, cross-feature services/events, pure Java rules, YAML/user-managed configuration, useful comments, readable one-line signatures/calls and separate Angular HTML/CSS. Minimal frontend tests. No assistant builds/tests without explicit request; user reports green unless a failure is supplied. New major milestones begin in a new chat.

## Actual source

Day 28 gameplay remains the baseline, including V15 starting Player details, game read/command separation, self-only history discovery and recovery through authoritative HTTP state. Small cleanup is applied: unused Game.moveTokens removed, setup streams named, and external #gameActions receives current through the outlet's implicit context. Historical Day 28 companion proposals are not a reason to reapply those changes.

Day 29 includes application-loadtest.yml (8081/chess_loadtest/local HTTP cookie), TypeScript k6 authentication smoke/statistics baseline, strict noEmit compiler config, npm shortcuts and lockfile. Lockfile contains TypeScript 7.0.2 and @types/k6 2.3.1; k6 CLI version is not established. The application excludes default UserDetailsServiceAutoConfiguration while retaining custom authentication/session/CSRF security.

## Evidence and results

User reports smoke passed in approximately 0.7 seconds. Console reports describe 59/296/1,200 measured reads for 1/5/20 VUs. The archive contains no 1-VU summary; its saved 5-VU JSON is a different 290-read run with p95 66.72 ms/p99 639.65 ms. Keep it separate from the pasted 296-read run with p95 16.87 ms. Saved 20-VU JSON has 1,200 reads, zero measured failures and p95 16.31 ms. Both saved summaries have zero failed checks/unexpected HTTP failures. Full provenance, glossary and limits are in load-testing.md.

The assistant inspected files/JSON only. No application/test/build/type-check/database execution or independent benchmark was performed. Hardware/runtime inventory and resource observations remain unrecorded. No maximum throughput, production-user capacity, causal bottleneck or optimization claim is justified.

## Export correction

The saved summary JSON contains setup_data with session cookies. Earlier aggregate-only advice was incorrect for this workload. Normal teardown logs the sessions out; sanitize away setup_data before sharing or committing summaries. The documentation package does not mutate raw JSON/source/git staging. Consider a custom summary writer in the next code increment.

## Next increment

Implement a single-pair smoke workload: authenticate two Players, join the queue, obtain the game, make alternating legal moves and resign. Use actual DTOs/routes and both independent session jars. Then measure independent concurrent games. Neither workload exists yet. Preserve the current HTTP-command/recovery design; no silent retry.

Keep PGN UI, promotion gating, replay polish, full timeout draw adjudication, rating extensions, chat/rematch, public history and acceptance/abandonment deferred. Day 30 remains planned for final acceptance/presentation/release; deployment is a separate choice.
