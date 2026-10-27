# Development Progress

Reviewed on 27 October 2026 against `chess-web-app-day13-end.zip`. Source inspection confirms en passant candidate generation, immutable off-destination pawn removal, shared metadata handling and associated tests. No blocking defect was found in the reviewed Day 13 changes. The user reports all tests green, including the final additional tests. No independent tests or builds were run; no detailed test log or separate frontend build result was supplied.

Historical Day 6–12 sections describe earlier checkpoints. The Day 13 section defines current en passant behavior and supersedes earlier unsupported-en-passant descriptions.

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
| Movement | Knight/rook/bishop/queen/ordinary-king/ordinary-pawn strategies and explicit promotions and en passant (Day 13), shared traversal/application/filtering, resulting-square king safety, irreversible rook/king castling-right removal, centralized test fixtures; all-piece attack detection |

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

Test presence describes coverage. The user reports all tests green for the Day 13 final archive; this review ran no tests.

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

## Day 6 rules foundation — historical

The source contains pure Java `game.rules` and `game.rules.model` code: `PieceColor`, `PieceType`, immutable `Piece` and `Square` records, an immutable `Board`, `CastlingRights`, and `ChessPosition`. Coordinates run from a1=(0,0) to h8=(7,7). Board owns placement and defensively copies its map with `Map.copyOf`; Piece holds color and type only.

`ChessPosition` carries board, side to move, castling rights, optional en passant target, halfmove clock and fullmove number. `FenParser` validates FEN structure and representation constraints, not full legality or reachability. Separate `FenSerializer` emits canonical FEN.

`ChessRules` currently exposes only `initialPosition()`. `StandardChessRules` uses the parser and a private starting-FEN constant. `GameRulesConfig` wires the beans. `GameService` obtains the initial position through `ChessRules`, serializes it and passes nonblank initial FEN to `Game`; the entity no longer owns the starting-position constant. Rules/model code has no Spring, JPA, HTTP or persistence dependencies.

Focused tests cover square conversion, board immutability, position metadata, FEN parsing/serialization/round trips and standard initial placement. Game creation/read integration assertions check the expected initial FEN. Test presence is a source finding; passing results are user-reported.

Day 6 changes no migration, endpoint or frontend behavior. That historical Day 6 checkpoint had no movement pipeline. Day 7 adds the knight-only pipeline described below; special-move execution, outcomes and move history remain absent.

## Day 7 movement pipeline — historical checkpoint

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

## Day 7 source-review follow-ups — resolved in starting archive

Source inspection of `chess-web-app-day8-start.zip` confirmed:

- The en passant fixture in `whiteQuietMoveUpdatesMetadataAndPreservesOriginalPosition` uses halfmove clock zero; its quiet-move result uses one.
- `blackCaptureUpdatesFullmoveNumberAndWhiteCastlingRights` uses a Black knight on b3 moving to a1.
- The comment in `stillAllowsCandidateThatExposesOwnKing` explains that king safety belongs to LegalMoveGenerator.

Their presence establishes source corrections, not a rerun of corrected tests. The older Day 7 green-test report predates confirmation of these corrections. During Day 8 the user subsequently reported no problems after the guided increments; no detailed final test log was supplied.

Some older isolated movement/attack fixtures omit kings. Prefer complete, coherent FENs for ordinary tests. Label deliberately invalid defensive fixtures. The new rook king-capture fixture deliberately places Black in check on White's turn to isolate candidate exclusion and ray blocking.

The actual focused test package is `com.chessapp.game.rules.movement`:

```bash
./gradlew test --tests 'com.chessapp.game.rules.movement.MoveApplierTest'
./gradlew test --tests 'com.chessapp.game.rules.movement.*'
./gradlew test --tests 'com.chessapp.game.rules.*'
```

## Verification provenance

