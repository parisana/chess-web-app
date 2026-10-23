# Architecture

## Style and ownership

A single Spring Boot application, organized as a modular monolith with feature packages and conventional controller/service/DTO/entity/repository layers. Not DDD; no microservices or multi-project Gradle modules are required. Keep the domain term `Player`.

| Package | Ownership | State |
| --- | --- | --- |
| `auth` | Registration, local credentials, login | Implemented |
| `player` | Identity and profile data | Creation, lookup, current profile implemented |
| `security` | HTTP authentication, password encoding, JWT machinery | Implemented |
| `common` | Shared timestamps and request validation error handling | Implemented |
| `matchmaking` | Persistent queue, eligibility, and transactional pairing | Implemented |
| `game` | Initial state, active-player reservations, and participant-protected retrieval | Implemented |
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

## Error ownership

- `ApiExceptionHandler`: shared MVC handling and field-validation Problem Details.
- `PlayerExceptionHandler`: player duplicates and known database constraints.
- `AuthExceptionHandler`: invalid local credentials.
- Spring Security filter chain: missing/invalid bearer credentials.

Feature-owned error constants avoid a global catalog of every feature's codes. Field errors omit rejected values to avoid exposing passwords. Filter-chain errors do not currently share the MVC Problem Detail format. HTTP status consistency is implemented; a universal error-body contract is not.

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

Both forms prevent duplicate submissions, show progress/errors, and retain passwords after failure. Successful login clears its password; successful registration resets the form and redirects to login. Registration displays backend duplicate/field-validation errors. Material theming is configured in `material-theme.scss`; home currently uses a plain button.

The five focused HTTP/store tests cover bearer attachment, external URL exclusion, protected 401 clearing/redirect, profile-load failure cleanup, and logout. They do not test forms, the guard, successful login completion, public-auth token exclusion with an existing token, stale-token responses, or password retention. These are coverage limits, not requests for a broad frontend suite. The generated app title test is obsolete.

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

## Planned application growth

Spring application events may decouple appropriate internal reactions, but are not durable messaging. WebSocket/STOMP remains a candidate for later real-time delivery, not an implemented dependency. It will need authenticated connections and authorized subscriptions/commands. Redis and Kafka are outside the initial infrastructure. Spectators, tournaments, chat, engine analysis, and distributed scaling remain out of initial scope.
