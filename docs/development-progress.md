# Chess Web App — Development Progress

## Project Goal

Build a portfolio-quality chess web application over approximately 30 working days, with around 8 hours available per working day.

Primary emphasis: backend engineering, modularity, correctness, concurrency, security, persistence, and testing.

## Confirmed Architecture

- Modular monolith, deployed as one Spring Boot application.
- Package-by-feature with conventional layers inside each feature.
- **Not Domain-Driven Design (DDD).**
- Feature packages: `auth`, `player`, `matchmaking`, `game`, `notification`.
- Supporting packages: `security`, `common`.
- Conventional feature subpackages: `controller`, `service`, `dto`, `entity`, `repository`, plus additional packages when needed.
- Keep the domain term `Player`, not `User`.
- Use Lombok where appropriate, but avoid `@Data` on JPA entities.
- Use a minimal `BaseEntity` with `createdAt` and `updatedAt`.

## Technology Stack

- Java 21, Spring Boot, Gradle
- PostgreSQL, Spring Data JPA, Flyway
- Spring Security and JWT
- WebSocket with STOMP
- Spring application events
- Angular and TypeScript
- JUnit 5, Mockito, integration testing

## Configuration Conventions

- Use `application.yml`, never `.properties` in future instructions.
- Local credentials belong in ignored `application-local.yml`.
- Flyway owns database schema migrations.
- Hibernate uses `ddl-auto: validate`.
- Enable Flyway migration filename validation.
- Avoid unnecessary infrastructure such as Redis or Kafka initially.

## Day 1 Progress

Completed or established:
- Development environment and Git repository.
- Spring Boot project generated and imported into IntelliJ IDEA.
- PostgreSQL connectivity established.
- Architecture and package conventions decided.
- Flyway V1 creates the `players` table.
- Flyway V2 creates the `auth_credentials` table.
- Initial `Player`, `AuthCredential`, repositories, and registration DTOs designed.
- Registration service/controller and password hashing implementation provided.

Still to verify:
- Registration endpoint works end to end.
- Automated unit tests pass.
- Controller validation tests pass.
- Transaction rollback works against PostgreSQL.
- Latest changes committed to Git.

## Current Registration Design

`POST /api/v1/auth/register`

The `AuthService`:
1. Creates a player through `PlayerService`.
2. Encodes the password using Spring Security's `PasswordEncoder`.
3. Persists an `AuthCredential` containing the player ID and password hash.
4. Uses `@Transactional` for atomic registration.
5. Returns `RegisterResponse` without credentials.

## Known Improvements

- Avoid returning a JPA entity across feature boundaries; introduce a small player service contract.
- Return HTTP 409 for duplicate usernames/emails.
- Add consistent API error handling.
- Review password validation policy.
- Implement PostgreSQL-backed integration tests.

## Day 2 Priorities

1. Finish any outstanding Day 1 tests.
2. Improve registration error handling.
3. Refine cross-feature service contracts.
4. Implement login with password verification.
5. Design and implement JWT issuance and validation.
6. Add authentication and authorization tests.

## Guidance Preferences

- Explain engineering decisions and tradeoffs, not just code.
- Proceed incrementally rather than generating the entire application.
- Include suggested test/build commands but do not ask for routine confirmation.
- Pause only for actual errors or decisions requiring input.
- Keep examples consistent with the agreed architecture.

