# Roadmap

Reviewed on 26 October 2026 against `chess-web-app-day11-end.zip`. Source inspection confirms the Day 11 ordinary-pawn strategy, shared metadata updates, registration and associated tests. No blocking defect was found in the reviewed changes. The user reports all tests green. No independent tests or builds were run; no detailed test log or separate frontend build result was supplied.

## Working-session numbering

Working Days 1–11 identify actual development sessions and differ from the original 30-working-day estimate. Earlier work completed some planned later capabilities early. This page records implemented sessions and remaining capabilities; it does not establish a new complete 30-day schedule.

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

The final Day 10 archive contains these movement additions, and the user reports all tests green. Source review found no blocking defect in the Day 10 changes; tests/builds were not run independently.

## Original-plan reconciliation

Earlier planning placed security around original Day 5, connected login/profile/frontend around Days 6–7, rules/library selection around Day 8 and game persistence around Day 9. Those original estimates are distinct from the working-session labels above. The project selected its own rules implementation with a future library-adapter option. Initial persistence arrived before rules completion. The full original schedule is not reproduced here.

## Day 10 completion boundary

King movement is one square in all eight directions. Shared filtering examines the moving side's king on each resulting board. A king move removes both rights for that color; returning never restores them. Tests cover both colors, attacks after vacating the source, king adjacency, captures, pinned attackers, pawn/knight attack directions, metadata and existing-piece regressions.

Castling execution, pawn movement, outcomes and service/HTTP integration were excluded from this bounded milestone and remain pending.

## Proposed next increment — Day 12

Promotion is a candidate next bounded milestone: extend the move representation with an explicit promotion choice, generate all four standard choices, apply the selected replacement piece immutably and verify king safety and metadata. The final Day 11 source has been reviewed; promotion is the proposed next scope. En passant capture and castling execution remain separate later increments. This is a proposal, not implemented work.

## Remaining capabilities to schedule

- Complete pawn rules, promotion, en passant and castling execution.
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

## Day 11 completed checkpoint

Ordinary pawn generation for both colors, pawn halfmove-clock resets, double-advance en passant targets, shared registration and king-safety tests are supplied. Available promotion candidates and possible en passant captures fail explicitly; no partial candidate set is returned. These special moves and castling execution remain deferred.

No HTTP contract, DTO, migration, frontend implementation or persisted-game behavior changes. ChessRules exposes only initialPosition(); movement remains internal. The user reports all guided tests green; the final Day 11 source has been inspected with no blocking defect found in the reviewed changes. No tests/builds were run independently.

See [Architecture](architecture.md) for the precise movement boundaries.