Reviewed on 27 October 2026 against `chess-web-app-day13-end.zip`. Source inspection confirms en passant candidate generation, immutable off-destination pawn removal, shared metadata handling and associated tests. No blocking defect was found in the reviewed Day 13 changes. The user reports all tests green, including the final additional tests. No independent tests or builds were run; no detailed test log or separate frontend build result was supplied.

The Day 13 archive retains earlier regression tests, including the corrected return-to-corner fixture and promotion coverage. The current green result is user-reported; no detailed test log or separate frontend build/manual outcomes were supplied.

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
- Day 13 supports all six piece strategies, promotions and en passant; castling execution, complete legal-move enumeration, outcomes, move endpoint/history, chessboard UI, game page and clocks remain pending.
- Username updates are supported; email/password changes are not implemented.
- Concurrent username edits use last-write-wins behavior; no player optimistic locking.
- Sessions are in memory; browser reload requires login. No refresh tokens, revocation or server-side logout.
- Username/email matching remains exact; no normalization policy.
- Security filter-chain errors and MVC Problem Details have different body contracts.
- Secrets and configuration cleanup remain user-managed and outside this documentation update.

## Working preferences

Keep the focus on backend engineering and explain decisions incrementally. Include concise algorithm walkthroughs and selected test-board renderings. Prefer complete FEN fixtures with coherent metadata/turns unless invalidity is deliberate and explained. Use Lombok where appropriate, avoiding `@Data` on JPA entities. Add concise comments for non-obvious behavior. Keep frontend tests minimal. Provide verification commands without waiting for routine test-pass confirmation. Preserve passwords after failed form submissions.

## Deferred representation evaluation

Evaluate a 64-entry array against map storage after the MVP, or earlier for demonstrated benefits. Preserve Board API. Board identity equality remains a known representation characteristic. Repetition detection needs history beyond FEN.

## Day 8 rook movement and Strategy dispatch — historical

This section supersedes the knight-only limitations in the historical Day 7 section. The described Day 8 implementation is present in the reviewed end checkpoint.

- `PiecePseudoLegalMoveGenerator` is the strategy interface, exposing `pieceType()` and `generate(position, from)`.
- `KnightMoveGenerator` and `RookMoveGenerator` implement that interface. Their generation is pseudo-legal: own-king safety is checked later.
- `PseudoLegalMoveGenerator` registers strategies by PieceType, rejects duplicate registrations, and delegates by the source piece's type. Empty and opposing sources return no moves; unsupported own piece types throw UnsupportedOperationException instead of silently returning an empty set.
- Registration uses EnumMap; Map.copyOf stores an immutable snapshot. The stored map need not retain EnumMap representation or iteration order. The dispatcher only relies on lookup.
- Rook generation scans four orthogonal rays. Empty squares are candidates. The first opposing non-king occupant is a capture candidate; any occupant ends the ray. An opposing king blocks the ray but is never a capture candidate. SquareAttackDetector separately recognizes attacks on king squares.
- Shared MoveApplier and LegalMoveGenerator now depend on PseudoLegalMoveGenerator. No per-piece application or filtering classes are added.
- MoveApplier removes castling rights when a rook leaves its original corner or a rook is captured on its original corner. One move can remove rights for both colors. Returning a rook never restores a right. Turn, counters, en passant expiry and immutability behavior remain shared.
- Rook legal-move tests cover White and Black pins, moves along a pin line, blocking check and capturing the checking attacker. Existing knight tests remain regression coverage for the shared pipeline. Selected rook tests render boards through com.chessapp.BoardTestRenderer.

ChessRules still exposes only initialPosition(). GameService does not apply moves. No HTTP endpoint, migration, frontend behavior or persisted gameplay command changes in this increment. Empty knight/rook subsets do not establish checkmate or stalemate. King movement will require checking the king's destination square. Castling execution, pawn moves and promotion remain unsupported.

## Day 9 completed checkpoint — historical

Source review confirms:

- Package-private SlidingRayTraversal is shared by rook, bishop and queen strategies. Empty destinations are candidates; an opposing non-king occupant is a capture candidate; every occupant ends a ray.
- BishopMoveGenerator scans four diagonals. QueenMoveGenerator scans eight nonzero unit directions. Skipping (0,0) excludes no movement; with the current occupied source, that direction would immediately stop rather than loop forever.
- MovementTestFixtures centralizes the standard knight/rook/bishop/queen test registry and shared legal-generator construction. Direct strategy and registration-contract tests retain explicit construction.
- Bishop/queen tests cover central rays, edges, occupancy, captures, turn selection, wrong-piece rejection, king-capture exclusion and dispatch. Application tests cover capture metadata, corner-rook castling-right removal, Black counters and original-position preservation.
- Legal-move tests cover White/Black bishop pins, bishop blocking/capturing responses, White orthogonal and Black diagonal queen pins, and queen blocking/capturing responses. Existing knight/rook tests remain regression coverage. Selected tests render boards.
- MoveApplier Javadoc lists all four supported types. Candidate application and king-safety filtering remain shared. No bishop/queen-specific application or filtering classes were introduced.

Day 9 core scope and queen stretch goal are complete. Passing tests are user-reported for this final archive; this review did not run them independently.

Current boundary: ChessRules exposes only initialPosition(); GameService does not apply moves. Pawn/king generation, special-move execution, complete legal-move enumeration and outcomes remain pending. Empty results for this subset cannot establish checkmate or stalemate. King filtering must use the king's resulting square when king moves are introduced. Parser validation is structural; Board has identity equality. Repetition needs history. Authorization, persistence, transactions and gameplay concurrency remain service responsibilities. AI is outside the MVP; map-versus-array evaluation remains deferred.

No endpoint, migration, frontend implementation or persisted-game behavior changes in Day 9.

## Day 10 completed checkpoint — historical

- `KingMoveGenerator` implements `PiecePseudoLegalMoveGenerator` for ordinary one-square movement in all eight directions. It clips board edges, excludes friendly destinations and opposing-king captures, respects side to move and returns an immutable set. Castling is excluded even when rights exist.
- `LegalMoveGenerator` still requires exactly one king per color in the input. After applying each candidate, it locates the moving side's king on the resulting board and checks that square against the opponent's attacks. This handles king relocation, captures and attacks exposed by vacating the source.
- `MoveApplier` removes both castling rights for a moving king's color, preserves the opponent's rights except those affected by a captured corner rook, and never restores lost rights. Quiet-move/capture counters, turn switching, en passant expiry and original-position preservation remain shared.
- `MovementTestFixtures` registers knight, rook, bishop, queen and king strategies. Dispatch tests include kings; unsupported own pawns still throw rather than silently returning no moves.
- Tests cover center/corner king candidates, blockers/captures, source validation, explicit invalid adjacent-king defensive input, castling exclusion, check escapes, adjacent kings, protected and unprotected captures, pinned-knight attacks, White/Black pawn and knight attacks, attacks exposed by vacating either king's source, rights removal for both colors, returning kings and corner-rook capture metadata. Existing knight/rook/bishop/queen tests remain in the suite. Selected tests use `com.chessapp.BoardTestRenderer`.

The user reports all tests green for this final archive. Review was by source inspection; no independent tests or builds were run. The cosmetic MoveApplier Javadoc cleanup is present in the inspected Day 10 starting archive.

Current boundary: the movement pipeline supports five piece types, including ordinary king moves. Pawn movement, promotion, en passant execution, castling execution, complete legal-move enumeration and outcomes remain pending. Supported-piece subsets cannot establish checkmate or stalemate. Attack detection recognizes all six types and counts pinned pieces as attackers. Candidate generation excludes king captures; attack detection still recognizes attacks on king squares.

`ChessRules` still exposes only `initialPosition()`. `GameService` creates initial FEN through that boundary and `FenSerializer`; movement is not connected to services or HTTP. No endpoint, migration, frontend behavior, move history or persisted-game behavior changes were introduced in Day 10. Transactions, authorization, persistence and gameplay concurrency remain service responsibilities. FEN validation is structural, Board uses identity equality, repetition needs history, AI is outside the MVP, and map-versus-array evaluation remains deferred.

