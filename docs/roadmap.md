# Roadmap Reconciliation

Checkpoint documentation updated on 25 October 2026. The source baseline inspected was `chess-web-app-day8-start.zip` (archive commit `79871c70c4fe8d0065c61795ab37daf26642ba2d`). Both Day 7 MoveApplierTest fixture corrections were present in that archive. Day 8 implementation and test additions were supplied incrementally during the session; subsequent user replies reported no problems. No independent tests or builds were run. A fresh end-of-Day-8 source archive and its final verification report have not yet been reviewed. Day 8 sections describe the guided changes, not independently inspected final source.

## How to interpret day numbers

The original plan estimated 30 working days at roughly eight hours per day. Actual implementation changed order and completed some later milestones early. Our working-session labels (`Day 1` through `Day 8`) are therefore distinct from the original schedule's day numbers. Day 5 is now explicitly assigned to current-player username editing.

Use the current source and [development-progress.md](development-progress.md) to establish what exists. Use this page to distinguish historical estimates from newly agreed work. Do not infer the next scope solely from a ZIP filename or an old “next milestone” paragraph.

## Original milestones checked during reconciliation

The following is a summary retrieved from earlier planning conversations, not a verbatim copy of the complete original 30-day plan. The uploaded checkpoint did not contain that full plan. No complete replacement schedule is established by this page.

| Original schedule | Planned outcome | Current implementation |
| --- | --- | --- |
| Day 5 | Spring Security, JWT generation/verification, authentication handling, principal extraction and valid/invalid/expired token tests | Implemented; Spring Security resource-server support handles bearer authentication without a custom JWT filter |
| Day 6 | Login, profile read/update, Angular authentication services/forms and connected registration/login | Login, profile read, username update and frontend flow implemented; email/password changes deferred |
| Day 7 | Protected frontend routes/profile, logout, unauthorized handling and auth-flow review/testing | Core guard, profile loading, logout, 401 handling and focused tests implemented; no claim of exhaustive testing |
| Day 8 | Choose a Java chess library, define rules/game-state interfaces and test legal/illegal moves | Own pure Java rules implementation selected; representation/FEN/initial-position boundary complete; knight candidate application and king-safety filtering implemented in Day 7; guided Day 8 adds rooks and Strategy dispatch; complete legal gameplay pending. A future library adapter remains possible |
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
| Day 8 | Bounded rook increment supplied: Strategy dispatch, rook candidates, shared application/filtering, rook movement/capture castling rights, and focused tests. User reports no problems; final source review pending |

Checkpoint documentation updated on 25 October 2026. The source baseline inspected was `chess-web-app-day8-start.zip` (archive commit `79871c70c4fe8d0065c61795ab37daf26642ba2d`). Both Day 7 MoveApplierTest fixture corrections were present in that archive. Day 8 implementation and test additions were supplied incrementally during the session; subsequent user replies reported no problems. No independent tests or builds were run. A fresh end-of-Day-8 source archive and its final verification report have not yet been reviewed. Day 8 sections describe the guided changes, not independently inspected final source.

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

## Day 7 movement pipeline — historical checkpoint

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

Both Day 7 fixture corrections are present in the inspected Day 8 starting archive; see [development-progress.md](development-progress.md) for verification provenance.

## Remaining Day 8 checkpoint work and next scope

The guided Day 8 implementation is supplied. Final source review against a fresh archive and a final local regression-run report remain pending. This documentation bundle records the agreed changes without claiming inspection of the updated local repository.

A later increment may add bishop movement and consider sharing sliding-ray traversal with rooks, then add queen movement. Confirm that scope separately. Pawn movement/promotion/en passant, king movement/castling, and complete legal-move enumeration remain later work. Gameplay persistence/history, endpoints, outcomes, concurrency and reservation release remain service-level work.

## Day 8 rook movement and Strategy dispatch

This section supersedes the knight-only limitations in the historical Day 7 section. It documents changes supplied during the guided session; the final updated source archive has not yet been inspected.

- `PiecePseudoLegalMoveGenerator` is the strategy interface, exposing `pieceType()` and `generate(position, from)`.
- `KnightMoveGenerator` and `RookMoveGenerator` implement that interface. Their generation is pseudo-legal: own-king safety is checked later.
- `PseudoLegalMoveGenerator` registers strategies by PieceType, rejects duplicate registrations, and delegates by the source piece's type. Empty and opposing sources return no moves; unsupported own piece types throw UnsupportedOperationException instead of silently returning an empty set.
- Registration uses EnumMap; Map.copyOf stores an immutable snapshot. The stored map need not retain EnumMap representation or iteration order. The dispatcher only relies on lookup.
- Rook generation scans four orthogonal rays. Empty squares are candidates. The first opposing non-king occupant is a capture candidate; any occupant ends the ray. An opposing king blocks the ray but is never a capture candidate. SquareAttackDetector separately recognizes attacks on king squares.
- Shared MoveApplier and LegalMoveGenerator now depend on PseudoLegalMoveGenerator. No per-piece application or filtering classes are added.
- MoveApplier removes castling rights when a rook leaves its original corner or a rook is captured on its original corner. One move can remove rights for both colors. Returning a rook never restores a right. Turn, counters, en passant expiry and immutability behavior remain shared.
- Rook legal-move tests cover White and Black pins, moves along a pin line, blocking check and capturing the checking attacker. Existing knight tests remain regression coverage for the shared pipeline. Selected rook tests render boards through com.chessapp.BoardTestRenderer.

ChessRules still exposes only initialPosition(). GameService does not apply moves. No HTTP endpoint, migration, frontend behavior or persisted gameplay command changes in this increment. Empty knight/rook subsets do not establish checkmate or stalemate. King movement will require checking the king's destination square. Castling execution, pawn moves and promotion remain unsupported.
