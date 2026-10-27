# Roadmap

Reviewed on 27 October 2026 against `chess-web-app-day15-end.zip`. Source inspection confirms position-wide legal-move enumeration and `NORMAL` / `CHECK` / `CHECKMATE` / `STALEMATE` assessment for the side to move. No blocking defect was found in the reviewed Day 15 changes. The user reports all tests green after the final guided regressions. No independent tests or builds were run; no detailed test log or separate frontend build result was supplied.

## Working-session numbering

Working Days 1–15 identify actual development sessions and differ from the original 30-working-day estimate. Earlier work completed some planned later capabilities early. This page records implemented sessions and remaining capabilities; it does not establish a new complete 30-day schedule.

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

The Day 15 source adds complete enumeration and check/checkmate/stalemate assessment on top of the Day 14 movement pipeline. No blocking defect was found in the reviewed Day 15 changes. The user reports all tests green after final regressions. No tests/builds were run independently.

## Original-plan reconciliation

Earlier planning placed security around original Day 5, connected login/profile/frontend around Days 6–7, rules/library selection around Day 8 and game persistence around Day 9. Those original estimates are distinct from the working-session labels above. The project selected its own rules implementation with a future library-adapter option. Initial persistence arrived before rules completion. The full original schedule is not reproduced here.

## Day 10 completion boundary — historical

King movement is one square in all eight directions. Shared filtering examines the moving side's king on each resulting board. A king move removes both rights for that color; returning never restores them. Tests cover both colors, attacks after vacating the source, king adjacency, captures, pinned attackers, pawn/knight attack directions, metadata and existing-piece regressions.

Castling execution, pawn movement, outcomes and service/HTTP integration were excluded from Day 10. Pawn movement and promotion subsequently arrived in Days 11–12, en passant in Day 13 and standard castling in Day 14. Position assessment arrived in Day 15; persisted outcomes and service/HTTP integration remain pending.

## Proposed next increment — Day 16

Expose a legality-enforcing pure rules boundary before service/HTTP integration. Review the current source before finalizing operation names and return types.

- Extend the application-owned ChessRules interface and StandardChessRules implementation with complete legal-move lookup, legal move application and position assessment as appropriate to the immediate use case.
- Accept a move only when its exact value, including promotion choice, belongs to the legal set. Direct MoveApplier membership is pseudo-legal and does not establish king safety.
- Reuse the existing generators, applier and evaluator; wire their construction behind the rules boundary without duplicating movement algorithms.
- Add focused facade tests for ordinary and special moves, rejected unsafe/promotion proposals, metadata and original-position preservation. Preserve existing initial-position construction and game creation.
- Keep HTTP, persistence/history, concurrent gameplay updates and reservation release for a later bounded milestone.

This is proposed scope, not implemented behavior. See [day16-handoff.md](day16-handoff.md).

## Remaining capabilities to schedule

- Legality-enforcing application rules boundary and any selected remaining draw rules.
- Server-authoritative gameplay commands, persistence/history and concurrent-update handling.
- Game lifecycle/results and safe active-reservation release.
- Chessboard/game UI and clocks, where selected for the MVP.
- Internal events and authenticated, authorized real-time delivery; WebSocket/STOMP remains a candidate.
- Queue lifecycle improvements such as expiry and any selected acceptance/cancellation/rating behavior.
- Account email/password changes if selected.

Acceptance, cancellation, rating filters and expiry require an explicit scope decision. Redis/Kafka, spectators, tournaments, chat, engine analysis and distributed scaling remain outside initial scope. AI is outside the MVP. Map-versus-array storage evaluation stays deferred unless demonstrated benefits justify it, preserving the Board API.

## Engineering constraints

Keep the modular monolith, Player terminology and cross-feature service boundaries. The rules package remains free of Spring/JPA/HTTP concerns. `ChessRules` still exposes only `initialPosition()`; no service applies moves. Single-source generation cannot determine checkmate/stalemate. Candidate generation excludes king captures; attack detection includes pinned attackers. FEN is structural state, not proof of reachability or repetition history. Preserve useful comments, coherent FEN fixtures, selected board renderings, minimal frontend testing and user-managed secrets/configuration.

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
