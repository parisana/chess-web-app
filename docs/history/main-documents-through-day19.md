# Main-document checkpoints through Day 19

Archived on 30 October 2026 from the documentation included in the user’s `chess-web-app-day20-end.zip`. These documents contain historical checkpoint descriptions and superseded limitations. They are preserved for provenance; current behavior is described by the main documents and Day 20 review. No historical pass report is upgraded to independent verification.

Relative file links are adjusted for this archive location. Original heading text is preserved with deeper heading levels.

---

## Original file: README.md

### Chess Web App

#### Day 19 current checkpoint — source-reviewed, 29 October 2026

Reviewed the actual user archive `chess-web-app-day19-end.zip` against `chess-web-app-day18-end.zip`. No blocking defect was found in the reviewed Day 19 changes. Two small follow-ups are supplied inline and recorded in [the review](../day19-frontend-gameplay.md): add a submission guard to Game.refresh() and clear obsolete move feedback after a successful GET. They are not applied to production source by this documentation package.

The Angular frontend now supports guarded matched-game navigation, participant-protected game reads, a custom Player-oriented click-to-move board, explicit promotion including cancellation, versioned move submission, manual refresh, stale-state reload without command retry, uncertain-outcome blocking and completion feedback. Game and ChessBoard use separate TypeScript, HTML and CSS files.

The backend tree is byte-for-byte unchanged from the Day 18 archive. No new endpoint, migration or backend runtime configuration is introduced. The cm-chessboard dependency, declarations and asset/style additions are absent. package.json is unchanged; package-lock.json contains 15 package-version changes, and angular.json differs only in formatting. Those user changes are preserved.

Tests are treated as green under the standing instruction to report failures. The assistant ran no application tests, builds, migrations or browser acceptance checks, and claims no separately verified frontend build/manual result. Source inspection is the evidence for this review.

See [frontend gameplay flow](../frontend-gameplay-flow.md) for the lifecycle and Angular coordination, [Day 19 review](../day19-frontend-gameplay.md) for findings and [next-session handoff](../day19-handoff.md) for continuation. Earlier Day sections retain historical boundaries and do not override this checkpoint.

History, real-time delivery, clocks, additional draw rules, acceptance and disconnect/abandonment policy remain separate scope decisions. A board library is optional post-MVP polish, not an MVP requirement.

#### Day 17 checkpoint — historical source review, 28 October 2026

Reviewed the actual user implementation in `chess-web-app-day17-end(1).zip` against `chess-web-app-day16-end.zip`. No blocking defect was found in the reviewed Day 17 production/test changes. Tests are treated as green under the user's standing instruction to report failures; this is user-provided verification context, not an independent run or a detailed per-command log. No tests, builds or migrations were run by the assistant, and no separate frontend build result is claimed.

`GameService.submitMove(gameId, requestingPlayerId, expectedVersion, move)` now coordinates participant/turn checks, exact legal application, resulting-position assessment, current-FEN persistence, stale/concurrent-command rejection and atomic checkmate/stalemate completion with reservation release. `GameMoveResult.from(game, assessment)` owns mechanical result mapping; `Game.finish()` calls its extracted switch-based `validateResult()`. There is no separate production GameMoveService.

V6 adds completion fields and consistency constraints. GameStatus is IN_PROGRESS/FINISHED, with separate result, termination reason and finish time. The unchanged game GET response fields can now carry an advanced FEN and FINISHED status; version/result/reason/time remain absent from that HTTP response. No HTTP move route, move history, frontend gameplay or disconnect policy is added. See the [reviewed implementation and verification commands](../day17-gameplay-service.md).

Day 16 and earlier milestone sections below retain historical behavior/evidence. Their initial-only and pending-gameplay descriptions do not override this checkpoint. The earlier closing-test passing-status uncertainty is retained as historical provenance; current test status follows the user's later standing instruction.

##### Historical Day 16 review provenance

Reviewed on 28 October 2026 against `chess-web-app-day16-end.zip`. Source inspection confirms the legality-enforcing facade, complete production strategy registration, facade regression tests and both [closing checks](../day16-closing-checks.md). No blocking defect was found in the reviewed Day 16 changes. The user reports all tests green through increments 1–2. Both later closing tests are present, but no subsequent explicit passing report was supplied for them. No tests or builds were run independently; no detailed test log or separate frontend build result was supplied.

The Day 16 section describes the inspected implementation and supersedes earlier initial-position-only boundary and test-only-construction statements. Historical sections retain the behavior and evidence available at those checkpoints.

#### Day 16 boundary — historical

`ChessRules` exposes `initialPosition()`, `legalMoves(position)`, `applyMove(position, move)` and `evaluate(position)`. Lookup returns the complete immutable legal set. Application requires exact membership in that set, including the promotion choice, before delegating to `MoveApplier`. Assessment returns `PositionStatus` for the side to move. The existing pipeline remains shared; no per-piece application services or duplicate movement algorithms are introduced.

`GameService` still calls only `initialPosition()` and serializes it for creation. Games remain at their initial FEN in `IN_PROGRESS`. Move submission, persistence/history, gameplay concurrency, terminal lifecycle and active-reservation release remain pending. No endpoint, DTO, migration or frontend implementation changes in Day 16. Authorization, transactions, persistence and concurrency remain service responsibilities.

The rules facade is assembled in `game.config.GameRulesConfig`; rules code has no Spring/JPA/HTTP dependencies. See the architecture document for construction and the development-progress document for verification provenance.

A portfolio project built as a modular monolith: package-by-feature with conventional layers, keeping `Player` terminology. Cross-feature interactions use services. This project implements its own pure Java chess rules; AI is outside the MVP.

Historical Day 15 source-review provenance: reviewed on 27 October 2026 against `chess-web-app-day15-end.zip`. Source inspection confirms position-wide legal-move enumeration and `NORMAL` / `CHECK` / `CHECKMATE` / `STALEMATE` assessment for the side to move. No blocking defect was found in the reviewed Day 15 changes. The user reports all tests green after the final guided regressions. No independent tests or builds were run; no detailed test log or separate frontend build result was supplied.

#### Stack

- Backend: Java 21, Spring Boot 4.1.1, Gradle wrapper 9.8.1, PostgreSQL, JPA, Flyway, Spring Security/JWT, Lombok and YAML.
- Frontend: Angular 21, Angular Material, standalone components, reactive forms and in-memory authentication.

#### Current capabilities

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

#### Run locally

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

#### Local verification

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

#### Current limits

Gameplay uses manual refresh for match discovery and opponent moves. There is no move-history listing, real-time delivery or chess clock. The frontend displays the server-accepted FEN and does not duplicate move legality or retain a check indicator from POST after GET.

Checkmate/stalemate finish games and release reservations transactionally. Disconnecting, closing the page or leaving the queue does not finish a game; timeout/abandonment policy remains deferred.

Repetition, move-count draws, dead-position detection, resignation and agreement are outside assessment. FEN is structural state, not repetition history. AI and map-versus-array evaluation remain deferred.

Pairing runs on join. Sessions remain in memory, so browser reload requires login; Home can rediscover an active game afterward. Logout does not leave the queue. Secrets and runtime configuration remain user-managed.

#### Documentation

- [Frontend gameplay lifecycle and Angular flow](../frontend-gameplay-flow.md)
- [Day 19 source review](../day19-frontend-gameplay.md)
- [After Day 19 handoff](../day19-handoff.md)

- [Architecture](../architecture.md)
- [Development progress and verification](../development-progress.md)
- [Roadmap](../roadmap.md)
- [API contracts](../api-contracts.md)
- [Frontend setup and behavior](../../frontend/README.md)
- [Movement pipeline walkthrough](../movement-pipeline-architecture.md)
- [Day 17 implementation and review](../day17-gameplay-service.md)
- [After Day 17 handoff](../day18-handoff.md)
- [After Day 16 handoff — historical](../day17-handoff.md)
- [Day 16 closing checks](../day16-closing-checks.md)
- [Post-Day-16 source review and proposed integration scope](../post-day16-integration-proposal.md) — historical proposal; its larger service option was selected and implemented.

#### Day 13 completed checkpoint — historical, 27 October 2026

En passant is implemented for both colors. Generation requires the correct source rank, one forward diagonal step, matching target metadata, an empty destination and an adjacent opposing pawn. An inconsistent or unavailable opportunity contributes no en passant candidate and does not suppress ordinary moves. Attempting an omitted capture fails exact candidate membership with `IllegalArgumentException`; no unsupported-en-passant exception remains.

`MoveApplier` validates candidate membership before identifying a pawn's diagonal move into an empty square as en passant. It relocates the pawn and removes the opposing pawn from `(destination.file, source.rank)` through immutable Board operations. `Board.removePiece()` requires a nonnull occupied square and performs placement only; callers own chess-rule validation. The original position remains unchanged.

Shared handling switches turns, resets the pawn halfmove clock, increments the fullmove number after Black, and expires the target. En passant preserves existing castling rights. Ordinary moves, promotion choices and irreversible king/rook rights removal remain supported. An occupied diagonal follows ordinary capture rules even if inconsistent target metadata names it; no adjacent pawn is removed in that case. Promotion choices attached to en passant fail candidate membership.

`LegalMoveGenerator` remains shared and unchanged. It checks the moving side's king after both pawn squares have been vacated. Tests cover both colors, both capture directions in generation, FEN results, original-position preservation, missing/inconsistent metadata, absent/friendly/non-pawn adjacent pieces, wrong source ranks, occupied targets, opportunity expiry, ordinary advances instead of capture, castling-right preservation, invalid promotion choices, exposed rook/bishop attacks and removal of checking pawns. Board tests cover immutable removal and invalid removal inputs. The three obsolete unsupported-case tests were replaced. Selected tests use `com.chessapp.BoardTestRenderer`.

The user reports all tests green for the final Day 13 archive. Review was by source inspection; no independent tests or builds were run.

Movement remains internal pure Java code. `ChessRules` exposes only `initialPosition()`; `GameService` still stores the initial FEN and does not apply moves. Day 13 changes no HTTP contract, DTO, migration or frontend implementation. Castling execution, complete legal-move enumeration, outcomes, move history and gameplay integration remain pending. Generation handles a single source; an empty result cannot establish checkmate or stalemate. FEN parsing is structural, not proof of legality or reachability; the implementation does not reconstruct the preceding move from history. Board equality is identity-based, repetition requires history, and authorization, transactions, persistence and gameplay concurrency remain service responsibilities. AI and map-versus-array evaluation remain deferred.

#### Day 14 implementation — historical, 27 October 2026

Standard castling is implemented for both colors and both sides. Candidates require the matching castling right, the king on e1/e8, a same-color rook on its original corner, and an empty intervening path. Inconsistent rights/placement omit the affected candidate while ordinary moves remain available. Rights are historical metadata; local placement checks do not reconstruct move history or distinguish a promoted rook from an original rook.

`MoveApplier` validates exact candidate membership, relocates the king and rook through immutable Board operations, and reuses shared metadata handling. Castling increments the halfmove clock once, increments the fullmove number after Black, switches turns once, clears the en passant target and permanently removes both rights for the moving king's color. Promotion choices on castling are rejected through candidate membership.

`LegalMoveGenerator` identifies castling separately from path safety. It checks the starting square on the original board, then the transit square with the king relocated and the rook still at its corner. The existing shared safety check examines the destination on the completed castling board. `MoveApplier` intentionally does not check attacks. Pinned pieces still count as attackers. Queenside b1/b8 must be empty but may be attacked; an attacked rook does not itself prohibit castling.

Source-confirmed coverage includes all four relocations and resulting FENs, ordinary king moves alongside castling, individual/absent rights, every intervening square, missing/wrong-color/non-rook corner pieces, displaced kings, attacks on starting/transit/destination squares, pinned-knight attacks, attacked rooks and b-file squares, en passant expiry, promotion-choice rejection and original-position preservation. `KingMoveGeneratorTest` replaces its obsolete castling-exclusion expectation. `CastlingTest` contains ten parameterized test methods; selected cases use `com.chessapp.BoardTestRenderer`. Existing movement, promotion and en passant test sources are unchanged.

Verification: the user reported green tests through the king-safety increment. Final additional boundary tests are present, with no subsequent explicit passing report. No independent tests or builds were run.

Movement remains internal pure Java code. `ChessRules` exposes only `initialPosition()`; `GameService` creates and stores the initial FEN and does not apply moves. Complete legal-move enumeration, outcomes, move history and gameplay integration remain pending. Single-source generation cannot establish checkmate or stalemate. FEN parsing remains structural, Board equality remains identity-based, and repetition requires history. Authorization, transactions, persistence and gameplay concurrency remain service responsibilities. AI and map-versus-array evaluation remain deferred.

#### Day 15 completed checkpoint — 27 October 2026

Reviewed on 27 October 2026 against `chess-web-app-day15-end.zip`. Source inspection confirms position-wide legal-move enumeration and `NORMAL` / `CHECK` / `CHECKMATE` / `STALEMATE` assessment for the side to move. No blocking defect was found in the reviewed Day 15 changes. The user reports all tests green after the final guided regressions. No independent tests or builds were run; no detailed test log or separate frontend build result was supplied.

Position-wide enumeration reuses the existing single-source filtering body. Position assessment examines the current king and the complete reply set; the persisted game lifecycle is unchanged. See the architecture and development-progress documents for responsibilities and inspected coverage.


---

## Original file: frontend/README.md

### Chess Frontend

#### Day 19 source-reviewed checkpoint — 29 October 2026

Angular 21 standalone application with Angular Material, signals, reactive forms and in-memory authentication. Reviewed against chess-web-app-day19-end.zip. The frontend implements a custom click-to-move board; no chessboard library dependency remains.

No blocking defect was found in the reviewed Day 19 changes. Two minor guard/message follow-ups are supplied inline and have not been silently applied. See [review findings](../day19-frontend-gameplay.md). Tests follow the standing user instruction; no assistant tests/builds or browser checks were run.

#### Run locally

From frontend/, using the declared npm 11.6.2 and a Node version accepted by the locked dependencies:

```bash
npm ci
npm start
```

Open http://localhost:4200. npm start enables proxy.conf.json, forwarding /api/** to the separately running backend on port 8080. Plain ng serve does not enable that proxy automatically.

package.json is unchanged from Day 18. The supplied lockfile includes 15 package-version changes, including Angular build/CLI tooling. angular.json has formatting changes only; runtime/secret configuration remains user-managed.

#### Authentication and profile

Login loads /api/v1/players/me before establishing the session. The token and Player are in memory; browser reload requires login again. Protected API requests receive the token through the existing interceptor. A protected 401 clears the matching session and redirects an established Player; 403 does not clear it.

Failed form submissions preserve passwords. Successful login clears the password; successful registration resets the form. Home supports username PATCH, validation/conflict feedback and immediate in-memory profile updates.

#### Matchmaking and navigation

Home loads status on entry and offers join, leave and manual refresh. MATCHED shows the game ID and an Open game link. Pairing happens on join; the waiting Player refreshes to discover it.

Repeated join can return the active game. Leaving does not cancel an established game; logout does not leave the queue. After login, Home can rediscover an active game.

#### Gameplay

The guarded games/:gameId page loads participant-protected GET /api/v1/games/{gameId}. GameApiService handles GET and POST transport; Game coordinates snapshots, requests, promotion and recovery.

ChessBoard reads FEN, orientation and enabled inputs and emits moveProposed. It renders Unicode pieces, square coordinates, an orientation for the current Player and a selection highlight. Game and ChessBoard have separate .ts/.html/.css files.

