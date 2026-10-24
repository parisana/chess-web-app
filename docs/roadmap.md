# Roadmap Reconciliation

Reviewed on 24 October 2026 against `chess-web-app-day7-end.zip` (archive commit `323715a1a49cb03867bcfd0b20096436f04d744f`). The user reports that all tests are green for this Day 7 checkpoint. This review inspected source and documentation only; no independent tests or builds were run. Separate frontend build/manual-check results were not supplied. Fixture corrections recorded in the development-progress document have not been applied or rerun as part of this review.

## How to interpret day numbers

The original plan estimated 30 working days at roughly eight hours per day. Actual implementation changed order and completed some later milestones early. Our working-session labels (`Day 1` through `Day 7`) are therefore distinct from the original schedule's day numbers. Day 5 is now explicitly assigned to current-player username editing.

Use the current source and [development-progress.md](development-progress.md) to establish what exists. Use this page to distinguish historical estimates from newly agreed work. Do not infer the next scope solely from a ZIP filename or an old “next milestone” paragraph.

## Original milestones checked during reconciliation

The following is a summary retrieved from earlier planning conversations, not a verbatim copy of the complete original 30-day plan. The uploaded checkpoint did not contain that full plan. No complete replacement schedule is established by this page.

| Original schedule | Planned outcome | Current implementation |
| --- | --- | --- |
| Day 5 | Spring Security, JWT generation/verification, authentication handling, principal extraction and valid/invalid/expired token tests | Implemented; Spring Security resource-server support handles bearer authentication without a custom JWT filter |
| Day 6 | Login, profile read/update, Angular authentication services/forms and connected registration/login | Login, profile read, username update and frontend flow implemented; email/password changes deferred |
| Day 7 | Protected frontend routes/profile, logout, unauthorized handling and auth-flow review/testing | Core guard, profile loading, logout, 401 handling and focused tests implemented; no claim of exhaustive testing |
| Day 8 | Choose a Java chess library, define rules/game-state interfaces and test legal/illegal moves | Own pure Java rules implementation selected; representation/FEN/initial-position boundary complete; knight-only candidate application and king-safety filtering implemented; complete legal gameplay pending. A future library adapter remains possible |
| Day 9 | Persist games/participants with migrations, repositories and persistence tests | Initial game persistence with participant IDs and tests implemented; no separate participant entity is currently needed |

Matchmaking pairing and active-game reservation work are also already implemented. This crosswalk does not imply that all intermediate or later original tasks are complete.

## Current working-session status

| Working session | Status |
| --- | --- |
| Days 1–3 | Authentication/profile foundation, frontend authentication and persistent queue delivered across the earlier sessions |
| Day 4 | Initial game persistence, active-player reservations, transactional pairing, matched-game discovery and participant-protected retrieval complete |
| Day 5 | Username editing via authenticated PATCH, backend integration tests and a minimal Angular editor complete |
| Day 6 | Pure Java representation/FEN and initial-position rules boundary complete; user reports all local tests passing |
| Day 7 | Movement scope implemented: Move, immutable relocation, knight candidate/application/filtering, all-piece attack detection and test visualization. User reports green tests; review found fixture cleanup |

Reviewed on 24 October 2026 against `chess-web-app-day7-end.zip` (archive commit `323715a1a49cb03867bcfd0b20096436f04d744f`). The user reports that all tests are green for this Day 7 checkpoint. This review inspected source and documentation only; no independent tests or builds were run. Separate frontend build/manual-check results were not supplied. Fixture corrections recorded in the development-progress document have not been applied or rerun as part of this review.

## Remaining scope to schedule

These are known pending capabilities, not an agreed order or daily commitment:

- Account email/password changes, if selected for a future increment.
- Complete our rules implementation and server-authoritative legal moves; a library adapter is an optional future alternative.
- Move persistence/history and gameplay concurrency handling.
- Chessboard/game UI.
- Game lifecycle/results and safe release of active-player reservations.
- Internal application events and authenticated, authorized real-time delivery.
- Queue lifecycle improvements and clocks, where selected for the MVP.

Specific behavior for acceptance, cancellation, rating filters and expiry still needs an explicit scope decision. Their absence does not automatically make them the next milestone.

## Agreed Day 5 scope

Goal: allow an authenticated player to change their username and see the returned profile immediately in the existing frontend session.

Included: PATCH `/api/v1/players/me`, registration-consistent username validation, same-name no-op behavior, duplicate conflict handling, PostgreSQL/HTTP integration tests, a minimal Angular username form, API error documentation and checkpoint reconciliation.

Deferred: email/password changes, normalization, restricted username characters, player optimistic locking, gameplay rules, notifications and game lifecycle changes. No schema change is required.

Completion checks: backend regression tests, existing minimal frontend tests/build, and manual save/conflict/validation/session-display checks. Implementation is recorded as delivered; see `development-progress.md` for the limits of verification evidence.

## Day 7 movement pipeline

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

Fixture corrections are listed in [development-progress.md](development-progress.md). They do not change the implemented movement scope.

## Proposed next working session

Extend the pure Java pipeline to sliding movement, starting with one bounded rook increment. Introduce shared piece dispatch when the second generator requires it, and extend MoveApplier to remove castling rights when a rook moves. Reuse candidate application, square-attack detection and king-safety filtering instead of creating per-piece application/filtering classes.

Bishop and queen generation, pawn movement/promotion/en passant, king movement/castling and full legal-move enumeration remain to be scheduled incrementally. A gameplay endpoint, persistence/history, concurrency handling, outcomes and reservation release require later service-level work. This proposal is not implemented Day 8 work.

Deferred TODO: evaluate a 64-entry array against map storage after the MVP, or earlier if concrete benefits justify it; preserve Board API. AI selection is outside the MVP. Repetition detection requires history beyond FEN.
