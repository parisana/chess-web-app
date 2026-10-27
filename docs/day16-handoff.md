# Day 16 handoff

Continue my chess web app from the completed Day 15 source checkpoint. Review actual source, test locations and all six project documents before proposing changes, then guide me incrementally through Day 16. Apply the accompanying Day 15 documentation replacements first if they are not yet included in the next source archive.

Read README.md, docs/architecture.md, docs/development-progress.md, docs/roadmap.md, docs/api-contracts.md and frontend/README.md. Also read docs/movement-pipeline-architecture.md for the explanatory companion.

## Architecture and application state

Modular monolith, package-by-feature with conventional layers; not DDD. Keep Player terminology. Cross-feature interactions use services rather than another feature's repositories. Java 21, Spring Boot 4.1.1, checked-in Gradle wrapper (9.8.1 in this checkpoint), PostgreSQL, JPA, Flyway, Spring Security/JWT, Lombok, YAML, Angular 21 and Angular Material.

Authentication, profile GET/PATCH, persistent matchmaking, transactional pairing, active-player reservations and participant-protected game retrieval are implemented. Games persist the initial FEN in IN_PROGRESS. ChessRules exposes only initialPosition(); StandardChessRules uses FenParser, and GameService uses ChessRules with FenSerializer for creation. No service or HTTP endpoint applies moves. No reservation release or persisted terminal lifecycle exists.

## Rules foundation

Immutable pure Java rules model, separate FEN parser/serializer, PiecePseudoLegalMoveGenerator strategies for all six piece types, immutable duplicate-rejecting dispatch registry, shared SlidingRayTraversal, exact pseudo-legal candidate membership in MoveApplier, and shared LegalMoveGenerator safety filtering. SquareAttackDetector recognizes all six types, king-square attacks and pinned attackers. Candidates exclude king captures. MoveApplier does not establish king safety.

Promotion requires an explicit queen/rook/bishop/knight choice; Move equality includes it. Quiet and capture promotions work for both colors, with no automatic queen. En passant validates metadata, geometry, rank, empty target and adjacent opposing pawn; application removes the pawn from (destination.file, source.rank). Double advances record the passed-over target even without an available capture; other moves expire it. Shared application updates turns, counters and irreversible king/corner-rook castling rights.

Standard castling supports both colors/sides: rights, home king, same-color corner rook and an empty path including queenside b-file. Legal filtering checks the original start, transit with king moved/rook unmoved, and completed destination board. Attacked rooks and attacked b1/b8 do not independently prohibit castling. Pinned attackers count. Rights/local placement cannot prove history or original-rook identity.

## Day 15 completed implementation

LegalMoveGenerator.generate(position) validates exactly one king per color before traversing all 64 squares, selecting moving-side pieces and returning an immutable union. The original generate(position, from) remains usable. Both call private generateForSource(); input king-count validation runs once per public call, while resulting-board king safety remains per candidate. Every source/candidate uses the original position.

game.rules.evaluation contains PositionEvaluator and PositionStatus. The evaluator obtains the complete legal set first, allowing invalid-input/unsupported movement failures to propagate. It then locates the validated moving king and checks attacks on the current board. Safe with replies => NORMAL; attacked with replies => CHECK; attacked without replies => CHECKMATE; safe without replies => STALEMATE. findMovingKing relies on prior generator validation. PositionStatus is not persisted GameStatus, and NORMAL does not rule out other draw conditions.

MovementTestFixtures and its factory methods are public test-only infrastructure reused by movement/evaluation tests. No production wiring was added. New tests are movement/PositionLegalMoveGeneratorTest and evaluation/PositionEvaluatorTest. Coverage includes both colors, 20 opening moves, side ownership, immutable output, FEN preservation, missing kings, incomplete registry propagation, empty pinned-piece subsets, castling aggregation, eight promotion choices across quiet/capture destinations, safe/unsafe en passant, four assessments and knight block/capture replies when the king cannot move. Selected boards are rendered by com.chessapp.BoardTestRenderer.

Source review of chess-web-app-day15-end.zip found no blocking defect in the reviewed Day 15 changes. The user reports all tests green after the final guided regressions. No independent tests or builds were run; no detailed test log or separate frontend build result was supplied. All seven existing documents in that archive were unchanged from Day 14; replacement documents and this handoff were provided separately. Confirm their application in the next attachment.

## Proposed Day 16 scope

Expose a legality-enforcing pure rules boundary before services/HTTP. Review existing ChessRules, StandardChessRules, GameRulesConfig, movement/evaluation classes, fixtures and initial-position/game-creation tests before selecting method names, packaging and return types.

1. Extend the application-owned rules boundary for complete legal-move lookup, legal application and position assessment as justified by the immediate use case.
2. For application, validate exact membership in the LEGAL set, including promotion choice, before delegating to MoveApplier. Do not accept a move merely because pseudo-legal application succeeds.
3. Reuse the existing pipeline and wire construction behind StandardChessRules; keep Spring configuration outside the pure rules packages. Avoid creating duplicate algorithms or per-piece application services.
4. Add focused facade tests for ordinary/special moves, rejected king-exposing moves, castling path restrictions, invalid/missing promotion choices, metadata and original preservation. Preserve initial-position behavior and existing game creation.

This is proposed scope, not implementation. Do not silently add HTTP, persistence/history, concurrent gameplay commands, terminal lifecycle or reservation release. Choose the next integration milestone separately.

## Boundaries and preferences

Parser validation is structural, not proof of legality/reachability. Board uses identity equality: compare contents or FEN explicitly. Repetition requires history beyond FEN. Do not silently add repetition, move-count draws, dead-position detection, clocks, resignation, agreement or AI. Authorization, transactions, persistence, concurrency and reservation release remain service responsibilities. Map-versus-64-entry-array evaluation stays deferred, preserving Board API. Working-day numbering differs from the original 30-day schedule.

Guide incrementally with brief algorithms, useful comments and selected board renderings. Review actual source and test locations first. Do not run tests/builds independently unless requested; provide local commands. Distinguish inspected source from user-reported results. Do not pause for routine test-pass confirmations. Use complete, coherent FENs and label deliberately invalid defensive fixtures. Use Lombok where appropriate; no @Data on JPA entities. Minimal frontend tests. Leave secrets/configuration cleanup to me. Synchronize documentation and provide updated files.

Local rules verification from backend/: ./gradlew test --tests 'com.chessapp.game.rules.*'