Click an own piece, then its destination. Click it again to deselect or another own piece to change source. The board clears selection before emitting. Promotion requires an explicit QUEEN/ROOK/BISHOP/KNIGHT choice; cancel sends no request and leaves no stale selection. Castling uses the king's source/destination; en passant uses the pawn's source/destination.

POST includes the displayed expectedVersion. Only server-accepted FEN updates the board. Illegal moves preserve the position; stale/turn/finished errors reload without command retry. Uncertain outcomes block proposals until a successful read. Completion feedback uses persisted result/reason/time and disables further moves.

Opponent moves require manual refresh. A GET load clears the current snapshot and recreates the board after success. GET has no positionStatus, so the page does not maintain a persistent check indicator.

#### Minimal verification

Optional commands from frontend/:

```bash
npm test -- --watch=false
npm run build
```

The five existing tests in src/app/auth/services/auth-flow.spec.ts are unchanged. Day 19 introduces no game-component/parser tests or end-to-end runner. No separate frontend build or manual acceptance result is claimed.

Suggested manual scenarios: two-session matching and opposite orientation; ordinary and illegal moves; promotion cancellation/underpromotion; stale-state reload; uncertain-outcome refresh; terminal feedback and another queue join.

#### Current limits and conventions

No drag-and-drop, legal-move highlighting, move-history interface, clocks or real-time subscription. Disconnecting does not finish a game. Additional draw rules, resignation, agreement, acceptance and abandonment policy remain separately scoped.

Keep backend legality authoritative and frontend testing minimal. New game/board components use separate templates/styles. Board-library integration is optional post-MVP polish.

#### Documentation

- [Gameplay flow and Angular coordination](../frontend-gameplay-flow.md)
- [Day 19 source review](../day19-frontend-gameplay.md)
- [API contracts](../api-contracts.md)
- [Architecture](../architecture.md)
- [Progress](../development-progress.md)
- [Roadmap](../roadmap.md)


---

## Original file: docs/architecture.md

### Architecture

#### Day 19 current checkpoint — source-reviewed, 29 October 2026

Reviewed the actual user archive `chess-web-app-day19-end.zip` against `chess-web-app-day18-end.zip`. No blocking defect was found in the reviewed Day 19 changes. Two small follow-ups are supplied inline and recorded in [the review](../day19-frontend-gameplay.md): add a submission guard to Game.refresh() and clear obsolete move feedback after a successful GET. They are not applied to production source by this documentation package.

The Angular frontend now supports guarded matched-game navigation, participant-protected game reads, a custom Player-oriented click-to-move board, explicit promotion including cancellation, versioned move submission, manual refresh, stale-state reload without command retry, uncertain-outcome blocking and completion feedback. Game and ChessBoard use separate TypeScript, HTML and CSS files.

The backend tree is byte-for-byte unchanged from the Day 18 archive. No new endpoint, migration or backend runtime configuration is introduced. The cm-chessboard dependency, declarations and asset/style additions are absent. package.json is unchanged; package-lock.json contains 15 package-version changes, and angular.json differs only in formatting. Those user changes are preserved.

Tests are treated as green under the standing instruction to report failures. The assistant ran no application tests, builds, migrations or browser acceptance checks, and claims no separately verified frontend build/manual result. Source inspection is the evidence for this review.

See [frontend gameplay flow](../frontend-gameplay-flow.md) for the lifecycle and Angular coordination, [Day 19 review](../day19-frontend-gameplay.md) for findings and [next-session handoff](../day19-handoff.md) for continuation. Earlier Day sections retain historical boundaries and do not override this checkpoint.

History, real-time delivery, clocks, additional draw rules, acceptance and disconnect/abandonment policy remain separate scope decisions. A board library is optional post-MVP polish, not an MVP requirement.

#### Day 18 checkpoint — historical HTTP gameplay

Authenticated POST /api/v1/games/{gameId}/moves now delegates to the existing
GameService command using JWT-derived Player identity. SubmitMoveRequest
validates request shape before conversion into Move. Specific gameplay
rejections have game-owned HTTP status/code mappings.

GameResponse.from(game) maps participant-protected reads, now including
version, result, terminationReason and finishedAt. Successful move commands
return the existing GameMoveResult.

The HTTP tests cover committed commands, POST/GET agreement, authentication,
validation, failure precedence, special moves and terminal results/release.
Tests are treated as green under the user's standing instruction; no
independent tests/builds or final Day 18 archive review are claimed.

No migration, runtime configuration, rules implementation or frontend
implementation changes are included. History, frontend gameplay, real-time
delivery, clocks and disconnect policy remain pending.

See [Day 18 HTTP gameplay](../day18-http-gameplay.md) for the contract and coverage.
Earlier checkpoint sections describe historical boundaries.

#### Day 17 checkpoint — historical source review, 28 October 2026

Reviewed the actual user implementation in `chess-web-app-day17-end(1).zip` against `chess-web-app-day16-end.zip`. No blocking defect was found in the reviewed Day 17 production/test changes. Tests are treated as green under the user's standing instruction to report failures; this is user-provided verification context, not an independent run or a detailed per-command log. No tests, builds or migrations were run by the assistant, and no separate frontend build result is claimed.

`GameService.submitMove(gameId, requestingPlayerId, expectedVersion, move)` now coordinates participant/turn checks, exact legal application, resulting-position assessment, current-FEN persistence, stale/concurrent-command rejection and atomic checkmate/stalemate completion with reservation release. `GameMoveResult.from(game, assessment)` owns mechanical result mapping; `Game.finish()` calls its extracted switch-based `validateResult()`. There is no separate production GameMoveService.

V6 adds completion fields and consistency constraints. GameStatus is IN_PROGRESS/FINISHED, with separate result, termination reason and finish time. The unchanged game GET response fields can now carry an advanced FEN and FINISHED status; version/result/reason/time remain absent from that HTTP response. No HTTP move route, move history, frontend gameplay or disconnect policy is added. See the [reviewed implementation and verification commands](../day17-gameplay-service.md).

Day 16 and earlier milestone sections below retain historical behavior/evidence. Their initial-only and pending-gameplay descriptions do not override this checkpoint. The earlier closing-test passing-status uncertainty is retained as historical provenance; current test status follows the user's later standing instruction.

##### Historical Day 16 review provenance

Reviewed on 28 October 2026 against `chess-web-app-day16-end.zip`. Source inspection confirms the legality-enforcing facade, complete production strategy registration, facade regression tests and both [closing checks](../day16-closing-checks.md). No blocking defect was found in the reviewed Day 16 changes. The user reports all tests green through increments 1–2. Both later closing tests are present, but no subsequent explicit passing report was supplied for them. No tests or builds were run independently; no detailed test log or separate frontend build result was supplied.

The Day 16 section describes the inspected implementation and supersedes earlier initial-position-only boundary and test-only-construction statements. Historical sections retain the behavior and evidence available at those checkpoints.

#### Day 16 rules facade — historical

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

#### Day 15 implementation — historical foundation

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

#### Style and ownership

A single Spring Boot application, organized as a modular monolith with feature packages and conventional controller/service/DTO/entity/repository layers. Not DDD; no microservices or multi-project Gradle modules are required. Keep the domain term `Player`.

| Package | Ownership | State |
| --- | --- | --- |
| `auth` | Registration, local credentials, login | Implemented |
| `player` | Identity and profile data | Creation, lookup, current profile read and username updates |
| `security` | HTTP authentication, password encoding, JWT machinery | Implemented |
| `common` | Shared timestamps and request validation error handling | Implemented |
| `matchmaking` | Persistent queue, eligibility, and transactional pairing | Implemented |
| `game` | Game creation/read/move use cases, active reservations, completion and pure Java rules | Service and HTTP gameplay, concurrency, checkmate/stalemate completion and custom Angular board implemented; history deferred |
| `notification` | Authorized outbound updates | Planned |

Controllers handle transport and delegate. Services coordinate use cases and transaction boundaries. Repositories remain within their owning feature. Cross-feature calls use service operations and DTOs. Concrete services are sufficient; interfaces and mapper frameworks are introduced only when needed.

Auth consumes `PlayerResponse` from `PlayerService`; it does not import player entities or repositories. `PlayerResponse` is also the `/players/me` HTTP contract. `RegisterResponse` deliberately retains the API field `playerId`, while `PlayerResponse` uses `id`.

#### Registration and persistence

`AuthController` validates a registration request. Transactional `AuthService.register()` creates a player through `PlayerService`, encodes the password, saves credentials, and returns a registration DTO. All database writes must succeed or roll back together. The current order creates the player before encoding; rollback tests intentionally fail that later step.

PostgreSQL is durable storage. Flyway V1 creates players; V2 creates local credentials. Hibernate uses `ddl-auto: validate`, and Open EntityManager in View is disabled. Applied migrations remain immutable; schema changes use new migrations.

`AuthCredential.playerId` is a scalar foreign key rather than a JPA relationship. Its uniqueness permits one local credential per player. The foreign key has no deletion cascade. Future account deletion and external login methods need explicit designs.

`BaseEntity` contains creation/update `Instant` timestamps managed by JPA callbacks. SQL defaults initialize timestamps for non-JPA inserts, but direct SQL updates do not automatically advance `updated_at`.

Username/email availability checks improve feedback. PostgreSQL constraints remain authoritative under concurrency. `PlayerExceptionHandler` translates the named username/email constraints to 409 and rethrows unknown integrity failures. It is global advice because player failures can originate from auth controllers; its schema knowledge remains owned by player.

Identity lookup currently compares exact stored values. No canonicalization or case-insensitive identity policy is implemented. Any future normalization change must coordinate registration, lookup, existing rows, and unique constraints.

#### Credential verification and login

`LoginController` validates input and delegates to `LoginService`. That service calls read-only transactional `AuthService.authenticate()`, then signs an access token after credential verification returns.

Authentication loads a player through the player service, retrieves local credentials, and calls `PasswordEncoder.matches`. Unknown accounts and missing local credentials use a dummy hash generated once at bean initialization. This reduces a timing difference but does not guarantee identical response timings.

Invalid credentials share one message and auth-owned error code. Login returns `accessToken`, `tokenType`, and `expiresIn` in seconds, with `Cache-Control: no-store`. The implemented login response does not contain the player profile.

Password encoding uses `DelegatingPasswordEncoder`, bcrypt by default. Registration enforces minimum length; registration and login enforce BCrypt's 72-byte UTF-8 maximum. No password trimming or normalization is performed. DTO validation is triggered at the HTTP boundary; calling services directly does not automatically validate DTO annotations.

#### JWT issuance and request authentication

`JwtTokenService` uses `JwtEncoder`, an injected clock, issuer, and TTL. JWTs use HS256 with at least 32 decoded key bytes. Claims are `sub` (player ID), `iss`, `iat`, and `exp`. Tokens carry no profile fields.

`JwtConfig` provides encoder/decoder beans and requires a configured key. The decoder trusts HS256 and validates issuer, required expiration, expiration and optional not-before validity with 60 seconds of skew, and a subject parseable as a positive Java Long. Timestamp validation and issuance use the injected clock.

`SecurityConfig` permits POST registration/login, protects other requests, uses stateless sessions, and delegates bearer processing to Spring Security's resource server support. No custom JWT filter is needed. CSRF is disabled for the current bearer-header API design; revisit this if cookie-based authentication is introduced.

`GET /api/v1/players/me` belongs to `PlayerController` because it returns a current database-backed profile. It extracts the validated subject and returns `PlayerResponse`, or 401 if the player no longer exists. The controller currently uses `Objects.requireNonNull` as an assertion of decoder validation; controller-only tests must supply a valid principal.

Deleting a player does not cryptographically revoke its JWT. `/players/me` detects deletion through its lookup; future endpoints must apply their own resource and account checks. Refresh tokens, revocation, logout endpoints, account-status checks, rate limiting, and key rotation machinery are not implemented.

#### Current-player username updates

`PATCH /api/v1/players/me` belongs to `PlayerController`. It validates `UpdatePlayerRequest` and derives player identity from the JWT subject. The body contains only the username; no client-supplied player ID chooses the update target. Email/password changes are outside this use case.

Transactional `PlayerService.updateUsername()` returns `Optional<PlayerResponse>`. A missing player returns empty; the controller translates that result into 401 using `ResponseStatusException`, consistently with the current profile-read endpoint. HTTP concerns remain at the controller boundary.

Submitting the current username returns without mutation. Otherwise, the service checks username availability, calls `Player.changeUsername()` on the managed entity and flushes through the repository. JPA dirty checking writes the update; an additional `save()` is unnecessary. The inherited `@PreUpdate` callback updates `updatedAt` for a changed entity. Flushing executes SQL without committing.

The availability check improves feedback but cannot guarantee uniqueness under concurrency. PostgreSQL's existing `uk_players_username` constraint remains authoritative, and `PlayerExceptionHandler` maps its violation to `USERNAME_ALREADY_EXISTS`. Concurrent edits to the same player's username currently use last-write-wins behavior; `Player` has no optimistic-lock version. The `Game` version field does not protect player edits.

Username validation remains nonblank and 3–30 Java UTF-16 string units, matching registration. There is no trimming, case normalization or special-character restriction. Existing IDs, credentials, game references and JWT subjects remain unchanged. No migration is introduced.

`ProfileEditor` is a standalone component under `player/components/profile-editor`, embedded in authenticated Home. `PlayerApiService` handles PATCH transport; the component coordinates form feedback and updates `AuthSessionStore` from the returned profile. It ignores successful responses if the token or current player has changed. Destruction cancels its subscription. Server validation remains authoritative, errors preserve input, and successful saves reset the form to the returned username. The welcome message observes the same store through `AuthService`.

The Day 5 integration-test increment covers persistence, same-username no-op behavior, duplicate rejection, validation, JWT-derived ownership, missing authentication, deleted-player access and reuse of the existing JWT after renaming. Transactional HTTP tests flush/clear before persistence assertions and roll back fixtures; they do not establish real HTTP commit behavior or reproduce a concurrent rename race.

#### Error ownership

- `ApiExceptionHandler`: shared MVC handling and field-validation Problem Details.
- `PlayerExceptionHandler`: player duplicates and known database constraints.
- `AuthExceptionHandler`: invalid local credentials.
- `MatchmakingExceptionHandler`: unavailable authenticated players.
- `GameExceptionHandler`: active-game conflicts, missing games, and participant access denial.
- Spring Security filter chain: missing/invalid bearer credentials.

Feature-owned error constants avoid a global catalog of every feature's codes. Field errors omit rejected values to avoid exposing passwords. Filter-chain errors do not currently share the MVC Problem Detail format. HTTP status consistency is implemented; a universal error-body contract is not. [api-contracts.md](../api-contracts.md) lists the expected status/code combinations by action, including failures without a guaranteed application code.

#### Tests and transaction semantics

Mockito tests cover registration, authentication, and duplicate prechecks. Standalone MockMvc covers request validation and selected exception mapping. PostgreSQL tests exercise persisted registration, actual unique constraints, and service rollback. Full-context MockMvc tests exercise registration/login/profile access and actual JWT decoding.

JWT tests include missing/malformed token, valid token with a real player, altered signature, expiration, wrong issuer, missing/invalid subject including numeric overflow, missing expiration, and deleted-player lookup.

Transactional tests normally roll back fixtures. Synchronous MockMvc shares the test thread; a real HTTP server would not share that transaction. The rollback-specific integration test has no enclosing test transaction and uses a mocked encoder to fail after player creation. It proves rollback at that failure point, not every possible persistence failure.

