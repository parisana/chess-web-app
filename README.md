# Chess Web App

## Day 19 current checkpoint — source-reviewed, 29 October 2026

Reviewed the actual user archive `chess-web-app-day19-end.zip` against `chess-web-app-day18-end.zip`. No blocking defect was found in the reviewed Day 19 changes. Two small follow-ups are supplied inline and recorded in [the review](docs/day19-frontend-gameplay.md): add a submission guard to Game.refresh() and clear obsolete move feedback after a successful GET. They are not applied to production source by this documentation package.

The Angular frontend now supports guarded matched-game navigation, participant-protected game reads, a custom Player-oriented click-to-move board, explicit promotion including cancellation, versioned move submission, manual refresh, stale-state reload without command retry, uncertain-outcome blocking and completion feedback. Game and ChessBoard use separate TypeScript, HTML and CSS files.

The backend tree is byte-for-byte unchanged from the Day 18 archive. No new endpoint, migration or backend runtime configuration is introduced. The cm-chessboard dependency, declarations and asset/style additions are absent. package.json is unchanged; package-lock.json contains 15 package-version changes, and angular.json differs only in formatting. Those user changes are preserved.

Tests are treated as green under the standing instruction to report failures. The assistant ran no application tests, builds, migrations or browser acceptance checks, and claims no separately verified frontend build/manual result. Source inspection is the evidence for this review.

See [frontend gameplay flow](docs/frontend-gameplay-flow.md) for the lifecycle and Angular coordination, [Day 19 review](docs/day19-frontend-gameplay.md) for findings and [next-session handoff](docs/day19-handoff.md) for continuation. Earlier Day sections retain historical boundaries and do not override this checkpoint.

History, real-time delivery, clocks, additional draw rules, acceptance and disconnect/abandonment policy remain separate scope decisions. A board library is optional post-MVP polish, not an MVP requirement.

## Day 17 checkpoint — historical source review, 28 October 2026

Reviewed the actual user implementation in `chess-web-app-day17-end(1).zip` against `chess-web-app-day16-end.zip`. No blocking defect was found in the reviewed Day 17 production/test changes. Tests are treated as green under the user's standing instruction to report failures; this is user-provided verification context, not an independent run or a detailed per-command log. No tests, builds or migrations were run by the assistant, and no separate frontend build result is claimed.

`GameService.submitMove(gameId, requestingPlayerId, expectedVersion, move)` now coordinates participant/turn checks, exact legal application, resulting-position assessment, current-FEN persistence, stale/concurrent-command rejection and atomic checkmate/stalemate completion with reservation release. `GameMoveResult.from(game, assessment)` owns mechanical result mapping; `Game.finish()` calls its extracted switch-based `validateResult()`. There is no separate production GameMoveService.

V6 adds completion fields and consistency constraints. GameStatus is IN_PROGRESS/FINISHED, with separate result, termination reason and finish time. The unchanged game GET response fields can now carry an advanced FEN and FINISHED status; version/result/reason/time remain absent from that HTTP response. No HTTP move route, move history, frontend gameplay or disconnect policy is added. See the [reviewed implementation and verification commands](docs/day17-gameplay-service.md).

Day 16 and earlier milestone sections below retain historical behavior/evidence. Their initial-only and pending-gameplay descriptions do not override this checkpoint. The earlier closing-test passing-status uncertainty is retained as historical provenance; current test status follows the user's later standing instruction.

### Historical Day 16 review provenance

Reviewed on 28 October 2026 against `chess-web-app-day16-end.zip`. Source inspection confirms the legality-enforcing facade, complete production strategy registration, facade regression tests and both [closing checks](docs/day16-closing-checks.md). No blocking defect was found in the reviewed Day 16 changes. The user reports all tests green through increments 1–2. Both later closing tests are present, but no subsequent explicit passing report was supplied for them. No tests or builds were run independently; no detailed test log or separate frontend build result was supplied.

