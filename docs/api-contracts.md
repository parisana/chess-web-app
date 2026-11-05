# API Contracts

Current Day 28 contracts, reviewed on 5 November 2026 against actual user chess-web-app-day28-end.zip. See [review](day28-ui-review.md). Session/CSRF contracts remain; legal hints, starting snapshots and finished-game discovery are added. No assistant application execution.

## Shared conventions

- Protected endpoints require a valid CHESS_SESSION cookie. State-changing requests also require a matching XSRF-TOKEN cookie and X-XSRF-TOKEN header. This includes anonymous login/registration and logout.
- Protected self-service actions derive player identity from the authenticated session principal name; clients do not choose the acting player ID.
- MVC validation returns Problem Detail with `code: VALIDATION_FAILED` and an `errors` array containing `field` and `message`. Rejected password values are omitted.
- Duplicate-identity and feature-owned exceptions return Problem Detail with their application code.
- Missing/invalid session authentication use Spring Security handling; no application code or uniform MVC error body is guaranteed.
- Deleted-player failures from profile GET/PATCH and statistics GET use `ResponseStatusException` with 401; no application code is guaranteed. Matchmaking has its own `AUTHENTICATED_PLAYER_UNAVAILABLE` code.
- Malformed JSON, incompatible field types, path conversion failures and unexpected server errors may have different bodies. Clients need a generic fallback.
- The frontend handles protected 401 session cleanup centrally; action-specific UI handles known codes. A 403 does not invalidate the session.

## Endpoint index

| Method | Route | Authentication | Successful response |
| --- | --- | --- | --- |
| POST | `/api/v1/auth/register` | Anonymous access + CSRF | 201; `playerId`, `username`, `email` |
| POST | `/api/v1/auth/login` | Anonymous access + CSRF | 200; `id`, `username`, `email`; session cookie; no-store |
| GET | `/api/v1/auth/csrf` | Anonymous access | 204; initializes CSRF cookie; no-store |
| POST | `/api/v1/auth/logout` | Valid CSRF; authentication may be absent | 204; invalidates current session; clears cookies; no-store |
| GET | `/api/v1/players/me` | Session cookie | 200; `id`, `username`, `email` |
| GET | `/api/v1/players/me/statistics` | Session cookie | 200; `playerId`, `rating`, `wins`, `draws`, `losses`, `gamesPlayed`; no-store |
| PATCH | `/api/v1/players/me` | Session cookie | 200; updated `id`, `username`, `email` |
| POST | `/api/v1/matchmaking/queue` | Session cookie | 200; queue status |
| GET | `/api/v1/matchmaking/queue/me` | Session cookie | 200; queue status |
| DELETE | `/api/v1/matchmaking/queue/me` | Session cookie | 204; no body |
| GET | `/api/v1/games` | Session cookie | 200; self-only PlayedGamesResponse; no-store |
| GET | `/api/v1/games/{gameId}/legal-moves` | Participant session | 200; versioned legal commands; no-store |
| GET | `/api/v1/games/{gameId}` | Participant session | 200; game state |
| GET | `/api/v1/games/{gameId}/pgn` | Participant session | 200; UTF-8 PGN attachment |
| POST | `/api/v1/games/{gameId}/moves` | Participant session | 200; GameMoveResult |
| GET | `/api/v1/games/{gameId}/moves` | Participant session | 200; GameHistoryResponse |
| POST | `/api/v1/games/{gameId}/resignation` | Participant session | 200; GameResponse |
| POST | `/api/v1/games/{gameId}/draw-offer` | Participant session | 200; GameResponse |
| POST | `/api/v1/games/{gameId}/draw-offer/accept` | Participant session | 200; GameResponse |
| POST | `/api/v1/games/{gameId}/draw-offer/decline` | Participant session | 200; GameResponse |
| POST | `/api/v1/games/{gameId}/draw-offer/withdraw` | Participant session | 200; GameResponse |

There is no public game-creation endpoint: pairing invokes creation internally. Participant-authenticated POST /api/v1/games/{gameId}/moves delegates to GameCommandService. Pure rules exceptions are not themselves the HTTP contract; game-owned application exceptions map gameplay rejections.

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

Success returns PlayerResponse (`id`, `username`, `email`) with `Cache-Control: no-store` and establishes the session cookie. The frontend reinitializes CSRF before storing the local profile; startup restoration separately reads the self-profile. Unknown accounts and incorrect/missing local credentials receive the same invalid-credentials response.

