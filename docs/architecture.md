# Architecture

Reviewed on 28 October 2026 against `chess-web-app-day16-end.zip`. Source inspection confirms the legality-enforcing facade, complete production strategy registration, facade regression tests and both [closing checks](day16-closing-checks.md). No blocking defect was found in the reviewed Day 16 changes. The user reports all tests green through increments 1–2. Both later closing tests are present, but no subsequent explicit passing report was supplied for them. No tests or builds were run independently; no detailed test log or separate frontend build result was supplied.

The Day 16 section describes the inspected implementation and supersedes earlier initial-position-only boundary and test-only-construction statements. Historical sections retain the behavior and evidence available at those checkpoints.

## Day 16 rules facade

`ChessRules` exposes `initialPosition()`, `legalMoves(position)`, `applyMove(position, move)` and `evaluate(position)`. Lookup returns the complete immutable legal set. Application requires exact membership in that set, including the promotion choice, before delegating to `MoveApplier`. Assessment returns `PositionStatus` for the side to move. The existing pipeline remains shared; no per-piece application services or duplicate movement algorithms are introduced.

`GameService` still calls only `initialPosition()` and serializes it for creation. Games remain at their initial FEN in `IN_PROGRESS`. Move submission, persistence/history, gameplay concurrency, terminal lifecycle and active-reservation release remain pending. No endpoint, DTO, migration or frontend implementation changes in Day 16. Authorization, transactions, persistence and concurrency remain service responsibilities.

| ChessRules operation | Delegate | Contract |
| --- | --- | --- |
| initialPosition() | FenParser with the existing initial FEN | Preserve standard initial placement and metadata |
| legalMoves(position) | LegalMoveGenerator.generate(position) | Complete immutable Set<Move> for the side to move |
| applyMove(position, move) | Legal membership, then MoveApplier.apply | Reject unsafe proposals; return a new ChessPosition |
| evaluate(position) | PositionEvaluator.evaluate(position) | NORMAL, CHECK, CHECKMATE or STALEMATE |

`StandardChessRules` uses Lombok `@RequiredArgsConstructor` and `@NonNull` for its parser, legal generator, applier and evaluator dependencies. `GameRulesConfig.chessRules()` creates one dispatcher registering all six piece strategies, one applier and attack detector, then the legal generator and evaluator. It passes these collaborators into the facade. Internal movement/evaluation objects do not require individual Spring beans. FenParser and FenSerializer remain beans; Spring configuration stays in game.config.

Application algorithm: require nonnull position/move; enumerate the complete legal set from the original input; reject a proposal absent from that set with IllegalArgumentException; delegate to the existing applier. Move record equality includes the promotion choice. Legal generation supplies king safety and staged castling safety; direct pseudo-legal application alone is insufficient. Validation/generation failures propagate without becoming empty reply sets or outcomes.

Accepted application repeats candidate work already performed during legal enumeration. This preserves existing validation contracts. No caching, validation-skipping application path or combined result DTO is introduced. Evaluation remains a separate operation; it is not automatically performed after application.

Rules calls assume an otherwise valid standard-chess position. King-count validation and structural FEN parsing do not prove historical reachability. Board equality is identity-based; compare content or FEN. PositionStatus is separate from persisted GameStatus; NORMAL does not exclude other draw conditions. Repetition, move-count draws, dead positions, clocks, resignation, agreement, AI and board-storage evaluation remain outside this increment.

Historical Day 15 source-review provenance: reviewed on 27 October 2026 against `chess-web-app-day15-end.zip`. Source inspection confirms position-wide legal-move enumeration and `NORMAL` / `CHECK` / `CHECKMATE` / `STALEMATE` assessment for the side to move. No blocking defect was found in the reviewed Day 15 changes. The user reports all tests green after the final guided regressions. No independent tests or builds were run; no detailed test log or separate frontend build result was supplied.