The Day 16 section describes the inspected implementation and supersedes earlier initial-position-only boundary and test-only-construction statements. Historical sections retain the behavior and evidence available at those checkpoints.

## Day 16 boundary — historical

`ChessRules` exposes `initialPosition()`, `legalMoves(position)`, `applyMove(position, move)` and `evaluate(position)`. Lookup returns the complete immutable legal set. Application requires exact membership in that set, including the promotion choice, before delegating to `MoveApplier`. Assessment returns `PositionStatus` for the side to move. The existing pipeline remains shared; no per-piece application services or duplicate movement algorithms are introduced.

`GameService` still calls only `initialPosition()` and serializes it for creation. Games remain at their initial FEN in `IN_PROGRESS`. Move submission, persistence/history, gameplay concurrency, terminal lifecycle and active-reservation release remain pending. No endpoint, DTO, migration or frontend implementation changes in Day 16. Authorization, transactions, persistence and concurrency remain service responsibilities.

The rules facade is assembled in `game.config.GameRulesConfig`; rules code has no Spring/JPA/HTTP dependencies. See the architecture document for construction and the development-progress document for verification provenance.

A portfolio project built as a modular monolith: package-by-feature with conventional layers, keeping `Player` terminology. Cross-feature interactions use services. This project implements its own pure Java chess rules; AI is outside the MVP.

Historical Day 15 source-review provenance: reviewed on 27 October 2026 against `chess-web-app-day15-end.zip`. Source inspection confirms position-wide legal-move enumeration and `NORMAL` / `CHECK` / `CHECKMATE` / `STALEMATE` assessment for the side to move. No blocking defect was found in the reviewed Day 15 changes. The user reports all tests green after the final guided regressions. No independent tests or builds were run; no detailed test log or separate frontend build result was supplied.

## Stack

- Backend: Java 21, Spring Boot 4.1.1, Gradle wrapper 9.8.1, PostgreSQL, JPA, Flyway, Spring Security/JWT, Lombok and YAML.
- Frontend: Angular 21, Angular Material, standalone components, reactive forms and in-memory authentication.

## Current capabilities

- Registration/login with validation, BCrypt and HS256 JWT authentication.
- Authenticated player profile GET and username PATCH, with an Angular profile editor.
- Persistent matchmaking queue, transactional pairing, active-player reservations, manual matched-game discovery and participant-protected game retrieval.
- Immutable chess representation, separate FEN parser/serializer and a rules facade for initial position, legal lookup/application and assessment; game creation uses its initial-position operation.
- Strategy-based knight, rook, bishop, queen, king (including standard castling), pawn, explicit promotion and en passant candidates; shared sliding traversal, candidate application and king-safety filtering.
- Position-wide legal-move enumeration and current-side normal/check/checkmate/stalemate assessment.
- GameService move commands with participant/turn checks, version protection, persisted current FEN, checkmate/stalemate results and atomic release of both active reservations.
- Authenticated HTTP move submission and version/result-bearing game reads.
- Angular game navigation, custom board, explicit promotion, manual refresh and recovery/completion feedback.
- Attack detection for all six piece types, including pinned attackers. Resulting-board king safety handles relocation and captures. Moving kings and moving/captured corner rooks remove affected castling rights permanently.

## Run locally

Use Java 21 and the checked-in Gradle wrapper. Review `backend/src/main/resources/application.yaml` for the configured PostgreSQL connection and JWT settings. Database/secret configuration remains user-managed. Flyway applies V1–V6; Hibernate validates the schema.

From `backend/`:

```bash
./gradlew bootRun
```

From `frontend/`, use the declared npm 11.6.2 and a compatible Node version:

```bash
npm ci
npm start
```

Open `http://localhost:4200`. The npm start script enables the proxy forwarding `/api/**` to the backend on port 8080.

## Local verification

From `backend/`, focused rules/configuration/entity/service tests require no database:

```bash
./gradlew test --tests 'com.chessapp.game.rules.*' --tests 'com.chessapp.game.config.GameRulesConfigTest' --tests 'com.chessapp.game.entity.GameTest' --tests 'com.chessapp.game.service.GameMoveServiceTest'
```

With the configured PostgreSQL test database available:

```bash
SPRING_PROFILES_ACTIVE=test ./gradlew test
```

Backend reports are generated at `backend/build/reports/tests/test/index.html`.

From `frontend/`, after dependency installation:

```bash
npm test -- --watch=false
npm run build
```

These commands were not executed by the assistant. Under the user's standing instruction, tests are treated as green unless a failure is reported. No detailed per-command log or separate frontend build result was supplied. The focused PostgreSQL command is in docs/day17-gameplay-service.md.

## Current limits

Gameplay uses manual refresh for match discovery and opponent moves. There is no move-history listing, real-time delivery or chess clock. The frontend displays the server-accepted FEN and does not duplicate move legality or retain a check indicator from POST after GET.

Checkmate/stalemate finish games and release reservations transactionally. Disconnecting, closing the page or leaving the queue does not finish a game; timeout/abandonment policy remains deferred.

Repetition, move-count draws, dead-position detection, resignation and agreement are outside assessment. FEN is structural state, not repetition history. AI and map-versus-array evaluation remain deferred.

Pairing runs on join. Sessions remain in memory, so browser reload requires login; Home can rediscover an active game afterward. Logout does not leave the queue. Secrets and runtime configuration remain user-managed.

## Documentation

- [Frontend gameplay lifecycle and Angular flow](docs/frontend-gameplay-flow.md)
- [Day 19 source review](docs/day19-frontend-gameplay.md)
- [After Day 19 handoff](docs/day19-handoff.md)

- [Architecture](docs/architecture.md)
- [Development progress and verification](docs/development-progress.md)
- [Roadmap](docs/roadmap.md)
- [API contracts](docs/api-contracts.md)
- [Frontend setup and behavior](frontend/README.md)
- [Movement pipeline walkthrough](docs/movement-pipeline-architecture.md)
- [Day 17 implementation and review](docs/day17-gameplay-service.md)
- [After Day 17 handoff](docs/day18-handoff.md)
- [After Day 16 handoff — historical](docs/day17-handoff.md)
- [Day 16 closing checks](docs/day16-closing-checks.md)
- [Post-Day-16 source review and proposed integration scope](docs/post-day16-integration-proposal.md) — historical proposal; its larger service option was selected and implemented.

## Day 13 completed checkpoint — historical, 27 October 2026

En passant is implemented for both colors. Generation requires the correct source rank, one forward diagonal step, matching target metadata, an empty destination and an adjacent opposing pawn. An inconsistent or unavailable opportunity contributes no en passant candidate and does not suppress ordinary moves. Attempting an omitted capture fails exact candidate membership with `IllegalArgumentException`; no unsupported-en-passant exception remains.

`MoveApplier` validates candidate membership before identifying a pawn's diagonal move into an empty square as en passant. It relocates the pawn and removes the opposing pawn from `(destination.file, source.rank)` through immutable Board operations. `Board.removePiece()` requires a nonnull occupied square and performs placement only; callers own chess-rule validation. The original position remains unchanged.

Shared handling switches turns, resets the pawn halfmove clock, increments the fullmove number after Black, and expires the target. En passant preserves existing castling rights. Ordinary moves, promotion choices and irreversible king/rook rights removal remain supported. An occupied diagonal follows ordinary capture rules even if inconsistent target metadata names it; no adjacent pawn is removed in that case. Promotion choices attached to en passant fail candidate membership.

`LegalMoveGenerator` remains shared and unchanged. It checks the moving side's king after both pawn squares have been vacated. Tests cover both colors, both capture directions in generation, FEN results, original-position preservation, missing/inconsistent metadata, absent/friendly/non-pawn adjacent pieces, wrong source ranks, occupied targets, opportunity expiry, ordinary advances instead of capture, castling-right preservation, invalid promotion choices, exposed rook/bishop attacks and removal of checking pawns. Board tests cover immutable removal and invalid removal inputs. The three obsolete unsupported-case tests were replaced. Selected tests use `com.chessapp.BoardTestRenderer`.

