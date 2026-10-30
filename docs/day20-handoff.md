# After Day 20 — next working session

Continue from the actual user chess-web-app-day20-end.zip plus the Day 20 documentation replacements and any subsequently applied inline changes. Recheck actual source before proposing implementation. No assistant-generated production implementation archive replaces the user’s source.

## Working agreement

The user implements inline solutions in coherent increments. Keep the modular monolith, conventional layers, Player naming, cross-feature service/event boundaries and pure Java rules. Preserve useful comments, minimal frontend testing, separate component HTML/CSS and user-managed configuration. Do not execute tests/builds unless explicitly requested. Treat tests as green unless the user reports a failure; do not pause for routine confirmation. Documentation replacement packages are welcome.

## Reviewed state

Source-reviewed on 30 October 2026. No blocking defect found in inspected Day 20 changes. User reports implementation looks good. No assistant application tests/builds/migrations/browser checks were run; no independent frontend acceptance result is claimed.

GameService publishes immutable GameChangedEvent values. The notification listener sends GAME_CREATED/GAME_UPDATED hints to both stored participants AFTER_COMMIT. Moves remain HTTP commands; reads remain participant-protected. Checkmate/stalemate complete and release reservations atomically. No rules/migration change was added.

WebSocket CONNECT uses the existing decoder and strict expiry. Only the exact configured private subscription is permitted; SEND/raw broker/other-user/wildcard destinations are denied. Session access is tracked and checked on outbound application messages. Physical socket closure at exact expiry is not scheduled server-side. YAML-backed settings include 10s heartbeat, validated between 1 and 300 seconds. Frontend addresses/timing remain separately configured.

App owns one RealtimeService lifecycle. Home coalesces reads and periodically checks unknown/WAITING/MATCHED status. Home automatically discovers the match but intentionally requires Open game; no automatic board navigation is expected. Home’s signal is queueStatusResponse and page templates expose realtimeService.

Game handles newer-version hints, reconnect/focus reads, visible-page safety checks, deferred GET during POST and cancelled safety GET before POST. Same-version reads preserve snapshot identity/promotion; older reads cannot overwrite newer accepted state. Uncertain outcomes queue a GET without replaying POST. Initial GET failures without snapshots are not timer-retried. Sessions remain in memory; reload requires login.

## Read first

README.md, frontend/README.md, docs/architecture.md, docs/development-progress.md, docs/roadmap.md, docs/api-contracts.md, docs/frontend-gameplay-flow.md, docs/realtime-updates-flow.md and docs/day20-realtime-review.md. Use the movement pipeline companion when changing rules. Historical checkpoints live under docs/history and existing Day documents.

## Review notes

The proposed WebSocketPropertiesTest is absent from the archive; do not assume its coverage. Present tests cover inbound authorization, outbound expiry/disconnect and transaction timing/rollback using mocked outbound transport. There is no full socket integration test or new frontend coordinator suite. Optional unused imports: combineLatest in game.ts and map in home.ts. Documentation replacements apply no production cleanup.

The reported lazy-chunk 404 was resolved. The board-navigation question is clarified as intended behavior, not an open defect.

## Next milestone

Discuss and select the next milestone before implementation. Persisted move history is a reasonable candidate; real-time delivery is now implemented. Decide legacy-game continuity explicitly because current FEN cannot reconstruct prior moves. Minimal move-list display, replay and SAN/PGN are distinct scope choices. A focused reliability/test milestone is another option.

History, clocks, extra draws, resignation/agreement, acceptance, ratings and disconnect/abandonment policy remain separate decisions. Closing a page does not finish the game or release reservations. No move highlighting or persistent check indicator is supplied. Begin a new major milestone in a new chat.