Historical Day 6–15 sections describe earlier checkpoints. The Day 16 section defines the inspected facade; Day 15 describes its enumeration and assessment foundation. Day 14 remains authoritative for castling and Day 13 for en passant; historical statements that enumeration or assessment are pending are superseded.

## Day 15 implementation — historical foundation

`LegalMoveGenerator` now has two public operations: `generate(position, from)` for one source and `generate(position)` for every piece belonging to the side to move. Both require exactly one king per color before generation. The position-wide operation scans all 64 squares, evaluates each moving-side source against the same original position, and returns an immutable `Set<Move>`. It preserves promotion choices through Move equality.

Both entry points use private `generateForSource()`. Input king-count validation runs once per public call; the existing resulting-board king lookup/check remains per candidate. Castling still checks the original starting square, king-only transit board and completed destination board. Candidate generation, exact pseudo-legal application and all-piece attack detection retain their previous responsibilities.

`game.rules.evaluation.PositionEvaluator` first obtains the complete legal set. Generation failures propagate; an invalid position or an unsupported own-piece type must not become a terminal assessment. After king-count validation succeeds, the evaluator locates the moving king and checks attacks on the current board through `SquareAttackDetector`. It does not apply a reply to decide whether the input is check.

| Moving king attacked? | Legal replies available? | PositionStatus |
| --- | --- | --- |
| No | Yes | NORMAL |
| Yes | Yes | CHECK |
| Yes | No | CHECKMATE |
| No | No | STALEMATE |

`findMovingKing()` relies on the preceding generator validation. Its `IllegalStateException` represents a broken internal assumption, rather than the normal invalid-input path. The evaluator assumes an otherwise valid standard-chess position; it does not establish reachability. `NORMAL` does not establish the absence of other draw conditions.

The public test-only `MovementTestFixtures` and its two factory methods now support reuse from the evaluation test package. No production Spring wiring was added.

Position-wide enumeration and position assessment are implemented internally. `generate(position, from)` remains source-specific; its empty result alone cannot determine an outcome. `generate(position)` collects all moving-side legal moves, and `PositionEvaluator` combines reply availability with current-board attack detection. `PositionStatus` is a rules assessment, separate from persisted `GameStatus`. `ChessRules` still exposes only `initialPosition()`; `GameService` still stores the initial FEN and does not apply moves. There is no move-submission endpoint, move history, terminal persisted lifecycle or active-reservation release. Repetition, move-count draws, dead-position detection, clocks, resignation and agreement are not evaluated. Authorization, transactions, persistence and gameplay concurrency remain service responsibilities. FEN parsing is structural, Board equality is identity-based, and repetition requires history. AI and map-versus-array evaluation remain deferred.

## Style and ownership

A single Spring Boot application, organized as a modular monolith with feature packages and conventional controller/service/DTO/entity/repository layers. Not DDD; no microservices or multi-project Gradle modules are required. Keep the domain term `Player`.

| Package | Ownership | State |
| --- | --- | --- |
| `auth` | Registration, local credentials, login | Implemented |
| `player` | Identity and profile data | Creation, lookup, current profile read and username updates |
| `security` | HTTP authentication, password encoding, JWT machinery | Implemented |
| `common` | Shared timestamps and request validation error handling | Implemented |
| `matchmaking` | Persistent queue, eligibility, and transactional pairing | Implemented |
| `game` | Initial state, active-player reservations, participant-protected retrieval and pure Java rules | Persistence/access implemented; all six strategies, promotion, en passant, castling, complete legal-move enumeration and position assessment; gameplay integration pending |
| `notification` | Authorized outbound updates | Planned |

Controllers handle transport and delegate. Services coordinate use cases and transaction boundaries. Repositories remain within their owning feature. Cross-feature calls use service operations and DTOs. Concrete services are sufficient; interfaces and mapper frameworks are introduced only when needed.

