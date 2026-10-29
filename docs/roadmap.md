# Roadmap

## Day 19 current checkpoint — source-reviewed, 29 October 2026

Reviewed the actual user archive `chess-web-app-day19-end.zip` against `chess-web-app-day18-end.zip`. No blocking defect was found in the reviewed Day 19 changes. Two small follow-ups are supplied inline and recorded in [the review](day19-frontend-gameplay.md): add a submission guard to Game.refresh() and clear obsolete move feedback after a successful GET. They are not applied to production source by this documentation package.

The Angular frontend now supports guarded matched-game navigation, participant-protected game reads, a custom Player-oriented click-to-move board, explicit promotion including cancellation, versioned move submission, manual refresh, stale-state reload without command retry, uncertain-outcome blocking and completion feedback. Game and ChessBoard use separate TypeScript, HTML and CSS files.

The backend tree is byte-for-byte unchanged from the Day 18 archive. No new endpoint, migration or backend runtime configuration is introduced. The cm-chessboard dependency, declarations and asset/style additions are absent. package.json is unchanged; package-lock.json contains 15 package-version changes, and angular.json differs only in formatting. Those user changes are preserved.

Tests are treated as green under the standing instruction to report failures. The assistant ran no application tests, builds, migrations or browser acceptance checks, and claims no separately verified frontend build/manual result. Source inspection is the evidence for this review.

See [frontend gameplay flow](frontend-gameplay-flow.md) for the lifecycle and Angular coordination, [Day 19 review](day19-frontend-gameplay.md) for findings and [next-session handoff](day19-handoff.md) for continuation. Earlier Day sections retain historical boundaries and do not override this checkpoint.

History, real-time delivery, clocks, additional draw rules, acceptance and disconnect/abandonment policy remain separate scope decisions. A board library is optional post-MVP polish, not an MVP requirement.

## Day 18 checkpoint — historical HTTP gameplay

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

See [Day 18 HTTP gameplay](day18-http-gameplay.md) for the contract and coverage.
Earlier checkpoint sections describe historical boundaries.

## Day 17 checkpoint — historical source review, 28 October 2026

Reviewed the actual user implementation in `chess-web-app-day17-end(1).zip` against `chess-web-app-day16-end.zip`. No blocking defect was found in the reviewed Day 17 production/test changes. Tests are treated as green under the user's standing instruction to report failures; this is user-provided verification context, not an independent run or a detailed per-command log. No tests, builds or migrations were run by the assistant, and no separate frontend build result is claimed.

`GameService.submitMove(gameId, requestingPlayerId, expectedVersion, move)` now coordinates participant/turn checks, exact legal application, resulting-position assessment, current-FEN persistence, stale/concurrent-command rejection and atomic checkmate/stalemate completion with reservation release. `GameMoveResult.from(game, assessment)` owns mechanical result mapping; `Game.finish()` calls its extracted switch-based `validateResult()`. There is no separate production GameMoveService.

V6 adds completion fields and consistency constraints. GameStatus is IN_PROGRESS/FINISHED, with separate result, termination reason and finish time. The unchanged game GET response fields can now carry an advanced FEN and FINISHED status; version/result/reason/time remain absent from that HTTP response. No HTTP move route, move history, frontend gameplay or disconnect policy is added. See the [reviewed implementation and verification commands](day17-gameplay-service.md).

Day 16 and earlier milestone sections below retain historical behavior/evidence. Their initial-only and pending-gameplay descriptions do not override this checkpoint. The earlier closing-test passing-status uncertainty is retained as historical provenance; current test status follows the user's later standing instruction.

### Historical Day 16 review provenance

Reviewed on 28 October 2026 against `chess-web-app-day16-end.zip`. Source inspection confirms the legality-enforcing facade, complete production strategy registration, facade regression tests and both [closing checks](day16-closing-checks.md). No blocking defect was found in the reviewed Day 16 changes. The user reports all tests green through increments 1–2. Both later closing tests are present, but no subsequent explicit passing report was supplied for them. No tests or builds were run independently; no detailed test log or separate frontend build result was supplied.