| Failure | Status | Application code |
| --- | --- | --- |
| Invalid fields | 400 | `VALIDATION_FAILED` |
| Invalid credentials | 401 | `INVALID_CREDENTIALS` |

## Read self-profile — GET `/api/v1/players/me`

No request body. Success returns the current database-backed `PlayerResponse` (`id`, `username`, `email`).

| Failure | Status | Application code |
| --- | --- | --- |
| Missing, invalid or expired session | 401 | Not guaranteed |
| Authenticated player no longer exists | 401 | Not guaranteed |

## Read self-statistics — GET `/api/v1/players/me/statistics`

Player ID comes exclusively from the authenticated session principal name. Success is 200 with Cache-Control: no-store:

```json
{"playerId":7,"rating":1200,"wins":0,"draws":0,"losses":0,"gamesPlayed":0}
```

No email or persistence version is included. gamesPlayed equals wins+draws+losses. Ratings can be negative; no floor is imposed. Missing/invalid authentication is rejected by security. A session for a deleted Player yields 401, matching self-profile policy. An existing Player missing statistics is an internal invariant failure, not a default zero response or a new structured feature error.

V11 excludes games already finished at migration time; active games count when they later finish. All four supported completion reasons count, including zero-move resignation/agreement. Duplicate completion events do not apply a second update. Completion and statistics share one transaction; a failed rating update prevents the completion from committing. This GET performs ordinary reads and adds no mutation or retry behavior. No public leaderboard, rating-history or statistics-write endpoint exists.

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
- Existing sessions retain the stable Player ID after a rename.
- Concurrent edits to the same player's username use last-write-wins behavior.
- Missing/null username is invalid. This endpoint is a username-specific update, not a general JSON Patch or JSON Merge Patch implementation. Additional fields are outside its supported contract.

| Failure | Status | Application code |
| --- | --- | --- |
| Invalid username | 400 | `VALIDATION_FAILED` |
| Username taken | 409 | `USERNAME_ALREADY_EXISTS` |
| Missing, invalid or expired session | 401 | Not guaranteed |
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

Repeated joins preserve the original waiting timestamp or return the existing active game. Leaving does not cancel an established game. Logout does not leave the queue. Pairing is triggered on join. Day 20 notifications request queue reads automatically; manual refresh remains available. Home offers Open game after discovering MATCHED and does not automatically navigate.

| Action | Failure | Status | Application code |
| --- | --- | --- | --- |
| Join, status, leave | Missing, invalid or expired session | 401 | Not guaranteed |
| Join, status, leave | Authenticated player no longer exists | 401 | `AUTHENTICATED_PLAYER_UNAVAILABLE` |
| Join during pairing | Reservation conflict with another game-creation caller | 409 | `PLAYER_ALREADY_IN_GAME` |

An ordinary repeated join by an already matched player returns 200 `MATCHED`; it is not a conflict. The reservation-conflict case describes a competing creation race and rolls back the pairing transaction.

## Read game — GET `/api/v1/games/{gameId}`

No request body. Success returns id, whitePlayerId, blackPlayerId, status, currentFen, createdAt, version, result, terminationReason, finishedAt and nullable drawOfferedByPlayerId through GameResponse.from(game). GameResponse also includes projected clock and nullable whiteUsernameAtStart, blackUsernameAtStart, whiteRatingAtStart and blackRatingAtStart. Either participant can read; opponent email is not returned. Snapshots remain fixed after profile/rating changes; legacy values are null. New games start in IN_PROGRESS at the initial FEN. Accepted commands advance FEN/version and may produce FINISHED. Completion fields are null during active play. GET does not reevaluate the position and has no positionStatus.

| Failure | Status | Application code |
| --- | --- | --- |
| Missing, invalid or expired session | 401 | Not guaranteed |
| Game does not exist | 404 | `GAME_NOT_FOUND` |
| Authenticated requester is not a participant | 403 | `GAME_ACCESS_DENIED` |

## Submit move — POST `/api/v1/games/{gameId}/moves`

Authenticated Player identity comes from the authenticated session principal name.

```json
{
  "expectedVersion": 0,
  "from": "e2",
  "to": "e4",
  "promotion": null
}
```

expectedVersion is required and nonnegative. Coordinates are required lowercase algebraic squares from a1 through h8 and must differ. Promotion is omitted/null or exactly QUEEN, ROOK, BISHOP or KNIGHT; board-dependent validity remains in the rules pipeline. No automatic queen choice is supplied.