Auth consumes `PlayerResponse` from `PlayerService`; it does not import player entities or repositories. `PlayerResponse` is also the `/players/me` HTTP contract. `RegisterResponse` deliberately retains the API field `playerId`, while `PlayerResponse` uses `id`.

## Registration and persistence

`AuthController` validates a registration request. Transactional `AuthService.register()` creates a player through `PlayerService`, encodes the password, saves credentials, and returns a registration DTO. All database writes must succeed or roll back together. The current order creates the player before encoding; rollback tests intentionally fail that later step.

PostgreSQL is durable storage. Flyway V1 creates players; V2 creates local credentials. Hibernate uses `ddl-auto: validate`, and Open EntityManager in View is disabled. Applied migrations remain immutable; schema changes use new migrations.

`AuthCredential.playerId` is a scalar foreign key rather than a JPA relationship. Its uniqueness permits one local credential per player. The foreign key has no deletion cascade. Future account deletion and external login methods need explicit designs.

`BaseEntity` contains creation/update `Instant` timestamps managed by JPA callbacks. SQL defaults initialize timestamps for non-JPA inserts, but direct SQL updates do not automatically advance `updated_at`.

Username/email availability checks improve feedback. PostgreSQL constraints remain authoritative under concurrency. `PlayerExceptionHandler` translates the named username/email constraints to 409 and rethrows unknown integrity failures. It is global advice because player failures can originate from auth controllers; its schema knowledge remains owned by player.

Identity lookup currently compares exact stored values. No canonicalization or case-insensitive identity policy is implemented. Any future normalization change must coordinate registration, lookup, existing rows, and unique constraints.

## Credential verification and login

`LoginController` validates input and delegates to `LoginService`. That service calls read-only transactional `AuthService.authenticate()`, then signs an access token after credential verification returns.

Authentication loads a player through the player service, retrieves local credentials, and calls `PasswordEncoder.matches`. Unknown accounts and missing local credentials use a dummy hash generated once at bean initialization. This reduces a timing difference but does not guarantee identical response timings.

Invalid credentials share one message and auth-owned error code. Login returns `accessToken`, `tokenType`, and `expiresIn` in seconds, with `Cache-Control: no-store`. The implemented login response does not contain the player profile.

Password encoding uses `DelegatingPasswordEncoder`, bcrypt by default. Registration enforces minimum length; registration and login enforce BCrypt's 72-byte UTF-8 maximum. No password trimming or normalization is performed. DTO validation is triggered at the HTTP boundary; calling services directly does not automatically validate DTO annotations.

## JWT issuance and request authentication

`JwtTokenService` uses `JwtEncoder`, an injected clock, issuer, and TTL. JWTs use HS256 with at least 32 decoded key bytes. Claims are `sub` (player ID), `iss`, `iat`, and `exp`. Tokens carry no profile fields.

`JwtConfig` provides encoder/decoder beans and requires a configured key. The decoder trusts HS256 and validates issuer, required expiration, expiration and optional not-before validity with 60 seconds of skew, and a subject parseable as a positive Java Long. Timestamp validation and issuance use the injected clock.

`SecurityConfig` permits POST registration/login, protects other requests, uses stateless sessions, and delegates bearer processing to Spring Security's resource server support. No custom JWT filter is needed. CSRF is disabled for the current bearer-header API design; revisit this if cookie-based authentication is introduced.

`GET /api/v1/players/me` belongs to `PlayerController` because it returns a current database-backed profile. It extracts the validated subject and returns `PlayerResponse`, or 401 if the player no longer exists. The controller currently uses `Objects.requireNonNull` as an assertion of decoder validation; controller-only tests must supply a valid principal.

Deleting a player does not cryptographically revoke its JWT. `/players/me` detects deletion through its lookup; future endpoints must apply their own resource and account checks. Refresh tokens, revocation, logout endpoints, account-status checks, rate limiting, and key rotation machinery are not implemented.