Expected PostgreSQL constraint failures can appear in test logs. Do not continue querying within a failed transaction. Identity sequences need not rewind after rollback. Existing data from earlier committed tests is not removed by adding transactional annotations.

#### Frontend authentication

Angular 21 uses standalone components, reactive forms, signals, lazy routes, and Angular Material. `AuthApiService` handles auth HTTP calls; `PlayerApiService` handles profile HTTP calls. `AuthService` orchestrates login; `AuthSessionStore` owns in-memory token/player state. The interceptor injects the store rather than `AuthService`, avoiding an HTTP/service dependency cycle.

The login sequence is token issuance → store token → GET `/api/v1/players/me` → store player. `isAuthenticated` requires both token and player. Failure clears the state. The `authGuard` redirects unauthenticated users from `/home` to `/login`; server authentication remains the enforcement boundary.

The interceptor attaches tokens only to relative `/api/` URLs and excludes public login/register. On a protected-request 401, it clears state only if the current token matches the request's token. An established player session is redirected with `sessionExpired=true`; an initial profile failure stays on the login form. External requests and 403 responses bypass session invalidation.

Token/player state is not persisted, so reload requires another login. There is no expiry timer or token refresh; an idle page may retain displayed state until a protected request receives 401. Client logout clears memory; the JWT itself remains valid until expiry. Cancellation of the login subscription after token issuance can leave a token without a player; authentication still evaluates false. Revisit cleanup if this causes a concrete navigation problem.

Both forms prevent duplicate submissions, show progress/errors, and retain passwords after failure. Successful login clears its password; successful registration resets the form and redirects to login. Registration displays backend duplicate/field-validation errors. Material theming is configured in `material-theme.scss`; home uses Material cards, buttons, and progress indicators for queue controls and matched-game discovery.

The five focused HTTP/store tests cover bearer attachment, external URL exclusion, protected 401 clearing/redirect, profile-load failure cleanup, and logout. They do not test forms, the guard, successful login completion, public-auth token exclusion with an existing token, stale-token responses, or password retention. These are coverage limits, not requests for a broad frontend suite. There is no generated `app.spec.ts` in this checkpoint; the five authentication tests are the current frontend suite.

#### Configuration

Shared configuration lives in `application.yaml`; test overrides use `application-test.yml`. Both are YAML. An ignored `application-local.yml`, if used, requires the `local` profile. Shared database defaults permit optional environment overrides. Secret configuration is user-managed and outside this increment.

Flyway migration-name validation is enabled only in the test profile; enabling it in shared configuration is an optional small follow-up.

#### Matchmaking queue

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

#### Game creation and transactional pairing

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

#### Day 6 rules foundation — historical

The source contains pure Java `game.rules` and `game.rules.model` code: `PieceColor`, `PieceType`, immutable `Piece` and `Square` records, an immutable `Board`, `CastlingRights`, and `ChessPosition`. Coordinates run from a1=(0,0) to h8=(7,7). Board owns placement and defensively copies its map with `Map.copyOf`; Piece holds color and type only.

`ChessPosition` carries board, side to move, castling rights, optional en passant target, halfmove clock and fullmove number. `FenParser` validates FEN structure and representation constraints, not full legality or reachability. Separate `FenSerializer` emits canonical FEN.

`ChessRules` currently exposes only `initialPosition()`. `StandardChessRules` uses the parser and a private starting-FEN constant. `GameRulesConfig` wires the beans. `GameService` obtains the initial position through `ChessRules`, serializes it and passes nonblank initial FEN to `Game`; the entity no longer owns the starting-position constant. Rules/model code has no Spring, JPA, HTTP or persistence dependencies.

Focused tests cover square conversion, board immutability, position metadata, FEN parsing/serialization/round trips and standard initial placement. Game creation/read integration assertions check the expected initial FEN. Test presence is a source finding; passing results are user-reported.

Day 6 changes no migration, endpoint or frontend behavior. That historical Day 6 checkpoint had no movement pipeline. Day 7 adds the knight-only pipeline described below; special-move execution, outcomes and move history remain absent.

#### Day 7 movement pipeline — historical checkpoint

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

#### Rules package organization

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

#### Rules ownership and extension boundary

`GameService` consumes the application-owned `ChessRules` interface. Define additional operations from actual use cases and keep input/output types application-owned. A future library adapter can implement the interface and translate library types internally; no placeholder adapter exists now. This is dependency inversion through an application-owned abstraction, with dependency injection at the Spring boundary.

Transactions, persistence, participant authorization and gameplay concurrency remain in services. AI move selection is outside the MVP. Repetition detection requires history beyond a single FEN.

`Board` currently has identity equality. Consequently, `ChessPosition` record equality does not establish board-content equality. Tests compare square contents where needed.

Deferred TODO: evaluate a 64-entry array against map storage after the MVP, or earlier if profiling or concrete implementation benefits justify it. Preserve the board API.

#### Planned application growth

Spring application events may decouple appropriate internal reactions, but are not durable messaging. WebSocket/STOMP remains a candidate for later real-time delivery, not an implemented dependency. It will need authenticated connections and authorized subscriptions/commands. Redis and Kafka are outside the initial infrastructure. Spectators, tournaments, chat, engine analysis, and distributed scaling remain out of initial scope.

#### Current gameplay boundary

GameService owns creation, authorized reads and transactional move submission. It validates membership/lifecycle/expected version/turn, establishes exact legal membership, applies through ChessRules, evaluates the returned position and changes the managed entity. GameMoveResult.from performs mechanical mapping. This organization keeps conventional feature layers without a separate production move service.

JPA @Version is flushed before any terminal release. Concurrent losers become StaleGameVersionException with the original cause; commands are not retried automatically. Game.finish uses its extracted validateResult switch. Completion stores result/reason/time and deletes exactly two active reservations by game ID in the same transaction. Release failures or later enclosing failures roll back both JPA and JDBC writes. V6 enforces supported in-progress/completed combinations. See day17-gameplay-service.md for failure precedence, precise test locations and evidence limits.

The Angular frontend discovers gameId through queue status and exposes an Open game link to guarded games/:gameId. GameApiService owns GET/POST transport. Game owns snapshots, pending promotion, submission and recovery; ChessBoard owns display projection, orientation and square selection. Inputs carry FEN/orientation/enabled downward, and moveProposed carries coordinates upward. The board clears selection before emitting, so promotion cancellation cannot retain the old source. Templates/styles are separate files.

Accepted POST results replace position/version/completion fields while retaining participant metadata. Manual reads clear and recreate the board; stale reads use switchMap, and POST handlers ignore replaced snapshot references. Uncertain move outcomes block further proposals until a successful read. No commands are replayed automatically. Spring application events and WebSocket/STOMP remain planned; no notification feature is implemented. See frontend-gameplay-flow.md for the full trace.

Queue operations acquire the queue advisory lock before queue access, including read-only status requests. This is a deliberate small-system serialization tradeoff. The lock is cooperative; database constraints remain necessary for game-creation callers outside matchmaking.

#### Day 8 rook movement and Strategy dispatch — historical

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

#### Day 9 completed checkpoint — historical

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

#### Day 10 completed checkpoint — historical

- `KingMoveGenerator` implements `PiecePseudoLegalMoveGenerator` for ordinary one-square movement in all eight directions. It clips board edges, excludes friendly destinations and opposing-king captures, respects side to move and returns an immutable set. Castling is excluded even when rights exist.
- `LegalMoveGenerator` still requires exactly one king per color in the input. After applying each candidate, it locates the moving side's king on the resulting board and checks that square against the opponent's attacks. This handles king relocation, captures and attacks exposed by vacating the source.
- `MoveApplier` removes both castling rights for a moving king's color, preserves the opponent's rights except those affected by a captured corner rook, and never restores lost rights. Quiet-move/capture counters, turn switching, en passant expiry and original-position preservation remain shared.
- `MovementTestFixtures` registers knight, rook, bishop, queen and king strategies. Dispatch tests include kings; unsupported own pawns still throw rather than silently returning no moves.
- Tests cover center/corner king candidates, blockers/captures, source validation, explicit invalid adjacent-king defensive input, castling exclusion, check escapes, adjacent kings, protected and unprotected captures, pinned-knight attacks, White/Black pawn and knight attacks, attacks exposed by vacating either king's source, rights removal for both colors, returning kings and corner-rook capture metadata. Existing knight/rook/bishop/queen tests remain in the suite. Selected tests use `com.chessapp.BoardTestRenderer`.

The user reports all tests green for this final archive. Review was by source inspection; no independent tests or builds were run. The cosmetic MoveApplier Javadoc cleanup is present in the inspected Day 10 starting archive.

Current boundary: the movement pipeline supports five piece types, including ordinary king moves. Pawn movement, promotion, en passant execution, castling execution, complete legal-move enumeration and outcomes remain pending. Supported-piece subsets cannot establish checkmate or stalemate. Attack detection recognizes all six types and counts pinned pieces as attackers. Candidate generation excludes king captures; attack detection still recognizes attacks on king squares.

`ChessRules` still exposes only `initialPosition()`. `GameService` creates initial FEN through that boundary and `FenSerializer`; movement is not connected to services or HTTP. No endpoint, migration, frontend behavior, move history or persisted-game behavior changes were introduced in Day 10. Transactions, authorization, persistence and gameplay concurrency remain service responsibilities. FEN validation is structural, Board uses identity equality, repetition needs history, AI is outside the MVP, and map-versus-array evaluation remains deferred.

#### Day 11 completed checkpoint — historical, 26 October 2026

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

#### Day 12 completed checkpoint — historical, 27 October 2026

Promotion is implemented for both colors, for quiet advances and captures, with explicit queen, rook, bishop and knight choices. Ordinary moves retain the two-argument constructor. No choice defaults to queen.

`Move` validates replacement-type shape. Position-dependent validity is established by exact pseudo-legal candidate membership, including the choice. `MoveApplier` preserves the pawn's color and applies the replacement through an immutable Board operation; `LegalMoveGenerator` checks the moving side's king on the resulting board.

Pawn halfmove resets, Black fullmove increments, turn changes, en passant expiry and captured-corner-rook castling-right removal remain shared. A promoted corner rook never grants castling rights. The test renderer lists promotion choices distinctly.

The user reports all tests green for the final Day 12 archive. Review was by source inspection; no independent tests or builds were run.

Movement remains internal pure Java code. `ChessRules` exposes only `initialPosition()`; `GameService` still stores the initial FEN and does not apply moves. Day 12 changes no HTTP contract, DTO, migration or frontend implementation. En passant capture execution, castling execution, complete legal-move enumeration, outcomes, history and gameplay integration remain pending. A source-specific result cannot establish checkmate or stalemate.

##### Promotion responsibilities and failure behavior

- `Move(from, to, Optional<PieceType> promotion)` requires nonnull, distinct squares and a nonnull Optional. Only queen, rook, bishop and knight are accepted as replacement choices. The two-argument constructor uses `Optional.empty()`. Record equality includes the choice.
- The move value does not inspect a board or prove legality. A missing choice on an available promotion, or a choice attached to an ordinary move, fails `MoveApplier` membership validation with `IllegalArgumentException` when generation completes normally.
- `PawnMoveGenerator` expands each available terminal-rank advance/capture into four immutable candidates. Blockers, friendly destinations and king-capture exclusions remain enforced before expansion. Moving-side pawns already on rank 1 or 8 remain invalid.
- `Board.movePiece(from, to, destinationPiece)` copies placement, removes the source and replaces the destination. It requires an occupied source, distinct squares and nonnull inputs. It intentionally does not enforce piece color, pawn movement, promotion ranks or king safety; callers own those rules.
- `MoveApplier` uses the original pawn for metadata and the chosen replacement for destination placement. Castling-right removal inspects the original position, including any captured rook. Rights are never added.
- `LegalMoveGenerator` remains shared and unchanged. It requires one king per color and checks the moving side's king against attacks after each candidate is applied. Candidate generation excludes king captures; attack detection includes king-square attacks and pinned attackers.
- An empty forward diagonal matching the en passant target still throws `UnsupportedOperationException` for the entire source, even if ordinary candidates exist. This is a conservative metadata guard, not captured-pawn validation or proof of legal en passant. Application and filtering propagate it.

FEN parsing remains structural, not proof of legality/reachability. Board equality remains identity-based. Repetition requires history. Authorization, transactions, persistence and gameplay concurrency remain service responsibilities. AI and map-versus-array evaluation remain deferred.

#### Day 13 completed checkpoint — historical, 27 October 2026

En passant is implemented for both colors. Generation requires the correct source rank, one forward diagonal step, matching target metadata, an empty destination and an adjacent opposing pawn. An inconsistent or unavailable opportunity contributes no en passant candidate and does not suppress ordinary moves. Attempting an omitted capture fails exact candidate membership with `IllegalArgumentException`; no unsupported-en-passant exception remains.

`MoveApplier` validates candidate membership before identifying a pawn's diagonal move into an empty square as en passant. It relocates the pawn and removes the opposing pawn from `(destination.file, source.rank)` through immutable Board operations. `Board.removePiece()` requires a nonnull occupied square and performs placement only; callers own chess-rule validation. The original position remains unchanged.

Shared handling switches turns, resets the pawn halfmove clock, increments the fullmove number after Black, and expires the target. En passant preserves existing castling rights. Ordinary moves, promotion choices and irreversible king/rook rights removal remain supported. An occupied diagonal follows ordinary capture rules even if inconsistent target metadata names it; no adjacent pawn is removed in that case. Promotion choices attached to en passant fail candidate membership.

`LegalMoveGenerator` remains shared and unchanged. It checks the moving side's king after both pawn squares have been vacated. Tests cover both colors, both capture directions in generation, FEN results, original-position preservation, missing/inconsistent metadata, absent/friendly/non-pawn adjacent pieces, wrong source ranks, occupied targets, opportunity expiry, ordinary advances instead of capture, castling-right preservation, invalid promotion choices, exposed rook/bishop attacks and removal of checking pawns. Board tests cover immutable removal and invalid removal inputs. The three obsolete unsupported-case tests were replaced. Selected tests use `com.chessapp.BoardTestRenderer`.

The user reports all tests green for the final Day 13 archive. Review was by source inspection; no independent tests or builds were run.

Movement remains internal pure Java code. `ChessRules` exposes only `initialPosition()`; `GameService` still stores the initial FEN and does not apply moves. Day 13 changes no HTTP contract, DTO, migration or frontend implementation. Castling execution, complete legal-move enumeration, outcomes, move history and gameplay integration remain pending. Generation handles a single source; an empty result cannot establish checkmate or stalemate. FEN parsing is structural, not proof of legality or reachability; the implementation does not reconstruct the preceding move from history. Board equality is identity-based, repetition requires history, and authorization, transactions, persistence and gameplay concurrency remain service responsibilities. AI and map-versus-array evaluation remain deferred.

##### En passant representation and application

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

#### Day 14 implementation — historical, 27 October 2026

Standard castling is implemented for both colors and both sides. Candidates require the matching castling right, the king on e1/e8, a same-color rook on its original corner, and an empty intervening path. Inconsistent rights/placement omit the affected candidate while ordinary moves remain available. Rights are historical metadata; local placement checks do not reconstruct move history or distinguish a promoted rook from an original rook.

`MoveApplier` validates exact candidate membership, relocates the king and rook through immutable Board operations, and reuses shared metadata handling. Castling increments the halfmove clock once, increments the fullmove number after Black, switches turns once, clears the en passant target and permanently removes both rights for the moving king's color. Promotion choices on castling are rejected through candidate membership.

