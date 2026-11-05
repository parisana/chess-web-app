# After Day 28 — next working session

Continue from actual user chess-web-app-day28-end.zip plus these documentation replacements and any subsequent user-applied changes. Never substitute an assistant-generated implementation archive. Reviewed on 5 November 2026.

Read README, day28-ui-review, day28-readability-cleanup, architecture, roadmap, API contracts and frontend README first; session/tab companions retain Day 27 details.

## Working agreement

User applies coherent inline increments. Conventional modular monolith/feature layers; Player naming; cross-feature services/events; pure Java rules; YAML/user-managed configuration; useful comments; separate Angular HTML/CSS; minimal frontend tests. Keep short method declarations and invocations on one line when readable. John Doe is the example name for an anonymous example person; do not invent historical participant details. No assistant builds/tests without explicit request; user reports green. New major milestones begin in a new chat.

## Current source

Warm amber shared shell with code-selectable blue/green; board-first Home and game workspace; projected board between clock rows; saved starting names/Elo through V15__add_game_participant_snapshots.sql; destination-square icon promotion popup; versioned legal hints and two-square illegal feedback. Inactive clock is dimmed; active clock shimmers diagonally. Clocks remain server-authoritative with calculatedAt and no pause semantics.

Game actions now contain incoming draw responses and resign/accept confirmations. MoveHistoryReplay owns inline notation and stretched compact controls; Game owns replay cursor/permissions/requests. Messages/activity has feedback and placeholder chat only. Generic game route remains /games/:gameId. Home resumes active games; reviewing a finished game shows Return to current game when applicable. Find another match starts a search directly.

GameCommandService replaces the old GameService name for mutations. GameQueryService owns game/history/legal/list/active-ID reads. GameExportService remains separate and still uses current Player names for PGN. Self-only GET /api/v1/games returns a Slice-backed, subject-relative list; /games stores page in query parameters. Other-Player visibility is not implemented.

## Closing proposals versus applied source

The archive still contains unused Game.moveTokens and long constructor/ngOnInit blocks. day28-readability-cleanup proposes removing that computed and extracting subscription setup into named methods, preserving order/operators/guards and constructor injection context. It also shows an optional local game-actions ng-template. These are instructions, not edits already applied to the uploaded implementation. Ask for updated source only when exact subsequent changes are necessary; do not assume these proposals were applied.

## Evidence and limits

User reports everything green. Assistant performed source/document inspection only; no application execution. No previous-archive diff totals, comprehensive UI acceptance, V14-to-V15 isolated upgrade, SQL plan benchmark or complete auth-race proof is claimed. Deep-read tests and query-counter boundaries are in the review. Day 27 session/tab/socket limits remain. Promotion chooser legality gating and intermittent action-click investigation remain deferred; no silent retry.

## Next milestone

Select separately. Portfolio preparation is a candidate; chat/rematch, public history, additional draws and match acceptance/abandonment are options, not authorization. Preserve PGN UI, replay, promotion, timeout and rating deferrals. Avoid expanding cleanup into a new state-management architecture.