## Current-player username updates

`PATCH /api/v1/players/me` belongs to `PlayerController`. It validates `UpdatePlayerRequest` and derives player identity from the JWT subject. The body contains only the username; no client-supplied player ID chooses the update target. Email/password changes are outside this use case.

Transactional `PlayerService.updateUsername()` returns `Optional<PlayerResponse>`. A missing player returns empty; the controller translates that result into 401 using `ResponseStatusException`, consistently with the current profile-read endpoint. HTTP concerns remain at the controller boundary.

Submitting the current username returns without mutation. Otherwise, the service checks username availability, calls `Player.changeUsername()` on the managed entity and flushes through the repository. JPA dirty checking writes the update; an additional `save()` is unnecessary. The inherited `@PreUpdate` callback updates `updatedAt` for a changed entity. Flushing executes SQL without committing.

The availability check improves feedback but cannot guarantee uniqueness under concurrency. PostgreSQL's existing `uk_players_username` constraint remains authoritative, and `PlayerExceptionHandler` maps its violation to `USERNAME_ALREADY_EXISTS`. Concurrent edits to the same player's username currently use last-write-wins behavior; `Player` has no optimistic-lock version. The `Game` version field does not protect player edits.

Username validation remains nonblank and 3–30 Java UTF-16 string units, matching registration. There is no trimming, case normalization or special-character restriction. Existing IDs, credentials, game references and JWT subjects remain unchanged. No migration is introduced.

`ProfileEditor` is a standalone component under `player/components/profile-editor`, embedded in authenticated Home. `PlayerApiService` handles PATCH transport; the component coordinates form feedback and updates `AuthSessionStore` from the returned profile. It ignores successful responses if the token or current player has changed. Destruction cancels its subscription. Server validation remains authoritative, errors preserve input, and successful saves reset the form to the returned username. The welcome message observes the same store through `AuthService`.

The Day 5 integration-test increment covers persistence, same-username no-op behavior, duplicate rejection, validation, JWT-derived ownership, missing authentication, deleted-player access and reuse of the existing JWT after renaming. Transactional HTTP tests flush/clear before persistence assertions and roll back fixtures; they do not establish real HTTP commit behavior or reproduce a concurrent rename race.

## Error ownership

- `ApiExceptionHandler`: shared MVC handling and field-validation Problem Details.
- `PlayerExceptionHandler`: player duplicates and known database constraints.
- `AuthExceptionHandler`: invalid local credentials.
- `MatchmakingExceptionHandler`: unavailable authenticated players.
- `GameExceptionHandler`: active-game conflicts, missing games, and participant access denial.
- Spring Security filter chain: missing/invalid bearer credentials.

Feature-owned error constants avoid a global catalog of every feature's codes. Field errors omit rejected values to avoid exposing passwords. Filter-chain errors do not currently share the MVC Problem Detail format. HTTP status consistency is implemented; a universal error-body contract is not. [api-contracts.md](api-contracts.md) lists the expected status/code combinations by action, including failures without a guaranteed application code.

## Tests and transaction semantics

Mockito tests cover registration, authentication, and duplicate prechecks. Standalone MockMvc covers request validation and selected exception mapping. PostgreSQL tests exercise persisted registration, actual unique constraints, and service rollback. Full-context MockMvc tests exercise registration/login/profile access and actual JWT decoding.

JWT tests include missing/malformed token, valid token with a real player, altered signature, expiration, wrong issuer, missing/invalid subject including numeric overflow, missing expiration, and deleted-player lookup.

Transactional tests normally roll back fixtures. Synchronous MockMvc shares the test thread; a real HTTP server would not share that transaction. The rollback-specific integration test has no enclosing test transaction and uses a mocked encoder to fail after player creation. It proves rollback at that failure point, not every possible persistence failure.

