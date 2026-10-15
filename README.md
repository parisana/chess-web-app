
# Chess Web App

A portfolio-quality real-time multiplayer chess application.

## Technology Stack

### Frontend
- Angular
- TypeScript

### Backend
- Java 21
- Spring Boot
- Spring Security
- Spring Data JPA
- Maven

### Database
- PostgreSQL
- Flyway

### Real-time Communication
- WebSockets
- STOMP

### Testing
- JUnit 5
- Mockito
- Integration and concurrency tests
- Angular testing tools

## Architecture

The application follows a modular monolith architecture.
The backend is organized by feature, with layers inside
each feature.

The server is authoritative for chess rules, legal moves,
game state, and game results.

PostgreSQL is the durable source of truth.

## Project Structure

- `backend/` — Spring Boot application
- `frontend/` — Angular application
- `docs/` — Architecture decisions and API documentation

## Development Status

Day 1: Repository initialization and project foundation.

