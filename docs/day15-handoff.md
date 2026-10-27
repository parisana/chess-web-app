# Day 15 handoff

Continue my chess web app from the attached Day 14 source checkpoint. Review actual source and all six project documents before proposing changes, then guide me incrementally through Day 15.

Architecture: modular monolith, package-by-feature with conventional layers, not DDD. Keep Player terminology. Cross-feature interactions use services. Java 21, Spring Boot 4.1.1, Gradle wrapper, PostgreSQL, JPA, Flyway, Spring Security/JWT, Lombok, YAML, Angular 21 and Angular Material.

Completed application features: authentication, profile GET/PATCH, persistent matchmaking, transactional pairing, active-player reservations and participant-protected game retrieval. Immutable pure Java rules model and separate FEN parser/serializer. ChessRules exposes only initialPosition(); GameService uses it with FenSerializer to create the initial stored state. Movement is not connected to services or HTTP.

Movement pipeline: PiecePseudoLegalMoveGenerator strategies for all six types, immutable registry with explicit duplicate/unsupported failures, shared SlidingRayTraversal, exact candidate-membership validation in MoveApplier and single-source LegalMoveGenerator requiring exactly one king per color. SquareAttackDetector recognizes all six types and pinned attackers. Candidates exclude king captures. MovementTestFixtures centralizes standard construction. Selected tests use com.chessapp.BoardTestRenderer.

Promotion supports explicit queen/rook/bishop/knight choices for both colors, quiet moves and captures; no silent queen default. Move equality includes the choice. En passant generation validates metadata, rank, geometry, empty destination and adjacent opposing pawn; application removes the pawn from (destination.file, source.rank). Double advances record the passed-over target even if nobody can capture; other moves expire it. Shared handling updates turns, counters and irreversible king/corner-rook rights removal.

Day 14 implements standard castling for both colors/sides:
- KingMoveGenerator checks appropriate rights, king on e1/e8, same-color corner rook and empty path, including queenside b-file. Inconsistent rights/placement omit the affected candidate and preserve ordinary moves.
- MoveApplier first validates exact membership, then relocates both pieces immutably. It does not check attacks. Castling increments the halfmove clock once, increments fullmove after Black, switches turns once, expires en passant and removes both moving-king rights permanently.
- LegalMoveGenerator uses separate isCastlingMove() and isCastlingPathSafe() helpers. It checks the original starting square, transit with the king moved and rook unmoved, then destination on the completed board through the shared final-board check.
- Pinned attackers count. Attacked rooks and attacked b1/b8 do not themselves prohibit castling. Rights and local placement cannot prove history or original-rook identity.
- CastlingTest contains ten parameterized test methods covering all four relocations/FENs, rights, blockers, invalid placement, start/transit/destination attacks, pinned knights, attacked rooks/b-file, target expiry, promotion-choice rejection and original-position preservation. KingMoveGeneratorTest replaces the old exclusion expectation. Other source/tests are unchanged from Day 13.

Final Day 14 source review found no blocking defect in the reviewed changes. Green tests were reported through the king-safety increment, before the final additional boundary tests. Those additional tests are present in the final archive, but no subsequent explicit passing report was supplied. No independent tests or builds were run. Documentation replacements were provided separately; inspect the next attachment to confirm they were applied.

Proposed Day 15 scope: position-wide legal-move enumeration and check/checkmate/stalemate assessment. Review existing classes before selecting names, packaging and validation responsibilities. Aggregate the existing pipeline across moving-side pieces; distinguish check with available replies, checkmate and stalemate. Use coherent FEN fixtures for both colors, including special moves and check escapes. Single-source empty results alone do not determine an outcome. Do not silently add repetition, move-count draws, dead-position detection, clocks, resignation, agreement or persisted lifecycle behavior.

Boundaries: parser validation is structural, not proof of legality/reachability; Board uses identity equality, so compare contents/FEN explicitly. Repetition requires history beyond FEN. Authorization, transactions, persistence, concurrency and active-reservation release remain service responsibilities. AI is outside MVP. Map versus 64-entry array evaluation is deferred, preserving Board API. Working-day numbering differs from the original 30-day schedule.

Preferences: guide incrementally with brief algorithms and useful comments; review actual source and test locations first. Do not run tests/builds independently unless requested; provide local commands. Distinguish inspected source from reported results. Do not pause for routine test-pass confirmations. Use complete FENs with appropriate turns, labeling defensive invalid fixtures. Include selected board renderings. Use Lombok where appropriate; no @Data on JPA entities. Minimal frontend tests. Leave secrets/configuration cleanup to me. Keep documentation synchronized and provide updated files.

Read: README.md, docs/architecture.md, docs/development-progress.md, docs/roadmap.md, docs/api-contracts.md, frontend/README.md.
