# Development Progress

Reviewed on 24 October 2026 against `chess-web-app-day7-start.zip` (archive commit `16637bef7414cf6e55d8b4b099b786325f5fe2a1`), the completed Day 6 checkpoint. The user explicitly reports that all local tests passed. This review inspected source and documentation only; no independent tests or builds were run. Separate frontend build/manual-check results were not supplied.

Day numbers identify working sessions and differ from the original 30-working-day schedule. See [roadmap.md](roadmap.md) for planning and [api-contracts.md](api-contracts.md) for endpoint behavior.

## Current checkpoint

| Area | Implemented |
| --- | --- |
| Architecture | Modular monolith, package-by-feature with conventional layers; keep `Player`; not DDD |
| Backend stack | Java 21, Spring Boot 4.1.1, Gradle wrapper 9.8.1, PostgreSQL, JPA, Flyway, Lombok, YAML |
| Frontend stack | Angular 21, Angular Material, standalone components, reactive forms and signals |
| Registration | Transactional player/credential creation, BCrypt, validation, duplicate handling and rollback coverage |
| Login and security | Email/password login, HS256 JWT issuance/validation, JWT-derived player identity, protected APIs |
| Player profile | Authenticated GET and PATCH `/api/v1/players/me`; PATCH changes username only |
| Profile editor | Angular username form, server error feedback and immediate in-memory profile update |
| Frontend authentication | Registration/login, in-memory session, interceptor, guard, logout and protected-request 401 handling |
| Queue | Persistent join/leave/status, repeated-join timestamp preservation, authenticated endpoints |
| Pairing | Oldest-first eligible pairing on join, random colors, transaction-level queue advisory lock |
| Games | Initial FEN and `IN_PROGRESS` persistence; one-active-game-per-player reservations |
| Atomicity | Game creation, both reservations and removal of paired queue entries commit or roll back together |
| Discovery | `MATCHED` queue status with game ID; manual Angular status refresh |
| Game access | Participant-protected `GET /api/v1/games/{gameId}` |
| Rules foundation | Pure Java immutable representation, separate FEN parser/serializer, initial-position rules boundary |

## Schema

| Migration | Responsibility |
| --- | --- |
| V1 | Players |
| V2 | Local authentication credentials |
| V3 | Matchmaking queue and ordering index |
| V4 | Initial game state, participants, timestamps and optimistic-lock version |
| V5 | Active-player reservations and backfill from existing in-progress games |

Applied migrations remain immutable. Future schema changes use new Flyway SQL migrations. Hibernate validates the schema rather than generating it.

## Existing backend and frontend test coverage

Backend coverage includes:

- Registration service/controller validation, duplicate handling and BCrypt password-byte limits.
- PostgreSQL player uniqueness, registration persistence and rollback.
- Login and JWT security, including valid, malformed, expired and altered tokens; issuer, subject and expiration requirements; deleted-player profile access.
- Queue persistence, repeated join/leave, concurrent joins, authenticated HTTP operations and deleted-player rejection.
- Initial game persistence and invalid/missing participant rejection.
- Active-game conflicts, rollback of partial reservations, and concurrent creation sharing a player.
- Pairing of two players, removal of paired entries, rollback with the enclosing transaction, and concurrent joins without duplicate games.
- Game-read HTTP access for both participants, nonparticipants, missing games and missing authentication.

The five focused Angular tests in `auth-flow.spec.ts` cover protected bearer attachment, exclusion of external requests, protected 401 cleanup/redirect, profile-load failure cleanup and logout. There is no `app.spec.ts` in this ZIP. No broad frontend component or end-to-end suite is introduced.

Test presence describes coverage. The user reports all local tests passing for the completed Day 6 checkpoint; this review did not reproduce that run.

## Day 5 username-update increment

Implementation present in the reviewed checkpoint:

- `UpdatePlayerRequest` validates a nonblank username of 3–30 Java UTF-16 string units.
- `Player.changeUsername()` provides an explicit mutation operation.
- Transactional `PlayerService.updateUsername()` preserves same-username submissions as no-ops, checks duplicates and flushes the managed entity.
- `PlayerController` exposes authenticated PATCH `/api/v1/players/me`; a missing player is translated to 401 at the HTTP boundary.
- Existing database uniqueness and exception translation protect username ownership when prechecks race.
- `PlayerUpdateIntegrationTest` covers persistence, no-op timestamp preservation, duplicate conflicts, invalid fields, authenticated ownership, missing authentication, deleted players and existing-token reuse.
- `PlayerApiService.updateUsername()` calls PATCH; `ProfileEditor` is embedded in Home.
- The editor updates the session store from the successful server response, updates the welcome message immediately, preserves input after failures and ignores stale-session success responses.
- Username special characters remain allowed. Email/password changes and normalization remain deferred.
- No new migration, token claim, token refresh mechanism or frontend test suite is introduced.

These changes are present in the reviewed source. See verification provenance below.

## Day 6 rules foundation

The source contains pure Java `game.rules` and `game.rules.model` code: `PieceColor`, `PieceType`, immutable `Piece` and `Square` records, an immutable `Board`, `CastlingRights`, and `ChessPosition`. Coordinates run from a1=(0,0) to h8=(7,7). Board owns placement and defensively copies its map with `Map.copyOf`; Piece holds color and type only.

`ChessPosition` carries board, side to move, castling rights, optional en passant target, halfmove clock and fullmove number. `FenParser` validates FEN structure and representation constraints, not full legality or reachability. Separate `FenSerializer` emits canonical FEN.

