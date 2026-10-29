# Frontend gameplay flow

This companion explains the minimal frontend gameplay milestone: matched-game navigation, a custom board, explicit promotion, HTTP move submission, manual refresh, stale-state recovery and completion feedback.

## Evidence and scope

Source-reviewed on 29 October 2026 against chess-web-app-day19-end.zip, compared with the Day 18 archive. The custom board, promotion cancellation, page/board file separation and library removal are confirmed in actual source. The backend is byte-for-byte unchanged from Day 18. Two small follow-ups are recommendations, not applied production changes; see [Day 19 review](day19-frontend-gameplay.md).

No independent assistant tests or builds were run. Existing tests follow the standing user instruction that failures will be reported; this is not an independently verified frontend build or acceptance result.

The selected frontend is a custom click-to-move board. HTML and CSS are separate files for Game and ChessBoard. A board library is optional post-MVP polish, not an MVP completion requirement.

## Ownership and file map

| Owner | Responsibility |
| --- | --- |
| Backend GameService | Participant authorization, turn/version checks, legal moves, persistence and completion |
| Angular Game page | Read snapshots, coordinate proposals and promotion, send requests, recover and present completion |
| Angular ChessBoard | Render the supplied FEN, orient squares, track selection and emit proposals |
| GameApiService | Transport GET and POST requests |
| Existing authentication services/interceptor | Maintain the in-memory session, attach bearer tokens and handle authenticated 401 responses |

Frontend files under frontend/src/app/:

| File | Purpose |
| --- | --- |
| app.routes.ts | Guarded games/:gameId route |
| home/pages/home/home.ts | Queue status and Open game navigation |
| game/models/game-response.ts | GET response types |
| game/models/game-move.ts | Proposed move, request and POST response types |
| game/models/fen-display.ts | Display-only FEN projection |
| game/services/game-api.service.ts | Game HTTP transport |
| game/pages/game/game.ts | Gameplay orchestration and state |
| game/pages/game/game.html | Page controls and board bindings |
| game/pages/game/game.css | Page styling |
| game/components/chess-board/chess-board.ts | Board display computations and selection |
| game/components/chess-board/chess-board.html | Board markup |
| game/components/chess-board/chess-board.css | Square, piece and selection styling |

Separating HTML/CSS does not change the component lifecycle or data flow. The TypeScript decorator connects the files using templateUrl and styleUrl.

## 1. Matchmaking creates the game

Home sends POST /api/v1/matchmaking/queue. The interceptor attaches the bearer token; the backend derives Player identity from the validated JWT subject.

Within the matchmaking transaction:

1. Acquire the queue lock.
2. Check for an existing active game.
3. Otherwise insert or retain the Player's queue membership.
4. Find two eligible queued Players.
5. Assign colors and call GameService.createGame().
6. Obtain the initial position through ChessRules and serialize it.
7. Save the game and reserve both Players.
8. Remove the paired queue entries.

The transaction commits the game, reservations and queue changes together. If the caller already has an active game, joining returns that game rather than creating another.

The joining Player can receive MATCHED immediately. A previously waiting Player discovers MATCHED on a later status read. Home loads status on entry and supports manual refresh.

The game exists before either Player opens its page. Route navigation never creates a game.

## 2. Opening the game loads a snapshot

Open game navigates to /games/{gameId}. The route guard checks local authentication. Backend authentication and participant authorization remain mandatory regardless of the guard.

Game.ngOnInit() combines:

- route.paramMap: the initial route parameters and subsequent parameter changes.
- refreshRequests: explicit refresh events.
- startWith(undefined): an initial refresh value so combineLatest can emit immediately.

Each combined event enters switchMap, which subscribes to the newest read operation and unsubscribes from the previous one. This prevents an older read response from replacing a newer snapshot. It is not a guarantee that server-side processing of an old request stops.

defer runs the load setup when that inner operation is subscribed to. The reviewed implementation clears game and pending promotion, validates the route ID, sets loading and requests GET /api/v1/games/{gameId}.

On success, it sets the game signal and clears needsRefresh. On failure, it presents an error. catchError is inside the request, preserving the outer route/refresh stream for future attempts.

finalize clears loading on completion, error or unsubscription. takeUntilDestroyed unsubscribes when the page is destroyed.

