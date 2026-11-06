# Apply the Day 29 game-smoke increment

Copy these files into the matching paths in your project. This is a load-test
increment, not a substitute for the user's implementation archive.

- New: load-tests/smoke-game.ts and load-tests/run-k6.mjs.
- Replace: load-tests/package.json. Dependencies and metadata are preserved
  from the user's day29-mid archive; existing baseline scripts now use the
  aggregate-report runner. Keep any additional local scripts you added later.
- Replace the supplied Markdown documents with these synchronized versions.
- No package-lock change or npm install is required for these script-only edits.
- No backend/frontend production source or database migration is included.

From load-tests/, with the loadtest backend on 8081 and an empty test queue:

```bash
npm run test:game
```

Type checking runs first. Success requires smoke_games_completed=1, all checks
passing and zero unexpected HTTP failures. The runner creates results/ and writes
results/game-smoke.json without setup_data. Do not append --summary-export.
Do not rerun the statistics baselines solely because of this reporting change.
Existing historical raw reports still need sanitization before committing/sharing.
No assistant build, type check or load-test execution was performed.

If it fails, share the failing check/status and summary rather than HTTP debug
logs. Commands are never retried. A failed run may leave queue/active-game
fixtures; inspect those before repeating the smoke. Normal success logs out both
sessions and leaves two test Players plus one finished game with four moves.

The other npm entry points are unchanged: test:smoke for auth, baseline:stats:1vu,
baseline:stats:5vu and baseline:stats:20vu for statistics reads. New entry points:
smoke:game (k6 runner only), test:game (type check followed by smoke:game).
