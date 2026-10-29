# Day 19 — frontend gameplay source review

## Evidence

Reviewed on 29 October 2026 against the user's chess-web-app-day19-end.zip, compared with chess-web-app-day18-end.zip. Inspected game models, FEN display parser, API service, page, board, routing, Home, configuration/dependency deltas and existing gameplay/authentication boundaries.

No blocking defect was found in the reviewed Day 19 changes. This is a source review, not a claim of runtime acceptance or compilation. Tests follow the user's standing instruction that failures will be reported. No assistant application tests, builds, migrations or browser checks were run.

## Confirmed implementation

- Guarded lazy-loaded games/:gameId and Home's MATCHED Open game link.
- Typed GET/POST contracts matching backend fields, including the GET id versus POST gameId difference.
- Custom Player-oriented board, Unicode pieces, square labels and click selection.
- Explicit promotion, underpromotion and cancellation. Selection is cleared before output emission; cancellation sends no request.
- Separate game.ts/game.html/game.css and chess-board.ts/chess-board.html/chess-board.css.
- Versioned move submission, server-accepted FEN updates, stale-state reload without retry, uncertain-outcome blocking, terminal feedback and subscription cleanup.
- Existing in-memory authentication/interceptor behavior retained.
- No production/test/backend configuration changes relative to Day 18.
- cm-chessboard is absent from dependencies, lockfile, declarations and asset/style configuration.
- package.json is unchanged; lockfile has 15 package-version changes. angular.json differs only in formatting. These user-managed changes were preserved.

## Two minor inline follow-ups

Neither change below is applied to the user's implementation by this documentation package.

### 1. Guard refresh() during submission

The template already disables Refresh while submitting. Add the same check to the method so programmatic calls obey the same boundary:

```ts
refresh(): void {
  if (
    this.loading() ||
    this.submitting() ||
    !this.validGameId()
  ) {
    return;
  }

  this.refreshRequests.next();
}
```

Automatic recovery deliberately emits refreshRequests directly from the POST error handler. It can therefore reload without depending on the submitting flag being cleared by finalize first.

### 2. Clear obsolete move feedback after a successful GET

The archived successful read subscription clears needsRefresh but retains moveMessage. A previous message asking for a refresh can therefore remain after that refresh succeeds. Add:

```ts
.subscribe(game => {
  this.game.set(game);
  this.needsRefresh.set(false);
  this.moveMessage.set(null);
});
```

Retain feedback when the read fails. Clear errorMessage at the start of each read as already implemented.

## Documentation corrections

The prior main documents still described service-only moves or frontend gameplay as pending. API contracts even listed completion/version fields and then said they were not exposed. The replacement package updates the current checkpoint, API descriptions, frontend guide, roadmap and progress, and marks old milestone statements historical.

The frontend flow companion is now grounded in the actual archive. The historical day18-handoff.md incorrectly contained an After Day 17 handoff; its replacement records the correct historical boundary and points to the new handoff.

## Verification and boundaries

No game/parser tests are added in Day 19; the existing five authentication tests are unchanged. Source inspection confirms the backend tree is unchanged, not that a new test run passed.

Use the suggested manual scenarios in frontend-gameplay-flow.md when exercising the UI. No assistant pass-confirmation pause is required. Library integration is optional post-MVP polish. History, real-time delivery, clocks, extra draw rules and disconnect policy remain separate decisions.

## Suggested closing commit

After applying the inline follow-ups and documentation replacements:

```text
fix(frontend): tighten refresh handling and synchronize gameplay docs
```

If committing documentation alone:

```text
docs: synchronize Day 19 gameplay documentation and handoff
```
