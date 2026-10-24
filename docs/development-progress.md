# Development Progress

Reviewed on 24 October 2026 against `chess-web-app-day7-end.zip` (archive commit `323715a1a49cb03867bcfd0b20096436f04d744f`). The user reports that all tests are green for this Day 7 checkpoint. This review inspected source and documentation only; no independent tests or builds were run. Separate frontend build/manual-check results were not supplied. Fixture corrections recorded in the development-progress document have not been applied or rerun as part of this review.

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
| Movement | Knight-only pseudo-legal generation, candidate application and king-safety filtering; all-piece attack detection |

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

Test presence describes coverage. The user reports all local tests passing for the completed Day 7 checkpoint; this review did not reproduce that run.

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

Day 6 changes no migration, endpoint or frontend behavior. That historical Day 6 checkpoint had no movement pipeline. Day 7 adds the knight-only pipeline described below; special-move execution, outcomes and move history remain absent.

## Day 7 movement pipeline

Implemented in the reviewed checkpoint:

- `Move` is an immutable source/destination value with nonnull, distinct squares. It represents a proposal, not proof of legality; promotion is not represented yet.
- `Board.movePiece()` creates a new placement, replacing a captured destination piece and preserving the original. It performs placement checks only, not chess-movement validation.
- `KnightMoveGenerator` enumerates the eight knight offsets, clips board boundaries, respects side to move, excludes friendly destinations and king capture, and returns an immutable set. Empty sources and opposing knights produce an empty set; non-knight sources are rejected.
- `MoveApplier` currently accepts pseudo-legal knight candidates only. It updates placement, side to move, en passant expiry, halfmove/fullmove counters and castling rights affected by a captured corner rook. It does not check king safety. Counter increments reject integer overflow.
- `SquareAttackDetector` recognizes pawn, knight, king, rook, bishop and queen attacks from either color, independent of side to move. Sliding rays stop at the first occupied square; pinned pieces still count as attackers.
- `LegalMoveGenerator` requires exactly one king of each color, applies candidates and rejects those leaving the moving side's king attacked. It handles a single knight source; it assumes an otherwise valid standard-chess position rather than validating full legality/reachability.
- Shared `MoveApplier` and `LegalMoveGenerator` replace the knight-specific application/filtering classes. Both still depend on `KnightMoveGenerator`; dispatch for other pieces is deferred until a second movement type is introduced.
- `com.chessapp.BoardTestRenderer` lives in test sources. It marks inspected/source squares and supplied destinations, prints sorted candidates and can be included in assertion failure messages. It does not generate moves. `Candidates: (none)` means no moves were supplied to the renderer, not a game outcome.

Tests cover board relocation/capture immutability, knight offsets/edges/occupancy/turn selection, candidate metadata and castling-right changes, attack types/blockers/pawn direction/pinned attacks, and king-safety filtering including checks answered by blocking or capture. Legal-move and attack tests use matching subpackages; other tests remain in their existing locations.

Pseudo-legal moves follow movement and occupancy rules without establishing own-king safety. Legal moves additionally satisfy king safety and applicable special restrictions. This checkpoint generates only a knight subset: an empty result cannot establish checkmate or stalemate.

No new migration, endpoint, frontend behavior, move history or persisted gameplay command is introduced. `ChessRules` still exposes only `initialPosition()`; `GameService` does not call the movement pipeline.

## Day 7 source-review follow-ups

No blocking defect was identified in the reviewed knight pipeline. Two fixture inconsistencies from the guided examples should be corrected in `MoveApplierTest`:

1. `whiteQuietMoveUpdatesMetadataAndPreservesOriginalPosition`: `e6` records the immediately preceding double pawn move, so the original halfmove clock must be zero. Change the input to `4k3/8/8/4p3/3N4/8/8/4K3 w - e6 0 12`, and the expected candidate to `4k3/8/8/4pN2/8/8/8/4K3 b - - 1 12`.
2. `blackCaptureUpdatesFullmoveNumberAndWhiteCastlingRights`: the original knight on c2 already attacks White's king on e1 while it is Black's turn. Put the knight on b3 instead: input `4k3/8/8/8/8/1n6/8/R3K2R b KQ - 8 12`, move `b3 -> a1`. The expected candidate remains `4k3/8/8/8/8/8/8/n3K2R w K - 0 13`.

These are test-fixture corrections, not movement-code changes. They are recommendations against the uploaded checkpoint; the supplied documentation does not apply source changes or claim the corrected tests were executed.

Some earlier low-level movement/attack fixtures still omit kings. Prefer complete, coherent FEN positions and scenario-appropriate turns for ordinary behavior tests. Explicit empty-board, missing-king and defensive king-capture fixtures may deliberately be invalid, but label that intent. The FEN parser's structural validation is intentionally not a position-legality validator. In `MoveApplierTest.stillAllowsCandidateThatExposesOwnKing`, update the stale comment to explain that king-safety filtering is deliberately outside MoveApplier (it now exists separately).

Minor optional cleanup: `LegalMoveGenerator` still uses the constructor message "Knight move applier is required"; rename it to "Move applier is required".

After applying the fixture corrections, run locally from `backend/`:

```bash
./gradlew test --tests 'com.chessapp.game.rules.MoveApplierTest'
./gradlew test --tests 'com.chessapp.game.rules.*'
```

No independent execution is recorded for these commands.

## Verification provenance

Reviewed on 24 October 2026 against `chess-web-app-day7-end.zip` (archive commit `323715a1a49cb03867bcfd0b20096436f04d744f`). The user reports that all tests are green for this Day 7 checkpoint. This review inspected source and documentation only; no independent tests or builds were run. Separate frontend build/manual-check results were not supplied. Fixture corrections recorded in the development-progress document have not been applied or rerun as part of this review.

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
- Knight-only rules support; no complete legal-move list, special-move execution, outcomes, move endpoint/history, chessboard UI, game page or clocks.
- Username updates are supported; email/password changes are not implemented.
- Concurrent username edits use last-write-wins behavior; no player optimistic locking.
- Sessions are in memory; browser reload requires login. No refresh tokens, revocation or server-side logout.
- Username/email matching remains exact; no normalization policy.
- Security filter-chain errors and MVC Problem Details have different body contracts.
- Secrets and configuration cleanup remain user-managed and outside this documentation update.

## Working preferences

Keep the focus on backend engineering and explain decisions incrementally. Include concise algorithm walkthroughs and selected test-board renderings. Prefer complete FEN fixtures with coherent metadata/turns unless invalidity is deliberate and explained. Use Lombok where appropriate, avoiding `@Data` on JPA entities. Add concise comments for non-obvious behavior. Keep frontend tests minimal. Provide verification commands without waiting for routine test-pass confirmation. Preserve passwords after failed form submissions.

## Proposed next working session

Extend the pure Java pipeline to sliding movement, starting with one bounded rook increment. Introduce shared piece dispatch when the second generator requires it, and extend MoveApplier to remove castling rights when a rook moves. Reuse candidate application, square-attack detection and king-safety filtering instead of creating per-piece application/filtering classes.

Bishop and queen generation, pawn movement/promotion/en passant, king movement/castling and full legal-move enumeration remain to be scheduled incrementally. A gameplay endpoint, persistence/history, concurrency handling, outcomes and reservation release require later service-level work. This proposal is not implemented Day 8 work.

## Deferred representation evaluation

Evaluate a 64-entry array against map storage after the MVP, or earlier for demonstrated benefits. Preserve Board API. Board identity equality remains a known representation characteristic. Repetition detection needs history beyond FEN.
