# Roadmap Reconciliation

Reviewed on 24 October 2026 against `chess-web-app-day7-start.zip` (archive commit `16637bef7414cf6e55d8b4b099b786325f5fe2a1`), the completed Day 6 checkpoint. The user explicitly reports that all local tests passed. This review inspected source and documentation only; no independent tests or builds were run. Separate frontend build/manual-check results were not supplied.

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
| Day 8 | Choose a Java chess library, define rules/game-state interfaces and test legal/illegal moves | Own pure Java rules implementation selected; representation/FEN/initial-position boundary complete; legal-move processing pending. A future library adapter remains possible |
| Day 9 | Persist games/participants with migrations, repositories and persistence tests | Initial game persistence with participant IDs and tests implemented; no separate participant entity is currently needed |

Matchmaking pairing and active-game reservation work are also already implemented. This crosswalk does not imply that all intermediate or later original tasks are complete.

## Current working-session status

| Working session | Status |
| --- | --- |
| Days 1–3 | Authentication/profile foundation, frontend authentication and persistent queue delivered across the earlier sessions |
| Day 4 | Initial game persistence, active-player reservations, transactional pairing, matched-game discovery and participant-protected retrieval complete |
| Day 5 | Username editing via authenticated PATCH, backend integration tests and a minimal Angular editor complete |
| Day 6 | Pure Java representation/FEN and initial-position rules boundary complete; user reports all local tests passing |
| Day 7 | Proposed: move value, knight pseudo-legal generation, candidate application and progression toward king-safety filtering |

Reviewed on 24 October 2026 against `chess-web-app-day7-start.zip` (archive commit `16637bef7414cf6e55d8b4b099b786325f5fe2a1`), the completed Day 6 checkpoint. The user explicitly reports that all local tests passed. This review inspected source and documentation only; no independent tests or builds were run. Separate frontend build/manual-check results were not supplied.

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

## Proposed Day 7 scope

Goal: build a small, pure Java movement pipeline incrementally, without exposing partial rules as playable chess.

1. Add an application-owned `Move` value with source and destination squares. Validate representation only; add promotion choice when pawn promotion becomes an actual use case.
2. Add knight-only pseudo-legal generation: board edges, side to move, friendly occupancy and enemy captures. Exclude king capture. Use an explicitly knight-specific component; do not advertise a complete move list.
3. Apply generated ordinary knight moves to a new candidate position, preserving the original. Update turn, en passant target, counters and castling rights affected by a captured rook. This is internal candidate application, not an authorized gameplay command.
4. Implement square-attack detection for every piece type, then use it to reject knight candidates that leave the moving side's king attacked. Require valid king fixtures for this stage. Partial move generation cannot safely substitute for attack detection.
5. Add focused tests and reconcile the checkpoint documentation with the work actually completed. If the attack-detection increment needs more time, keep it as the next bounded step rather than claiming legal gameplay is complete.

Pseudo-legal moves obey movement/occupancy rules without establishing the moving king's safety. Legal moves also satisfy king safety and all applicable special-move restrictions. Even after king-safety filtering, a knight-only result is only a subset of legal moves; an empty subset does not establish checkmate or stalemate.

No move endpoint, persistence/history, frontend, outcome, special-move execution or AI work is included. `ChessRules` remains unchanged until an actual service use case requires another operation. Service-layer transactions, authorization and gameplay concurrency stay outside the rules code.

This is a proposal at the Day 7 start checkpoint, not implemented work.

Deferred TODO: evaluate a 64-entry array against map storage after the MVP, or earlier if justified. Preserve the board API. AI move selection is outside the MVP; repetition detection needs history beyond FEN.