The Day 16 section describes the inspected implementation and supersedes earlier initial-position-only boundary and test-only-construction statements. Historical sections retain the behavior and evidence available at those checkpoints.

## Day 16 source-reviewed milestone — historical

The legality-enforcing pure rules facade, special-move/assessment regressions and both closing checks are present in the reviewed Day 16 source. No blocking defect was found in these changes. The user reports all tests green through increments 1–2; a later passing report for the closing checks is not yet recorded.

`ChessRules` exposes `initialPosition()`, `legalMoves(position)`, `applyMove(position, move)` and `evaluate(position)`. Lookup returns the complete immutable legal set. Application requires exact membership in that set, including the promotion choice, before delegating to `MoveApplier`. Assessment returns `PositionStatus` for the side to move. The existing pipeline remains shared; no per-piece application services or duplicate movement algorithms are introduced.

`GameService` still calls only `initialPosition()` and serializes it for creation. Games remain at their initial FEN in `IN_PROGRESS`. Move submission, persistence/history, gameplay concurrency, terminal lifecycle and active-reservation release remain pending. No endpoint, DTO, migration or frontend implementation changes in Day 16. Authorization, transactions, persistence and concurrency remain service responsibilities.

## Next milestone decision

Minimal frontend gameplay is implemented and source-reviewed. Apply the two small inline guard/message follow-ups if not already done; no new application feature is selected by this review.

Discuss the next coherent milestone in a new chat. Candidates include persisted move history or authenticated, authorized real-time updates. Clocks, additional draw rules and disconnect/abandonment policy remain distinct decisions.

A board library is optional post-MVP polish. The custom board is the current implementation; do not reintroduce library integration as an MVP prerequisite.

## Working-session numbering

Working Days 1–19 identify actual development sessions and differ from the original 30-working-day estimate. Earlier work completed some planned later capabilities early. This page records implemented sessions and remaining capabilities; it does not establish a new complete 30-day schedule.

## Completed working sessions

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

## Original-plan reconciliation

Earlier planning placed security around original Day 5, connected login/profile/frontend around Days 6–7, rules/library selection around Day 8 and game persistence around Day 9. Those original estimates are distinct from the working-session labels above. The project selected its own rules implementation with a future library-adapter option. Initial persistence arrived before rules completion. The full original schedule is not reproduced here.

## Day 10 completion boundary — historical

King movement is one square in all eight directions. Shared filtering examines the moving side's king on each resulting board. A king move removes both rights for that color; returning never restores them. Tests cover both colors, attacks after vacating the source, king adjacency, captures, pinned attackers, pawn/knight attack directions, metadata and existing-piece regressions.

Castling execution, pawn movement, outcomes and service/HTTP integration were excluded from Day 10. Pawn movement and promotion subsequently arrived in Days 11–12, en passant in Day 13 and standard castling in Day 14. Position assessment arrived in Day 15; persisted outcomes and service/HTTP integration remain pending.

## Remaining capabilities to schedule

- Move history and a separately selected history/replay interface.
- Authenticated, authorized real-time delivery and appropriate internal events.
- Clocks and any separately selected timeout/disconnect/abandonment policy.
- Additional draw rules, resignation or draw agreement if selected.
- Queue expiry, acceptance/cancellation and rating behavior if selected.
- Player profile statistics/ELO and account email/password changes if selected.
- Optional board polish or a board-library replacement after a concrete need is established.

HTTP gameplay and the minimal custom-board frontend are implemented. Acceptance, cancellation, rating filters and expiry require explicit scope decisions. Redis/Kafka, spectators, tournaments, chat, engine analysis and distributed scaling remain outside initial scope. AI is outside the MVP. Map-versus-array evaluation remains deferred unless demonstrated benefits justify it, preserving the Board API.

