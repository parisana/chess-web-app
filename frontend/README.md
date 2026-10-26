# Chess Frontend

Checkpoint reviewed on 25 October 2026 against the attached `chess-web-app-day8-end(2).zip`. The knight/rook Strategy pipeline and its tests were inspected. Both Day 8 cleanup items are present: `returningRookToOriginalCornerDoesNotRestoreCastlingRight` starts with `1n4k1/8/8/8/8/8/8/R3K2R w KQ - 0 20`, and MoveApplier documents knight and rook support. No blocking movement-pipeline defect was found in this source review. The user reported all tests green for the earlier Day 8 end archive; a rerun of the corrected fixture is not confirmed. No independent tests or builds were run. Day 9 guided changes through queen generation/dispatch have a user-reported green-test result; final queen pipeline tests remain pending. No fresh Day 9 source archive has been inspected. Days 6 and 7 change no frontend implementation. Console board rendering exists only in backend test code; it is not a chessboard UI.

Angular 21 standalone application with Angular Material, reactive forms and in-memory authentication. Backend calls use relative `/api/v1` URLs.

## Run locally

From `frontend/`, use the project's declared npm 11.6.2 and a Node version accepted by the locked Angular packages:

```bash
npm ci
npm start
```

Open `http://localhost:4200`. `npm start` loads `proxy.conf.json`, forwarding `/api/**` to the separately running Spring backend at port 8080. Plain `ng serve` does not enable this proxy automatically.

## Build and minimal tests

```bash
npm test -- --watch=false
npm run build
```

Five focused tests in `src/app/auth/services/auth-flow.spec.ts` cover bearer attachment, external URL exclusion, protected 401 cleanup/redirect, cleanup after profile loading fails, and logout. There is no `app.spec.ts` in this checkpoint. No end-to-end runner is configured.

The user reports all tests green for the earlier Day 8 end archive; rerun of the corrected fixture is not confirmed. This source/documentation review ran no tests or builds. Separate frontend build and manual-check results were not supplied.

## Authentication behavior

Registration and login use Material forms. Login loads `/api/v1/players/me` before establishing an authenticated session. Home is guarded. Logout clears the in-memory token/player and returns to login; a browser reload also loses the session.

The interceptor attaches bearer tokens to relative protected API requests. A protected-request 401 clears the matching session and redirects an established player to login; a 403 does not clear the session. Failed form submissions preserve passwords. Successful login clears the password; successful registration resets the form.

## Username editing

Home embeds the standalone `ProfileEditor` component. It initializes the form from the in-memory player, validates a nonblank username of 3–30 UTF-16 string units, and calls PATCH `/api/v1/players/me` through `PlayerApiService`.

The existing interceptor attaches the token. A successful response updates `AuthSessionStore`, immediately updating the welcome message. The form then resets to the server-returned username. Errors preserve the input and show validation, conflict, session or fallback feedback. A response for a changed session is ignored. No new frontend test suite was introduced for this increment.

Usernames may contain spaces and special characters. No trimming or case normalization is performed. Email/password changes are outside this form. Username changes do not require a new token.

Manual checks: successful rename and immediate header update; duplicate rejection with input retained; invalid input feedback; navigating back to Home with the updated profile. Use the browser network panel to inspect PATCH response statuses/codes listed in [API contracts](../docs/api-contracts.md).

## Matchmaking behavior

Home loads queue status when opened and provides Material join, leave and refresh controls with progress/error feedback. It displays `NOT_QUEUED`, the enqueue time for `WAITING`, or the game ID for `MATCHED`.

Pairing runs on the backend when players join. The first player manually refreshes to discover the match. Leaving removes waiting membership without cancelling a game; logout does not leave the queue.

There is no game API service, game route, chessboard or real-time subscription in this checkpoint.

See [development-progress.md](../docs/development-progress.md) for current coverage and limitations, [architecture.md](../docs/architecture.md) for engineering decisions, and [roadmap.md](../docs/roadmap.md) for planning status.

## Day 8 boundary

The guided Day 8 increment adds internal knight/rook Strategy dispatch, rook movement, shared application/filtering and rook castling-right updates. It changes no endpoint, response shape or frontend implementation. ChessRules still exposes only initialPosition(); persisted games still contain the initial state. There is no move-submission API or chessboard UI. The final Day 8 source has now been reviewed; corrected-fixture rerun confirmation remains outstanding.

## Day 9 guided checkpoint — 26 October 2026

This section describes the Day 9 guided changes and supersedes earlier Day 9 plans. Historical Day 7/8 sections describe their original checkpoints.

- Shared package-private SlidingRayTraversal handles candidate traversal for rooks, bishops and queens. Every occupant ends a ray; only opposing non-king occupants are capture candidates. SquareAttackDetector retains separate attack semantics.
- BishopMoveGenerator and QueenMoveGenerator implement PiecePseudoLegalMoveGenerator. Queen generation uses eight nonzero unit directions; (0,0) is skipped because it represents no movement. In the current helper a zero direction encounters the occupied source and stops; it does not actually cause an infinite loop.
- MovementTestFixtures centralizes standard test strategy registration and shared legal-generator construction. Direct piece tests and deliberate registration-contract tests keep explicit construction.
- Guided tests cover bishop/queen candidates and dispatch; bishop application and king-safety scenarios; existing knight/rook regressions remain.
- Final supplied increment adds queen capture/metadata application tests, White orthogonal and Black diagonal pins, and blocking/capturing a checking rook. These final tests have not yet been reported as run.
- MoveApplier Javadoc should list knight, rook, bishop and queen support. ChessRules still exposes only initialPosition(); movement is not connected to GameService or HTTP.

Verification: the user reported the tests green after queen generation and dispatch were supplied. No precise command or log was provided. This is user-reported evidence, not an independently reproduced run or inspection of a fresh Day 9 archive. The final queen pipeline tests are supplied in the same response as this documentation and remain pending local verification. No independent tests or builds were run.

Movement remains a subset: pawn and king generation, special moves, complete legal-move enumeration, checkmate/stalemate, history and persisted gameplay commands remain later work. The king-square filtering assumption must be revisited for king movement. Parser validation remains structural; board content must be compared explicitly because Board has identity equality. Repetition detection needs history. Service-level authorization, transactions and gameplay concurrency remain outside pure rules. AI is outside the MVP. Map-versus-array evaluation remains deferred.

From backend/:

```bash
./gradlew test --tests 'com.chessapp.game.rules.*'
```

For a full backend regression run with the configured PostgreSQL test database available:

```bash
SPRING_PROFILES_ACTIVE=test ./gradlew test
```

No frontend changes are introduced by Day 9. No endpoint, response shape, migration or persisted-game behavior changes.
