# Roadmap

Reviewed on 27 October 2026 against `chess-web-app-day12-end.zip`. Source inspection confirms explicit promotion choices, candidate generation, immutable replacement, shared metadata handling, renderer updates and associated tests. No blocking defect was found in the reviewed Day 12 changes. The user reports all tests green. No independent tests or builds were run; no detailed test log or separate frontend build result was supplied.

## Working-session numbering

Working Days 1–12 identify actual development sessions and differ from the original 30-working-day estimate. Earlier work completed some planned later capabilities early. This page records implemented sessions and remaining capabilities; it does not establish a new complete 30-day schedule.

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

The final Day 12 archive contains the completed promotion changes. The user reports all tests green; source review found no blocking defect in the reviewed changes. Tests/builds were not run independently.

## Original-plan reconciliation

Earlier planning placed security around original Day 5, connected login/profile/frontend around Days 6–7, rules/library selection around Day 8 and game persistence around Day 9. Those original estimates are distinct from the working-session labels above. The project selected its own rules implementation with a future library-adapter option. Initial persistence arrived before rules completion. The full original schedule is not reproduced here.

## Day 10 completion boundary — historical

King movement is one square in all eight directions. Shared filtering examines the moving side's king on each resulting board. A king move removes both rights for that color; returning never restores them. Tests cover both colors, attacks after vacating the source, king adjacency, captures, pinned attackers, pawn/knight attack directions, metadata and existing-piece regressions.

Castling execution, pawn movement, outcomes and service/HTTP integration were excluded from Day 10. Pawn movement and promotion subsequently arrived in Days 11–12; the other capabilities remain pending.

## Proposed next increment — Day 13

En passant capture execution is the proposed next bounded milestone; it is not implemented yet. Inspect the Day 12 checkpoint before selecting exact changes.

- Validate the target, source/destination geometry, empty destination and opposing captured pawn on the adjacent square. FEN metadata alone does not establish a valid capture.
- Apply source removal, destination placement and off-destination captured-pawn removal immutably, preserving ordinary moves and promotions.
- Preserve turn changes, pawn halfmove resets, Black fullmove increments, target expiry and irreversible castling-right handling.
- Test both colors, expiry, invalid metadata/captured-pawn cases, immutability, FEN results and resulting-board king safety. In particular, removing two pawns can expose a rook or bishop attack on the moving king.
- Replace the conservative unsupported guard with explicit supported behavior and define malformed-metadata handling deliberately.

Castling execution, outcomes and service/HTTP integration remain separate later work. Day 13 is a proposed scope, not an implementation claim.

## Remaining capabilities to schedule

- Complete en passant capture and castling execution.
- Complete legal-move enumeration and game outcomes.
- Server-authoritative gameplay commands, persistence/history and concurrent-update handling.
- Game lifecycle/results and safe active-reservation release.
- Chessboard/game UI and clocks, where selected for the MVP.
- Internal events and authenticated, authorized real-time delivery; WebSocket/STOMP remains a candidate.
- Queue lifecycle improvements such as expiry and any selected acceptance/cancellation/rating behavior.
- Account email/password changes if selected.

Acceptance, cancellation, rating filters and expiry require an explicit scope decision. Redis/Kafka, spectators, tournaments, chat, engine analysis and distributed scaling remain outside initial scope. AI is outside the MVP. Map-versus-array storage evaluation stays deferred unless demonstrated benefits justify it, preserving the Board API.

## Engineering constraints

Keep the modular monolith, Player terminology and cross-feature service boundaries. The rules package remains free of Spring/JPA/HTTP concerns. `ChessRules` still exposes only `initialPosition()`; no service applies moves. A subset of supported pieces cannot determine checkmate/stalemate. Candidate generation excludes king captures; attack detection includes pinned attackers. FEN is structural state, not proof of reachability or repetition history. Preserve useful comments, coherent FEN fixtures, selected board renderings, minimal frontend testing and user-managed secrets/configuration.

## Day 12 completed checkpoint — 27 October 2026

Promotion is implemented for both colors, for quiet advances and captures, with explicit queen, rook, bishop and knight choices. Ordinary moves retain the two-argument constructor. No choice defaults to queen.

`Move` validates replacement-type shape. Position-dependent validity is established by exact pseudo-legal candidate membership, including the choice. `MoveApplier` preserves the pawn's color and applies the replacement through an immutable Board operation; `LegalMoveGenerator` checks the moving side's king on the resulting board.

Pawn halfmove resets, Black fullmove increments, turn changes, en passant expiry and captured-corner-rook castling-right removal remain shared. A promoted corner rook never grants castling rights. The test renderer lists promotion choices distinctly.

The user reports all tests green for the final Day 12 archive. Review was by source inspection; no independent tests or builds were run.

Movement remains internal pure Java code. `ChessRules` exposes only `initialPosition()`; `GameService` still stores the initial FEN and does not apply moves. Day 12 changes no HTTP contract, DTO, migration or frontend implementation. En passant capture execution, castling execution, complete legal-move enumeration, outcomes, history and gameplay integration remain pending. A source-specific result cannot establish checkmate or stalemate.