## Engineering constraints

Keep the modular monolith, Player terminology and cross-feature service boundaries. The rules package remains free of Spring/JPA/HTTP concerns. `ChessRules` retains its four pure facade operations; GameService now owns persisted move application and completion. Single-source generation cannot determine checkmate/stalemate. Candidate generation excludes king captures; attack detection includes pinned attackers. FEN is structural state, not proof of reachability or repetition history. Preserve useful comments, coherent FEN fixtures, selected board renderings, minimal frontend testing and user-managed secrets/configuration.

## Day 13 completed checkpoint — historical, 27 October 2026

En passant is implemented for both colors. Generation requires the correct source rank, one forward diagonal step, matching target metadata, an empty destination and an adjacent opposing pawn. An inconsistent or unavailable opportunity contributes no en passant candidate and does not suppress ordinary moves. Attempting an omitted capture fails exact candidate membership with `IllegalArgumentException`; no unsupported-en-passant exception remains.

`MoveApplier` validates candidate membership before identifying a pawn's diagonal move into an empty square as en passant. It relocates the pawn and removes the opposing pawn from `(destination.file, source.rank)` through immutable Board operations. `Board.removePiece()` requires a nonnull occupied square and performs placement only; callers own chess-rule validation. The original position remains unchanged.

Shared handling switches turns, resets the pawn halfmove clock, increments the fullmove number after Black, and expires the target. En passant preserves existing castling rights. Ordinary moves, promotion choices and irreversible king/rook rights removal remain supported. An occupied diagonal follows ordinary capture rules even if inconsistent target metadata names it; no adjacent pawn is removed in that case. Promotion choices attached to en passant fail candidate membership.

`LegalMoveGenerator` remains shared and unchanged. It checks the moving side's king after both pawn squares have been vacated. Tests cover both colors, both capture directions in generation, FEN results, original-position preservation, missing/inconsistent metadata, absent/friendly/non-pawn adjacent pieces, wrong source ranks, occupied targets, opportunity expiry, ordinary advances instead of capture, castling-right preservation, invalid promotion choices, exposed rook/bishop attacks and removal of checking pawns. Board tests cover immutable removal and invalid removal inputs. The three obsolete unsupported-case tests were replaced. Selected tests use `com.chessapp.BoardTestRenderer`.

The user reports all tests green for the final Day 13 archive. Review was by source inspection; no independent tests or builds were run.

Movement remains internal pure Java code. `ChessRules` exposes only `initialPosition()`; `GameService` still stores the initial FEN and does not apply moves. Day 13 changes no HTTP contract, DTO, migration or frontend implementation. Castling execution, complete legal-move enumeration, outcomes, move history and gameplay integration remain pending. Generation handles a single source; an empty result cannot establish checkmate or stalemate. FEN parsing is structural, not proof of legality or reachability; the implementation does not reconstruct the preceding move from history. Board equality is identity-based, repetition requires history, and authorization, transactions, persistence and gameplay concurrency remain service responsibilities. AI and map-versus-array evaluation remain deferred.

## Day 14 boundary — historical, 27 October 2026

Standard castling for both colors and both sides is implemented internally. Candidate generation checks rights and placement; application relocates both pieces; legal filtering checks starting, transit and destination safety. See [architecture.md](architecture.md) for responsibilities and verification provenance. Day 14 changes no endpoint, DTO, migration or frontend implementation.

Movement remains internal pure Java code. `ChessRules` exposes only `initialPosition()`; `GameService` creates and stores the initial FEN and does not apply moves. Complete legal-move enumeration, outcomes, move history and gameplay integration remain pending. Single-source generation cannot establish checkmate or stalemate. FEN parsing remains structural, Board equality remains identity-based, and repetition requires history. Authorization, transactions, persistence and gameplay concurrency remain service responsibilities. AI and map-versus-array evaluation remain deferred.