`LegalMoveGenerator` identifies castling separately from path safety. It checks the starting square on the original board, then the transit square with the king relocated and the rook still at its corner. The existing shared safety check examines the destination on the completed castling board. `MoveApplier` intentionally does not check attacks. Pinned pieces still count as attackers. Queenside b1/b8 must be empty but may be attacked; an attacked rook does not itself prohibit castling.

Source-confirmed coverage includes all four relocations and resulting FENs, ordinary king moves alongside castling, individual/absent rights, every intervening square, missing/wrong-color/non-rook corner pieces, displaced kings, attacks on starting/transit/destination squares, pinned-knight attacks, attacked rooks and b-file squares, en passant expiry, promotion-choice rejection and original-position preservation. `KingMoveGeneratorTest` replaces its obsolete castling-exclusion expectation. `CastlingTest` contains ten parameterized test methods; selected cases use `com.chessapp.BoardTestRenderer`. Existing movement, promotion and en passant test sources are unchanged.

Verification: the user reported green tests through the king-safety increment. Final additional boundary tests are present, with no subsequent explicit passing report. No independent tests or builds were run.

Movement remains internal pure Java code. `ChessRules` exposes only `initialPosition()`; `GameService` creates and stores the initial FEN and does not apply moves. Complete legal-move enumeration, outcomes, move history and gameplay integration remain pending. Single-source generation cannot establish checkmate or stalemate. FEN parsing remains structural, Board equality remains identity-based, and repetition requires history. Authorization, transactions, persistence and gameplay concurrency remain service responsibilities. AI and map-versus-array evaluation remain deferred.


---

## Original file: docs/development-progress.md

### Development Progress

#### Day 19 current checkpoint — source-reviewed, 29 October 2026

Reviewed the actual user archive `chess-web-app-day19-end.zip` against `chess-web-app-day18-end.zip`. No blocking defect was found in the reviewed Day 19 changes. Two small follow-ups are supplied inline and recorded in [the review](../day19-frontend-gameplay.md): add a submission guard to Game.refresh() and clear obsolete move feedback after a successful GET. They are not applied to production source by this documentation package.

The Angular frontend now supports guarded matched-game navigation, participant-protected game reads, a custom Player-oriented click-to-move board, explicit promotion including cancellation, versioned move submission, manual refresh, stale-state reload without command retry, uncertain-outcome blocking and completion feedback. Game and ChessBoard use separate TypeScript, HTML and CSS files.

The backend tree is byte-for-byte unchanged from the Day 18 archive. No new endpoint, migration or backend runtime configuration is introduced. The cm-chessboard dependency, declarations and asset/style additions are absent. package.json is unchanged; package-lock.json contains 15 package-version changes, and angular.json differs only in formatting. Those user changes are preserved.

Tests are treated as green under the standing instruction to report failures. The assistant ran no application tests, builds, migrations or browser acceptance checks, and claims no separately verified frontend build/manual result. Source inspection is the evidence for this review.

See [frontend gameplay flow](../frontend-gameplay-flow.md) for the lifecycle and Angular coordination, [Day 19 review](../day19-frontend-gameplay.md) for findings and [next-session handoff](../day19-handoff.md) for continuation. Earlier Day sections retain historical boundaries and do not override this checkpoint.

History, real-time delivery, clocks, additional draw rules, acceptance and disconnect/abandonment policy remain separate scope decisions. A board library is optional post-MVP polish, not an MVP requirement.

#### Day 18 checkpoint — historical HTTP gameplay

Authenticated POST /api/v1/games/{gameId}/moves now delegates to the existing
GameService command using JWT-derived Player identity. SubmitMoveRequest
validates request shape before conversion into Move. Specific gameplay
rejections have game-owned HTTP status/code mappings.

GameResponse.from(game) maps participant-protected reads, now including
version, result, terminationReason and finishedAt. Successful move commands
return the existing GameMoveResult.

The HTTP tests cover committed commands, POST/GET agreement, authentication,
validation, failure precedence, special moves and terminal results/release.
Tests are treated as green under the user's standing instruction; no
independent tests/builds or final Day 18 archive review are claimed.

No migration, runtime configuration, rules implementation or frontend
implementation changes are included. History, frontend gameplay, real-time
delivery, clocks and disconnect policy remain pending.

See [Day 18 HTTP gameplay](../day18-http-gameplay.md) for the contract and coverage.
Earlier checkpoint sections describe historical boundaries.

#### Day 17 checkpoint — historical source review, 28 October 2026

Reviewed the actual user implementation in `chess-web-app-day17-end(1).zip` against `chess-web-app-day16-end.zip`. No blocking defect was found in the reviewed Day 17 production/test changes. Tests are treated as green under the user's standing instruction to report failures; this is user-provided verification context, not an independent run or a detailed per-command log. No tests, builds or migrations were run by the assistant, and no separate frontend build result is claimed.

`GameService.submitMove(gameId, requestingPlayerId, expectedVersion, move)` now coordinates participant/turn checks, exact legal application, resulting-position assessment, current-FEN persistence, stale/concurrent-command rejection and atomic checkmate/stalemate completion with reservation release. `GameMoveResult.from(game, assessment)` owns mechanical result mapping; `Game.finish()` calls its extracted switch-based `validateResult()`. There is no separate production GameMoveService.

V6 adds completion fields and consistency constraints. GameStatus is IN_PROGRESS/FINISHED, with separate result, termination reason and finish time. The unchanged game GET response fields can now carry an advanced FEN and FINISHED status; version/result/reason/time remain absent from that HTTP response. No HTTP move route, move history, frontend gameplay or disconnect policy is added. See the [reviewed implementation and verification commands](../day17-gameplay-service.md).

Day 16 and earlier milestone sections below retain historical behavior/evidence. Their initial-only and pending-gameplay descriptions do not override this checkpoint. The earlier closing-test passing-status uncertainty is retained as historical provenance; current test status follows the user's later standing instruction.

##### Historical Day 16 review provenance

Reviewed on 28 October 2026 against `chess-web-app-day16-end.zip`. Source inspection confirms the legality-enforcing facade, complete production strategy registration, facade regression tests and both [closing checks](../day16-closing-checks.md). No blocking defect was found in the reviewed Day 16 changes. The user reports all tests green through increments 1–2. Both later closing tests are present, but no subsequent explicit passing report was supplied for them. No tests or builds were run independently; no detailed test log or separate frontend build result was supplied.

The Day 16 section describes the inspected implementation and supersedes earlier initial-position-only boundary and test-only-construction statements. Historical sections retain the behavior and evidence available at those checkpoints.

#### Day 16 inspected implementation and verification — historical

`ChessRules` exposes `initialPosition()`, `legalMoves(position)`, `applyMove(position, move)` and `evaluate(position)`. Lookup returns the complete immutable legal set. Application requires exact membership in that set, including the promotion choice, before delegating to `MoveApplier`. Assessment returns `PositionStatus` for the side to move. The existing pipeline remains shared; no per-piece application services or duplicate movement algorithms are introduced.

`GameService` still calls only `initialPosition()` and serializes it for creation. Games remain at their initial FEN in `IN_PROGRESS`. Move submission, persistence/history, gameplay concurrency, terminal lifecycle and active-reservation release remain pending. No endpoint, DTO, migration or frontend implementation changes in Day 16. Authorization, transactions, persistence and concurrency remain service responsibilities.

Inspected production changes are limited to ChessRules, StandardChessRules and GameRulesConfig. GameService, existing movement/evaluation implementations, other existing backend tests, frontend implementation and non-document configuration are unchanged from the inspected Day 15 checkpoint. Existing StandardChessRulesTest retains its starting-placement and metadata tests, updates facade construction through MovementTestFixtures, and exercises the new public operations.

Source-confirmed coverage through increment 2, for which the user reports all tests green:

- Complete immutable opening lookup, NORMAL assessment, ordinary e2-e4 application and original FEN preservation.
- A pinned knight proposal that is pseudo-legal but rejected by legal lookup and facade application.
- All four castling relocations with complete resulting FENs; en passant for both colors; quiet/capture promotions with all four replacement types represented across selected cases.
- Castling rejected while in check, through check and into check; unsafe en passant rejected for both colors after rank blockers disappear.
- Missing promotion choices for quiet/capture cases in both colors; extra choices on ordinary pawn moves, en passant and castling.
- All four assessments for both colors and original-position preservation. Selected pinned-knight moves are rendered by BoardTestRenderer.

The facade tests do not independently exercise every replacement type for every promotion geometry; lower-level existing promotion tests provide that broader matrix. Coverage statements above describe inspected test source plus the user's earlier passing report; they do not establish an independent run.

Both closing checks are present: StandardChessRulesTest rejects missing/duplicate kings across lookup/application/evaluation, and GameRulesConfigTest constructs the production bean graph without Boot/database infrastructure and checks all six strategy sources. Their passing status has not been explicitly reported after their addition. See [day16-closing-checks.md](../day16-closing-checks.md).

Focused local commands from backend/:

```bash
./gradlew test --tests 'com.chessapp.game.rules.*'
./gradlew test --tests 'com.chessapp.game.config.GameRulesConfigTest'
```

The second command is separate because game.config is outside the rules test package. With the configured PostgreSQL test database, existing GameCreationIntegrationTest verifies initial game persistence through the wired boundary. No independent database run or separate Day 16 integration result is claimed.

Historical Day 15 source-review provenance: reviewed on 27 October 2026 against `chess-web-app-day15-end.zip`. Source inspection confirms position-wide legal-move enumeration and `NORMAL` / `CHECK` / `CHECKMATE` / `STALEMATE` assessment for the side to move. No blocking defect was found in the reviewed Day 15 changes. The user reports all tests green after the final guided regressions. No independent tests or builds were run; no detailed test log or separate frontend build result was supplied.

Historical Day 6–15 sections describe earlier checkpoints. Day 16 defines the inspected facade and Day 15 records its enumeration/assessment foundation. Earlier pending/unsupported descriptions apply only to their historical checkpoints.

Day numbers identify working sessions and differ from the original 30-working-day schedule. See [roadmap.md](../roadmap.md) for planning and [api-contracts.md](../api-contracts.md) for endpoint behavior.

#### Current checkpoint

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
| Games | Initial creation and service-owned legal FEN updates; expected-version/JPA optimistic protection; FINISHED results and atomic reservation release |
| Atomicity | Creation/pairing remain atomic; terminal FEN/result/version and deletion of both active reservations also commit or roll back together |
| Discovery | MATCHED queue status, manual refresh and Open game navigation |
| Game access | Participant-protected GET with version/result fields and JWT-derived POST moves |
| Frontend gameplay | Guarded game page, custom oriented board, explicit promotion, versioned moves, refresh/recovery and completion feedback |
| Rules foundation | Pure Java immutable representation, separate FEN parser/serializer, initial-position/legal-lookup/legal-application/assessment rules facade (source-reviewed Day 16) |
| Movement | Knight/rook/bishop/queen/king/pawn strategies, explicit promotions, en passant and standard castling (Day 14), shared traversal/application/filtering, resulting-square king safety, irreversible rook/king castling-right removal, centralized test fixtures; all-piece attack detection |
| Position assessment | Pure NORMAL/CHECK/CHECKMATE/STALEMATE assessment; GameService maps resulting mate/stalemate into persisted completion |

#### Day 15 implementation — historical foundation

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

#### Day 15 source-confirmed coverage

- `movement/PositionLegalMoveGeneratorTest`: 20 opening moves for White and for Black after e2-e4; moving-side ownership; immutable output; original FEN preservation; an empty pinned-knight result alongside exactly four available king moves; rejection of an empty board without kings.
- Aggregation includes both castling sides for both colors; en passant that removes a checking pawn; exclusion of en passant that exposes a rook attack; and all eight quiet/capture promotion combinations per tested pawn (four replacement choices for each of two destinations).
- `evaluation/PositionEvaluatorTest`: normal play, check, checkmate and stalemate for both colors; check with an en passant reply; missing-king rejection; propagation of an incomplete-registry failure; and exactly two knight replies (block or capture) while the moving king has no legal move.
- Selected cases render boards with `com.chessapp.BoardTestRenderer`; the opening aggregation test also renders each source separately. Renderer output describes supplied moves, not an independently calculated assessment.
- Earlier rules tests, including Day 14 castling boundaries, remain unchanged apart from making the shared fixture factories public. The user reports all tests green after the final guided additions. No independent execution or detailed report establishes a separate measured coverage claim.

The new tests do not prove exhaustive correctness of the rules engine. Null/duplicate-king cases at every new entry point, exhaustive special-move-only escape positions, and reference move-tree comparisons are not separately demonstrated by the new Day 15 tests.

#### Schema

| Migration | Responsibility |
| --- | --- |
| V1 | Players |
| V2 | Local authentication credentials |
| V3 | Matchmaking queue and ordering index |
| V4 | Initial game state, participants, timestamps and optimistic-lock version |
| V5 | Active-player reservations and backfill from existing in-progress games |
| V6 | Completion result/reason/time and lifecycle consistency constraints |

Applied migrations remain immutable. Future schema changes use new Flyway SQL migrations. Hibernate validates the schema rather than generating it.

#### Existing backend and frontend test coverage

Backend coverage includes:

- Registration service/controller validation, duplicate handling and BCrypt password-byte limits.
- PostgreSQL player uniqueness, registration persistence and rollback.
- Login and JWT security, including valid, malformed, expired and altered tokens; issuer, subject and expiration requirements; deleted-player profile access.
- Queue persistence, repeated join/leave, concurrent joins, authenticated HTTP operations and deleted-player rejection.
- Initial game persistence and invalid/missing participant rejection.
- Active-game conflicts, rollback of partial reservations, and concurrent creation sharing a player.
- Pairing of two players, removal of paired entries, rollback with the enclosing transaction, and concurrent joins without duplicate games.
- Game-read HTTP access for both participants, nonparticipants, missing games and missing authentication.
- Day 18 request validation and committed HTTP move integration sources: real JWTs, POST/GET agreement, failure precedence, special moves and terminal release.
- Day 17 rules/service/entity/PostgreSQL sources retain broader legality, rollback and optimistic-race coverage.

The five focused Angular tests in `auth-flow.spec.ts` cover protected bearer attachment, exclusion of external requests, protected 401 cleanup/redirect, profile-load failure cleanup and logout. There is no `app.spec.ts` in this ZIP. No broad frontend component or end-to-end suite is introduced.

Test presence describes inspected source. Day 19 adds no frontend tests; the existing authentication tests remain unchanged. Passing status follows the standing user instruction, not an independent run. No tests/builds or browser acceptance checks were executed in this review.

#### Day 5 username-update increment

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

#### Day 6 rules foundation — historical

The source contains pure Java `game.rules` and `game.rules.model` code: `PieceColor`, `PieceType`, immutable `Piece` and `Square` records, an immutable `Board`, `CastlingRights`, and `ChessPosition`. Coordinates run from a1=(0,0) to h8=(7,7). Board owns placement and defensively copies its map with `Map.copyOf`; Piece holds color and type only.

`ChessPosition` carries board, side to move, castling rights, optional en passant target, halfmove clock and fullmove number. `FenParser` validates FEN structure and representation constraints, not full legality or reachability. Separate `FenSerializer` emits canonical FEN.

`ChessRules` currently exposes only `initialPosition()`. `StandardChessRules` uses the parser and a private starting-FEN constant. `GameRulesConfig` wires the beans. `GameService` obtains the initial position through `ChessRules`, serializes it and passes nonblank initial FEN to `Game`; the entity no longer owns the starting-position constant. Rules/model code has no Spring, JPA, HTTP or persistence dependencies.

