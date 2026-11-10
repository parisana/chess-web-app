# Day 18 — HTTP gameplay

## Scope

Expose the existing GameService gameplay command through authenticated HTTP.
Extend game reads so clients can obtain the version required for their next
command and inspect completion details after the game finishes.

Implementation was supplied through guided inline increments. Tests are
treated as green under the user's standing instruction to report failures.
The assistant did not independently run tests or builds. A final Day 18
implementation archive has not yet been source-reviewed.

## Game reads

GET /api/v1/games/{gameId} remains participant-protected.

GameResponse contains:

- id
- whitePlayerId
- blackPlayerId
- status
- currentFen
- createdAt
- version
- result
- terminationReason
- finishedAt

GameResponse.from(game) owns mechanical mapping.

Active games expose null result, terminationReason and finishedAt.
Finished games expose their persisted completion details.

Reads do not mutate the game or increment its version. They do not
re-evaluate the position. PositionStatus remains part of the move result.

## Move submission

POST /api/v1/games/{gameId}/moves

Example request:

{
  "expectedVersion": 0,
  "from": "e2",
  "to": "e4",
  "promotion": null
}

The controller derives the requesting Player from the validated JWT subject.
The body does not choose the acting Player, color, accepted FEN or outcome.

SubmitMoveRequest validates:

- A required, nonnegative expectedVersion.
- Required lowercase algebraic squares between a1 and h8.
- Distinct source and destination squares.
- An optional explicit QUEEN, ROOK, BISHOP or KNIGHT promotion choice.

An omitted or null promotion becomes Optional.empty(). There is no
automatic queen promotion.

After Bean Validation succeeds, toMove() converts the request into the
existing Move value. Board-dependent validity remains in the rules pipeline.
The distinct-square validation property is named distinctSquares.

Success returns 200 with GameMoveResult:

- gameId
- currentFen
- version
- positionStatus
- status
- result
- terminationReason
- finishedAt

The client uses the returned version for its next command. After a stale
version response, it reloads the game before deciding what to submit.
Commands are not automatically retried.

## Error contract

| Failure | Status | Code |
| --- | --- | --- |
| Invalid request fields | 400 | VALIDATION_FAILED |
| Missing or invalid bearer token | 401 | Not guaranteed |
| Requester is not a participant | 403 | GAME_ACCESS_DENIED |
| Missing game | 404 | GAME_NOT_FOUND |
| Finished game | 409 | GAME_ALREADY_FINISHED |
| Stale version | 409 | STALE_GAME_VERSION |
| Wrong turn | 409 | NOT_PLAYERS_TURN |
| Well-formed but illegal move | 422 | ILLEGAL_GAME_MOVE |

GameErrorCodes owns game-specific codes. GameExceptionHandler maps the
specific application exceptions. ApiExceptionHandler retains shared
field-validation handling.

Malformed JSON and incompatible JSON types return 400 without promising
VALIDATION_FAILED. Security-filter failures retain their existing contract.

Request validation happens before the service command. For well-formed
commands, service precedence remains: missing game, participant membership,
lifecycle, version, turn, exact legal membership.

Unexpected stored-state and rules failures are not broadly translated into
client move rejections.

## Architecture and persistence

GameService remains the owner of authorization, transactions, legal
application, assessment, persistence, concurrency and terminal release.

The controller introduces no movement algorithms. The pure rules graph,
expected-version checks, JPA optimistic locking and atomic reservation
release retain their Day 17 behavior.

No migration or runtime/security configuration change is needed.

## Verification coverage

SubmitMoveRequestTest covers request constraints and Move conversion.

GameReadIntegrationTest covers initial state, advanced FEN/version and
completion-bearing reads. It retains its enclosing test transaction.

GameMoveHttpIntegrationTest has no enclosing test transaction. It exercises
real signed JWTs, committed HTTP moves, fresh database reads and subsequent
GETs by both participants.

HTTP coverage includes ordinary moves, selected special moves, explicit
underpromotion, promotion failures, authentication, access, error precedence,
stale commands, validation, malformed input and both-color mate/stalemate.

Existing Day 17 tests retain the broader rules, transaction rollback and
deterministic optimistic-race coverage.

## Remaining boundary

Move history, frontend gameplay, real-time delivery and chess clocks remain
pending. Additional draw rules, resignation/agreement and any disconnect or
abandonment policy require separate scope decisions.

Disconnecting still leaves an in-progress game and its reservations active.