## Day 11 completed checkpoint — historical, 26 October 2026

This historical section records Day 11. The Day 12 section below supersedes its promotion limitations.

- `PawnMoveGenerator` implements `PiecePseudoLegalMoveGenerator` for White and Black. Ordinary candidates include a one-square advance into an empty square, a two-square advance from the starting rank with both squares empty, and captures of opposing non-king pieces on forward diagonals. Empty/opposing sources return no moves; non-pawn sources are rejected. Results are immutable.
- Available advances or captures reaching a terminal rank throw `UnsupportedOperationException` because promotion is not represented. A pawn already on rank 1 or 8 is rejected with `IllegalArgumentException` when generating for the moving side.
- An empty forward-diagonal destination matching the en passant target throws `UnsupportedOperationException`. This is a conservative metadata guard, not validation of the captured pawn or proof of a legal en passant capture. Unrelated targets do not prevent ordinary generation.
- These unsupported cases fail generation for the entire source rather than return an incomplete candidate set, including when ordinary candidates also exist. The guard occurs before own-king safety filtering. `MoveApplier` and `LegalMoveGenerator` propagate the failure.
- Shared application resets the halfmove clock on every pawn move or capture. A double pawn advance sets the passed-over square as the new en passant target, even if no opposing pawn can capture. Every other supported move clears the previous target. Black moves increment the fullmove number; turns, castling-right handling and immutable placement remain shared.
- `MovementTestFixtures` registers all six piece types. The unsupported-dispatch test uses an intentionally incomplete registry, preserving that contract independently of standard registrations.
- Guided direct tests cover both directions, ranks, blockers, captures, edges, turn/source selection, unsupported special cases and immutability. Application tests cover counters, target creation/replacement/expiry, original-position preservation and special-case rejection. Legal-filter tests cover file/diagonal pins for both colors, double-advance blocks of rook check, capture of checking knights and unsupported-case propagation. Selected tests render boards through `com.chessapp.BoardTestRenderer`.

Verification: the user reports all guided tests green. Source inspection of the final Day 11 archive confirms these changes, with no blocking defect found in the reviewed changes. This does not establish an independently reproduced test run.

The pipeline generates moves for one source and still supports only bounded movement subsets. Promotion, en passant capture execution and castling execution remain unsupported. No complete legal-move enumeration, checkmate/stalemate, history, move endpoint, migration or frontend implementation is added. `ChessRules` exposes only `initialPosition()`; stored games retain their initial FEN. Authorization, transactions, persistence and gameplay concurrency remain service responsibilities. Attack detection includes pinned attackers and attacks on king squares; candidates exclude king captures. FEN parsing remains structural, Board uses identity equality, repetition needs history and AI is outside the MVP.

## Day 12 completed checkpoint — historical, 27 October 2026

Promotion is implemented for both colors, for quiet advances and captures, with explicit queen, rook, bishop and knight choices. Ordinary moves retain the two-argument constructor. No choice defaults to queen.

`Move` validates replacement-type shape. Position-dependent validity is established by exact pseudo-legal candidate membership, including the choice. `MoveApplier` preserves the pawn's color and applies the replacement through an immutable Board operation; `LegalMoveGenerator` checks the moving side's king on the resulting board.

Pawn halfmove resets, Black fullmove increments, turn changes, en passant expiry and captured-corner-rook castling-right removal remain shared. A promoted corner rook never grants castling rights. The test renderer lists promotion choices distinctly.

The user reports all tests green for the final Day 12 archive. Review was by source inspection; no independent tests or builds were run.

Movement remains internal pure Java code. `ChessRules` exposes only `initialPosition()`; `GameService` still stores the initial FEN and does not apply moves. Day 12 changes no HTTP contract, DTO, migration or frontend implementation. En passant capture execution, castling execution, complete legal-move enumeration, outcomes, history and gameplay integration remain pending. A source-specific result cannot establish checkmate or stalemate.