Success returns 200 with gameId, currentFen, version, positionStatus, status, result, terminationReason and finishedAt. positionStatus assesses the side to move and is NORMAL, CHECK, CHECKMATE or STALEMATE. Lifecycle is IN_PROGRESS/FINISHED; results are WHITE_WIN/BLACK_WIN/DRAW; supported termination reasons are CHECKMATE/STALEMATE/RESIGNATION/DRAW_AGREEMENT. Move-driven completion uses CHECKMATE/STALEMATE; explicit lifecycle commands produce the other two.

| Failure | Status | Application code |
| --- | --- | --- |
| Invalid request fields | 400 | VALIDATION_FAILED |
| Missing/invalid session authentication | 401 | Not guaranteed |
| Nonparticipant | 403 | GAME_ACCESS_DENIED |
| Missing game | 404 | GAME_NOT_FOUND |
| Finished game | 409 | GAME_ALREADY_FINISHED |
| Stale observed/concurrent version | 409 | STALE_GAME_VERSION |
| Wrong turn | 409 | NOT_PLAYERS_TURN |
| Well-formed illegal proposal | 422 | ILLEGAL_GAME_MOVE |

Request validation precedes service lookup. Well-formed command precedence is missing game, membership, lifecycle, version, turn and exact legal membership. Malformed JSON/incompatible types return 400 without promising VALIDATION_FAILED. Unexpected rules/stored-state failures are not broadly reclassified as client illegality.

Clients use the accepted response version for the next command. Reload stale state without automatically replaying a move. An uncertain network failure may follow a commit, so read before proposing another move.

The Angular page preserves GET-only participant metadata when applying a POST response. Both participants can read a finished game. Terminal release permits another queue join. Day 20 GAME_UPDATED notifications request a fresh GET for the opponent as well. The separate history GET exposes persisted moves including SAN.

## Read move history — GET `/api/v1/games/{gameId}/moves`

No body or pagination parameters. Either stored participant may read active or finished history. Identity comes from the authenticated session. Lookup precedes membership checking, using the existing GAME_NOT_FOUND (404) and GAME_ACCESS_DENIED (403) contracts. Missing/invalid credentials receive 401 without a guaranteed MVC code.

GameHistoryResponse fields:

| Field | Meaning |
| --- | --- |
| gameId | Requested game |
| version | Captured game concurrency version |
| historyStartFen | Position immediately before the first recorded move |
| historyComplete | Every move since creation is recorded; false for V7 legacy games |
| recordedMoveCount | Number of recorded individual moves after the baseline |
| moves | Ordered ascending by sequenceNumber, bounded by that captured count |

Each move contains sequenceNumber, playerId, from, to, promotion, resultingFen, playedAt and san. Sequence begins at 1 relative to the baseline, not the chess fullmove number. Promotion is null or QUEEN/ROOK/BISHOP/KNIGHT. New application writes require nonblank SAN of at most 32 characters; old pre-SAN rows may return null. Castling/en passant each occupy one entry. playedAt and terminal finishedAt use the same timestamp for a completing move. Internal move-row IDs are not exposed.

The baseline and resulting FENs supply Angular replay. The separate PGN endpoint below uses this stored history; its Angular download UI is deferred. There is no SAN backfill or reconstruction of missing pre-V7 moves. Reads currently return the full recorded list; pagination/retention limits are not implemented. History shape is unchanged; move POST adds clock in Day 26; GameResponse adds nullable drawOfferedByPlayerId in Day 22.

## Export PGN — GET `/api/v1/games/{gameId}/pgn`

No request body, expectedVersion, replay cursor or pagination parameters. Either participant can export an active or finished game when history is supported. Lookup precedes membership checks; authorization precedes history availability disclosure. This read does not mutate game/version/history/reservations or publish game events.

Successful response:

| Header | Value |
| --- | --- |
| Content-Type | application/x-chess-pgn;charset=UTF-8 |
| Content-Disposition | attachment; filename="game-{gameId}.pgn" |
| Cache-Control | no-store |

Body contains Event, Site, Date, Round, White, Black and Result headers. Event is Casual Game, Site is ?, Round is -, Date uses creation time formatted as uuuu.MM.dd in UTC, and Player names use current usernames with backslash/quote escaping and control-character replacement. Nonstandard baselines add FEN and SetUp=1 supplemental tags. Header spacing/case above describes semantics rather than an exact HTTP serialization string.