Expected PostgreSQL constraint failures can appear in test logs. Do not continue querying within a failed transaction. Identity sequences need not rewind after rollback. Existing data from earlier committed tests is not removed by adding transactional annotations.

## Frontend authentication

Angular 21 uses standalone components, reactive forms, signals, lazy routes, and Angular Material. `AuthApiService` handles auth HTTP calls; `PlayerApiService` handles profile HTTP calls. `AuthService` orchestrates login; `AuthSessionStore` owns in-memory token/player state. The interceptor injects the store rather than `AuthService`, avoiding an HTTP/service dependency cycle.

The login sequence is token issuance → store token → GET `/api/v1/players/me` → store player. `isAuthenticated` requires both token and player. Failure clears the state. The `authGuard` redirects unauthenticated users from `/home` to `/login`; server authentication remains the enforcement boundary.

The interceptor attaches tokens only to relative `/api/` URLs and excludes public login/register. On a protected-request 401, it clears state only if the current token matches the request's token. An established player session is redirected with `sessionExpired=true`; an initial profile failure stays on the login form. External requests and 403 responses bypass session invalidation.

Token/player state is not persisted, so reload requires another login. There is no expiry timer or token refresh; an idle page may retain displayed state until a protected request receives 401. Client logout clears memory; the JWT itself remains valid until expiry. Cancellation of the login subscription after token issuance can leave a token without a player; authentication still evaluates false. Revisit cleanup if this causes a concrete navigation problem.

Both forms prevent duplicate submissions, show progress/errors, and retain passwords after failure. Successful login clears its password; successful registration resets the form and redirects to login. Registration displays backend duplicate/field-validation errors. Material theming is configured in `material-theme.scss`; home uses Material cards, buttons, and progress indicators for queue controls and matched-game discovery.

The five focused HTTP/store tests cover bearer attachment, external URL exclusion, protected 401 clearing/redirect, profile-load failure cleanup, and logout. They do not test forms, the guard, successful login completion, public-auth token exclusion with an existing token, stale-token responses, or password retention. These are coverage limits, not requests for a broad frontend suite. There is no generated `app.spec.ts` in this checkpoint; the five authentication tests are the current frontend suite.

## Configuration

Shared configuration lives in `application.yaml`; test overrides use `application-test.yml`. Both are YAML. An ignored `application-local.yml`, if used, requires the `local` profile. Shared database defaults permit optional environment overrides. Secret configuration is user-managed and outside this increment.

Flyway migration-name validation is enabled only in the test profile; enabling it in shared configuration is an optional small follow-up.

## Matchmaking queue

The matchmaking feature owns persistent queue membership through conventional
controller/service/DTO/entity/repository layers.

Controllers derive player identity from the validated JWT subject. Clients
cannot select another player through a request body or parameter.
MatchmakingService checks player existence through PlayerService.

Flyway V3 creates matchmaking_queue. A unique player_id constraint enforces
one entry per player. An index on enqueued_at and id supports deterministic
oldest-entry selection.

Joining uses a PostgreSQL upsert that preserves the existing entry ID and
enqueue timestamp. The conflict update locks the existing row until the
service transaction completes. Repeated joins perform an update without
changing business data.

Leaving uses a bulk JPQL delete. Repeated leave succeeds when no entry exists.
This operation bypasses JPA removal callbacks and remove cascades; the current
queue entity requires neither.

## Game creation and transactional pairing

Flyway V4 creates games with two distinct players, an initial FEN,
IN_PROGRESS status, timestamps, and an optimistic-lock version.

V5 creates active_game_players. Its player_id primary key prevents a player
from occupying multiple active games across either color assignment.
The game feature owns reservation writes.

GameService creates the game through JPA, flushes it, and reserves both
players through JdbcTemplate. Both use the same datasource and participate
in the same Spring-managed transaction. Flushing executes pending SQL;
it does not commit. A reservation conflict rolls back the game and any
partial reservation.

