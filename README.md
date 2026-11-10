# Chess Web App

A two-player chess application built with Java 21, Spring Boot, PostgreSQL and Angular. The backend is a modular monolith with a pure Java rules engine. It owns move legality, game clocks and results.

- Server-side sessions, player profiles and persistent matchmaking
- Timed games with promotion, castling, en passant, resignation and draw offers
- Private WebSocket snapshots for live play, with HTTP recovery after reconnect
- SAN move history, replay, a participant-only PGN endpoint and Elo updates

See the [game board](docs/screenshots/game_board.png) and [White wins screen](docs/screenshots/white_wins.png).

## Run locally

Install Java 21, PostgreSQL and Node.js compatible with Angular 21. Create a PostgreSQL database and role, then configure `DB_URL`, `DB_USERNAME` and `DB_PASSWORD` for the backend. Flyway applies the schema at startup.

From `backend/`:

```bash
./gradlew bootRun
```

From `frontend/`:

```bash
npm ci
npm start
```

Open [localhost:4200](http://localhost:4200). The Angular dev proxy sends API and WebSocket traffic to port 8080. To try multiplayer locally, use two independent browser sessions, sign in as different players and join matchmaking in both.

## Verification

Run `./gradlew test` from `backend/` with the configured PostgreSQL test database. From `frontend/`, run `npm test -- --watch=false` and `npm run build`.

## Local performance

Two k6 runs at **500 concurrent games** each held **1,000 WebSocket connections** for at least three minutes and delivered all **21,000 expected snapshots**. Command-to-state p99 was **2.07–2.12 s** with the backend, database and k6 sharing a four-logical-CPU machine. See the [workload, commands and results](docs/load-testing.md).

## Documentation

| Guide | Covers |
| --- | --- |
| [Architecture](docs/architecture.md) | Feature boundaries, persistence, rules and real-time delivery |
| [API contracts](docs/api-contracts.md) | HTTP endpoints and private STOMP messages |
| [Frontend](frontend/README.md) | Local development and gameplay state |
| [Performance investigations](docs/performance-investigations/README.md) | Connection-pool starvation and its resolution |
| [Roadmap](docs/roadmap.md) | MVP acceptance and deferred work |
| [Real-time updates](docs/realtime-updates-flow.md) | Snapshot delivery, session checks and recovery |
| [Documentation archive](docs/history/README.md) | Earlier designs, experiments and review notes |
