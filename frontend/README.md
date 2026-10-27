# Chess Frontend

Reviewed on 27 October 2026 against `chess-web-app-day12-end.zip`. Source inspection confirms explicit promotion choices, candidate generation, immutable replacement, shared metadata handling, renderer updates and associated tests. No blocking defect was found in the reviewed Day 12 changes. The user reports all tests green. No independent tests or builds were run; no detailed test log or separate frontend build result was supplied.

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

Day 12 test results are user-reported. No independent tests/builds or separate frontend build/manual-check results are recorded.

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

## Day 12 completed checkpoint — 27 October 2026

Promotion is implemented for both colors, for quiet advances and captures, with explicit queen, rook, bishop and knight choices. Ordinary moves retain the two-argument constructor. No choice defaults to queen.

`Move` validates replacement-type shape. Position-dependent validity is established by exact pseudo-legal candidate membership, including the choice. `MoveApplier` preserves the pawn's color and applies the replacement through an immutable Board operation; `LegalMoveGenerator` checks the moving side's king on the resulting board.

Pawn halfmove resets, Black fullmove increments, turn changes, en passant expiry and captured-corner-rook castling-right removal remain shared. A promoted corner rook never grants castling rights. The test renderer lists promotion choices distinctly.

The user reports all tests green for the final Day 12 archive. Review was by source inspection; no independent tests or builds were run.

Movement remains internal pure Java code. `ChessRules` exposes only `initialPosition()`; `GameService` still stores the initial FEN and does not apply moves. Day 12 changes no HTTP contract, DTO, migration or frontend implementation. En passant capture execution, castling execution, complete legal-move enumeration, outcomes, history and gameplay integration remain pending. A source-specific result cannot establish checkmate or stalemate.