Matchmaking operations acquire the same transaction-level PostgreSQL
advisory lock before accessing queue state. This serializes operations
on the single queue across cooperating application instances.
The lock is released when the surrounding transaction finishes.

Pairing selects the oldest entries by enqueued_at and id, checks active-game
eligibility, randomly assigns colors, creates the game, and removes both
queue entries. These writes commit or roll back together.

The reservation constraint independently protects against another
game-creation caller that does not acquire the queue advisory lock.

Queue status is NOT_QUEUED, WAITING, or MATCHED. MATCHED includes gameId.
Repeated joins return an existing active game. Leaving removes queue
membership and does not cancel an established game.

GET /api/v1/games/{gameId} derives the requesting player from the validated
JWT subject. GameService permits either participant to read the game,
returns 404 for a missing game, and rejects nonparticipants with 403.
The response contains player IDs rather than opponent profile details.

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

## Rules package organization

| Package | Responsibility |
| --- | --- |
| `game.rules` | Application-owned ChessRules interface and StandardChessRules implementation |
| `game.rules.model` | Immutable board, piece, square, position and move representation |
| `game.rules.fen` | FenParser and FenSerializer |
| `game.rules.movement` | PiecePseudoLegalMoveGenerator strategies for knight/rook/bishop/queen/king/pawn, SlidingRayTraversal, PseudoLegalMoveGenerator dispatcher, shared MoveApplier and LegalMoveGenerator |
| `game.rules.attack` | SquareAttackDetector |
| `game.rules.evaluation` | PositionEvaluator and PositionStatus for the side to move |
| `game.config` | Spring bean wiring; outside the pure rules subsystem |

GameRulesConfig now constructs movement/evaluation collaborators behind ChessRules. Focused unit tests also construct them directly. The Day 14 pipeline supports all six piece strategies, including pawn movement, explicit promotions, en passant and standard castling. LegalMoveGenerator locates the moving side’s king on each resulting board, including when the king relocates. Keep candidate application and king-safety filtering shared.

## Rules ownership and extension boundary

`GameService` consumes the application-owned `ChessRules` interface. Define additional operations from actual use cases and keep input/output types application-owned. A future library adapter can implement the interface and translate library types internally; no placeholder adapter exists now. This is dependency inversion through an application-owned abstraction, with dependency injection at the Spring boundary.

Transactions, persistence, participant authorization and gameplay concurrency remain in services. AI move selection is outside the MVP. Repetition detection requires history beyond a single FEN.

`Board` currently has identity equality. Consequently, `ChessPosition` record equality does not establish board-content equality. Tests compare square contents where needed.

Deferred TODO: evaluate a 64-entry array against map storage after the MVP, or earlier if profiling or concrete implementation benefits justify it. Preserve the board API.

## Planned application growth

Spring application events may decouple appropriate internal reactions, but are not durable messaging. WebSocket/STOMP remains a candidate for later real-time delivery, not an implemented dependency. It will need authenticated connections and authorized subscriptions/commands. Redis and Kafka are outside the initial infrastructure. Spectators, tournaments, chat, engine analysis, and distributed scaling remain out of initial scope.

## Current gameplay boundary

`GameStatus` currently contains only `IN_PROGRESS`. The stored FEN is the initial position; no service applies moves. `@Version` is present for future concurrent game updates, but move-command conflict handling is not implemented. There is no terminal status, result, lifecycle endpoint, or reservation-release operation.

The Angular frontend has no game API service, game route, or chessboard. It discovers `gameId` through manual queue-status refresh. Spring application events and WebSocket/STOMP delivery remain planned; no notification feature is implemented.

Queue operations acquire the queue advisory lock before queue access, including read-only status requests. This is a deliberate small-system serialization tradeoff. The lock is cooperative; database constraints remain necessary for game-creation callers outside matchmaking.

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

### Promotion responsibilities and failure behavior