## 3. Angular's reactive building blocks

| API | Meaning in this implementation |
| --- | --- |
| signal(initialValue) | Store a value that consumers can track |
| signal() | Read the current value |
| signal.set(value) | Replace the stored value |
| computed(callback) | Derive a cached, read-only value from signals read by the callback |
| input() | Receive a parent-supplied value as a signal |
| output() | Define an event the child can emit to its parent |
| effect(callback) | Run a callback initially and rerun it when tracked signal dependencies change |

Computed values are lazy and memoized. Changing a dependency invalidates the cached result; the next read derives the current result. Dependency tracking follows the signals actually read during execution.

Angular tracks signal reads in templates, including OnPush templates, and schedules updates for those consumers. Replacing game with a new response object therefore updates the relevant bindings; no manual board redraw is needed.

### Game-page state

| State | Meaning |
| --- | --- |
| game | Latest loaded or accepted game snapshot |
| loading | A game GET is running |
| submitting | A move POST is running |
| needsRefresh | Another proposal must wait for a successful read |
| pendingPromotion | A proposed pawn move awaiting a piece choice |
| moveMessage | Move rejection or recovery feedback |
| canSubmitMove | Derived interaction permission |

canSubmitMove requires a loaded in-progress game, an authenticated participant, no read or submission in progress, no refresh requirement and an observed side to move matching that Player.

This is interface coordination. The backend checks the actual current turn and game state independently.

## 4. Parent inputs render the board

The Game template passes:

- fen: current.currentFen.
- orientation: WHITE or BLACK from the current Player's participant ID.
- enabled: canSubmitMove() and no pending promotion.

ChessBoard.position derives a display projection from FEN. ChessBoard.rows derives the ordered cells for the orientation. The template renders those cells and highlights selectedSquare.

FEN placement is listed from rank 8 to rank 1, with files a through h. Digits represent empty-square runs. Orientation changes display order; it does not change algebraic names. a1 remains dark in both orientations.

parseFenForDisplay checks six fields, piece-placement shape and the active-color field. It does not validate the remaining FEN fields, chess legality, check or reachability.

### Selection reset effect

The reviewed custom board registers:

```ts
effect(() => {
  this.fen();
  this.orientation();
  this.enabled();

  this.selectedSquare.set(null);
});
```

The first three calls deliberately read dependencies. Their returned values are unused: Angular tracks the reads and schedules the effect when one of those values changes. Writing selectedSquare does not read it, so this callback does not subscribe to selection changes.

The effect clears selection after a position/orientation/interaction-state change. Selecting a square alone does not rerun it.

Effects are managed by Angular's lifecycle; this is not polling and is not a callback run synchronously inside every signal.set(). Prefer computed for derived values. Keep this effect limited to the existing selection reset rather than using effects to copy all application state.

The library-specific afterNextRender, rendering queue and imperative DOM synchronization are absent from the restored custom board.

## 5. Square selection and promotion

When White clicks e2, the board stores selectedSquare = e2. Clicking another own piece replaces that selection. Clicking the selected square again clears it.

Clicking e4 after selecting e2 executes:

```ts
this.selectedSquare.set(null);
this.moveProposed.emit({ from: 'e2', to: 'e4' });
```

The parent receives the proposal through:

```html
(moveProposed)="proposeMove($event)"
```

The board does not call HTTP or modify FEN.

Game.proposeMove() checks interaction permission and inspects the displayed piece. A pawn proposal ending on its promotion rank stores pendingPromotion. Other proposals call sendMove immediately.

### Why canceling promotion cannot leave the old selection

1. The board clears selectedSquare before emitting the proposal.
2. The page stores pendingPromotion.
3. The pending proposal disables board interaction.
4. Cancel sets pendingPromotion to null.
5. No POST was sent and FEN/version are unchanged.
6. The Player starts again by selecting a source square.

The selection-reset effect is additional protection when enabled changes; cancellation does not depend on the effect having already run. The explicit clear before emit is sufficient.

Choosing QUEEN, ROOK, BISHOP or KNIGHT clears the pending proposal and sends that explicit choice. No automatic queen is chosen. Refresh also clears pending promotion.