Movetext reuses persisted SAN; every White move is numbered and an initial Black move uses the baseline fullmove number plus three periods. Fullmove advances after Black. Lines wrap between tokens below 80 characters. The final marker matches Result: * for active games, 1-0 for WHITE_WIN, 0-1 for BLACK_WIN, 1/2-1/2 for DRAW. A completed game with no moves still exports its final marker. A pending draw offer has no SAN entry or special export marker.

The service captures result/version and a move-count boundary before reading rows at or below that boundary. Later moves are excluded, relying on the existing atomic append-only write model. The export can already be older than the live game when received; no browser replay selection limits its contents. No ETag/version response header or username history is supplied.

| Failure | HTTP | Application code |
| --- | --- | --- |
| Missing/invalid session authentication | 401 | Not guaranteed |
| Missing game | 404 | GAME_NOT_FOUND |
| Nonparticipant | 403 | GAME_ACCESS_DENIED |
| Incomplete history, row-count mismatch, sequence gap or missing/blank SAN | 409 | PGN_EXPORT_UNAVAILABLE |

V7 legacy history is rejected even when its recorded list is empty. Pre-SAN null entries are rejected without reconstruction. Unexpected malformed stored FEN or internal snapshot mismatches are not reclassified as legacy availability. Formatting trusts internally persisted SAN; it is not an arbitrary PGN/SAN validator or a legality replay. There is no Angular download integration in this checkpoint. See [flow](replay-pgn-flow.md).

## Explicit lifecycle commands

All five routes require participant authentication. The actor comes from the session; no request-supplied Player ID selects the actor. Each uses a JSON object with required, nonnegative Long expectedVersion:

```json
{"expectedVersion": 3}
```

Resignation uses ResignGameRequest; the four draw endpoints share DrawOfferRequest. Success returns 200 with the complete GameResponse, including nullable drawOfferedByPlayerId and the new concurrency version. Neither FEN nor recorded move count changes and no move row is inserted.

| POST suffix under /api/v1/games/{gameId} | Preconditions after common checks | Outcome |
| --- | --- | --- |
| /resignation | Either participant, any turn | FINISHED; opponent wins; RESIGNATION |
| /draw-offer | No pending offer; either participant, any turn | Remains active; sender stored |
| /draw-offer/accept | Pending opponent offer | FINISHED; DRAW; DRAW_AGREEMENT |
| /draw-offer/decline | Pending opponent offer | Remains active; offer cleared |
| /draw-offer/withdraw | Pending own offer | Remains active; offer cleared |

Play continues during an offer. Any accepted move by either side clears it. A rejected move preserves it. Every completion clears it. This is the application's negotiation policy, not a claim of tournament draw-offer procedure. No expiry, minimum move requirement, offer throttling or negotiation audit is implemented.

Well-formed command precedence is missing game, participant membership, active lifecycle, expected version, clock expiry, then offer presence/role. For offering, pending-offer detection follows the common checks. Field validation happens before service lookup.

| Failure | HTTP | Application code |
| --- | --- | --- |
| Invalid fields | 400 | VALIDATION_FAILED |
| Missing/invalid session authentication | 401 | Not guaranteed |
| Nonparticipant | 403 | GAME_ACCESS_DENIED |
| Missing game | 404 | GAME_NOT_FOUND |
| Finished game | 409 | GAME_ALREADY_FINISHED |
| Stale observed/concurrent version | 409 | STALE_GAME_VERSION |
| Existing offer when offering | 409 | DRAW_OFFER_ALREADY_PENDING |
| No offer when accepting/declining/withdrawing | 409 | DRAW_OFFER_NOT_FOUND |
| Sender accepts/declines, or recipient withdraws | 409 | DRAW_OFFER_ACTION_NOT_ALLOWED |

Resignation and agreement set finishedAt from Clock, truncated to microseconds, and require exactly two reservation deletions. Flush, completion, release and event publication share one transaction. A competing command is rejected rather than replayed. Repeated successful lifecycle requests do not have an idempotency key; reread current state after a conflict or uncertain response.

A lifecycle change advances history's captured version even when its move list stays identical. Clients must reconcile board and history versions as before. Notifications remain GAME_UPDATED hints with no offer/result payload.

## Real-time updates

WebSocket messages are server-to-client change hints. Existing REST routes and error precedence are unchanged by Day 20; move commands remain HTTP POST.

### Addresses and configuration

