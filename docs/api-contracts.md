# API Contracts

Checkpoint reviewed on 25 October 2026 against the attached `chess-web-app-day8-end(2).zip`. The knight/rook Strategy pipeline and its tests were inspected. Both Day 8 cleanup items are present: `returningRookToOriginalCornerDoesNotRestoreCastlingRight` starts with `1n4k1/8/8/8/8/8/8/R3K2R w KQ - 0 20`, and MoveApplier documents knight and rook support. No blocking movement-pipeline defect was found in this source review. The user reported all tests green for the earlier Day 8 end archive; a rerun of the corrected fixture is not confirmed. No independent tests or builds were run. Day 9 guided changes through queen generation/dispatch have a user-reported green-test result; final queen pipeline tests remain pending. No fresh Day 9 source archive has been inspected. Days 6 and 7 introduce no endpoint or response-shape change. GameService now obtains initial FEN through the rules boundary; games still contain only the initial position.

This document lists expected application failures by action. It is not an exhaustive catalog of framework, transport or infrastructure failures. An absent application code is intentional where the current implementation does not guarantee one.

## Shared conventions

- Protected endpoints require `Authorization: Bearer <token>`.
- Protected self-service actions derive player identity from the validated JWT subject; clients do not choose the acting player ID.
- MVC validation returns Problem Detail with `code: VALIDATION_FAILED` and an `errors` array containing `field` and `message`. Rejected password values are omitted.
- Duplicate-identity and feature-owned exceptions return Problem Detail with their application code.
- Missing/invalid bearer credentials use Spring Security handling; no application code or uniform MVC error body is guaranteed.
- Deleted-player failures from profile GET/PATCH use `ResponseStatusException` with 401; no application code is guaranteed. Matchmaking has its own `AUTHENTICATED_PLAYER_UNAVAILABLE` code.
- Malformed JSON, incompatible field types, path conversion failures and unexpected server errors may have different bodies. Clients need a generic fallback.
- The frontend handles protected 401 session cleanup centrally; action-specific UI handles known codes. A 403 does not invalidate the session.

## Endpoint index

| Method | Route | Authentication | Successful response |
| --- | --- | --- | --- |
| POST | `/api/v1/auth/register` | Public | 201; `playerId`, `username`, `email` |
| POST | `/api/v1/auth/login` | Public | 200; `accessToken`, `tokenType`, `expiresIn` |
| GET | `/api/v1/players/me` | Bearer JWT | 200; `id`, `username`, `email` |
| PATCH | `/api/v1/players/me` | Bearer JWT | 200; updated `id`, `username`, `email` |
| POST | `/api/v1/matchmaking/queue` | Bearer JWT | 200; queue status |
| GET | `/api/v1/matchmaking/queue/me` | Bearer JWT | 200; queue status |
| DELETE | `/api/v1/matchmaking/queue/me` | Bearer JWT | 204; no body |
| GET | `/api/v1/games/{gameId}` | Participant bearer JWT | 200; game state |

There is no public game-creation or move-submission endpoint in the current implementation. Pairing invokes game creation internally. Day 7 movement classes are pure Java internal components; their exceptions are not HTTP application error contracts. Stored game state still remains at the initial position.

## Register — POST `/api/v1/auth/register`

Request fields: `username`, `email`, `password`.

Username: nonblank, 3–30 Java UTF-16 string units. Email: nonblank, valid email format, at most 255 units. Password: nonblank, at least 8 units and at most 72 UTF-8 bytes. No trimming or normalization is applied. Success returns the registered identity without credentials or a token.

| Failure | Status | Application code |
| --- | --- | --- |
| Invalid fields | 400 | `VALIDATION_FAILED` |
| Username taken | 409 | `USERNAME_ALREADY_EXISTS` |
| Email registered | 409 | `EMAIL_ALREADY_EXISTS` |

## Login — POST `/api/v1/auth/login`

Request fields: `email`, `password`. Email matching is exact. Login validates nonblank fields, email format/maximum length and the BCrypt byte maximum; it does not impose registration's minimum password length.

Success returns `accessToken`, `tokenType: Bearer` and `expiresIn` in seconds, with `Cache-Control: no-store`. The frontend then loads the self-profile. Unknown accounts and incorrect/missing local credentials receive the same invalid-credentials response.

| Failure | Status | Application code |
| --- | --- | --- |
| Invalid fields | 400 | `VALIDATION_FAILED` |
| Invalid credentials | 401 | `INVALID_CREDENTIALS` |

