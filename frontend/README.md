# Chess Frontend — Day 29 checkpoint

Angular standalone frontend with Material, signals, RxJS and persistent-session authentication. Current readability state inspected on 6 November 2026 against actual user chess-web-app-day29-mid.zip; gameplay behavior remains the Day 28 baseline. User reports everything green; no assistant build/test/browser execution.

## UI TODOs

- [ ] Add a rotating hourglass beside the clock of the Player whose turn it is.
      Switch it when the turn changes and stop it when the clock is paused or the game ends.

## Development

From frontend/, use compatible Node tooling, npm ci after dependency changes, and npm start. The start script runs the development server and development compilation; a separate production build is not required before starting it. npm run build produces production output for deployment or an explicit production-build check. Development proxy.conf.json forwards /api and WebSocket upgrades to port 8080; restart after proxy changes.

## Shell and routes

App owns header navigation/logout, reserved sidebar and theme selection. Its ChessTheme is amber by default with blue/green alternatives in code, not a persisted settings UI. Home displays a disabled initial board beside profile/statistics and MatchmakingPanel. /games is the self-only finished-game list, with page in the query string. /games/:gameId is the same participant-protected page for active games and review. Route ordering places the list before game details and wildcard.

Home's MatchmakingPanel resumes an existing game. Finished-game review does not redirect solely because an active game exists; it offers Return to current game. Find another match joins immediately and follows a started/observed waiting search into its match. Pending status reads coalesce during requests. Uncertain commands reconcile through GET, never automatic command retry.

## Gameplay ownership

Game owns accepted snapshots, commands, recovery, history/cursor and permissions. ChessBoard renders a supplied FEN, orientation and hints and emits proposed coordinates; it performs no optimistic move or chess legality validation. GameClocks renders rows around the projected board, estimates elapsed time from receipt and never decides a result. Live countdowns continue during replay. Inactive display dimming does not pause backend time. Starting names/Elo come from immutable game snapshots; unknown legacy ratings are not invented.

Legal hints are cached by game version, local authentication revision and route visit; selection filters that set locally, without a request per piece. Hint failures permit normal commands. Four promotion commands share one destination marker. Explicit 422 ILLEGAL_GAME_MOVE briefly highlights both attempted endpoints. Other rejections retain normal reconciliation/error handling and are not misrepresented as chess illegality.

Promotion icons begin on the destination and extend inward, with focus trapping, Escape and cancellation. The shape-based chooser can still open before a king-safety rejection; legality gating is deferred. Game actions contain offer responses and resign/accept confirmation. Messages/activity is feedback and future chat only.

MoveHistoryReplay owns inline SAN tokens, coordinate fallback, numbering and compact stretched controls. It emits cursor requests; Game checks permissions and owns replay selection. A numeric cursor is read-only even at the final recorded entry; Latest explicitly returns to live following. Older accepted history may remain available during recovery. Backend PGN export exists; its frontend download remains deferred.

## Synchronization and authentication

Game route switchMap cancels previous reads; exhaustMap allows one coordinated board/history read. Relevant hints, reconnect, focus and visible periodic checks reconcile snapshots. Reads queue during POST; a nonblocking safety read can be cancelled for a permitted command. Same-version clock reads refresh estimates without replacing the accepted game object. Known-newer updates block proposals until synchronization. No command is silently replayed.

Startup restores csrf/profile. AuthSessionStore holds profile/revision, while the browser holds CHESS_SESSION. Writes use Angular XSRF headers. Protected 401 clears only a matching local revision; 403 does not log out. The App shell owns logout. Realtime/session-sync lifecycles are independent of route changes. [Session](../docs/session-authentication-flow.md) and [tab](../docs/tab-synchronization-flow.md) companions document best-effort channel and socket limits.

## Readability and verification

GameClocks uses ng-template to reuse clock rows. The supplied source now removes unused Game.moveTokens and calls named initialization methods from constructor/ngOnInit, preserving legal-hint initialization in the constructor. Game actions use an external #gameActions fragment with let-current supplied through the outlet context. Game retains orchestration; no new state service is added. The [closing guide](../docs/day28-readability-cleanup.md) is the earlier proposal, not the current applied-status record.

Source includes six auth-flow, three mocked tab-sync and four mocked matchmaking coordinator cases. This is not comprehensive rendered UI or browser acceptance. No new test is needed merely to mirror a mechanical extraction. When explicitly requested, user-run commands are npm test -- --watch=false and npm run build. Selected backend deep-read tests and unverified scenarios are in [Day 28 review](../docs/day28-ui-review.md).

Chat, rematch, spectators, drag/drop, persistent check status, further replay polish and public Player history remain deferred. See [architecture](../docs/architecture.md), [contracts](../docs/api-contracts.md), [roadmap](../docs/roadmap.md) and [handoff](../docs/day28-handoff.md).

## Backend load testing

Day 29 k6 scripts are a separate load-tests/ project, not Angular tests. They call the loadtest backend on port 8081 directly; Angular need not run. The auth smoke, paced statistics reads and subsequent independent HTTP game batches do not establish rendered UI, browser/session-tab or WebSocket-client acceptance. One k6 game VU orchestrates two Player sessions; it is not a browser. See [load testing](../docs/load-testing.md) for commands and measured results.