Addresses remain configured under app.websocket: endpoint /api/v1/ws, allowed-origin http://localhost:4200, application-prefix /app, user-prefix /user, broker-prefix /queue, updates-path /updates, first-message timeout 10s and heartbeat 10s. Derived user-send is /queue/updates and subscription is /user/queue/updates. Existing literal-path/origin/prefix/duration validation remains.

The HTTP upgrade requires the session cookie and accepts only the configured Origin. Handshake metadata contains the HTTP session ID, authenticated Player name and raw XSRF cookie. STOMP CONNECT then supplies exactly one native header:

```text
X-XSRF-TOKEN: <current CSRF cookie value>
```

Inherited authentication and persisted-session identity are rechecked before binding the socket. Native Authorization cannot authenticate. Missing HTTP authentication is rejected at upgrade (401); missing handshake CSRF or disallowed Origin is 403. CONNECT failures after upgrade use STOMP ERROR rather than a REST error body. See [detailed diagram](session-authentication-flow.md#websocket-authentication-in-detail).

### Client operations

| Operation | Policy |
| --- | --- |
| CONNECT / STOMP | Inherit authenticated HTTP session; validate handshake metadata and native CSRF |
| SUBSCRIBE | Bound session remains authenticated and unexpired; exact configured private destination only |
| UNSUBSCRIBE | Bound session remains authenticated and unexpired |
| Heartbeat | Bound session remains authenticated and unexpired |
| DISCONNECT | Allowed for cleanup, including framework-generated cleanup |
| SEND and other client commands | Rejected |

No game-topic, spectator or client-publication contract is supplied. Raw broker paths, another user’s destination and wildcard subscriptions are forbidden. STOMP failures are not REST Problem Detail responses; clients must not assume MVC error codes on the socket.

### Server update

```json
{
  "type": "GAME_UPDATED",
  "gameId": 10,
  "version": 3
}
```

Type is GAME_CREATED or GAME_UPDATED. GameCommandService events provide both stored participant IDs; the notification listener sends the hint to each Player after commit. GAME_UPDATED includes terminal moves and all accepted lifecycle changes. Each Player’s subscribed sessions may receive the notification; the payload has no FEN, move, result or opponent profile.

Angular validates recognized type, positive safe-integer gameId and nonnegative safe-integer version. Unknown/malformed payloads are ignored. The version is an observed game concurrency version, not a separate move-history index.

### Delivery and recovery

- A rolled-back transaction sends no successful-change notification. During pairing, delivery waits for the outer transaction, including queue removal.
- The simple broker is in-process and does not provide persistent replay, subscription receipts or a delivery acknowledgement guarantee. A successful enqueue is not proof of browser receipt.
- CONNECTED means the connection succeeded and the client sent SUBSCRIBE, not that subscription registration has been acknowledged.
- Stale/duplicate hints at or below the displayed game version are ignored. Out-of-order higher hints cause a fresh HTTP read; a read cannot replace a newer snapshot with an older one.
- Home rereads on connection/update events, with 30-second checks for unknown/WAITING/MATCHED state. It does not force board navigation.
- Game rereads on relevant newer hints, connections and focus/visibility return. Visible loaded active games, or loaded games still requiring board/history refresh, receive 30-second safety reads. Initial failed reads without a snapshot rely on manual refresh, connection/focus events or a new hint; the periodic filter requires a snapshot.
- Reconciliation waits during POST. A safety GET can be cancelled before POST. Commands are never replayed. Failed reads block proposals pending a successful read.

Server outbound application messages require a registered binding and a live persisted authenticated session at enqueue and handler checks. Disconnect removes access. Backend filtering does not proactively close the physical socket at the exact expiry instant; already transmitted messages cannot be recalled.

There is no frontend JWT expiry timer. Protected HTTP 401 clears a matching local revision and navigates to Login. Session clearing stops its client. Before initial connect/reconnect, csrf/profile HTTP preflight checks current access; preflight failure stops that lifecycle. Transport close/error otherwise enables reconnect attempts. STOMP rejection stops automatic reconnect for that session and marks live updates unavailable; it does not by itself clear a still-current HTTP session.

### Frontend timing

REALTIME_CONFIG currently declares reconnect delay 5000ms, connection timeout 10000ms, heartbeat interval 10000ms and reconciliation interval 30000ms. Its endpoint/subscription must match backend configuration; YAML is not automatically exposed to Angular. Development `/api/**` proxying includes WebSocket upgrades.

## Keeping contracts synchronized

When changing an endpoint, update its owning DTO/service/controller, corresponding integration assertions, this action's contract and the checkpoint progress together. Keep feature error constants in their owning package. Tests verify documented behavior; this document does not establish that a test run passed.

## Day 23 contract verification

Historical Day 23 bearer checks are superseded by the current session socket suite. Inspected Day 27 coverage includes upgrade 401 and disallowed-Origin 403; missing/incorrect CONNECT CSRF; forbidden subscriptions and client SEND; after-commit/rollback hints; logout affecting two sockets sharing one session; and session expiry filtering. ERROR frames may have no body. Test clients disable heartbeats and bounded observations do not prove all transport schedules, reconnect or browser behavior. See [review](day27-sessions-review.md).

## Day 25 completion extension

Registration creates Player and initial statistics within the enclosing credentials transaction. A successful game finish also commits both statistics and its processing marker. This adds no fields to GameResponse, GameMoveResult or PlayerUpdate. A statistics processing failure is an unexpected server failure, not a claim that a completed game was committed. Existing clients reconcile uncertain commands through GET rather than replaying POST. Day 25 did not change authentication; Day 27 now supplies server-side sessions instead of extending access-token lifetime or adding refresh tokens.

## Day 26 clocks and timeout extension

GameResponse and GameMoveResult append nullable clock. Untimed games return null. Timed responses carry whiteRemainingMillis, blackRemainingMillis, runningColor and calculatedAt (server Instant serialized as an ISO timestamp). Balances are projected at calculatedAt; they are not necessarily the turn-boundary balances stored in games. Finished clocks have runningColor=null and frozen balances. Reads do not change version; equal versions can carry different remaining times/calculatedAt. Historical JSON examples above omit this additive field.

TIMEOUT is an additional termination reason, currently requiring WHITE_WIN/BLACK_WIN. Expired well-formed commands return 409 GAME_CLOCK_EXPIRED after access/lifecycle/version checks. They do not themselves complete the game; scanner completion is separate. Existing offer/turn/legal guards remain. IN_PROGRESS with a zero projected running balance is possible while scanning is pending. No client timestamp or timeout POST route is added.

Timeout completion uses the existing GAME_UPDATED hint and synchronous statistics transaction. There is no per-tick WebSocket payload. PGN format is unchanged, using the persisted winning result without clock tags or timeout-specific SAN. Full impossible-mate timeout draw adjudication is deferred.

See [clock flow and pseudocode](game-clocks-flow.md) for timing, configuration, transaction boundaries and remaining limits.

## CSRF and logout precedence

GET /api/v1/auth/csrf returns 204/no-store and resolves the deferred CSRF cookie. POST logout is handled by Spring Security, invalidates the current session and returns 204/no-store; it is not a game command or all-device logout. All protected application writes pass CSRF before MVC validation and service authorization. Missing/incorrect CSRF can therefore produce 403 even if authentication is also missing; the 401 rows above assume CSRF is satisfied where required. CSRF/security failures do not guarantee MVC application codes. Reinitialize CSRF after login/logout; cookie clearing is not a server-side CSRF-token blacklist.

See [tab synchronization](tab-synchronization-flow.md) for client publication/restore order and concurrency limits.

## Day 28 legal hints — GET `/api/v1/games/{gameId}/legal-moves`

Participant-protected, no request body, 200/no-store. Response: gameId, version, moves. Each move has from, to, nullable promotion (QUEEN/ROOK/BISHOP/KNIGHT) and capture. Promotions remain four commands even when their destinations match. En passant is capture=true despite an empty destination. Either participant receives hints for the side to move; finished games return an empty moves list. Existence/membership errors follow game GET. Hints describe the captured position; they do not reserve a move or authorize playing after expiry/version change.

## Day 28 finished games — GET `/api/v1/games`

Authenticated self-only list, 200/no-store. Query parameters page default 0 and size default 20. Require page >= 0, size 1..50 and page * size <= Integer.MAX_VALUE; invalid requests return 400. No subject ID is accepted from the client.

Response has games, page, size, hasNext. Each entry has gameId, playerColor (WHITE/BLACK), opponentPlayerId, nullable opponentUsernameAtStart/opponentRatingAtStart, outcome (WIN/LOSS/DRAW), terminationReason and finishedAt. Perspective is the authenticated subject. Only their FINISHED games are included, ordered by finishedAt descending and ID descending. There is no total count. Offset pagination is not a snapshot across requests.

GameQueryService supplies reads; GameCommandService supplies writes. Future other-Player history requires an explicit access policy. Existing individual-game, history and export endpoints remain participant-only.
