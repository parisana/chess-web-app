# Chess Frontend

## Day 17 current checkpoint — source-reviewed, 28 October 2026

Reviewed the actual user implementation in `chess-web-app-day17-end(1).zip` against `chess-web-app-day16-end.zip`. No blocking defect was found in the reviewed Day 17 production/test changes. Tests are treated as green under the user's standing instruction to report failures; this is user-provided verification context, not an independent run or a detailed per-command log. No tests, builds or migrations were run by the assistant, and no separate frontend build result is claimed.

`GameService.submitMove(gameId, requestingPlayerId, expectedVersion, move)` now coordinates participant/turn checks, exact legal application, resulting-position assessment, current-FEN persistence, stale/concurrent-command rejection and atomic checkmate/stalemate completion with reservation release. `GameMoveResult.from(game, assessment)` owns mechanical result mapping; `Game.finish()` calls its extracted switch-based `validateResult()`. There is no separate production GameMoveService.

V6 adds completion fields and consistency constraints. GameStatus is IN_PROGRESS/FINISHED, with separate result, termination reason and finish time. The unchanged game GET response fields can now carry an advanced FEN and FINISHED status; version/result/reason/time remain absent from that HTTP response. No HTTP move route, move history, frontend gameplay or disconnect policy is added. See the [reviewed implementation and verification commands](../docs/day17-gameplay-service.md).

Day 16 and earlier milestone sections below retain historical behavior/evidence. Their initial-only and pending-gameplay descriptions do not override this checkpoint. The earlier closing-test passing-status uncertainty is retained as historical provenance; current test status follows the user's later standing instruction.

### Historical Day 16 review provenance

Reviewed on 28 October 2026 against `chess-web-app-day16-end.zip`. Source inspection confirms the legality-enforcing facade, complete production strategy registration, facade regression tests and both [closing checks](../docs/day16-closing-checks.md). No blocking defect was found in the reviewed Day 16 changes. The user reports all tests green through increments 1–2. Both later closing tests are present, but no subsequent explicit passing report was supplied for them. No tests or builds were run independently; no detailed test log or separate frontend build result was supplied.

The Day 16 section describes the inspected implementation and supersedes earlier initial-position-only boundary and test-only-construction statements. Historical sections retain the behavior and evidence available at those checkpoints.

## Day 16 boundary — historical

Backend rules are exposed through the legality-enforcing pure Java facade confirmed in the reviewed Day 16 source. There is no frontend implementation or HTTP contract change. Games still persist only their initial FEN in IN_PROGRESS. No game route, chessboard, move submission or real-time subscription is added. Existing frontend verification remains unchanged; no separate frontend build result is claimed.

Historical Day 15 source-review provenance: reviewed on 27 October 2026 against `chess-web-app-day15-end.zip`. Source inspection confirms position-wide legal-move enumeration and `NORMAL` / `CHECK` / `CHECKMATE` / `STALEMATE` assessment for the side to move. No blocking defect was found in the reviewed Day 15 changes. The user reports all tests green after the final guided regressions. No independent tests or builds were run; no detailed test log or separate frontend build result was supplied.

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

Current tests are treated as green under the user's standing instruction. Frontend implementation/tests are byte-for-byte unchanged from Day 16; no independent tests/builds or separate frontend build/manual-check results are recorded.

## Day 15 boundary — historical

Backend rules now support complete legal-move enumeration and normal/check/checkmate/stalemate assessment internally. No frontend implementation, endpoint or response shape changed. There is still no game route, chessboard, move submission or real-time subscription. Persisted games remain at their initial FEN in IN_PROGRESS.

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

## Day 13 completed checkpoint — historical, 27 October 2026

En passant is implemented for both colors. Generation requires the correct source rank, one forward diagonal step, matching target metadata, an empty destination and an adjacent opposing pawn. An inconsistent or unavailable opportunity contributes no en passant candidate and does not suppress ordinary moves. Attempting an omitted capture fails exact candidate membership with `IllegalArgumentException`; no unsupported-en-passant exception remains.

`MoveApplier` validates candidate membership before identifying a pawn's diagonal move into an empty square as en passant. It relocates the pawn and removes the opposing pawn from `(destination.file, source.rank)` through immutable Board operations. `Board.removePiece()` requires a nonnull occupied square and performs placement only; callers own chess-rule validation. The original position remains unchanged.

Shared handling switches turns, resets the pawn halfmove clock, increments the fullmove number after Black, and expires the target. En passant preserves existing castling rights. Ordinary moves, promotion choices and irreversible king/rook rights removal remain supported. An occupied diagonal follows ordinary capture rules even if inconsistent target metadata names it; no adjacent pawn is removed in that case. Promotion choices attached to en passant fail candidate membership.

`LegalMoveGenerator` remains shared and unchanged. It checks the moving side's king after both pawn squares have been vacated. Tests cover both colors, both capture directions in generation, FEN results, original-position preservation, missing/inconsistent metadata, absent/friendly/non-pawn adjacent pieces, wrong source ranks, occupied targets, opportunity expiry, ordinary advances instead of capture, castling-right preservation, invalid promotion choices, exposed rook/bishop attacks and removal of checking pawns. Board tests cover immutable removal and invalid removal inputs. The three obsolete unsupported-case tests were replaced. Selected tests use `com.chessapp.BoardTestRenderer`.

The user reports all tests green for the final Day 13 archive. Review was by source inspection; no independent tests or builds were run.

Movement remains internal pure Java code. `ChessRules` exposes only `initialPosition()`; `GameService` still stores the initial FEN and does not apply moves. Day 13 changes no HTTP contract, DTO, migration or frontend implementation. Castling execution, complete legal-move enumeration, outcomes, move history and gameplay integration remain pending. Generation handles a single source; an empty result cannot establish checkmate or stalemate. FEN parsing is structural, not proof of legality or reachability; the implementation does not reconstruct the preceding move from history. Board equality is identity-based, repetition requires history, and authorization, transactions, persistence and gameplay concurrency remain service responsibilities. AI and map-versus-array evaluation remain deferred.

## Day 14 boundary — historical, 27 October 2026

Standard castling for both colors and both sides is implemented internally. Candidate generation checks rights and placement; application relocates both pieces; legal filtering checks starting, transit and destination safety. See [architecture.md](../docs/architecture.md) for responsibilities and verification provenance. Day 14 changes no endpoint, DTO, migration or frontend implementation.

Movement remains internal pure Java code. `ChessRules` exposes only `initialPosition()`; `GameService` creates and stores the initial FEN and does not apply moves. Complete legal-move enumeration, outcomes, move history and gameplay integration remain pending. Single-source generation cannot establish checkmate or stalemate. FEN parsing remains structural, Board equality remains identity-based, and repetition requires history. Authorization, transactions, persistence and gameplay concurrency remain service responsibilities. AI and map-versus-array evaluation remain deferred.
