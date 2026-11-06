# Apply the 50-/100-game local increment

Copy the supplied concurrent-games.ts and package.json into load-tests/, retaining
any extra scripts you added locally. Dependencies are unchanged; no npm install,
lockfile or backend/frontend application change is required. Retain run-k6.mjs
from the previous increment. Replace the supplied matching Markdown documents.

The only workload changes are GAMES limit 100, setup timeout four minutes and
phase timestamps. Moves/pacing/assertions/custom timing definitions are unchanged.
The earlier 1/5/20 results remain recorded. New batch results are pending.

Use the procedure in docs/load-testing.md: capture the environment, start
vmstat -w -t 1 | tee results/games-50-system.txt in a separate terminal, then
run npm run baseline:games:50. Stop only the sampler after test/report completion.
Review before running :100. vmstat's first rate/CPU row is since boot, not this
workload. Capture setup and gameplay separately using the phase timestamps.

The future cloud-VM TODO is in docs/roadmap.md. No VM or 1,000-game test is part
of this increment. No assistant test/type-check/build/application execution.