Castling uses the king's source and destination, such as e1 to g1. En passant uses the pawn's source and destination. The backend supplies the complete resulting position for all special moves.

## 6. Submission and backend processing

sendMove sends the displayed version, coordinates and optional promotion:

```json
{
  "expectedVersion": 0,
  "from": "e2",
  "to": "e4",
  "promotion": null
}
```

The request never supplies the acting Player, accepted FEN, winner or result.

Bean Validation handles field shape before the service command. For a well-formed command, GameService checks:

1. Game existence.
2. Participant access.
3. In-progress lifecycle.
4. Expected version.
5. Turn ownership from persisted FEN.
6. Exact legal-move membership.

It applies the move through ChessRules, evaluates the next position, serializes it and mutates the managed game. Explicit flush updates the version and detects competing writes. An optimistic-lock failure becomes STALE_GAME_VERSION.

The controller returns after transactional service processing succeeds. Flush alone is not commit; terminal updates and reservation release can still roll back together.

On POST success, Game preserves id, participant IDs and createdAt from the GET snapshot and replaces the fields supplied by GameMoveResult: FEN, version, status and completion details.

The custom board displays that accepted FEN. It never performs an optimistic piece move.

The identity check this.game() !== current ignores a response whose original snapshot has been replaced. It does not cancel or undo a backend commit.

## 7. Recovery and manual refresh

| Situation | Reviewed client behavior |
| --- | --- |
| Illegal move, 422/ILLEGAL_GAME_MOVE | Keep the position and allow another proposal |
| Invalid request, 400 | Show rejection without applying a position |
| Stale version, wrong turn or already finished | Require a fresh read and trigger reload; never replay the command |
| Uncertain transport/server failure | Block proposals until a successful manual refresh |
| Access denied or missing game | Show the access/read error and block proposals pending refresh |
| Authenticated 401 | Existing interceptor clears the matching session and redirects |

A network failure does not prove the move failed: the backend may have committed before the response was lost. Read before deciding on another proposal.

Signals react to local state changes. They do not detect an opponent's database update. The opponent must refresh until real-time delivery is explicitly implemented.

A successful POST updates the existing board through inputs. A manual GET load currently clears game first, destroying the board; a successful response recreates it. Refresh failure leaves the read error visible.

GET has no positionStatus. The current UI therefore does not retain a CHECK indicator from POST across refreshed snapshots. Completion information is persisted and available through both contracts.

## 8. Completion and another match

CHECKMATE finishes the game with the mover's winning color. STALEMATE finishes it as DRAW. The backend persists status, result, terminationReason and finishedAt and releases exactly two active Player reservations in the same transaction.

The submitting Player sees completion from POST. The opponent sees it on refresh. FINISHED makes canSubmitMove false, disabling further board interaction.

Returning Home loads queue status again. Released Players can join another match. A finished game remains participant-readable by its ID; queue status does not act as a history listing.

Authentication remains in memory. Browser reload requires login again; Home can then rediscover an active game.

## Milestone closing

The Day 19 archive has now been source-reviewed. The custom board, separate component files and complete library removal are confirmed. The Refresh button already blocks submissions, but refresh() itself lacks that guard; successful reads also retain old moveMessage text. Apply the two small inline follow-ups from day19-frontend-gameplay.md if desired before the closing commit. No production changes are included in this documentation bundle.

Focused manual scenarios:

- Match two authenticated Players and inspect opposite orientations.
- Submit an ordinary move and refresh the opponent.
- Reject an illegal proposal without changing the displayed position.
- Start promotion, cancel, then select a different source; confirm no request on cancel.
- Choose an explicit underpromotion.
- Recover a stale proposal without automatically replaying it.
- Refresh while a square is selected.
- Complete a game and confirm both Players can queue again.

History, clocks, real-time delivery, extra draw rules, acceptance workflows and disconnect/abandonment policy remain separate scope decisions. Closing the page or disconnecting does not finish a game.

## References

- [Angular signals](https://angular.dev/guide/signals)
- [Angular effect API](https://angular.dev/api/core/effect)
- [Angular component outputs](https://angular.dev/guide/components/outputs)
- [RxJS switchMap](https://rxjs.dev/api/operators/switchMap)