Focused tests cover square conversion, board immutability, position metadata, FEN parsing/serialization/round trips and standard initial placement. Game creation/read integration assertions check the expected initial FEN. Test presence is a source finding; passing results are user-reported.

Day 6 changes no migration, endpoint or frontend behavior. That historical Day 6 checkpoint had no movement pipeline. Day 7 adds the knight-only pipeline described below; special-move execution, outcomes and move history remain absent.

#### Day 7 movement pipeline — historical checkpoint

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

#### Day 7 source-review follow-ups — resolved in starting archive

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

#### Day 15 verification provenance — historical

Reviewed on 27 October 2026 against `chess-web-app-day15-end.zip`. Source inspection confirms position-wide legal-move enumeration and `NORMAL` / `CHECK` / `CHECKMATE` / `STALEMATE` assessment for the side to move. No blocking defect was found in the reviewed Day 15 changes. The user reports all tests green after the final guided regressions. No independent tests or builds were run; no detailed test log or separate frontend build result was supplied.

The Day 15 archive retains the earlier regression tests, including final Day 14 castling boundary tests. The current passing report follows the final Day 15 additions. Earlier Day 14 statements below preserve the narrower report available at that historical checkpoint. No detailed test log or separate frontend build/manual outcomes were supplied.

##### Local verification

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
11. Open the matched game in both sessions; check opposite orientations and submit/refresh an ordinary move.
12. Cancel promotion and select a different source; verify no request on cancel.
13. Exercise stale/uncertain outcome recovery and terminal feedback. See frontend-gameplay-flow.md for the full suggested manual list.

#### Current limitations

- One queue; cooperating queue operations, including status reads, serialize through one advisory lock.
- Pairing runs on join; no background pairing worker, rating filter, queue expiry or automatic leave on logout.
- Games start immediately in IN_PROGRESS; no acceptance stage.
- Manual refresh discovers matches and opponent moves; no real-time delivery.
- Custom click-to-move board with Unicode pieces; no drag-and-drop, legal-move highlighting or persistent check indicator.
- Checkmate/stalemate persist completion and release reservations; cancellation, disconnect expiry and abandonment remain deferred.
- No move history, clocks, repetition/move-count/dead-position draws, resignation or draw agreement.
- Username updates are supported; email/password changes and profile optimistic locking are not implemented.
- Sessions are in memory; browser reload requires login. No refresh tokens, revocation or server-side logout.
- Username/email matching remains exact; no normalization policy.
- Security filter-chain errors and MVC Problem Details have different body contracts.
- Two minor review follow-ups remain inline recommendations, not applied production changes; see day19-frontend-gameplay.md.
- Secrets and runtime configuration remain user-managed.

#### Working preferences

Keep the focus on backend engineering and explain decisions incrementally. The user implements solutions provided inline step by step; do not silently substitute a generated full implementation archive. Prefer larger coherent milestones that fit comfortably in one chat, divided into a few increments. Treat tests as green under the user's standing instruction unless failures are reported, while clearly separating that from independent execution. Do not pause for routine pass confirmations. Include concise algorithm walkthroughs and selected test-board renderings. Prefer complete FEN fixtures with coherent metadata/turns unless invalidity is deliberate and explained. Use Lombok where appropriate, avoiding `@Data` on JPA entities. Add concise comments for non-obvious behavior. Keep frontend tests minimal. New game/board components use separate HTML and CSS files; avoid broad unrelated frontend refactors. Provide verification commands without waiting for routine test-pass confirmation. Preserve passwords after failed form submissions.

#### Deferred representation evaluation

Evaluate a 64-entry array against map storage after the MVP, or earlier for demonstrated benefits. Preserve Board API. Board identity equality remains a known representation characteristic. Repetition detection needs history beyond FEN.

#### Day 8 rook movement and Strategy dispatch — historical

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

#### Day 9 completed checkpoint — historical

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

#### Day 10 completed checkpoint — historical

- `KingMoveGenerator` implements `PiecePseudoLegalMoveGenerator` for ordinary one-square movement in all eight directions. It clips board edges, excludes friendly destinations and opposing-king captures, respects side to move and returns an immutable set. Castling is excluded even when rights exist.
- `LegalMoveGenerator` still requires exactly one king per color in the input. After applying each candidate, it locates the moving side's king on the resulting board and checks that square against the opponent's attacks. This handles king relocation, captures and attacks exposed by vacating the source.
- `MoveApplier` removes both castling rights for a moving king's color, preserves the opponent's rights except those affected by a captured corner rook, and never restores lost rights. Quiet-move/capture counters, turn switching, en passant expiry and original-position preservation remain shared.
- `MovementTestFixtures` registers knight, rook, bishop, queen and king strategies. Dispatch tests include kings; unsupported own pawns still throw rather than silently returning no moves.
- Tests cover center/corner king candidates, blockers/captures, source validation, explicit invalid adjacent-king defensive input, castling exclusion, check escapes, adjacent kings, protected and unprotected captures, pinned-knight attacks, White/Black pawn and knight attacks, attacks exposed by vacating either king's source, rights removal for both colors, returning kings and corner-rook capture metadata. Existing knight/rook/bishop/queen tests remain in the suite. Selected tests use `com.chessapp.BoardTestRenderer`.

The user reports all tests green for this final archive. Review was by source inspection; no independent tests or builds were run. The cosmetic MoveApplier Javadoc cleanup is present in the inspected Day 10 starting archive.

Current boundary: the movement pipeline supports five piece types, including ordinary king moves. Pawn movement, promotion, en passant execution, castling execution, complete legal-move enumeration and outcomes remain pending. Supported-piece subsets cannot establish checkmate or stalemate. Attack detection recognizes all six types and counts pinned pieces as attackers. Candidate generation excludes king captures; attack detection still recognizes attacks on king squares.

`ChessRules` still exposes only `initialPosition()`. `GameService` creates initial FEN through that boundary and `FenSerializer`; movement is not connected to services or HTTP. No endpoint, migration, frontend behavior, move history or persisted-game behavior changes were introduced in Day 10. Transactions, authorization, persistence and gameplay concurrency remain service responsibilities. FEN validation is structural, Board uses identity equality, repetition needs history, AI is outside the MVP, and map-versus-array evaluation remains deferred.

#### Day 11 completed checkpoint — historical, 26 October 2026

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

#### Day 12 completed checkpoint — historical, 27 October 2026

Promotion is implemented for both colors, for quiet advances and captures, with explicit queen, rook, bishop and knight choices. Ordinary moves retain the two-argument constructor. No choice defaults to queen.

`Move` validates replacement-type shape. Position-dependent validity is established by exact pseudo-legal candidate membership, including the choice. `MoveApplier` preserves the pawn's color and applies the replacement through an immutable Board operation; `LegalMoveGenerator` checks the moving side's king on the resulting board.

Pawn halfmove resets, Black fullmove increments, turn changes, en passant expiry and captured-corner-rook castling-right removal remain shared. A promoted corner rook never grants castling rights. The test renderer lists promotion choices distinctly.

The user reports all tests green for the final Day 12 archive. Review was by source inspection; no independent tests or builds were run.

Movement remains internal pure Java code. `ChessRules` exposes only `initialPosition()`; `GameService` still stores the initial FEN and does not apply moves. Day 12 changes no HTTP contract, DTO, migration or frontend implementation. En passant capture execution, castling execution, complete legal-move enumeration, outcomes, history and gameplay integration remain pending. A source-specific result cannot establish checkmate or stalemate.

##### Day 12 source-confirmed coverage

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

#### Day 13 completed checkpoint — historical, 27 October 2026

En passant is implemented for both colors. Generation requires the correct source rank, one forward diagonal step, matching target metadata, an empty destination and an adjacent opposing pawn. An inconsistent or unavailable opportunity contributes no en passant candidate and does not suppress ordinary moves. Attempting an omitted capture fails exact candidate membership with `IllegalArgumentException`; no unsupported-en-passant exception remains.

`MoveApplier` validates candidate membership before identifying a pawn's diagonal move into an empty square as en passant. It relocates the pawn and removes the opposing pawn from `(destination.file, source.rank)` through immutable Board operations. `Board.removePiece()` requires a nonnull occupied square and performs placement only; callers own chess-rule validation. The original position remains unchanged.

Shared handling switches turns, resets the pawn halfmove clock, increments the fullmove number after Black, and expires the target. En passant preserves existing castling rights. Ordinary moves, promotion choices and irreversible king/rook rights removal remain supported. An occupied diagonal follows ordinary capture rules even if inconsistent target metadata names it; no adjacent pawn is removed in that case. Promotion choices attached to en passant fail candidate membership.

`LegalMoveGenerator` remains shared and unchanged. It checks the moving side's king after both pawn squares have been vacated. Tests cover both colors, both capture directions in generation, FEN results, original-position preservation, missing/inconsistent metadata, absent/friendly/non-pawn adjacent pieces, wrong source ranks, occupied targets, opportunity expiry, ordinary advances instead of capture, castling-right preservation, invalid promotion choices, exposed rook/bishop attacks and removal of checking pawns. Board tests cover immutable removal and invalid removal inputs. The three obsolete unsupported-case tests were replaced. Selected tests use `com.chessapp.BoardTestRenderer`.

The user reports all tests green for the final Day 13 archive. Review was by source inspection; no independent tests or builds were run.

Movement remains internal pure Java code. `ChessRules` exposes only `initialPosition()`; `GameService` still stores the initial FEN and does not apply moves. Day 13 changes no HTTP contract, DTO, migration or frontend implementation. Castling execution, complete legal-move enumeration, outcomes, move history and gameplay integration remain pending. Generation handles a single source; an empty result cannot establish checkmate or stalemate. FEN parsing is structural, not proof of legality or reachability; the implementation does not reconstruct the preceding move from history. Board equality is identity-based, repetition requires history, and authorization, transactions, persistence and gameplay concurrency remain service responsibilities. AI and map-versus-array evaluation remain deferred.

#### Day 14 implementation — historical, 27 October 2026

Standard castling is implemented for both colors and both sides. Candidates require the matching castling right, the king on e1/e8, a same-color rook on its original corner, and an empty intervening path. Inconsistent rights/placement omit the affected candidate while ordinary moves remain available. Rights are historical metadata; local placement checks do not reconstruct move history or distinguish a promoted rook from an original rook.

`MoveApplier` validates exact candidate membership, relocates the king and rook through immutable Board operations, and reuses shared metadata handling. Castling increments the halfmove clock once, increments the fullmove number after Black, switches turns once, clears the en passant target and permanently removes both rights for the moving king's color. Promotion choices on castling are rejected through candidate membership.

`LegalMoveGenerator` identifies castling separately from path safety. It checks the starting square on the original board, then the transit square with the king relocated and the rook still at its corner. The existing shared safety check examines the destination on the completed castling board. `MoveApplier` intentionally does not check attacks. Pinned pieces still count as attackers. Queenside b1/b8 must be empty but may be attacked; an attacked rook does not itself prohibit castling.

Source-confirmed coverage includes all four relocations and resulting FENs, ordinary king moves alongside castling, individual/absent rights, every intervening square, missing/wrong-color/non-rook corner pieces, displaced kings, attacks on starting/transit/destination squares, pinned-knight attacks, attacked rooks and b-file squares, en passant expiry, promotion-choice rejection and original-position preservation. `KingMoveGeneratorTest` replaces its obsolete castling-exclusion expectation. `CastlingTest` contains ten parameterized test methods; selected cases use `com.chessapp.BoardTestRenderer`. Existing movement, promotion and en passant test sources are unchanged.

Verification: the user reported green tests through the king-safety increment. Final additional boundary tests are present, with no subsequent explicit passing report. No independent tests or builds were run.

Movement remains internal pure Java code. `ChessRules` exposes only `initialPosition()`; `GameService` creates and stores the initial FEN and does not apply moves. Complete legal-move enumeration, outcomes, move history and gameplay integration remain pending. Single-source generation cannot establish checkmate or stalemate. FEN parsing remains structural, Board equality remains identity-based, and repetition requires history. Authorization, transactions, persistence and gameplay concurrency remain service responsibilities. AI and map-versus-array evaluation remain deferred.

#### Day 15 completion boundary

Position-wide enumeration and position assessment are implemented internally. `generate(position, from)` remains source-specific; its empty result alone cannot determine an outcome. `generate(position)` collects all moving-side legal moves, and `PositionEvaluator` combines reply availability with current-board attack detection. `PositionStatus` is a rules assessment, separate from persisted `GameStatus`. `ChessRules` still exposes only `initialPosition()`; `GameService` still stores the initial FEN and does not apply moves. There is no move-submission endpoint, move history, terminal persisted lifecycle or active-reservation release. Repetition, move-count draws, dead-position detection, clocks, resignation and agreement are not evaluated. Authorization, transactions, persistence and gameplay concurrency remain service responsibilities. FEN parsing is structural, Board equality is identity-based, and repetition requires history. AI and map-versus-array evaluation remain deferred.


---

## Original file: docs/roadmap.md

### Roadmap

#### Day 19 current checkpoint — source-reviewed, 29 October 2026

Reviewed the actual user archive `chess-web-app-day19-end.zip` against `chess-web-app-day18-end.zip`. No blocking defect was found in the reviewed Day 19 changes. Two small follow-ups are supplied inline and recorded in [the review](../day19-frontend-gameplay.md): add a submission guard to Game.refresh() and clear obsolete move feedback after a successful GET. They are not applied to production source by this documentation package.

The Angular frontend now supports guarded matched-game navigation, participant-protected game reads, a custom Player-oriented click-to-move board, explicit promotion including cancellation, versioned move submission, manual refresh, stale-state reload without command retry, uncertain-outcome blocking and completion feedback. Game and ChessBoard use separate TypeScript, HTML and CSS files.

The backend tree is byte-for-byte unchanged from the Day 18 archive. No new endpoint, migration or backend runtime configuration is introduced. The cm-chessboard dependency, declarations and asset/style additions are absent. package.json is unchanged; package-lock.json contains 15 package-version changes, and angular.json differs only in formatting. Those user changes are preserved.

Tests are treated as green under the standing instruction to report failures. The assistant ran no application tests, builds, migrations or browser acceptance checks, and claims no separately verified frontend build/manual result. Source inspection is the evidence for this review.

See [frontend gameplay flow](../frontend-gameplay-flow.md) for the lifecycle and Angular coordination, [Day 19 review](../day19-frontend-gameplay.md) for findings and [next-session handoff](../day19-handoff.md) for continuation. Earlier Day sections retain historical boundaries and do not override this checkpoint.

History, real-time delivery, clocks, additional draw rules, acceptance and disconnect/abandonment policy remain separate scope decisions. A board library is optional post-MVP polish, not an MVP requirement.

#### Day 18 checkpoint — historical HTTP gameplay

Authenticated POST /api/v1/games/{gameId}/moves now delegates to the existing
GameService command using JWT-derived Player identity. SubmitMoveRequest
validates request shape before conversion into Move. Specific gameplay
rejections have game-owned HTTP status/code mappings.

GameResponse.from(game) maps participant-protected reads, now including
version, result, terminationReason and finishedAt. Successful move commands
return the existing GameMoveResult.

The HTTP tests cover committed commands, POST/GET agreement, authentication,
validation, failure precedence, special moves and terminal results/release.
Tests are treated as green under the user's standing instruction; no
independent tests/builds or final Day 18 archive review are claimed.

