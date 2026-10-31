# After Day 24 — next working session

Continue from actual user chess-web-app-day24-end.zip plus Day 24 documentation replacements and any subsequent inline changes. Never replace the user's baseline with an assistant-generated implementation archive.

## Working agreement

User implements coherent inline increments. Preserve modular monolith/conventional feature layers, Player naming, cross-feature services/events, pure Java rules, useful comments, separate Angular HTML/CSS, minimal frontend tests and user-managed configuration. Do not run tests/builds without explicit request. Treat tests as green unless a failure is reported. Include a brief what-to-understand note with test increments and identify important tests for deep reading. Documentation replacements are welcome. New major milestones start in a new chat.

## Reviewed checkpoint

Source-reviewed on 31 October 2026 against actual user Day 24 ZIP, compared with Day 23 source including documentation replacements. Four files added, nine changed, none removed; twelve source/test paths plus roadmap.md. No blocking issue found in inspected changes. Core rules/GameService/repositories/entities/migrations/config/dependencies unchanged. User reports increments working; tests follow the standing green-unless-reported agreement. No assistant application tests/builds/migrations/browser checks ran. User did not manually verify route-switch replay reset; source contains the reset. GameMigrationIntegrationTest.java naming is now confirmed in source; do not request cleanup again.

## Implemented Day 24

Backend GET /api/v1/games/{gameId}/pgn is participant-protected. GameExportService loads/authorizes before availability disclosure, captures GameResponse/count, reads append-only history through that count and resolves current usernames via PlayerService. PgnFormatter lives in game.export outside pure rules and reuses persisted SAN. It rejects incomplete history, row-count mismatch, sequence gaps and missing/blank SAN with 409 PGN_EXPORT_UNAVAILABLE. ID/version mismatch is an internal argument failure. No SAN reconstruction or legality replay.

Headers include current usernames, UTC creation date, result and custom-baseline FEN/SetUp. Move numbering comes from baseline side/fullmove, never game version/recorded sequence. Active result is *; finished markers are 1-0/0-1/1/2-1/2. Lifecycle-only completion adds no move. Controller emits UTF-8 application/x-chess-pgn attachment game-{id}.pgn with no-store. Count bounding relies on atomic append-only writes, not general repeatable-read or a new concurrent-export test.

PgnFormatterTest has eight methods/ten invocations (three result cases). GameReadIntegrationTest adds six methods/seven invocations for both participants/headers, auth, missing game, membership before legacy rejection, persisted e4 plus resignation and pre-SAN null compatibility. Existing test @Transactional rollback fixtures are appropriate for these read/format assertions. Tests do not establish external importer compatibility or every lifecycle export/concurrency path.

Angular replayIndex=null follows game.currentFen; zero displays baseline; n displays resultingFen after recorded move n. replayHistory validates game identity, version not ahead, count and sequences while allowing an older accepted list. displayedFen feeds board/FEN details. All numeric selections disable shared mutation permission, including the final entry. Latest alone returns to live following. Start/Previous/Next/entry clicks cancel pending promotion/confirmation; POST blocks replay navigation. Live updates refresh game/history without changing the cursor. Route setup and history-clearing 403/404 reset it. Previous from live uses available history length minus one, which may lag the actual board when catching up.

## Explicitly deferred

The user chose to defer only the Angular PGN download UI. Backend endpoint/tests remain implemented. The proposed Blob client/button/error/cancellation code is absent; do not claim it exists or remove the backend. Replay does not require PGN.

Automatically returning to live when Next reaches the true synchronized end was discussed only. Current Next stays read-only and Latest is explicit. No implementation authorized.

The user noticed promotion selection can appear before king-safety rejection and wants to fix that personally later. Document it as premature UI choice, not proof the backend accepts self-check. A moderate future version-associated legal-move API/frontend extension can gate selection, reusing existing rules. No code change authorized. The tracking note is added by these documentation replacements because it was absent in the upload.

In-memory authentication is separate from persisted PostgreSQL game/history. Do not infer missing persistence from the user's difficulty navigating to another game. No new frontend tests were added. Existing initial failed board GET without a snapshot still lacks timer retry; loaded finished games recover incomplete/outdated history. Notification/socket/migration limits from Day 23 remain, without implicitly authorizing reliability work.

## Retained application behavior

Day 22 resignation/draw policy remains: participant offer/resign regardless of turn; recipient accepts/declines; sender withdraws. Accepted moves/completion clear offers; rejected moves preserve them. Public GameService methods own transactions, optimistic flush and exact reservation release. AFTER_COMMIT hints are best-effort and cannot roll back committed games. Lifecycle versions advance independently of moves and require history reconciliation even for unchanged lists. One Angular POST lock remains; uncertain/conflicting commands reconcile through GET without replay. Home keeps explicit Open game. Disconnect never finishes a game or releases reservations.

## Read first

README.md, docs/day24-replay-pgn-review.md, docs/replay-pgn-flow.md, docs/development-progress.md, docs/roadmap.md and docs/architecture.md. Consult API, frontend-gameplay, history, lifecycle and realtime companions for the selected feature. Pure rules remain in movement-pipeline-architecture.md; Day 23 reliability evidence remains in its review/testing guide. Historical absence claims are superseded by newer checkpoint evidence where coverage changed.

## Next milestone

Day 24 is complete in the reviewed scope with documented deferrals. Select the next major milestone before implementation. Ratings/statistics, clocks, extra rule-based draws, acceptance and abandonment remain separate choices; no next feature is authorized by this handoff. Do not silently implement deferred PGN UI, promotion legality gating or optional replay interaction.
