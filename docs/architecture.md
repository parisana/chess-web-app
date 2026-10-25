# Architecture

Checkpoint documentation updated on 25 October 2026. The source baseline inspected was `chess-web-app-day8-start.zip` (archive commit `79871c70c4fe8d0065c61795ab37daf26642ba2d`). Both Day 7 MoveApplierTest fixture corrections were present in that archive. Day 8 implementation and test additions were supplied incrementally during the session; subsequent user replies reported no problems. No independent tests or builds were run. A fresh end-of-Day-8 source archive and its final verification report have not yet been reviewed. Day 8 sections describe the guided changes, not independently inspected final source. See `development-progress.md` for verification and `roadmap.md` for scheduling.

## Style and ownership

A single Spring Boot application, organized as a modular monolith with feature packages and conventional controller/service/DTO/entity/repository layers. Not DDD; no microservices or multi-project Gradle modules are required. Keep the domain term `Player`.

| Package | Ownership | State |
| --- | --- | --- |
| `auth` | Registration, local credentials, login | Implemented |
| `player` | Identity and profile data | Creation, lookup, current profile read and username updates |
| `security` | HTTP authentication, password encoding, JWT machinery | Implemented |
| `common` | Shared timestamps and request validation error handling | Implemented |
| `matchmaking` | Persistent queue, eligibility, and transactional pairing | Implemented |
| `game` | Initial state, active-player reservations, participant-protected retrieval and pure Java rules | Persistence/access implemented; guided Day 8 pipeline supports knights and rooks |
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

## Day 6 rules foundation

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
| `game.rules.movement` | PiecePseudoLegalMoveGenerator strategies, knight/rook generators, PseudoLegalMoveGenerator dispatcher, shared MoveApplier and LegalMoveGenerator |
| `game.rules.attack` | SquareAttackDetector |
| `game.config` | Spring bean wiring; outside the pure rules subsystem |

Only initial-position/FEN components are wired into the application. Movement components are constructed directly in unit tests. The guided Day 8 pipeline supports knights and rooks. The fixed king square in LegalMoveGenerator is correct for these pieces; revisit this when king movement is added. Keep candidate application and king-safety filtering shared.

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

## Day 8 rook movement and Strategy dispatch

This section supersedes the knight-only limitations in the historical Day 7 section. It documents changes supplied during the guided session; the final updated source archive has not yet been inspected.

- `PiecePseudoLegalMoveGenerator` is the strategy interface, exposing `pieceType()` and `generate(position, from)`.
- `KnightMoveGenerator` and `RookMoveGenerator` implement that interface. Their generation is pseudo-legal: own-king safety is checked later.
- `PseudoLegalMoveGenerator` registers strategies by PieceType, rejects duplicate registrations, and delegates by the source piece's type. Empty and opposing sources return no moves; unsupported own piece types throw UnsupportedOperationException instead of silently returning an empty set.
- Registration uses EnumMap; Map.copyOf stores an immutable snapshot. The stored map need not retain EnumMap representation or iteration order. The dispatcher only relies on lookup.
- Rook generation scans four orthogonal rays. Empty squares are candidates. The first opposing non-king occupant is a capture candidate; any occupant ends the ray. An opposing king blocks the ray but is never a capture candidate. SquareAttackDetector separately recognizes attacks on king squares.
- Shared MoveApplier and LegalMoveGenerator now depend on PseudoLegalMoveGenerator. No per-piece application or filtering classes are added.
- MoveApplier removes castling rights when a rook leaves its original corner or a rook is captured on its original corner. One move can remove rights for both colors. Returning a rook never restores a right. Turn, counters, en passant expiry and immutability behavior remain shared.
- Rook legal-move tests cover White and Black pins, moves along a pin line, blocking check and capturing the checking attacker. Existing knight tests remain regression coverage for the shared pipeline. Selected rook tests render boards through com.chessapp.BoardTestRenderer.

ChessRules still exposes only initialPosition(). GameService does not apply moves. No HTTP endpoint, migration, frontend behavior or persisted gameplay command changes in this increment. Empty knight/rook subsets do not establish checkmate or stalemate. King movement will require checking the king's destination square. Castling execution, pawn moves and promotion remain unsupported.