No migration, runtime configuration, rules implementation or frontend
implementation changes are included. History, frontend gameplay, real-time
delivery, clocks and disconnect policy remain pending.

See [Day 18 HTTP gameplay](../day18-http-gameplay.md) for the contract and coverage.
Earlier checkpoint sections describe historical boundaries.

#### Day 17 checkpoint — historical source review, 28 October 2026

Reviewed the actual user implementation in `chess-web-app-day17-end(1).zip` against `chess-web-app-day16-end.zip`. No blocking defect was found in the reviewed Day 17 production/test changes. Tests are treated as green under the user's standing instruction to report failures; this is user-provided verification context, not an independent run or a detailed per-command log. No tests, builds or migrations were run by the assistant, and no separate frontend build result is claimed.

`GameService.submitMove(gameId, requestingPlayerId, expectedVersion, move)` now coordinates participant/turn checks, exact legal application, resulting-position assessment, current-FEN persistence, stale/concurrent-command rejection and atomic checkmate/stalemate completion with reservation release. `GameMoveResult.from(game, assessment)` owns mechanical result mapping; `Game.finish()` calls its extracted switch-based `validateResult()`. There is no separate production GameMoveService.

V6 adds completion fields and consistency constraints. GameStatus is IN_PROGRESS/FINISHED, with separate result, termination reason and finish time. The unchanged game GET response fields can now carry an advanced FEN and FINISHED status; version/result/reason/time remain absent from that HTTP response. No HTTP move route, move history, frontend gameplay or disconnect policy is added. See the [reviewed implementation and verification commands](../day17-gameplay-service.md).

Day 16 and earlier milestone sections below retain historical behavior/evidence. Their initial-only and pending-gameplay descriptions do not override this checkpoint. The earlier closing-test passing-status uncertainty is retained as historical provenance; current test status follows the user's later standing instruction.

##### Historical Day 16 review provenance

Reviewed on 28 October 2026 against `chess-web-app-day16-end.zip`. Source inspection confirms the legality-enforcing facade, complete production strategy registration, facade regression tests and both [closing checks](../day16-closing-checks.md). No blocking defect was found in the reviewed Day 16 changes. The user reports all tests green through increments 1–2. Both later closing tests are present, but no subsequent explicit passing report was supplied for them. No tests or builds were run independently; no detailed test log or separate frontend build result was supplied.

The Day 16 section describes the inspected implementation and supersedes earlier initial-position-only boundary and test-only-construction statements. Historical sections retain the behavior and evidence available at those checkpoints.

#### Day 16 source-reviewed milestone — historical

The legality-enforcing pure rules facade, special-move/assessment regressions and both closing checks are present in the reviewed Day 16 source. No blocking defect was found in these changes. The user reports all tests green through increments 1–2; a later passing report for the closing checks is not yet recorded.

`ChessRules` exposes `initialPosition()`, `legalMoves(position)`, `applyMove(position, move)` and `evaluate(position)`. Lookup returns the complete immutable legal set. Application requires exact membership in that set, including the promotion choice, before delegating to `MoveApplier`. Assessment returns `PositionStatus` for the side to move. The existing pipeline remains shared; no per-piece application services or duplicate movement algorithms are introduced.

`GameService` still calls only `initialPosition()` and serializes it for creation. Games remain at their initial FEN in `IN_PROGRESS`. Move submission, persistence/history, gameplay concurrency, terminal lifecycle and active-reservation release remain pending. No endpoint, DTO, migration or frontend implementation changes in Day 16. Authorization, transactions, persistence and concurrency remain service responsibilities.

#### Next milestone decision

Minimal frontend gameplay is implemented and source-reviewed. Apply the two small inline guard/message follow-ups if not already done; no new application feature is selected by this review.

Discuss the next coherent milestone in a new chat. Candidates include persisted move history or authenticated, authorized real-time updates. Clocks, additional draw rules and disconnect/abandonment policy remain distinct decisions.

A board library is optional post-MVP polish. The custom board is the current implementation; do not reintroduce library integration as an MVP prerequisite.

#### Working-session numbering

Working Days 1–19 identify actual development sessions and differ from the original 30-working-day estimate. Earlier work completed some planned later capabilities early. This page records implemented sessions and remaining capabilities; it does not establish a new complete 30-day schedule.

#### Completed working sessions

| Session | Delivered scope |
| --- | --- |
| Days 1–3 | Project setup, authentication/profile foundation, Angular authentication and persistent queue foundation |
| Day 4 | Initial game persistence, active-player reservations, transactional pairing, matched-game discovery and participant-protected retrieval |
| Day 5 | Authenticated username PATCH, backend tests and Angular profile editor |
| Day 6 | Immutable representation, separate FEN parser/serializer and initial-position rules boundary |
| Day 7 | Move/board relocation, knight movement, candidate application, all-piece attack detection, king-safety filtering and test rendering |
| Day 8 | Strategy dispatch, rook movement, shared pipeline and corner-rook castling-right updates |
| Day 9 | Shared sliding traversal, bishop/queen strategies, centralized movement fixtures and pipeline tests |
| Day 10 | Ordinary king candidates, resulting-square king safety, irreversible king castling-right removal and associated tests |
| Day 11 | Guided ordinary pawn generation, pawn metadata application, registration, dispatcher coverage and king-safety tests; final source reviewed; user reports green tests |
| Day 12 | Explicit promotion choices, quiet/capture generation, immutable replacement, shared king safety/metadata and renderer/test updates; source reviewed; user reports green tests |
| Day 13 | En passant generation, immutable off-destination capture, defensive metadata handling, expiry and king-safety tests; source reviewed; user reports green tests |
| Day 14 | Standard castling for both colors/sides, immutable two-piece application, staged king safety and boundary tests; final source reviewed; green tests reported through king-safety increment |
| Day 15 | Complete legal-move enumeration, shared filtering, current-side position assessment, cross-package test fixtures and both-color special-move/check-escape regressions; source reviewed; user reports all tests green |
| Day 16 | Legality-enforcing rules facade, production wiring, facade special-move/assessment regressions and closing tests; source reviewed; historical passing evidence described above |
| Day 17 | GameService move commands, exact legal application, version protection, checkmate/stalemate results, atomic reservation release, V6 and entity/unit/PostgreSQL test sources; source-reviewed, green status per standing user instruction |
| Day 18 | JWT-derived HTTP move submission, validated requests, version/result-bearing reads, game-owned error contracts and HTTP integration coverage |
| Day 19 | Custom Angular gameplay: guarded navigation, board display, explicit promotion, versioned moves, manual refresh, recovery, completion feedback, separate component HTML/CSS and lifecycle guide; source-reviewed with two minor follow-ups |

The Day 15 source adds complete enumeration and check/checkmate/stalemate assessment on top of the Day 14 movement pipeline. No blocking defect was found in the reviewed Day 15 changes. The user reports all tests green after final regressions. No tests/builds were run independently.

#### Original-plan reconciliation

Earlier planning placed security around original Day 5, connected login/profile/frontend around Days 6–7, rules/library selection around Day 8 and game persistence around Day 9. Those original estimates are distinct from the working-session labels above. The project selected its own rules implementation with a future library-adapter option. Initial persistence arrived before rules completion. The full original schedule is not reproduced here.

#### Day 10 completion boundary — historical

King movement is one square in all eight directions. Shared filtering examines the moving side's king on each resulting board. A king move removes both rights for that color; returning never restores them. Tests cover both colors, attacks after vacating the source, king adjacency, captures, pinned attackers, pawn/knight attack directions, metadata and existing-piece regressions.

Castling execution, pawn movement, outcomes and service/HTTP integration were excluded from Day 10. Pawn movement and promotion subsequently arrived in Days 11–12, en passant in Day 13 and standard castling in Day 14. Position assessment arrived in Day 15; persisted outcomes and service/HTTP integration remain pending.

#### Remaining capabilities to schedule

- Move history and a separately selected history/replay interface.
- Authenticated, authorized real-time delivery and appropriate internal events.
- Clocks and any separately selected timeout/disconnect/abandonment policy.
- Additional draw rules, resignation or draw agreement if selected.
- Queue expiry, acceptance/cancellation and rating behavior if selected.
- Player profile statistics/ELO and account email/password changes if selected.
- Optional board polish or a board-library replacement after a concrete need is established.

HTTP gameplay and the minimal custom-board frontend are implemented. Acceptance, cancellation, rating filters and expiry require explicit scope decisions. Redis/Kafka, spectators, tournaments, chat, engine analysis and distributed scaling remain outside initial scope. AI is outside the MVP. Map-versus-array evaluation remains deferred unless demonstrated benefits justify it, preserving the Board API.

#### Engineering constraints

Keep the modular monolith, Player terminology and cross-feature service boundaries. The rules package remains free of Spring/JPA/HTTP concerns. `ChessRules` retains its four pure facade operations; GameService now owns persisted move application and completion. Single-source generation cannot determine checkmate/stalemate. Candidate generation excludes king captures; attack detection includes pinned attackers. FEN is structural state, not proof of reachability or repetition history. Preserve useful comments, coherent FEN fixtures, selected board renderings, minimal frontend testing and user-managed secrets/configuration.

#### Day 13 completed checkpoint — historical, 27 October 2026

En passant is implemented for both colors. Generation requires the correct source rank, one forward diagonal step, matching target metadata, an empty destination and an adjacent opposing pawn. An inconsistent or unavailable opportunity contributes no en passant candidate and does not suppress ordinary moves. Attempting an omitted capture fails exact candidate membership with `IllegalArgumentException`; no unsupported-en-passant exception remains.

`MoveApplier` validates candidate membership before identifying a pawn's diagonal move into an empty square as en passant. It relocates the pawn and removes the opposing pawn from `(destination.file, source.rank)` through immutable Board operations. `Board.removePiece()` requires a nonnull occupied square and performs placement only; callers own chess-rule validation. The original position remains unchanged.

Shared handling switches turns, resets the pawn halfmove clock, increments the fullmove number after Black, and expires the target. En passant preserves existing castling rights. Ordinary moves, promotion choices and irreversible king/rook rights removal remain supported. An occupied diagonal follows ordinary capture rules even if inconsistent target metadata names it; no adjacent pawn is removed in that case. Promotion choices attached to en passant fail candidate membership.

`LegalMoveGenerator` remains shared and unchanged. It checks the moving side's king after both pawn squares have been vacated. Tests cover both colors, both capture directions in generation, FEN results, original-position preservation, missing/inconsistent metadata, absent/friendly/non-pawn adjacent pieces, wrong source ranks, occupied targets, opportunity expiry, ordinary advances instead of capture, castling-right preservation, invalid promotion choices, exposed rook/bishop attacks and removal of checking pawns. Board tests cover immutable removal and invalid removal inputs. The three obsolete unsupported-case tests were replaced. Selected tests use `com.chessapp.BoardTestRenderer`.

The user reports all tests green for the final Day 13 archive. Review was by source inspection; no independent tests or builds were run.

Movement remains internal pure Java code. `ChessRules` exposes only `initialPosition()`; `GameService` still stores the initial FEN and does not apply moves. Day 13 changes no HTTP contract, DTO, migration or frontend implementation. Castling execution, complete legal-move enumeration, outcomes, move history and gameplay integration remain pending. Generation handles a single source; an empty result cannot establish checkmate or stalemate. FEN parsing is structural, not proof of legality or reachability; the implementation does not reconstruct the preceding move from history. Board equality is identity-based, repetition requires history, and authorization, transactions, persistence and gameplay concurrency remain service responsibilities. AI and map-versus-array evaluation remain deferred.

#### Day 14 boundary — historical, 27 October 2026

Standard castling for both colors and both sides is implemented internally. Candidate generation checks rights and placement; application relocates both pieces; legal filtering checks starting, transit and destination safety. See [architecture.md](../architecture.md) for responsibilities and verification provenance. Day 14 changes no endpoint, DTO, migration or frontend implementation.

Movement remains internal pure Java code. `ChessRules` exposes only `initialPosition()`; `GameService` creates and stores the initial FEN and does not apply moves. Complete legal-move enumeration, outcomes, move history and gameplay integration remain pending. Single-source generation cannot establish checkmate or stalemate. FEN parsing remains structural, Board equality remains identity-based, and repetition requires history. Authorization, transactions, persistence and gameplay concurrency remain service responsibilities. AI and map-versus-array evaluation remain deferred.


---

## Original file: docs/api-contracts.md

### API Contracts

#### Day 19 current checkpoint — source-reviewed, 29 October 2026

Reviewed the actual user archive `chess-web-app-day19-end.zip` against `chess-web-app-day18-end.zip`. No blocking defect was found in the reviewed Day 19 changes. Two small follow-ups are supplied inline and recorded in [the review](../day19-frontend-gameplay.md): add a submission guard to Game.refresh() and clear obsolete move feedback after a successful GET. They are not applied to production source by this documentation package.

The Angular frontend now supports guarded matched-game navigation, participant-protected game reads, a custom Player-oriented click-to-move board, explicit promotion including cancellation, versioned move submission, manual refresh, stale-state reload without command retry, uncertain-outcome blocking and completion feedback. Game and ChessBoard use separate TypeScript, HTML and CSS files.

The backend tree is byte-for-byte unchanged from the Day 18 archive. No new endpoint, migration or backend runtime configuration is introduced. The cm-chessboard dependency, declarations and asset/style additions are absent. package.json is unchanged; package-lock.json contains 15 package-version changes, and angular.json differs only in formatting. Those user changes are preserved.

Tests are treated as green under the standing instruction to report failures. The assistant ran no application tests, builds, migrations or browser acceptance checks, and claims no separately verified frontend build/manual result. Source inspection is the evidence for this review.

See [frontend gameplay flow](../frontend-gameplay-flow.md) for the lifecycle and Angular coordination, [Day 19 review](../day19-frontend-gameplay.md) for findings and [next-session handoff](../day19-handoff.md) for continuation. Earlier Day sections retain historical boundaries and do not override this checkpoint.

History, real-time delivery, clocks, additional draw rules, acceptance and disconnect/abandonment policy remain separate scope decisions. A board library is optional post-MVP polish, not an MVP requirement.

#### Day 18 checkpoint — historical HTTP gameplay

Authenticated POST /api/v1/games/{gameId}/moves now delegates to the existing
GameService command using JWT-derived Player identity. SubmitMoveRequest
validates request shape before conversion into Move. Specific gameplay
rejections have game-owned HTTP status/code mappings.

GameResponse.from(game) maps participant-protected reads, now including
version, result, terminationReason and finishedAt. Successful move commands
return the existing GameMoveResult.

The HTTP tests cover committed commands, POST/GET agreement, authentication,
validation, failure precedence, special moves and terminal results/release.
Tests are treated as green under the user's standing instruction; no
independent tests/builds or final Day 18 archive review are claimed.

No migration, runtime configuration, rules implementation or frontend
implementation changes are included. History, frontend gameplay, real-time
delivery, clocks and disconnect policy remain pending.

See [Day 18 HTTP gameplay](../day18-http-gameplay.md) for the contract and coverage.
Earlier checkpoint sections describe historical boundaries.

#### Day 17 checkpoint — historical source review, 28 October 2026

Reviewed the actual user implementation in `chess-web-app-day17-end(1).zip` against `chess-web-app-day16-end.zip`. No blocking defect was found in the reviewed Day 17 production/test changes. Tests are treated as green under the user's standing instruction to report failures; this is user-provided verification context, not an independent run or a detailed per-command log. No tests, builds or migrations were run by the assistant, and no separate frontend build result is claimed.

