# Chess Frontend

Angular 21 standalone frontend with Material, signals, RxJS, persistent-session authentication, custom board and live clocks. Day 27 source-reviewed on 4 November 2026 against actual user chess-web-app-day27-end(1).zip. No assistant build/test/browser run is claimed. See [review](../docs/day26-clocks-review.md) and [clock flow](../docs/game-clocks-flow.md).

## Run

From frontend/, use compatible Node tooling and the declared npm 11.6.2:

```bash
npm ci
npm start
```

Open http://localhost:4200. The start script enables proxy.conf.json, forwarding `/api/**` and WebSocket upgrades to port 8080. Restart the dev server after proxy changes. If a generated lazy chunk returns 404 after dependency/rebuild changes, restart, wait for successful compilation and hard-refresh. If needed, stop the server and use `npx ng cache clean`, then restart. This was the reported Day 20 development issue, subsequently reported resolved; it is not a game-rule failure.

## Authentication and navigation

Startup initialization restores csrf then players/me before guards. Login returns a Player profile after session creation; Angular reinitializes CSRF before storing it. The browser retains CHESS_SESSION; Angular stores profile/revision only. Protected 401 compares captured revision; 403 does not clear authentication. Failed forms preserve passwords. Server logout is subscribed to and only successful logout clears local state. See [session flow](../docs/session-authentication-flow.md) and [tab diagrams](../docs/tab-synchronization-flow.md).

Home supports profile editing, self-statistics and persistent matchmaking. It reads queue state on entry, private updates and successful connections. A single pending read is drained after a current command/read. Unknown/WAITING/MATCHED status has 30-second recovery checks; NOT_QUEUED is idle. Home’s signal is named queueStatusResponse in this checkpoint.

**A match reveals Open game; it does not automatically navigate.** The Player clicks the link to open the guarded game route. Home now has separate home.ts, home.html and home.css files.

## Gameplay

Game owns GET snapshots, versioned POST commands, promotion and recovery. ChessBoard receives FEN/orientation/enabled and emits proposed coordinates. Game/board templates and CSS remain separate. The board does not validate chess legality or perform optimistic piece moves.

Select an own piece and destination; select another own piece to change source or click the selected square to cancel. Promotion requires an explicit choice; cancel sends no request and leaves no stale source. Castling proposes the king’s source/destination. En passant proposes the capturing pawn’s source/destination.

Relevant newer notifications request GET. A pending read waits during POST; safety reads can be cancelled before submission. Same-version reads preserve snapshot identity/promotion, and older reads cannot overwrite a newer snapshot. Game-state read failures block proposals; stale/uncertain command outcomes reconcile through GET without replay.

Games also reconcile after reconnect/focus and periodically while visible with a loaded active snapshot. The board stays visible during background reads. GET does not expose positionStatus, so there is no persistent check indicator. Terminal results disable all game commands; returning Home reads queue state and released Players can join again.

## Real-time connection

App instantiates RealtimeService once. It observes revision/authentication, checks csrf/profile before connecting, recreates subscriptions on reconnect and ignores stale callbacks. CONNECT sends X-XSRF-TOKEN and the handshake inherits cookie authentication. Heartbeats detect transport failures; clearing local state stops the client. No JWT expiry timer remains. STOMP rejection marks live delivery UNAVAILABLE while HTTP/manual recovery remains available.

@stomp/stompjs is declared as ^7.5.0. REALTIME_CONFIG supplies matching backend addresses plus reconnect/heartbeat/recovery timing. Backend YAML is not read by Angular.

## Verification and limits

Optional user-run commands:

```bash
npm test -- --watch=false
npm run build
```

The archive contains six auth-flow and three mocked tab-sync coordinator tests. No comprehensive real-browser notification/game coordinator suite is established. Suggested two-session scenarios are in the [review](../docs/day22-lifecycle-review.md).

The move list has separate coordinate and SAN columns. Pre-SAN entries show “—” for SAN. Recorded-position replay is implemented with explicit Latest return. Backend PGN export exists; its Angular download UI is deferred. Live clocks are implemented; drag-and-drop and legal-move highlighting remain absent. Promotion selection may open before backend king-safety rejection; this known UI improvement remains intentionally deferred. Disconnecting never decides game completion or releases reservations. No durable notification replay, auto board navigation or persisted login session is supplied.

See [gameplay coordination](../docs/frontend-gameplay-flow.md), [real-time diagrams](../docs/realtime-updates-flow.md), [contracts](../docs/api-contracts.md), [architecture](../docs/architecture.md) and [roadmap](../docs/roadmap.md).

## Move history coordination

After accepting game GET, Game reads GET /games/{gameId}/moves. History with the displayed version is accepted; older history is ignored; newer history queues blocking board synchronization. The previous list remains visible with catching-up feedback when outdated. History errors are separate from board errors and do not alone block proposals.

