# After Day 16 — handoff for the next working session

Continue my chess web app from the final Day 16 source archive. Review actual source, test locations and documentation before proposing the next integration milestone. This handoff does not preselect or authorize service/HTTP gameplay changes.

## Evidence and first actions

The assistant reviewed `chess-web-app-day16-end.zip` on 28 October 2026, including changed production/test sources, unchanged game-creation integration and the documentation replacements. No blocking defect was found in the reviewed Day 16 changes. The user reports all tests green through increments 1–2, including facade special-move/assessment regressions. No independent tests/builds, detailed test log or separate frontend build result was supplied.

Both closing checks are present: invalid/missing king counts propagated through all three position-taking facade operations, and GameRulesConfigTest covering actual production construction and all six strategy registrations without PostgreSQL. No later explicit passing report has been supplied for these closing tests. Do not repeat their implementation; incorporate any subsequent user report.

The uploaded archive included the prior documentation replacements unchanged. Apply the accompanying source-reviewed Day 16 replacements if absent: these update review provenance and confirm the closing tests are present. Read README.md, docs/architecture.md, docs/development-progress.md, docs/roadmap.md, docs/api-contracts.md, frontend/README.md and docs/movement-pipeline-architecture.md. Recheck actual source before new changes.

## Architecture and scope

Modular monolith, package-by-feature with conventional layers; not DDD. Keep Player terminology and cross-feature service calls rather than another feature's repositories. Java 21, Spring Boot 4.1.1, checked-in Gradle wrapper 9.8.1 in the inspected checkpoint, PostgreSQL/JPA/Flyway, Spring Security/JWT, Lombok, YAML, Angular 21 and Angular Material. Preserve user-managed secrets/configuration.

Authentication/profile GET/PATCH, persistent matchmaking, transactional pairing, active-player reservations and participant-protected game retrieval already exist. Games persist only their initial FEN in IN_PROGRESS. GameService uses ChessRules.initialPosition() with FenSerializer for creation. No service/HTTP move application, move history, terminal lifecycle or reservation release is part of Day 16.

## Source-confirmed Day 16 boundary

ChessRules exposes initialPosition(), legalMoves(ChessPosition), applyMove(ChessPosition, Move) and evaluate(ChessPosition). Return types are ChessPosition, Set<Move>, ChessPosition and PositionStatus respectively. No combined result DTO or source-specific facade overload was added.

StandardChessRules delegates complete lookup to LegalMoveGenerator, requires exact LEGAL-set membership before MoveApplier application, and delegates assessment to PositionEvaluator. Promotion choice is part of Move equality. Missing/inapplicable choices fail membership; there is no automatic queen. Rejections use IllegalArgumentException; null checks and other generation/validation failures propagate. Application does not automatically evaluate the result. Input positions remain unchanged.

Lombok @RequiredArgsConstructor/@NonNull provides the four-dependency constructor. GameRulesConfig assembles one complete six-strategy dispatcher, applier, detector, legal generator and evaluator behind ChessRules. FenParser/FenSerializer remain beans. Internal components need no individual Spring beans; pure rules classes remain free of Spring/JPA/HTTP. The existing test-only MovementTestFixtures is reused by facade tests. The two changed constructor sites are production configuration and StandardChessRulesTest. GameService and GameCreationIntegrationTest are unchanged from Day 15; the review did not execute integration tests.

## Existing rules contracts

Immutable pure Java model; separate structural FEN parser/serializer; all six strategy generators; immutable duplicate-rejecting registry; shared sliding traversal; pseudo-legal exact membership in MoveApplier; legal filtering with one king per color and resulting-board king safety. King captures are excluded from candidates; attack detection recognizes all piece types, attacks on kings and pinned attackers.

Promotions are explicit Q/R/B/N choices for both colors and quiet/capture destinations. En passant checks rank, geometry, metadata, empty target and an adjacent opposing pawn; it removes (destination.file, source.rank). Double advances record the passed-over target even without a capturer; other moves expire it. Shared application updates turns/counters and permanently removes affected king/corner-rook castling rights.

Standard castling checks rights, home king, same-color corner rook and clear path including queenside b-file. Legal filtering checks the original start, king-only transit with rook unmoved, and completed destination board. Attacked rooks/b-file squares alone do not prohibit castling. Placement/rights cannot prove original-rook identity or history. MoveApplier does not establish king safety.

Complete enumeration uses the original position for every source/candidate. PositionEvaluator obtains the complete legal set first, then combines current moving-king attacks with available replies into NORMAL/CHECK/CHECKMATE/STALEMATE. Failures propagate. PositionStatus is not persisted GameStatus; NORMAL does not rule out other draw conditions.

## Source-confirmed coverage

Existing StandardChessRulesTest preserves starting placement/metadata and adds immutable complete opening lookup, normal assessment, e2-e4 FEN/metadata, pinned-knight rejection, all four castling relocations, en passant for both colors, selected quiet/capture promotions with each of Q/R/B/N represented, unsafe special-move rejection, missing/inapplicable promotion choices, four assessments for both colors and original-FEN preservation. Lower-level promotion tests cover the broader replacement-choice matrix. Selected boards use com.chessapp.BoardTestRenderer.

## Next milestone decision

Choose the next integration milestone separately from this reviewed baseline. Discuss a bounded server-authoritative move use case and explicitly decide whether HTTP, history, concurrency, terminal lifecycle and reservation release belong in the same milestone or separate ones. Do not silently implement any of them from this handoff alone. Keep authorization/transactions/persistence/concurrency in services and the rules facade pure.

Do not silently add repetition, move-count draws, dead-position detection, clocks, resignation, agreement or AI. FEN is structural state, not proof of legality/reachability; repetition needs history. Board has identity equality: compare contents or FEN. Map-versus-64-entry-array evaluation remains deferred while preserving Board API.

Guide incrementally with brief algorithms, useful comments, coherent complete FENs and selected board renderings. Label deliberately invalid defensive fixtures. Use Lombok where useful and no @Data on JPA entities. Keep frontend tests minimal. Do not run tests/builds independently unless requested; provide commands and distinguish inspected source from user-reported results. Do not pause for routine test-pass confirmations. Synchronize documentation and provide updated files.

Local rules plus configuration verification from backend/:

```bash
./gradlew test --tests 'com.chessapp.game.rules.*' --tests 'com.chessapp.game.config.GameRulesConfigTest'
```

Existing game creation needs the configured PostgreSQL test database:

```bash
./gradlew test --tests 'com.chessapp.game.GameCreationIntegrationTest'
```