`GameService.submitMove(gameId, requestingPlayerId, expectedVersion, move)` now coordinates participant/turn checks, exact legal application, resulting-position assessment, current-FEN persistence, stale/concurrent-command rejection and atomic checkmate/stalemate completion with reservation release. `GameMoveResult.from(game, assessment)` owns mechanical result mapping; `Game.finish()` calls its extracted switch-based `validateResult()`. There is no separate production GameMoveService.

V6 adds completion fields and consistency constraints. GameStatus is IN_PROGRESS/FINISHED, with separate result, termination reason and finish time. The unchanged game GET response fields can now carry an advanced FEN and FINISHED status; version/result/reason/time remain absent from that HTTP response. No HTTP move route, move history, frontend gameplay or disconnect policy is added. See the [reviewed implementation and verification commands](../day17-gameplay-service.md).

Day 16 and earlier milestone sections below retain historical behavior/evidence. Their initial-only and pending-gameplay descriptions do not override this checkpoint. The earlier closing-test passing-status uncertainty is retained as historical provenance; current test status follows the user's later standing instruction.

##### Historical Day 16 review provenance

Reviewed on 28 October 2026 against `chess-web-app-day16-end.zip`. Source inspection confirms the legality-enforcing facade, complete production strategy registration, facade regression tests and both [closing checks](../day16-closing-checks.md). No blocking defect was found in the reviewed Day 16 changes. The user reports all tests green through increments 1–2. Both later closing tests are present, but no subsequent explicit passing report was supplied for them. No tests or builds were run independently; no detailed test log or separate frontend build result was supplied.

The Day 16 section describes the inspected implementation and supersedes earlier initial-position-only boundary and test-only-construction statements. Historical sections retain the behavior and evidence available at those checkpoints.

#### Day 16 boundary — historical

`ChessRules` exposes `initialPosition()`, `legalMoves(position)`, `applyMove(position, move)` and `evaluate(position)`. Lookup returns the complete immutable legal set. Application requires exact membership in that set, including the promotion choice, before delegating to `MoveApplier`. Assessment returns `PositionStatus` for the side to move. The existing pipeline remains shared; no per-piece application services or duplicate movement algorithms are introduced.

`GameService` still calls only `initialPosition()` and serializes it for creation. Games remain at their initial FEN in `IN_PROGRESS`. Move submission, persistence/history, gameplay concurrency, terminal lifecycle and active-reservation release remain pending. No endpoint, DTO, migration or frontend implementation changes in Day 16. Authorization, transactions, persistence and concurrency remain service responsibilities.

The new facade methods are internal Java contracts. An absent legal proposal produces IllegalArgumentException; malformed Move values may fail at construction, and invalid positions fail through the existing validation pipeline. These are not newly assigned HTTP statuses or application error codes. NORMAL/CHECK/CHECKMATE/STALEMATE are not added to persisted GameStatus or the game response.

Historical Day 15 source-review provenance: reviewed on 27 October 2026 against `chess-web-app-day15-end.zip`. Source inspection confirms position-wide legal-move enumeration and `NORMAL` / `CHECK` / `CHECKMATE` / `STALEMATE` assessment for the side to move. No blocking defect was found in the reviewed Day 15 changes. The user reports all tests green after the final guided regressions. No independent tests or builds were run; no detailed test log or separate frontend build result was supplied.

This document lists expected application failures by action. It is not an exhaustive catalog of framework, transport or infrastructure failures. An absent application code is intentional where the current implementation does not guarantee one.

#### Day 15 boundary — historical

Position-wide enumeration and position assessment are implemented internally. `generate(position, from)` remains source-specific; its empty result alone cannot determine an outcome. `generate(position)` collects all moving-side legal moves, and `PositionEvaluator` combines reply availability with current-board attack detection. `PositionStatus` is a rules assessment, separate from persisted `GameStatus`. `ChessRules` still exposes only `initialPosition()`; `GameService` still stores the initial FEN and does not apply moves. There is no move-submission endpoint, move history, terminal persisted lifecycle or active-reservation release. Repetition, move-count draws, dead-position detection, clocks, resignation and agreement are not evaluated. Authorization, transactions, persistence and gameplay concurrency remain service responsibilities. FEN parsing is structural, Board equality is identity-based, and repetition requires history. AI and map-versus-array evaluation remain deferred.

Day 15 adds no endpoint, DTO, migration, application error code or frontend behavior. Rules exceptions remain internal Java contracts. NORMAL/CHECK/CHECKMATE/STALEMATE are not new values in the game response.

#### Shared conventions

- Protected endpoints require `Authorization: Bearer <token>`.
- Protected self-service actions derive player identity from the validated JWT subject; clients do not choose the acting player ID.
- MVC validation returns Problem Detail with `code: VALIDATION_FAILED` and an `errors` array containing `field` and `message`. Rejected password values are omitted.
- Duplicate-identity and feature-owned exceptions return Problem Detail with their application code.
- Missing/invalid bearer credentials use Spring Security handling; no application code or uniform MVC error body is guaranteed.
- Deleted-player failures from profile GET/PATCH use `ResponseStatusException` with 401; no application code is guaranteed. Matchmaking has its own `AUTHENTICATED_PLAYER_UNAVAILABLE` code.
- Malformed JSON, incompatible field types, path conversion failures and unexpected server errors may have different bodies. Clients need a generic fallback.
- The frontend handles protected 401 session cleanup centrally; action-specific UI handles known codes. A 403 does not invalidate the session.

#### Endpoint index

| Method | Route | Authentication | Successful response |
| --- | --- | --- | --- |
| POST | `/api/v1/auth/register` | Public | 201; `playerId`, `username`, `email` |
| POST | `/api/v1/auth/login` | Public | 200; `accessToken`, `tokenType`, `expiresIn` |
| GET | `/api/v1/players/me` | Bearer JWT | 200; `id`, `username`, `email` |
| PATCH | `/api/v1/players/me` | Bearer JWT | 200; updated `id`, `username`, `email` |
| POST | `/api/v1/matchmaking/queue` | Bearer JWT | 200; queue status |
| GET | `/api/v1/matchmaking/queue/me` | Bearer JWT | 200; queue status |
| DELETE | `/api/v1/matchmaking/queue/me` | Bearer JWT | 204; no body |
| GET | `/api/v1/games/{gameId}` | Participant bearer JWT | 200; game state |
| POST | `/api/v1/games/{gameId}/moves` | Participant Bearer JWT | 200; GameMoveResult |

There is no public game-creation endpoint: pairing invokes creation internally. Participant-authenticated POST /api/v1/games/{gameId}/moves delegates to GameService. Pure rules exceptions are not themselves the HTTP contract; game-owned application exceptions map gameplay rejections.

#### Register — POST `/api/v1/auth/register`

Request fields: `username`, `email`, `password`.

Username: nonblank, 3–30 Java UTF-16 string units. Email: nonblank, valid email format, at most 255 units. Password: nonblank, at least 8 units and at most 72 UTF-8 bytes. No trimming or normalization is applied. Success returns the registered identity without credentials or a token.

| Failure | Status | Application code |
| --- | --- | --- |
| Invalid fields | 400 | `VALIDATION_FAILED` |
| Username taken | 409 | `USERNAME_ALREADY_EXISTS` |
| Email registered | 409 | `EMAIL_ALREADY_EXISTS` |

#### Login — POST `/api/v1/auth/login`

Request fields: `email`, `password`. Email matching is exact. Login validates nonblank fields, email format/maximum length and the BCrypt byte maximum; it does not impose registration's minimum password length.

Success returns `accessToken`, `tokenType: Bearer` and `expiresIn` in seconds, with `Cache-Control: no-store`. The frontend then loads the self-profile. Unknown accounts and incorrect/missing local credentials receive the same invalid-credentials response.

| Failure | Status | Application code |
| --- | --- | --- |
| Invalid fields | 400 | `VALIDATION_FAILED` |
| Invalid credentials | 401 | `INVALID_CREDENTIALS` |

#### Read self-profile — GET `/api/v1/players/me`

No request body. Success returns the current database-backed `PlayerResponse` (`id`, `username`, `email`).

| Failure | Status | Application code |
| --- | --- | --- |
| Missing, invalid or expired token | 401 | Not guaranteed |
| Authenticated player no longer exists | 401 | Not guaranteed |

#### Update username — PATCH `/api/v1/players/me`

Request:

```json
{
  "username": "new_username"
}
```

Success: 200 with the existing `PlayerResponse` shape:

```json
{
  "id": 1,
  "username": "new_username",
  "email": "player@example.com"
}
```

##### Rules

- Only the authenticated player's username changes. Email and ID are unchanged.
- Username must be nonblank and 3–30 Java UTF-16 string units long. Some supplementary Unicode characters occupy two units.
- Matching is exact and case-sensitive. Spaces and special characters are allowed; whitespace-only values are rejected. No trimming or normalization is performed.
- Submitting the current username succeeds without changing the entity or its update timestamp.
- Availability prechecks provide feedback; the existing PostgreSQL unique constraint protects against conflicting writes under concurrency.
- Existing JWTs remain valid because their subject contains the stable player ID.
- Concurrent edits to the same player's username use last-write-wins behavior.
- Missing/null username is invalid. This endpoint is a username-specific update, not a general JSON Patch or JSON Merge Patch implementation. Additional fields are outside its supported contract.

| Failure | Status | Application code |
| --- | --- | --- |
| Invalid username | 400 | `VALIDATION_FAILED` |
| Username taken | 409 | `USERNAME_ALREADY_EXISTS` |
| Missing, invalid or expired token | 401 | Not guaranteed |
| Authenticated player no longer exists | 401 | Not guaranteed |

A representative validation response is:

```json
{
  "type": "about:blank",
  "title": "Request validation failed",
  "status": 400,
  "detail": "One or more request fields are invalid",
  "instance": "/api/v1/players/me",
  "code": "VALIDATION_FAILED",
  "errors": [
    { "field": "username", "message": "must not be blank" }
  ]
}
```

Validation messages and the number of entries depend on the input and validator. Clients should use `field` and `code` rather than match literal messages.

#### Queue join/status/leave

POST `/api/v1/matchmaking/queue` joins and attempts pairing. GET `/api/v1/matchmaking/queue/me` reads status. DELETE `/api/v1/matchmaking/queue/me` removes waiting membership and returns 204, including repeated leave requests.

Queue responses contain `status`, `enqueuedAt` and `gameId`. `WAITING` has an enqueue timestamp; `MATCHED` has a game ID. Inapplicable fields are null under the current DTO. Status is `NOT_QUEUED`, `WAITING` or `MATCHED`.

Repeated joins preserve the original waiting timestamp or return the existing active game. Leaving does not cancel an established game. Logout does not leave the queue. Pairing is triggered on join; clients currently refresh manually.

| Action | Failure | Status | Application code |
| --- | --- | --- | --- |
| Join, status, leave | Missing, invalid or expired token | 401 | Not guaranteed |
| Join, status, leave | Authenticated player no longer exists | 401 | `AUTHENTICATED_PLAYER_UNAVAILABLE` |
| Join during pairing | Reservation conflict with another game-creation caller | 409 | `PLAYER_ALREADY_IN_GAME` |

An ordinary repeated join by an already matched player returns 200 `MATCHED`; it is not a conflict. The reservation-conflict case describes a competing creation race and rolls back the pairing transaction.

#### Read game — GET `/api/v1/games/{gameId}`

No request body. Success returns id, whitePlayerId, blackPlayerId, status, currentFen, createdAt, version, result, terminationReason and finishedAt through GameResponse.from(game). Either participant can read; opponent profile/email is not returned. New games start in IN_PROGRESS at the initial FEN. Accepted commands advance FEN/version and may produce FINISHED. Completion fields are null during active play. GET does not reevaluate the position and has no positionStatus.

| Failure | Status | Application code |
| --- | --- | --- |
| Missing, invalid or expired token | 401 | Not guaranteed |
| Game does not exist | 404 | `GAME_NOT_FOUND` |
| Authenticated requester is not a participant | 403 | `GAME_ACCESS_DENIED` |

#### Submit move — POST `/api/v1/games/{gameId}/moves`

Authenticated Player identity comes from the validated JWT subject.

```json
{
  "expectedVersion": 0,
  "from": "e2",
  "to": "e4",
  "promotion": null
}
```

expectedVersion is required and nonnegative. Coordinates are required lowercase algebraic squares from a1 through h8 and must differ. Promotion is omitted/null or exactly QUEEN, ROOK, BISHOP or KNIGHT; board-dependent validity remains in the rules pipeline. No automatic queen choice is supplied.

Success returns 200 with gameId, currentFen, version, positionStatus, status, result, terminationReason and finishedAt. positionStatus assesses the side to move and is NORMAL, CHECK, CHECKMATE or STALEMATE. Lifecycle is IN_PROGRESS/FINISHED; results are WHITE_WIN/BLACK_WIN/DRAW; implemented termination reasons are CHECKMATE/STALEMATE.

| Failure | Status | Application code |
| --- | --- | --- |
| Invalid request fields | 400 | VALIDATION_FAILED |
| Missing/invalid bearer credentials | 401 | Not guaranteed |
| Nonparticipant | 403 | GAME_ACCESS_DENIED |
| Missing game | 404 | GAME_NOT_FOUND |
| Finished game | 409 | GAME_ALREADY_FINISHED |
| Stale observed/concurrent version | 409 | STALE_GAME_VERSION |
| Wrong turn | 409 | NOT_PLAYERS_TURN |
| Well-formed illegal proposal | 422 | ILLEGAL_GAME_MOVE |

Request validation precedes service lookup. Well-formed command precedence is missing game, membership, lifecycle, version, turn and exact legal membership. Malformed JSON/incompatible types return 400 without promising VALIDATION_FAILED. Unexpected rules/stored-state failures are not broadly reclassified as client illegality.

Clients use the accepted response version for the next command. Reload stale state without automatically replaying a move. An uncertain network failure may follow a commit, so read before proposing another move.

The Angular page preserves GET-only participant metadata when applying a POST response. Both participants can read a finished game. Terminal release permits another queue join; no history-listing endpoint is implemented.

#### Keeping contracts synchronized

When changing an endpoint, update its owning DTO/service/controller, corresponding integration assertions, this action's contract and the checkpoint progress together. Keep feature error constants in their owning package. Tests verify documented behavior; this document does not establish that a test run passed.

#### Day 8 boundary

The guided Day 8 increment adds internal knight/rook Strategy dispatch, rook movement, shared application/filtering and rook castling-right updates. It changes no endpoint, response shape or frontend implementation. ChessRules still exposes only initialPosition(); persisted games still contain the initial state. There is no move-submission API or chessboard UI. The corrected fixture remains present in the final Day 9 source, for which the user reports all tests green.

#### Day 9 boundary

The reviewed Day 9 source adds bishop/queen movement, shared sliding traversal, centralized test fixtures and pipeline tests. The user reports all tests green; none were run independently. No endpoint, response shape, frontend implementation or migration changes. Games still store the initial position; no move-submission API or chessboard UI exists.

#### Day 10 boundary

Day 10 adds internal ordinary king candidates, resulting-square king-safety filtering and irreversible castling-right removal for moving kings. The shared test registry and tests are updated. Castling execution and pawn movement remain pending. No HTTP contract, DTO, migration, frontend implementation or persisted-game behavior changes. `ChessRules` still exposes only `initialPosition()`; there is no move-submission API or chessboard UI. The user reports all tests green for the final Day 10 archive; this review ran no tests or builds.

#### Day 13 completed checkpoint — historical, 27 October 2026

