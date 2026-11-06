# Apply the independent-game workload increment

Copy the supplied load-tests/concurrent-games.ts and package.json into the
matching paths. Keep run-k6.mjs and the other scripts from the previous increment.
The supplied package preserves dependencies and existing scripts and adds three
baseline:games shortcuts; retain any extra local scripts you added afterward.
Replace the two matching Markdown documents. No backend/frontend production
source, database migration, dependency or package-lock change is included.

From load-tests/, with the isolated backend on 8081 and no other queued clients:

```bash
npm run baseline:games:1
npm run baseline:games:5
```

Run sequentially. Inspect errors/leftover fixtures before repeating a failed run.
Each command type-checks first, then runs a batch of one game per VU, 20 moves per
game, one-second turn pauses and completion verification. Setup pairs first to
avoid cross-pairing. Normal success takes roughly 20 seconds plus setup/teardown,
not a fixed 60-second measurement window. Read docs/load-testing.md for scope,
metrics and expected counts. The :20 shortcut is provided for the next step after
reviewing the first results. No assistant test/compiler/application was run.