## Read self-profile — GET `/api/v1/players/me`

No request body. Success returns the current database-backed `PlayerResponse` (`id`, `username`, `email`).

| Failure | Status | Application code |
| --- | --- | --- |
| Missing, invalid or expired token | 401 | Not guaranteed |
| Authenticated player no longer exists | 401 | Not guaranteed |

## Update username — PATCH `/api/v1/players/me`

Request:

```json
{
  "username": "new_username"
}
```

Success: 200 with the existing `PlayerResponse` shape:

```json
{
  "id": 1,
  "username": "new_username",
  "email": "player@example.com"
}
```

### Rules

- Only the authenticated player's username changes. Email and ID are unchanged.
- Username must be nonblank and 3–30 Java UTF-16 string units long. Some supplementary Unicode characters occupy two units.
- Matching is exact and case-sensitive. Spaces and special characters are allowed; whitespace-only values are rejected. No trimming or normalization is performed.
- Submitting the current username succeeds without changing the entity or its update timestamp.
- Availability prechecks provide feedback; the existing PostgreSQL unique constraint protects against conflicting writes under concurrency.
- Existing JWTs remain valid because their subject contains the stable player ID.
- Concurrent edits to the same player's username use last-write-wins behavior.
- Missing/null username is invalid. This endpoint is a username-specific update, not a general JSON Patch or JSON Merge Patch implementation. Additional fields are outside its supported contract.

| Failure | Status | Application code |
| --- | --- | --- |
| Invalid username | 400 | `VALIDATION_FAILED` |
| Username taken | 409 | `USERNAME_ALREADY_EXISTS` |
| Missing, invalid or expired token | 401 | Not guaranteed |
| Authenticated player no longer exists | 401 | Not guaranteed |

A representative validation response is:

```json
{
  "type": "about:blank",
  "title": "Request validation failed",
  "status": 400,
  "detail": "One or more request fields are invalid",
  "instance": "/api/v1/players/me",
  "code": "VALIDATION_FAILED",
  "errors": [
    { "field": "username", "message": "must not be blank" }
  ]
}
```

Validation messages and the number of entries depend on the input and validator. Clients should use `field` and `code` rather than match literal messages.

## Queue join/status/leave

POST `/api/v1/matchmaking/queue` joins and attempts pairing. GET `/api/v1/matchmaking/queue/me` reads status. DELETE `/api/v1/matchmaking/queue/me` removes waiting membership and returns 204, including repeated leave requests.

Queue responses contain `status`, `enqueuedAt` and `gameId`. `WAITING` has an enqueue timestamp; `MATCHED` has a game ID. Inapplicable fields are null under the current DTO. Status is `NOT_QUEUED`, `WAITING` or `MATCHED`.

Repeated joins preserve the original waiting timestamp or return the existing active game. Leaving does not cancel an established game. Logout does not leave the queue. Pairing is triggered on join; clients currently refresh manually.

| Action | Failure | Status | Application code |
| --- | --- | --- | --- |
| Join, status, leave | Missing, invalid or expired token | 401 | Not guaranteed |
| Join, status, leave | Authenticated player no longer exists | 401 | `AUTHENTICATED_PLAYER_UNAVAILABLE` |
| Join during pairing | Reservation conflict with another game-creation caller | 409 | `PLAYER_ALREADY_IN_GAME` |

An ordinary repeated join by an already matched player returns 200 `MATCHED`; it is not a conflict. The reservation-conflict case describes a competing creation race and rolls back the pairing transaction.

## Read game — GET `/api/v1/games/{gameId}`

No request body. Success returns `id`, `whitePlayerId`, `blackPlayerId`, `status`, `currentFen` and `createdAt`. Either participant can read. Opponent email is not returned. Current games contain only `IN_PROGRESS` and the initial FEN.

| Failure | Status | Application code |
| --- | --- | --- |
| Missing, invalid or expired token | 401 | Not guaranteed |
| Game does not exist | 404 | `GAME_NOT_FOUND` |
| Authenticated requester is not a participant | 403 | `GAME_ACCESS_DENIED` |

## Keeping contracts synchronized

When changing an endpoint, update its owning DTO/service/controller, corresponding integration assertions, this action's contract and the checkpoint progress together. Keep feature error constants in their owning package. Tests verify documented behavior; this document does not establish that a test run passed.

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