The reading flag spans both GETs. cancelReads is downstream of switchMap, so a permitted POST cancels either stage. Route cancellation also tears down both. Successful POST explicitly queues nonblocking reconciliation. Visible loaded finished games continue safety checks while history is missing, outdated or failed; they stop when board/history are synchronized. Initial game GET failures without a snapshot still require manual/focus/connection/hint recovery.

The table shows Entry, Player, Move and SAN. Entry is a recorded individual-move sequence relative to the baseline. Legacy incomplete history has an explanatory notice. SAN is generated in the backend; Angular does not duplicate chess legality or notation rules. See [history flow](../docs/move-history-flow.md).

## Day 22 lifecycle controls

GameApiService maps typed actions to resignation and draw-offer POST routes. GameResponse adds nullable drawOfferedByPlayerId and termination reasons RESIGNATION/DRAW_AGREEMENT. Move responses now also include clock; their accepted mapping explicitly clears the pending offer and maps the returned clock.

canSubmitGameCommand checks active participant/session and synchronization/submission state. canSubmitMove additionally checks turn and absence of lifecycle confirmation. canSubmitLifecycleAction ignores turn but requires promotion to be resolved. An offer does not pause play.

Resignation and draw acceptance use inline confirmation; decline, withdrawal and offer send directly. A newer snapshot, route change or blocking synchronization clears confirmation. Unchanged nonblocking reads preserve it. Any accepted move clears the offer; the pending offer is discovered through ordinary game GET.

executeGameCommand shares the existing submitting flag across moves and lifecycle commands. It cancels either GET stage before POST, ignores callbacks for replaced snapshots, applies the accepted response and queues board/history reconciliation. Conflicts and uncertain responses request fresh state without replay. The retained moveMessage signal now carries general action feedback.

No new frontend tests are included. The lifecycle companion and Day 22 review record suggested two-session checks and the actual coverage limits.

## Player statistics and refresh stream

Home imports PlayerStatistics and renders app-player-statistics after app-profile-editor. PlayerApiService.currentStatistics calls GET /api/v1/players/me/statistics; statistics are not cached in AuthSessionStore. The component has separate TS/HTML/CSS and displays rating, played, wins, draws and losses.

merge combines a manual Subject, RealtimeService.connections$ and updates$; startWith triggers the first GET. Any private hint can refresh statistics, even when counts did not change. switchMap cancels an older GET when another trigger arrives. The captured token/Player ID and response identity guard updates in tap. catchError is inside the HTTP branch and returns EMPTY, preserving future refreshes; previous values remain visible with error feedback. finalize resets loading on success, failure or cancellation. takeUntilDestroyed is downstream of switchMap and tears down the active request. No nested subscribe, rating-history API or periodic statistics polling is implemented.

Opening Home loads current values after game completion. A missed hint may leave the card stale until manual refresh, reconnection or a new Home instance. No manual browser acceptance is established by source review. The existing frontend auth tests are unchanged. See [statistics flow](../docs/player-statistics-flow.md) for RxJS and transaction diagrams.

## Day 26 clocks and timeout extension

Game accepts clock data on GET when its version is at least the displayed version, but replaces board identity only when needed. clockSnapshot is separate so equal-version clock refreshes preserve selection/promotion during nonblocking safety reads. Accepted move/lifecycle mappers replace clock data. Route initialization clears it; a 403/404 clears game and hides clocks, without an explicit clockSnapshot reset in that error branch.

GameClocks has separate TS/HTML/CSS and pseudocode.txt. It anchors server-calculated remaining times to performance.now at receipt, estimates the running Player's time every 100ms, clamps zero and rounds displayed seconds upward. calculatedAt is response metadata, not used for network-age correction. Delivery delay and sleep/background behavior can affect display. Existing focus/reconnect/periodic reads recover state. Local zero only shows waiting; backend owns completion and command rejection. Finished clocks freeze. GAME_CLOCK_EXPIRED triggers existing reconciliation without command replay.

Replay displays historical positions while clocks/status remain live. Latest remains explicit. Stronger banner/square-click behavior, sounds and new-live-move indicators are deferred until after MVP. No new frontend tests or assistant browser acceptance is claimed.

See [clock flow and pseudocode](../docs/game-clocks-flow.md) for timing, configuration, transaction boundaries and remaining limits.

## Session coordination

AuthSessionEventsService publishes only SESSION_CHANGED over BroadcastChannel. SessionSyncService clears local identity/revision, awaits Login navigation, checks revision and restores the backend session before opening Home. Another tab does not receive credentials or a profile through the channel. Delivery and simultaneous authentication schedules have documented limits; see the tab companion. Relative API URLs preserve built-in Angular XSRF integration through the development proxy.