The user reports all tests green for the final Day 13 archive. Review was by source inspection; no independent tests or builds were run.

Movement remains internal pure Java code. `ChessRules` exposes only `initialPosition()`; `GameService` still stores the initial FEN and does not apply moves. Day 13 changes no HTTP contract, DTO, migration or frontend implementation. Castling execution, complete legal-move enumeration, outcomes, move history and gameplay integration remain pending. Generation handles a single source; an empty result cannot establish checkmate or stalemate. FEN parsing is structural, not proof of legality or reachability; the implementation does not reconstruct the preceding move from history. Board equality is identity-based, repetition requires history, and authorization, transactions, persistence and gameplay concurrency remain service responsibilities. AI and map-versus-array evaluation remain deferred.

## Day 14 implementation — historical, 27 October 2026

Standard castling is implemented for both colors and both sides. Candidates require the matching castling right, the king on e1/e8, a same-color rook on its original corner, and an empty intervening path. Inconsistent rights/placement omit the affected candidate while ordinary moves remain available. Rights are historical metadata; local placement checks do not reconstruct move history or distinguish a promoted rook from an original rook.

`MoveApplier` validates exact candidate membership, relocates the king and rook through immutable Board operations, and reuses shared metadata handling. Castling increments the halfmove clock once, increments the fullmove number after Black, switches turns once, clears the en passant target and permanently removes both rights for the moving king's color. Promotion choices on castling are rejected through candidate membership.

`LegalMoveGenerator` identifies castling separately from path safety. It checks the starting square on the original board, then the transit square with the king relocated and the rook still at its corner. The existing shared safety check examines the destination on the completed castling board. `MoveApplier` intentionally does not check attacks. Pinned pieces still count as attackers. Queenside b1/b8 must be empty but may be attacked; an attacked rook does not itself prohibit castling.

Source-confirmed coverage includes all four relocations and resulting FENs, ordinary king moves alongside castling, individual/absent rights, every intervening square, missing/wrong-color/non-rook corner pieces, displaced kings, attacks on starting/transit/destination squares, pinned-knight attacks, attacked rooks and b-file squares, en passant expiry, promotion-choice rejection and original-position preservation. `KingMoveGeneratorTest` replaces its obsolete castling-exclusion expectation. `CastlingTest` contains ten parameterized test methods; selected cases use `com.chessapp.BoardTestRenderer`. Existing movement, promotion and en passant test sources are unchanged.

Verification: the user reported green tests through the king-safety increment. Final additional boundary tests are present, with no subsequent explicit passing report. No independent tests or builds were run.

Movement remains internal pure Java code. `ChessRules` exposes only `initialPosition()`; `GameService` creates and stores the initial FEN and does not apply moves. Complete legal-move enumeration, outcomes, move history and gameplay integration remain pending. Single-source generation cannot establish checkmate or stalemate. FEN parsing remains structural, Board equality remains identity-based, and repetition requires history. Authorization, transactions, persistence and gameplay concurrency remain service responsibilities. AI and map-versus-array evaluation remain deferred.

## Day 15 completed checkpoint — 27 October 2026

Reviewed on 27 October 2026 against `chess-web-app-day15-end.zip`. Source inspection confirms position-wide legal-move enumeration and `NORMAL` / `CHECK` / `CHECKMATE` / `STALEMATE` assessment for the side to move. No blocking defect was found in the reviewed Day 15 changes. The user reports all tests green after the final guided regressions. No independent tests or builds were run; no detailed test log or separate frontend build result was supplied.

Position-wide enumeration reuses the existing single-source filtering body. Position assessment examines the current king and the complete reply set; the persisted game lifecycle is unchanged. See the architecture and development-progress documents for responsibilities and inspected coverage.