`ChessRules` currently exposes only `initialPosition()`. `StandardChessRules` uses the parser and a private starting-FEN constant. `GameRulesConfig` wires the beans. `GameService` obtains the initial position through `ChessRules`, serializes it and passes nonblank initial FEN to `Game`; the entity no longer owns the starting-position constant. Rules/model code has no Spring, JPA, HTTP or persistence dependencies.

Focused tests cover square conversion, board immutability, position metadata, FEN parsing/serialization/round trips and standard initial placement. Game creation/read integration assertions check the expected initial FEN. Test presence is a source finding; passing results are user-reported.

Day 6 changes no migration, endpoint or frontend behavior. There is no move generation/application, king-safety check, special-move execution, outcome evaluation or move history.

## Verification provenance

Reviewed on 24 October 2026 against `chess-web-app-day7-start.zip` (archive commit `16637bef7414cf6e55d8b4b099b786325f5fe2a1`), the completed Day 6 checkpoint. The user explicitly reports that all local tests passed. This review inspected source and documentation only; no independent tests or builds were run. Separate frontend build/manual-check results were not supplied.

The local confirmation supersedes earlier pending verification language. It does not supply a detailed result log or separate frontend build/manual outcomes.

### Local verification

With Java 21 and the configured PostgreSQL test database available, from `backend/`:

```bash
./gradlew --version
SPRING_PROFILES_ACTIVE=test ./gradlew test
```

The integration test classes explicitly activate `test`. Reports: `build/reports/tests/test/index.html`.

From `frontend/` using the declared npm version and a compatible Node version:

```bash
npm ci
npm test -- --watch=false
npm run build
```

Manual smoke checks:

1. Register and sign in with two players in separate browser sessions.
2. Join the queue with both players; refresh the first player's status.
3. Confirm both discover the same game ID and that repeated join returns the active game.
4. Retrieve the game using each participant's token; confirm initial state and participant IDs.
5. Confirm a third player's token receives 403 and a request without authentication receives 401.
6. Confirm leaving the queue does not cancel the game.
7. Save an available username; confirm the welcome message changes immediately.
8. Try another player's username; confirm conflict feedback and preserved input.
9. Submit invalid values; confirm validation. Navigate away and back to Home and confirm the stored profile is reflected.
10. Use the existing token to retrieve `/players/me` after renaming; confirm it remains valid.

## Current limitations

- One queue; cooperating queue operations, including status reads, serialize through one advisory lock.
- Pairing runs on join; no background worker or rating filter.
- Manual status refresh; no application-event notifications or WebSocket/STOMP delivery.
- No queue expiry or automatic leave on logout.
- Games begin immediately in `IN_PROGRESS`; no acceptance stage.
- No cancellation, completion, results or active-reservation release. Matched players therefore remain unavailable for another game through the current APIs.
- No legal moves, move history, chessboard UI, game page or clocks.
- Username updates are supported; email/password changes are not implemented.
- Concurrent username edits use last-write-wins behavior; no player optimistic locking.
- Sessions are in memory; browser reload requires login. No refresh tokens, revocation or server-side logout.
- Username/email matching remains exact; no normalization policy.
- Security filter-chain errors and MVC Problem Details have different body contracts.
- Secrets and configuration cleanup remain user-managed and outside this documentation update.

## Working preferences

Keep the focus on backend engineering and explain decisions incrementally. Use Lombok where appropriate, avoiding `@Data` on JPA entities. Add concise comments for non-obvious behavior. Keep frontend tests minimal. Provide verification commands without waiting for routine test-pass confirmation. Preserve passwords after failed form submissions.

## Proposed Day 7 scope

Goal: build a small, pure Java movement pipeline incrementally, without exposing partial rules as playable chess.

1. Add an application-owned `Move` value with source and destination squares. Validate representation only; add promotion choice when pawn promotion becomes an actual use case.
2. Add knight-only pseudo-legal generation: board edges, side to move, friendly occupancy and enemy captures. Exclude king capture. Use an explicitly knight-specific component; do not advertise a complete move list.
3. Apply generated ordinary knight moves to a new candidate position, preserving the original. Update turn, en passant target, counters and castling rights affected by a captured rook. This is internal candidate application, not an authorized gameplay command.
4. Implement square-attack detection for every piece type, then use it to reject knight candidates that leave the moving side's king attacked. Require valid king fixtures for this stage. Partial move generation cannot safely substitute for attack detection.
5. Add focused tests and reconcile the checkpoint documentation with the work actually completed. If the attack-detection increment needs more time, keep it as the next bounded step rather than claiming legal gameplay is complete.

Pseudo-legal moves obey movement/occupancy rules without establishing the moving king's safety. Legal moves also satisfy king safety and all applicable special-move restrictions. Even after king-safety filtering, a knight-only result is only a subset of legal moves; an empty subset does not establish checkmate or stalemate.

No move endpoint, persistence/history, frontend, outcome, special-move execution or AI work is included. `ChessRules` remains unchanged until an actual service use case requires another operation. Service-layer transactions, authorization and gameplay concurrency stay outside the rules code.

This is a proposal at the Day 7 start checkpoint, not implemented work.

## Deferred representation evaluation

Evaluate a 64-entry array against map storage after the MVP, or earlier for demonstrated benefits. Preserve Board API. Board identity equality remains a known representation characteristic. Repetition detection needs history beyond FEN.
