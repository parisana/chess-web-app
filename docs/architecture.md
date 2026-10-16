# Architecture

## Purpose and style

Build a portfolio-quality multiplayer chess application over approximately 30 working days at eight hours per day. The emphasis is on backend engineering and a complete, maintainable implementation.

The backend is a single Spring Boot deployment: a modular monolith, packaged by feature with conventional layers. This is not DDD. A feature is currently a Java package, not a separate Gradle project or deployable service.

## Feature ownership

| Package | Responsibility | Current state |
| --- | --- | --- |
| `auth` | Registration, credentials, login use cases | Registration and local credential persistence implemented |
| `player` | Player identity and profile data | Player entity, repository, creation service implemented |
| `matchmaking` | Queue membership and pairing | Planned |
| `game` | Authoritative board, rules, moves, lifecycle, history | Planned |
| `notification` | Deliver authorized outbound updates | Planned |
| `security` | HTTP/WebSocket authentication and authorization | HTTP access rules and password encoder implemented |
| `common` | Small, genuinely shared components | Timestamp base entity implemented |

Keep the name `Player`. Add packages and classes when behavior requires them; do not create empty layers or generic abstractions in advance.

## Conventional layers

Feature packages may contain `controller`, `service`, `dto`, `entity`, and `repository`, plus focused packages such as `exception` or `config` when needed.

- Controllers handle transport, request validation, status codes, and delegation.
- Services implement use cases, business checks, and transaction boundaries.
- Repositories encapsulate database access within their owning feature.
- DTOs define external API responses and selected cross-feature service contracts.
- Entities describe persistence state and appropriate local behavior.

Features call another feature's selected service operations. They do not access its repositories or controllers. Service interfaces are optional; a clear concrete service is sufficient at this scale. Avoid circular feature dependencies.

## Implemented registration flow

1. `AuthController` accepts `POST /api/v1/auth/register` and validates `RegisterRequest`.
2. `AuthService.register()` starts a transaction.
3. `PlayerService.createPlayer()` persists a player through `PlayerRepository`.
4. The configured `PasswordEncoder` encodes the password.
5. `AuthCredentialRepository` persists the player ID and password hash.
6. The service returns `RegisterResponse`; successful transaction completion precedes the HTTP success response.

Player and credential creation must succeed or fail together. The transaction currently belongs to the public registration service method. The player save joins that transaction under normal Spring transaction propagation.

The current implementation returns a `Player` entity across the player/auth boundary. This is a documented temporary compromise. Day 2 will introduce a small player service result DTO so auth no longer imports player persistence entities. This does not require a DDD model or an interface for every service.

## Persistence decisions

PostgreSQL is the durable data store. Spring Data JPA handles persistence, and Flyway owns schema evolution. Hibernate uses `ddl-auto: validate`; Open EntityManager in View is disabled.

| Migration | Schema |
| --- | --- |
| V1 | `players`: identity ID, unique username, unique email, creation/update timestamps |
| V2 | `auth_credentials`: identity ID, unique player ID, password hash, timestamps, foreign key to players |

`AuthCredential.playerId` is a scalar ID rather than a JPA association. The database still enforces referential integrity. Its uniqueness currently permits one local password credential per player. Future external login methods require an explicit schema design.

The foreign key currently has no delete cascade. Deleting a player with credentials is rejected; a future deletion use case must define dependent-data handling explicitly.

`BaseEntity` contains `Instant` creation/update timestamps maintained by JPA callbacks. Database defaults support inserts outside JPA, but there is no database trigger maintaining `updated_at` for direct SQL updates. IDs remain in concrete entities.

Applied migrations should remain unchanged; introduce new versioned migrations for schema evolution. Database unique constraints are authoritative. A pre-insert existence check can improve feedback but cannot eliminate concurrent duplicate attempts.

Current username/email uniqueness follows the stored values and database comparison behavior. Normalization and case-insensitive identity rules have not yet been implemented and must be defined before login lookup semantics are finalized.

## Security

Currently, only `POST /api/v1/auth/register` is explicitly public; other requests require authentication. Sessions are stateless and CSRF is disabled in preparation for bearer-token APIs. Login and JWT authentication are not implemented, and protected-endpoint response behavior still needs tests.

The password encoder is `DelegatingPasswordEncoder`, using bcrypt by default and storing its algorithm identifier with the hash. API responses expose neither plaintext passwords nor hashes. Avoid Lombok-generated entity `toString()` methods that could expose credential fields.

Day 2 must align password validation with bcrypt's 72-byte UTF-8 limit; the existing `@Size(max = 72)` counts Java string length. Passwords must not be silently truncated or normalized.

Future JWT handling must verify signatures and claims, reject invalid/expired tokens, and establish the authenticated player identity. Authorization must verify resource ownership and game participation on the server. Token storage and logout behavior will be decided when Angular integration begins.

## Error handling

Request Bean Validation currently handles malformed registration fields. A consistent error representation and duplicate-identity `409 Conflict` handling are pending. Add email length validation matching the schema, and define username/email normalization deliberately.

Map known uniqueness conflicts to specific API errors; do not classify every database integrity failure as a duplicate. Unexpected failures must not leak SQL, passwords, or internal exception details.

## Planned game and matchmaking behavior

Matchmaking owns eligibility, queue membership, pairing, and coordination of game creation. The game feature owns the resulting game state and lifecycle. Concurrency controls must prevent duplicate active assignments and stale or competing move submissions.

The backend is authoritative for legal moves, turn order, game results, and persisted move history. Completed games must be durable; reconnection should recover authorized current state.

Use direct service calls where the caller needs a result. Spring application events may decouple appropriate internal reactions, but are not durable messaging. Choose transaction/event timing according to whether listeners may observe committed state.

WebSocket with STOMP is planned for real-time updates. Authenticate connections and authorize commands, subscriptions, and destinations. Clients submit requests; the server validates them and sends authorized results. Redis and Kafka are outside the initial infrastructure.

## Testing

- Mockito unit tests verify registration orchestration and exception propagation.
- Standalone MockMvc tests verify request mapping, validation, and controller responses; they do not execute the application security filter chain.
- PostgreSQL integration tests verify persistence and Spring configuration.
- A separate integration test without a test-managed transaction must prove the service transaction rolls back player creation after a later registration failure.
- Security-chain, login, JWT, concurrency, WebSocket, and Angular tests will follow their implementations.

The current registration integration test is transactional and activates `test`. Flush and clear before persistence reads to avoid relying on managed objects. Real HTTP server work runs outside the test thread's transaction, so test rollback alone does not clean up those writes. Test rollback does not remove rows left by earlier runs, and generated identity values need not be consecutive.

## Configuration

Use YAML. The current shared file is `application.yaml`; test overrides are in `application-test.yml`. Both extensions are valid. Ignored `application-local.yml` files may hold local overrides and require the `local` profile.

The shared datasource currently provides local defaults and optional environment-variable overrides. The test datasource explicitly selects `chess_test_db`. Enable Flyway migration-name validation in shared configuration as a follow-up; it currently appears only in the test profile.

## Scope

Initial target: registration/login, player profiles, matchmaking, multiplayer games, server rules, history, results, reconnection, automated tests, and deployment documentation. Spectators, tournaments, chat, engine analysis, and distributed scaling remain outside the initial scope.