- `Move(from, to, Optional<PieceType> promotion)` requires nonnull, distinct squares and a nonnull Optional. Only queen, rook, bishop and knight are accepted as replacement choices. The two-argument constructor uses `Optional.empty()`. Record equality includes the choice.
- The move value does not inspect a board or prove legality. A missing choice on an available promotion, or a choice attached to an ordinary move, fails `MoveApplier` membership validation with `IllegalArgumentException` when generation completes normally.
- `PawnMoveGenerator` expands each available terminal-rank advance/capture into four immutable candidates. Blockers, friendly destinations and king-capture exclusions remain enforced before expansion. Moving-side pawns already on rank 1 or 8 remain invalid.
- `Board.movePiece(from, to, destinationPiece)` copies placement, removes the source and replaces the destination. It requires an occupied source, distinct squares and nonnull inputs. It intentionally does not enforce piece color, pawn movement, promotion ranks or king safety; callers own those rules.
- `MoveApplier` uses the original pawn for metadata and the chosen replacement for destination placement. Castling-right removal inspects the original position, including any captured rook. Rights are never added.
- `LegalMoveGenerator` remains shared and unchanged. It requires one king per color and checks the moving side's king against attacks after each candidate is applied. Candidate generation excludes king captures; attack detection includes king-square attacks and pinned attackers.
- An empty forward diagonal matching the en passant target still throws `UnsupportedOperationException` for the entire source, even if ordinary candidates exist. This is a conservative metadata guard, not captured-pawn validation or proof of legal en passant. Application and filtering propagate it.

FEN parsing remains structural, not proof of legality/reachability. Board equality remains identity-based. Repetition requires history. Authorization, transactions, persistence and gameplay concurrency remain service responsibilities. AI and map-versus-array evaluation remain deferred.

## Day 13 completed checkpoint — historical, 27 October 2026

En passant is implemented for both colors. Generation requires the correct source rank, one forward diagonal step, matching target metadata, an empty destination and an adjacent opposing pawn. An inconsistent or unavailable opportunity contributes no en passant candidate and does not suppress ordinary moves. Attempting an omitted capture fails exact candidate membership with `IllegalArgumentException`; no unsupported-en-passant exception remains.

`MoveApplier` validates candidate membership before identifying a pawn's diagonal move into an empty square as en passant. It relocates the pawn and removes the opposing pawn from `(destination.file, source.rank)` through immutable Board operations. `Board.removePiece()` requires a nonnull occupied square and performs placement only; callers own chess-rule validation. The original position remains unchanged.

Shared handling switches turns, resets the pawn halfmove clock, increments the fullmove number after Black, and expires the target. En passant preserves existing castling rights. Ordinary moves, promotion choices and irreversible king/rook rights removal remain supported. An occupied diagonal follows ordinary capture rules even if inconsistent target metadata names it; no adjacent pawn is removed in that case. Promotion choices attached to en passant fail candidate membership.

`LegalMoveGenerator` remains shared and unchanged. It checks the moving side's king after both pawn squares have been vacated. Tests cover both colors, both capture directions in generation, FEN results, original-position preservation, missing/inconsistent metadata, absent/friendly/non-pawn adjacent pieces, wrong source ranks, occupied targets, opportunity expiry, ordinary advances instead of capture, castling-right preservation, invalid promotion choices, exposed rook/bishop attacks and removal of checking pawns. Board tests cover immutable removal and invalid removal inputs. The three obsolete unsupported-case tests were replaced. Selected tests use `com.chessapp.BoardTestRenderer`.

The user reports all tests green for the final Day 13 archive. Review was by source inspection; no independent tests or builds were run.

