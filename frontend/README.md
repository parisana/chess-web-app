# Chess Frontend

## Day 19 source-reviewed checkpoint — 29 October 2026

Angular 21 standalone application with Angular Material, signals, reactive forms and in-memory authentication. Reviewed against chess-web-app-day19-end.zip. The frontend implements a custom click-to-move board; no chessboard library dependency remains.

No blocking defect was found in the reviewed Day 19 changes. Two minor guard/message follow-ups are supplied inline and have not been silently applied. See [review findings](../docs/day19-frontend-gameplay.md). Tests follow the standing user instruction; no assistant tests/builds or browser checks were run.

## Run locally

From frontend/, using the declared npm 11.6.2 and a Node version accepted by the locked dependencies:

```bash
npm ci
npm start
```

Open http://localhost:4200. npm start enables proxy.conf.json, forwarding /api/** to the separately running backend on port 8080. Plain ng serve does not enable that proxy automatically.

package.json is unchanged from Day 18. The supplied lockfile includes 15 package-version changes, including Angular build/CLI tooling. angular.json has formatting changes only; runtime/secret configuration remains user-managed.

## Authentication and profile

Login loads /api/v1/players/me before establishing the session. The token and Player are in memory; browser reload requires login again. Protected API requests receive the token through the existing interceptor. A protected 401 clears the matching session and redirects an established Player; 403 does not clear it.

Failed form submissions preserve passwords. Successful login clears the password; successful registration resets the form. Home supports username PATCH, validation/conflict feedback and immediate in-memory profile updates.

## Matchmaking and navigation

Home loads status on entry and offers join, leave and manual refresh. MATCHED shows the game ID and an Open game link. Pairing happens on join; the waiting Player refreshes to discover it.

Repeated join can return the active game. Leaving does not cancel an established game; logout does not leave the queue. After login, Home can rediscover an active game.

## Gameplay

The guarded games/:gameId page loads participant-protected GET /api/v1/games/{gameId}. GameApiService handles GET and POST transport; Game coordinates snapshots, requests, promotion and recovery.

ChessBoard reads FEN, orientation and enabled inputs and emits moveProposed. It renders Unicode pieces, square coordinates, an orientation for the current Player and a selection highlight. Game and ChessBoard have separate .ts/.html/.css files.

Click an own piece, then its destination. Click it again to deselect or another own piece to change source. The board clears selection before emitting. Promotion requires an explicit QUEEN/ROOK/BISHOP/KNIGHT choice; cancel sends no request and leaves no stale selection. Castling uses the king's source/destination; en passant uses the pawn's source/destination.

POST includes the displayed expectedVersion. Only server-accepted FEN updates the board. Illegal moves preserve the position; stale/turn/finished errors reload without command retry. Uncertain outcomes block proposals until a successful read. Completion feedback uses persisted result/reason/time and disables further moves.

Opponent moves require manual refresh. A GET load clears the current snapshot and recreates the board after success. GET has no positionStatus, so the page does not maintain a persistent check indicator.

## Minimal verification

Optional commands from frontend/:

```bash
npm test -- --watch=false
npm run build
```

The five existing tests in src/app/auth/services/auth-flow.spec.ts are unchanged. Day 19 introduces no game-component/parser tests or end-to-end runner. No separate frontend build or manual acceptance result is claimed.

Suggested manual scenarios: two-session matching and opposite orientation; ordinary and illegal moves; promotion cancellation/underpromotion; stale-state reload; uncertain-outcome refresh; terminal feedback and another queue join.

## Current limits and conventions

No drag-and-drop, legal-move highlighting, move-history interface, clocks or real-time subscription. Disconnecting does not finish a game. Additional draw rules, resignation, agreement, acceptance and abandonment policy remain separately scoped.

Keep backend legality authoritative and frontend testing minimal. New game/board components use separate templates/styles. Board-library integration is optional post-MVP polish.

## Documentation

- [Gameplay flow and Angular coordination](../docs/frontend-gameplay-flow.md)
- [Day 19 source review](../docs/day19-frontend-gameplay.md)
- [API contracts](../docs/api-contracts.md)
- [Architecture](../docs/architecture.md)
- [Progress](../docs/development-progress.md)
- [Roadmap](../docs/roadmap.md)
