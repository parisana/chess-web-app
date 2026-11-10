# Day 22 — source review and closing notes

## Evidence

Reviewed on 31 October 2026 against the actual user chess-web-app-day22-end.zip, compared with chess-web-app-day21-end(1).zip containing Day 21 documentation replacements. Inspected changed production sources, migrations, frontend templates/models/styles and added test source. No blocking defect was found in inspected changes.

The user reports all tests green after adding the two missing HTTP tests. Their test run is user-provided evidence. No assistant tests, builds, migrations, database upgrade runs or browser checks were performed. Source review is not independent runtime acceptance.

## Confirmed implementation

- V9 expands completion consistency for resignation; V10 adds active-participant pending offers and agreed completion.
- Game stores one nullable sender. Accepted position updates and every completion clear offers; invalid arguments are rejected before mutation.
- GameService provides versioned resign/offer/accept/decline/withdraw. Membership precedes lifecycle/version failures, followed by offer presence/role.
- Shared private lifecycle helpers retain public transaction ownership. Optimistic flush precedes reservation release and event publication. Finished commands require exactly two deletions. Failure rolls back all writes and suppresses after-commit hints.
- GameResponse adds drawOfferedByPlayerId. GameMoveResult remains unchanged. Lifecycle commands advance version without changing FEN/count or inserting move rows.
- All five endpoints derive the actor from JWT; shared expected-version field validation and feature-owned 409 offer errors are wired.
- Angular uses one submitting lock and executeGameCommand for all game POSTs. Terminal actions require inline confirmation; promotion and lifecycle actions cannot overlap. New snapshots/blocking synchronization clear confirmation. Accepted moves explicitly clear offer ownership locally.
- Lifecycle success and uncertain/conflicting responses use existing board/history reconciliation without command replay. Both new termination labels are rendered.
- Correct notification test uses GameChangedEvent.Type.GAME_UPDATED.name() and webSocketProperties.updatesDestination(), matching the listener.

## Source-confirmed test evidence

| Class | Inspected Day 22 additions |
| --- | --- |
| GameTest | Offer ownership, duplicate rejection, clearing/reoffer, valid/invalid position updates, agreement result guard and finish clearing |
| GameMoveIntegrationTest | Either-color resignation, rejection/order, unchanged history, rollback/release failures, resignation after outer commit, move/resignation race, agreed completion, decline/withdraw, accepted/rejected move effects, actor errors, acceptance rollback/release failure and controlled move/acceptance race |
| GameMoveHttpIntegrationTest | Resignation result/authentication/version validation/error precedence/history preservation; draw workflow and pending snapshot GET; offer-specific error codes |

The race helper preloads the same expected version in two transactions, gates command execution with latches and asserts exactly one commit. The acceptance race also verifies only the committed hint reaches both mocked recipients. This is database transaction coverage source, not an actual socket delivery claim.

## Coverage gaps

The inspected ZIP lacks drawCommandsRequireAuthentication and drawCommandsValidateVersionAndPreserveFailurePrecedence. The user subsequently reports adding both tests and running all tests green. Those post-upload test implementations were not included in the inspected ZIP; retain this distinction at the next source review. Production controller/service checks are present. These tests are user-reported complete rather than a currently outstanding implementation task.

No new Angular lifecycle/coordinator tests, WebSocketPropertiesTest, full socket integration suite or actual prior-database migration-upgrade fixture is present. Dedicated notification-timing assertions for each nonterminal draw action and all pairwise lifecycle races are not supplied. Existing history-read bounding remains sequential. Do not claim these scenarios were independently exercised.

## Nonblocking cleanup

APPLY-DOCS.md is a stale Day 21 package instruction sheet; it is not used by the application and can be deleted after applying the prior package. This Day 22 ZIP intentionally contains no replacement root instruction file. Removing a file is a separate user action; extracting a documentation ZIP does not delete it.

Unchanged unused combineLatest in game.ts and map in home.ts remain. GameHistoryResponse's comment "almost always true" could say "false for V7 legacy games; unrelated to completion". A few Angular JSDoc blocks have uneven indentation, and a history-read comment still says "move POST" where any game command now applies. These are optional inline cleanups; this package changes no production files.

## Suggested user checks

1. Two sessions: offer/decline/withdraw update the opponent without manual refresh; offering out of turn is allowed.
2. Resignation and acceptance need a second click; cancellation sends no command. Newer state or blocking refresh dismisses confirmation.
3. Any accepted move clears an offer; illegal proposals preserve it.
4. Resignation/agreement show the same persisted outcome in both sessions; returning Home permits another queue join after release.
5. Submit during a safety/history GET and simulate uncertain network failure: actions do not overlap and are not replayed.
6. Finish with failed/outdated history: visible-page recovery catches up and then stops; lifecycle-only versions add no move row.

These are suggested checks, not an assistant pass log.

## Documentation package

Replaces current main documents, adds lifecycle diagrams/review/handoff and preserves prior main-document text in docs/history/main-documents-through-day21.md. Existing historical Day documents remain unchanged. No production code, test, migration, configuration or dependency changes are included.

Suggested documentation commit:

```text
docs: document resignation and draw agreement lifecycle
```