Movement remains internal pure Java code. `ChessRules` exposes only `initialPosition()`; `GameService` still stores the initial FEN and does not apply moves. Day 13 changes no HTTP contract, DTO, migration or frontend implementation. Castling execution, complete legal-move enumeration, outcomes, move history and gameplay integration remain pending. Generation handles a single source; an empty result cannot establish checkmate or stalemate. FEN parsing is structural, not proof of legality or reachability; the implementation does not reconstruct the preceding move from history. Board equality is identity-based, repetition requires history, and authorization, transactions, persistence and gameplay concurrency remain service responsibilities. AI and map-versus-array evaluation remain deferred.

### En passant representation and application

The fourth FEN field is the en passant target: the square passed over by the preceding double pawn advance and the empty landing square for a potential capture. It is not the captured pawn's current square. Double advances record this target even when no opposing pawn can capture. Other supported moves clear it; a later double advance replaces it with a new opportunity.

Example: `7k/8/8/3pP3/8/8/8/K7 w - d6 0 20`.

| Role | Square |
| --- | --- |
| White capturing pawn's source | `e5` |
| En passant target / capture destination | `d6` |
| Black captured pawn's current square | `d5` |
| Preceding double advance described by the metadata | `d7` to `d5` |

White may capture `e5` to `d6`, removing the Black pawn from `d5`, provided its king remains safe. White captures from rank 5 to rank 6; Black captures from rank 4 to rank 3. The captured square combines the destination file with the source rank. The opportunity is available only on the immediate reply.

The target is metadata, not proof of a valid capture. Generation checks the local geometry and placement listed above, while legal filtering checks resulting-board king safety. It does not prove historical reachability or validate the preceding pawn's starting square or the halfmove clock as a complete position-consistency policy. Parser structural checks, including permitted target ranks, remain unchanged.

## Day 14 implementation — historical, 27 October 2026

Standard castling is implemented for both colors and both sides. Candidates require the matching castling right, the king on e1/e8, a same-color rook on its original corner, and an empty intervening path. Inconsistent rights/placement omit the affected candidate while ordinary moves remain available. Rights are historical metadata; local placement checks do not reconstruct move history or distinguish a promoted rook from an original rook.

`MoveApplier` validates exact candidate membership, relocates the king and rook through immutable Board operations, and reuses shared metadata handling. Castling increments the halfmove clock once, increments the fullmove number after Black, switches turns once, clears the en passant target and permanently removes both rights for the moving king's color. Promotion choices on castling are rejected through candidate membership.

`LegalMoveGenerator` identifies castling separately from path safety. It checks the starting square on the original board, then the transit square with the king relocated and the rook still at its corner. The existing shared safety check examines the destination on the completed castling board. `MoveApplier` intentionally does not check attacks. Pinned pieces still count as attackers. Queenside b1/b8 must be empty but may be attacked; an attacked rook does not itself prohibit castling.

Source-confirmed coverage includes all four relocations and resulting FENs, ordinary king moves alongside castling, individual/absent rights, every intervening square, missing/wrong-color/non-rook corner pieces, displaced kings, attacks on starting/transit/destination squares, pinned-knight attacks, attacked rooks and b-file squares, en passant expiry, promotion-choice rejection and original-position preservation. `KingMoveGeneratorTest` replaces its obsolete castling-exclusion expectation. `CastlingTest` contains ten parameterized test methods; selected cases use `com.chessapp.BoardTestRenderer`. Existing movement, promotion and en passant test sources are unchanged.

Verification: the user reported green tests through the king-safety increment. Final additional boundary tests are present, with no subsequent explicit passing report. No independent tests or builds were run.

Movement remains internal pure Java code. `ChessRules` exposes only `initialPosition()`; `GameService` creates and stores the initial FEN and does not apply moves. Complete legal-move enumeration, outcomes, move history and gameplay integration remain pending. Single-source generation cannot establish checkmate or stalemate. FEN parsing remains structural, Board equality remains identity-based, and repetition requires history. Authorization, transactions, persistence and gameplay concurrency remain service responsibilities. AI and map-versus-array evaluation remain deferred.