### Day 12 source-confirmed coverage

- `model/MoveTest`: ordinary constructor compatibility, all four allowed choices, pawn/king choice rejection, choice-sensitive set membership, null components and equal-square rejection.
- `movement/PromotionTest`: both colors, quiet/capture candidates, both capture diagonals, blocked advances, exact four-choice sets and immutable results; all choices applied for both colors and both quiet/capture kinds; missing choices and choices on ordinary pawn/knight moves rejected.
- FEN assertions cover replacement color/type, source removal, captured-piece replacement, counters, turn changes, en passant expiry, captured-corner-rook rights and absent rights remaining absent after corner-rook promotion. Original positions remain unchanged.
- King-safety coverage checks safe promotions, rank pins, capture of checking rooks and blocking rook checks for both colors. Selected tests render candidate sets and filtered moves with distinct promotion suffixes.
- Existing pawn strategy/application/legal-filter tests retain en passant rejection/propagation; obsolete promotion-rejection rows are removed.

Coverage statements describe inspected source, not independently reproduced execution. No detailed test report was supplied. Boundary cases such as missing choices specifically on promotion captures and every invalid Board-overload input are not separately demonstrated by these new tests; they use shared validation paths.

Focused local verification from `backend/` (no database required):

```bash
./gradlew test --tests 'com.chessapp.game.rules.*'
```

## Day 13 completed checkpoint — 27 October 2026

En passant is implemented for both colors. Generation requires the correct source rank, one forward diagonal step, matching target metadata, an empty destination and an adjacent opposing pawn. An inconsistent or unavailable opportunity contributes no en passant candidate and does not suppress ordinary moves. Attempting an omitted capture fails exact candidate membership with `IllegalArgumentException`; no unsupported-en-passant exception remains.

`MoveApplier` validates candidate membership before identifying a pawn's diagonal move into an empty square as en passant. It relocates the pawn and removes the opposing pawn from `(destination.file, source.rank)` through immutable Board operations. `Board.removePiece()` requires a nonnull occupied square and performs placement only; callers own chess-rule validation. The original position remains unchanged.

Shared handling switches turns, resets the pawn halfmove clock, increments the fullmove number after Black, and expires the target. En passant preserves existing castling rights. Ordinary moves, promotion choices and irreversible king/rook rights removal remain supported. An occupied diagonal follows ordinary capture rules even if inconsistent target metadata names it; no adjacent pawn is removed in that case. Promotion choices attached to en passant fail candidate membership.

`LegalMoveGenerator` remains shared and unchanged. It checks the moving side's king after both pawn squares have been vacated. Tests cover both colors, both capture directions in generation, FEN results, original-position preservation, missing/inconsistent metadata, absent/friendly/non-pawn adjacent pieces, wrong source ranks, occupied targets, opportunity expiry, ordinary advances instead of capture, castling-right preservation, invalid promotion choices, exposed rook/bishop attacks and removal of checking pawns. Board tests cover immutable removal and invalid removal inputs. The three obsolete unsupported-case tests were replaced. Selected tests use `com.chessapp.BoardTestRenderer`.

The user reports all tests green for the final Day 13 archive. Review was by source inspection; no independent tests or builds were run.

Movement remains internal pure Java code. `ChessRules` exposes only `initialPosition()`; `GameService` still stores the initial FEN and does not apply moves. Day 13 changes no HTTP contract, DTO, migration or frontend implementation. Castling execution, complete legal-move enumeration, outcomes, move history and gameplay integration remain pending. Generation handles a single source; an empty result cannot establish checkmate or stalemate. FEN parsing is structural, not proof of legality or reachability; the implementation does not reconstruct the preceding move from history. Board equality is identity-based, repetition requires history, and authorization, transactions, persistence and gameplay concurrency remain service responsibilities. AI and map-versus-array evaluation remain deferred.
