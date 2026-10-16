# Chess Web App — Architecture

## 1. Architectural Style

The backend follows a **modular monolith architecture**, organized using package-by-feature with layers inside each feature.

The application runs as a single Spring Boot deployment. Each feature groups related functionality together, with clear responsibilities and controlled dependencies between features.

The project does not adopt Domain-Driven Design as an architectural methodology. We will use straightforward services, repositories, controllers, DTOs, and domain models where appropriate.

## 2. Technology Stack

### Backend
- Java 21
- Spring Boot
- Spring Web
- Spring Security
- Spring Data JPA
- Spring WebSocket with STOMP
- Spring application events
- Gradle

### Database and Migrations
- PostgreSQL
- Flyway

### Frontend
- Angular
- TypeScript

### Testing
- JUnit 5
- Mockito
- Spring Boot integration testing
- Angular testing tools

## 3. Module Structure

The base package is `com.chessapp`.

The backend is organized into the following feature and supporting modules:

- **auth** — player registration, login, and authentication-related use cases.
- **player** — player profiles and profile management.
- **matchmaking** — queue management, player pairing, and match initiation.
- **game** — chess moves, game state, game lifecycle, and results.
- **notification** — outbound notifications and notification delivery.
- **security** — HTTP and WebSocket authentication and authorization configuration.
- **common** — genuinely shared utilities and infrastructure components.

Features own their related functionality. Supporting modules such as `security` and `common` provide cross-cutting capabilities without taking ownership of feature-specific business logic.

## 4. Package Organization

Business features use layers within their feature packages. Packages are introduced as needed rather than required uniformly across all features.

Example:

    com.chessapp
    ├── auth
    │   ├── controller
    │   ├── service
    │   ├── dto
    │   └── repository
    ├── player
    │   ├── controller
    │   ├── service
    │   ├── dto
    │   └── repository
    ├── matchmaking
    │   ├── controller
    │   ├── service
    │   ├── dto
    │   └── repository
    ├── game
    │   ├── controller
    │   ├── service
    │   ├── dto
    │   └── repository
    ├── notification
    ├── security
    └── common

The packages above illustrate the intended organization. Additional packages, such as `config`, `mapper`, `websocket`, or `exception`, may be introduced inside the relevant feature when justified.

Not every feature needs a controller, repository, or every other layer.

## 5. Layer Responsibilities

### Controller Layer
- Handles incoming HTTP requests and WebSocket messages where applicable.
- Validates request structure and delegates to services.
- Converts application results into API responses.
- Avoids containing substantial business logic.

### Service Layer
- Implements application use cases and coordinates business operations.
- Applies business rules and validation that go beyond request-shape validation.
- Defines transaction boundaries where multiple operations must succeed or fail together.
- Coordinates interactions with repositories and other features.

### Repository Layer
- Encapsulates database access through Spring Data JPA or custom queries.
- Provides persistence operations to the relevant feature.
- Avoids exposing persistence implementation details unnecessarily across feature boundaries.

### DTOs
- Define request and response contracts.
- Separate external API representations from persistence entities.
- Prevent internal database models from becoming public API contracts.

### Models and Entities
- Represent application concepts and persisted data as appropriate.
- Use JPA entities for persistence where needed.
- Keep business rules close to the relevant models when that improves clarity, without requiring a DDD-style domain model.

## 6. Module Boundaries and Dependencies

- Each feature owns its implementation and related business logic.
- Features should avoid depending directly on another feature's internal controllers, repositories, or persistence entities.
- Cross-feature interactions should use clear service-level interfaces or selected Spring application events when appropriate.
- Circular dependencies between features should be avoided.
- The `common` package must not become a general dumping ground for unrelated classes.
- Shared functionality should be extracted only when there is a genuine need.

For example, matchmaking coordinates player pairing and game initiation. The game feature remains responsible for managing the resulting game's state and lifecycle.

## 7. Database and Persistence

PostgreSQL is the primary persistent data store.

Spring Data JPA handles relational persistence, while Flyway manages schema creation and evolution through versioned database migrations.

Database schema changes should be introduced through Flyway migrations rather than relying on Hibernate to create or update production schemas.

Hibernate schema validation is used during application startup to detect mismatches between mapped entities and the database schema.

Transactions should be defined around application operations that require atomic changes.

Concurrency-sensitive operations, especially matchmaking and move submission, must be designed to handle competing requests safely.

## 8. Authentication and Authorization

Spring Security handles HTTP authentication and authorization.

JWT-based authentication is used for authenticated API requests. WebSocket connections and subscriptions must also enforce appropriate authentication and authorization.

The backend must verify that players can access only their own protected resources and participate only in games for which they are authorized.

Passwords must be stored using a suitable password-hashing algorithm rather than plaintext.

Local credentials must not be committed to version control.

## 9. Matchmaking

The matchmaking feature manages the queue and pairs eligible players.

It must prevent a player from being assigned to multiple active matches and handle concurrent join, leave, and pairing requests safely.

Match creation and matchmaking state changes should maintain consistency, using appropriate transaction boundaries and concurrency controls.

Redis and message brokers are not part of the initial architecture. They may be evaluated later if actual requirements justify them.

## 10. Game Management

The game feature is authoritative for chess game state and lifecycle.

The backend validates moves, enforces turn order, rejects illegal or stale requests, and determines game outcomes.

It manages move history and game states such as active, completed, checkmate, stalemate, resignation, and supported draw conditions.

Completed games and their results must be persisted. Reconnection should allow authorized players to recover the current game state.

## 11. Real-Time Communication

Spring WebSocket with STOMP supports real-time matchmaking and game updates.

Client messages represent requests or commands. The server validates each command, updates authoritative state, and publishes the resulting events or state updates to authorized recipients.

Spring application events may be used for decoupling operations within the same application process. They are not a durable message broker and should not be relied upon for guaranteed delivery across process restarts.

## 12. Error Handling and Validation

The API should use consistent error responses and appropriate HTTP status codes.

Request validation should reject malformed input early. Business validation should reject operations that violate application rules, including unauthorized actions, invalid moves, and conflicting state changes.

Exceptions should be handled centrally where practical, without leaking sensitive implementation details to clients.

## 13. Testing Strategy

The project will use multiple levels of automated testing:

- **Unit tests** for service logic, validation, and chess rules.
- **Repository tests** for persistence queries and mappings.
- **Integration tests** for Spring configuration, database behavior, transactions, and security.
- **WebSocket tests** for message handling and authorization.
- **Angular tests** for frontend components and services.
- **End-to-end tests** for critical user journeys where practical.

Priority should be given to authentication, authorization, matchmaking concurrency, legal move validation, game completion, and persistence.

## 14. Configuration and Environments

Spring configuration uses YAML files.

- `application.yml` contains shared configuration and non-sensitive defaults.
- `application-local.yml` contains local-only overrides and credentials and must not be committed.
- Environment-specific configuration should be introduced as deployment requirements evolve.

The project should provide reproducible local setup instructions and document the required Java, Node.js, Angular, and PostgreSQL versions.

## 15. Initial Scope and Future Considerations

The initial project includes:

- Registration, login, and player profiles.
- Matchmaking and player pairing.
- Real-time multiplayer chess.
- Server-side move validation.
- Game persistence and history.
- Game completion and results.
- Reconnection and recovery.
- Automated testing and deployment documentation.

Spectators, tournaments, rankings, chat, engine analysis, and distributed scaling are outside the initial scope.

The architecture should remain simple enough to develop and maintain as a single application while keeping responsibilities sufficiently separated to support future evolution.