En passant is implemented for both colors. Generation requires the correct source rank, one forward diagonal step, matching target metadata, an empty destination and an adjacent opposing pawn. An inconsistent or unavailable opportunity contributes no en passant candidate and does not suppress ordinary moves. Attempting an omitted capture fails exact candidate membership with `IllegalArgumentException`; no unsupported-en-passant exception remains.

`MoveApplier` validates candidate membership before identifying a pawn's diagonal move into an empty square as en passant. It relocates the pawn and removes the opposing pawn from `(destination.file, source.rank)` through immutable Board operations. `Board.removePiece()` requires a nonnull occupied square and performs placement only; callers own chess-rule validation. The original position remains unchanged.

Shared handling switches turns, resets the pawn halfmove clock, increments the fullmove number after Black, and expires the target. En passant preserves existing castling rights. Ordinary moves, promotion choices and irreversible king/rook rights removal remain supported. An occupied diagonal follows ordinary capture rules even if inconsistent target metadata names it; no adjacent pawn is removed in that case. Promotion choices attached to en passant fail candidate membership.

`LegalMoveGenerator` remains shared and unchanged. It checks the moving side's king after both pawn squares have been vacated. Tests cover both colors, both capture directions in generation, FEN results, original-position preservation, missing/inconsistent metadata, absent/friendly/non-pawn adjacent pieces, wrong source ranks, occupied targets, opportunity expiry, ordinary advances instead of capture, castling-right preservation, invalid promotion choices, exposed rook/bishop attacks and removal of checking pawns. Board tests cover immutable removal and invalid removal inputs. The three obsolete unsupported-case tests were replaced. Selected tests use `com.chessapp.BoardTestRenderer`.

The user reports all tests green for the final Day 13 archive. Review was by source inspection; no independent tests or builds were run.

Movement remains internal pure Java code. `ChessRules` exposes only `initialPosition()`; `GameService` still stores the initial FEN and does not apply moves. Day 13 changes no HTTP contract, DTO, migration or frontend implementation. Castling execution, complete legal-move enumeration, outcomes, move history and gameplay integration remain pending. Generation handles a single source; an empty result cannot establish checkmate or stalemate. FEN parsing is structural, not proof of legality or reachability; the implementation does not reconstruct the preceding move from history. Board equality is identity-based, repetition requires history, and authorization, transactions, persistence and gameplay concurrency remain service responsibilities. AI and map-versus-array evaluation remain deferred.

#### Day 14 boundary — historical, 27 October 2026

Standard castling for both colors and both sides is implemented internally. Candidate generation checks rights and placement; application relocates both pieces; legal filtering checks starting, transit and destination safety. See [architecture.md](../architecture.md) for responsibilities and verification provenance. Day 14 changes no endpoint, DTO, migration or frontend implementation.

Movement remains internal pure Java code. `ChessRules` exposes only `initialPosition()`; `GameService` creates and stores the initial FEN and does not apply moves. Complete legal-move enumeration, outcomes, move history and gameplay integration remain pending. Single-source generation cannot establish checkmate or stalemate. FEN parsing remains structural, Board equality remains identity-based, and repetition requires history. Authorization, transactions, persistence and gameplay concurrency remain service responsibilities. AI and map-versus-array evaluation remain deferred.


---

## Original file: docs/frontend-gameplay-flow.md

### Frontend gameplay flow

This companion explains the minimal frontend gameplay milestone: matched-game navigation, a custom board, explicit promotion, HTTP move submission, manual refresh, stale-state recovery and completion feedback.

#### Evidence and scope

Source-reviewed on 29 October 2026 against chess-web-app-day19-end.zip, compared with the Day 18 archive. The custom board, promotion cancellation, page/board file separation and library removal are confirmed in actual source. The backend is byte-for-byte unchanged from Day 18. Two small follow-ups are recommendations, not applied production changes; see [Day 19 review](../day19-frontend-gameplay.md).

No independent assistant tests or builds were run. Existing tests follow the standing user instruction that failures will be reported; this is not an independently verified frontend build or acceptance result.

The selected frontend is a custom click-to-move board. HTML and CSS are separate files for Game and ChessBoard. A board library is optional post-MVP polish, not an MVP completion requirement.

#### Ownership and file map

| Owner | Responsibility |
| --- | --- |
| Backend GameService | Participant authorization, turn/version checks, legal moves, persistence and completion |
| Angular Game page | Read snapshots, coordinate proposals and promotion, send requests, recover and present completion |
| Angular ChessBoard | Render the supplied FEN, orient squares, track selection and emit proposals |
| GameApiService | Transport GET and POST requests |
| Existing authentication services/interceptor | Maintain the in-memory session, attach bearer tokens and handle authenticated 401 responses |

Frontend files under frontend/src/app/:

| File | Purpose |
| --- | --- |
| app.routes.ts | Guarded games/:gameId route |
| home/pages/home/home.ts | Queue status and Open game navigation |
| game/models/game-response.ts | GET response types |
| game/models/game-move.ts | Proposed move, request and POST response types |
| game/models/fen-display.ts | Display-only FEN projection |
| game/services/game-api.service.ts | Game HTTP transport |
| game/pages/game/game.ts | Gameplay orchestration and state |
| game/pages/game/game.html | Page controls and board bindings |
| game/pages/game/game.css | Page styling |
| game/components/chess-board/chess-board.ts | Board display computations and selection |
| game/components/chess-board/chess-board.html | Board markup |
| game/components/chess-board/chess-board.css | Square, piece and selection styling |

Separating HTML/CSS does not change the component lifecycle or data flow. The TypeScript decorator connects the files using templateUrl and styleUrl.

#### 1. Matchmaking creates the game

Home sends POST /api/v1/matchmaking/queue. The interceptor attaches the bearer token; the backend derives Player identity from the validated JWT subject.

Within the matchmaking transaction:

1. Acquire the queue lock.
2. Check for an existing active game.
3. Otherwise insert or retain the Player's queue membership.
4. Find two eligible queued Players.
5. Assign colors and call GameService.createGame().
6. Obtain the initial position through ChessRules and serialize it.
7. Save the game and reserve both Players.
8. Remove the paired queue entries.

The transaction commits the game, reservations and queue changes together. If the caller already has an active game, joining returns that game rather than creating another.

The joining Player can receive MATCHED immediately. A previously waiting Player discovers MATCHED on a later status read. Home loads status on entry and supports manual refresh.

The game exists before either Player opens its page. Route navigation never creates a game.

#### 2. Opening the game loads a snapshot

Open game navigates to /games/{gameId}. The route guard checks local authentication. Backend authentication and participant authorization remain mandatory regardless of the guard.

Game.ngOnInit() combines:

- route.paramMap: the initial route parameters and subsequent parameter changes.
- refreshRequests: explicit refresh events.
- startWith(undefined): an initial refresh value so combineLatest can emit immediately.

Each combined event enters switchMap, which subscribes to the newest read operation and unsubscribes from the previous one. This prevents an older read response from replacing a newer snapshot. It is not a guarantee that server-side processing of an old request stops.

defer runs the load setup when that inner operation is subscribed to. The reviewed implementation clears game and pending promotion, validates the route ID, sets loading and requests GET /api/v1/games/{gameId}.

On success, it sets the game signal and clears needsRefresh. On failure, it presents an error. catchError is inside the request, preserving the outer route/refresh stream for future attempts.

finalize clears loading on completion, error or unsubscription. takeUntilDestroyed unsubscribes when the page is destroyed.

#### 3. Angular's reactive building blocks

| API | Meaning in this implementation |
| --- | --- |
| signal(initialValue) | Store a value that consumers can track |
| signal() | Read the current value |
| signal.set(value) | Replace the stored value |
| computed(callback) | Derive a cached, read-only value from signals read by the callback |
| input() | Receive a parent-supplied value as a signal |
| output() | Define an event the child can emit to its parent |
| effect(callback) | Run a callback initially and rerun it when tracked signal dependencies change |

Computed values are lazy and memoized. Changing a dependency invalidates the cached result; the next read derives the current result. Dependency tracking follows the signals actually read during execution.

Angular tracks signal reads in templates, including OnPush templates, and schedules updates for those consumers. Replacing game with a new response object therefore updates the relevant bindings; no manual board redraw is needed.

##### Game-page state

| State | Meaning |
| --- | --- |
| game | Latest loaded or accepted game snapshot |
| loading | A game GET is running |
| submitting | A move POST is running |
| needsRefresh | Another proposal must wait for a successful read |
| pendingPromotion | A proposed pawn move awaiting a piece choice |
| moveMessage | Move rejection or recovery feedback |
| canSubmitMove | Derived interaction permission |

canSubmitMove requires a loaded in-progress game, an authenticated participant, no read or submission in progress, no refresh requirement and an observed side to move matching that Player.

This is interface coordination. The backend checks the actual current turn and game state independently.

#### 4. Parent inputs render the board

The Game template passes:

- fen: current.currentFen.
- orientation: WHITE or BLACK from the current Player's participant ID.
- enabled: canSubmitMove() and no pending promotion.

ChessBoard.position derives a display projection from FEN. ChessBoard.rows derives the ordered cells for the orientation. The template renders those cells and highlights selectedSquare.

FEN placement is listed from rank 8 to rank 1, with files a through h. Digits represent empty-square runs. Orientation changes display order; it does not change algebraic names. a1 remains dark in both orientations.

parseFenForDisplay checks six fields, piece-placement shape and the active-color field. It does not validate the remaining FEN fields, chess legality, check or reachability.

##### Selection reset effect

The reviewed custom board registers:

```ts
effect(() => {
  this.fen();
  this.orientation();
  this.enabled();

  this.selectedSquare.set(null);
});
```

The first three calls deliberately read dependencies. Their returned values are unused: Angular tracks the reads and schedules the effect when one of those values changes. Writing selectedSquare does not read it, so this callback does not subscribe to selection changes.

The effect clears selection after a position/orientation/interaction-state change. Selecting a square alone does not rerun it.

Effects are managed by Angular's lifecycle; this is not polling and is not a callback run synchronously inside every signal.set(). Prefer computed for derived values. Keep this effect limited to the existing selection reset rather than using effects to copy all application state.

The library-specific afterNextRender, rendering queue and imperative DOM synchronization are absent from the restored custom board.

#### 5. Square selection and promotion

When White clicks e2, the board stores selectedSquare = e2. Clicking another own piece replaces that selection. Clicking the selected square again clears it.

Clicking e4 after selecting e2 executes:

```ts
this.selectedSquare.set(null);
this.moveProposed.emit({ from: 'e2', to: 'e4' });
```

The parent receives the proposal through:

```html
(moveProposed)="proposeMove($event)"
```

The board does not call HTTP or modify FEN.

Game.proposeMove() checks interaction permission and inspects the displayed piece. A pawn proposal ending on its promotion rank stores pendingPromotion. Other proposals call sendMove immediately.

##### Why canceling promotion cannot leave the old selection

1. The board clears selectedSquare before emitting the proposal.
2. The page stores pendingPromotion.
3. The pending proposal disables board interaction.
4. Cancel sets pendingPromotion to null.
5. No POST was sent and FEN/version are unchanged.
6. The Player starts again by selecting a source square.

The selection-reset effect is additional protection when enabled changes; cancellation does not depend on the effect having already run. The explicit clear before emit is sufficient.

Choosing QUEEN, ROOK, BISHOP or KNIGHT clears the pending proposal and sends that explicit choice. No automatic queen is chosen. Refresh also clears pending promotion.

Castling uses the king's source and destination, such as e1 to g1. En passant uses the pawn's source and destination. The backend supplies the complete resulting position for all special moves.

#### 6. Submission and backend processing

sendMove sends the displayed version, coordinates and optional promotion:

```json
{
  "expectedVersion": 0,
  "from": "e2",
  "to": "e4",
  "promotion": null
}
```

The request never supplies the acting Player, accepted FEN, winner or result.

Bean Validation handles field shape before the service command. For a well-formed command, GameService checks:

1. Game existence.
2. Participant access.
3. In-progress lifecycle.
4. Expected version.
5. Turn ownership from persisted FEN.
6. Exact legal-move membership.

It applies the move through ChessRules, evaluates the next position, serializes it and mutates the managed game. Explicit flush updates the version and detects competing writes. An optimistic-lock failure becomes STALE_GAME_VERSION.

The controller returns after transactional service processing succeeds. Flush alone is not commit; terminal updates and reservation release can still roll back together.

On POST success, Game preserves id, participant IDs and createdAt from the GET snapshot and replaces the fields supplied by GameMoveResult: FEN, version, status and completion details.

The custom board displays that accepted FEN. It never performs an optimistic piece move.

The identity check this.game() !== current ignores a response whose original snapshot has been replaced. It does not cancel or undo a backend commit.

#### 7. Recovery and manual refresh

| Situation | Reviewed client behavior |
| --- | --- |
| Illegal move, 422/ILLEGAL_GAME_MOVE | Keep the position and allow another proposal |
| Invalid request, 400 | Show rejection without applying a position |
| Stale version, wrong turn or already finished | Require a fresh read and trigger reload; never replay the command |
| Uncertain transport/server failure | Block proposals until a successful manual refresh |
| Access denied or missing game | Show the access/read error and block proposals pending refresh |
| Authenticated 401 | Existing interceptor clears the matching session and redirects |

A network failure does not prove the move failed: the backend may have committed before the response was lost. Read before deciding on another proposal.

Signals react to local state changes. They do not detect an opponent's database update. The opponent must refresh until real-time delivery is explicitly implemented.

A successful POST updates the existing board through inputs. A manual GET load currently clears game first, destroying the board; a successful response recreates it. Refresh failure leaves the read error visible.

GET has no positionStatus. The current UI therefore does not retain a CHECK indicator from POST across refreshed snapshots. Completion information is persisted and available through both contracts.

#### 8. Completion and another match

CHECKMATE finishes the game with the mover's winning color. STALEMATE finishes it as DRAW. The backend persists status, result, terminationReason and finishedAt and releases exactly two active Player reservations in the same transaction.

The submitting Player sees completion from POST. The opponent sees it on refresh. FINISHED makes canSubmitMove false, disabling further board interaction.

Returning Home loads queue status again. Released Players can join another match. A finished game remains participant-readable by its ID; queue status does not act as a history listing.

Authentication remains in memory. Browser reload requires login again; Home can then rediscover an active game.

#### Milestone closing

The Day 19 archive has now been source-reviewed. The custom board, separate component files and complete library removal are confirmed. The Refresh button already blocks submissions, but refresh() itself lacks that guard; successful reads also retain old moveMessage text. Apply the two small inline follow-ups from day19-frontend-gameplay.md if desired before the closing commit. No production changes are included in this documentation bundle.

Focused manual scenarios:

- Match two authenticated Players and inspect opposite orientations.
- Submit an ordinary move and refresh the opponent.
- Reject an illegal proposal without changing the displayed position.
- Start promotion, cancel, then select a different source; confirm no request on cancel.
- Choose an explicit underpromotion.
- Recover a stale proposal without automatically replaying it.
- Refresh while a square is selected.
- Complete a game and confirm both Players can queue again.

History, clocks, real-time delivery, extra draw rules, acceptance workflows and disconnect/abandonment policy remain separate scope decisions. Closing the page or disconnecting does not finish a game.

#### References

- [Angular signals](https://angular.dev/guide/signals)
- [Angular effect API](https://angular.dev/api/core/effect)
- [Angular component outputs](https://angular.dev/guide/components/outputs)
- [RxJS switchMap](https://rxjs.dev/api/operators/switchMap)
