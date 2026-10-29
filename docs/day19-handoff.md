# After Day 19 — next working session

Continue from the user's actual chess-web-app-day19-end.zip plus the source-reviewed Day 19 documentation replacements and any subsequently applied inline fixes. Recheck actual source before proposing implementation.

## Working agreement

The user implements inline solutions in coherent increments. Do not silently generate/apply an entire implementation. Documentation replacement packages are welcome when requested. Do not execute tests/builds unless explicitly requested. Treat tests as green unless the user reports failure; do not pause for routine pass confirmation. Preserve user-managed configuration.

Keep the modular monolith, conventional feature layers, Player terminology, cross-feature service boundaries and pure Java rules. Frontend tests stay minimal. Game and board components use separate HTML/CSS files.

## Reviewed state

Source-reviewed on 29 October 2026 against the actual Day 19 ZIP and Day 18 baseline. No blocking defect was found in Day 19. The assistant ran no application tests/builds/migrations/browser checks and claims no independently verified frontend build or manual acceptance result.

Backend sources/tests/configuration are unchanged from Day 18. The HTTP command derives Player identity from JWT, validates request shape, applies service-owned legality/version/lifecycle checks and returns GameMoveResult. GET exposes version and completion fields but not positionStatus. Checkmate/stalemate persist results and atomically release reservations.

Frontend now has guarded game navigation, a custom oriented board, explicit promotion/cancellation, versioned HTTP moves, manual refresh, stale-state reload without retry, uncertain-outcome blocking and completion feedback. selectedSquare is cleared before emitting a proposal. Game owns orchestration; ChessBoard owns display/selection and emits coordinates.

The custom board is selected for the MVP. cm-chessboard dependency/declarations/assets are removed. A library is optional post-MVP polish. package.json is unchanged; lockfile patch updates and formatting-only angular.json changes were preserved.

## First follow-ups

Check whether the two minor inline changes from docs/day19-frontend-gameplay.md were applied after archiving:

1. Include submitting() in the Game.refresh() method guard, matching its button.
2. Clear moveMessage after a successful GET, alongside needsRefresh.

The documentation replacement package does not apply production changes. Do not assume these follow-ups are already in the source.

Read README.md, frontend/README.md, docs/architecture.md, docs/development-progress.md, docs/roadmap.md, docs/api-contracts.md and docs/frontend-gameplay-flow.md. The latter explains the lifecycle, signals/computed/effect, inputs/outputs and RxJS read flow.

## Next milestone selection

Discuss and select the next milestone before implementation. Candidates include persisted move history or authenticated real-time updates. Do not silently expand into both.

History, real-time delivery, clocks, extra draw rules, acceptance and disconnect/abandonment policy remain separate scope decisions. Closing a page/disconnecting does not finish the game or release reservations. Sessions remain in memory; browser reload requires login. No legal-move highlighting or persistent check indicator is supplied.

Start a new milestone in a new chat. Preserve this working agreement and recheck the user's latest source.
