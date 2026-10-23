# Chess Frontend

Updated 23 October 2026 for the Day 5 guided username-editor increment. Source review covered the Day 4 archive; no fresh Day 5 source archive has been inspected.

Angular 21 standalone application with Angular Material, reactive forms and in-memory authentication. Backend calls use relative `/api/v1` URLs.

## Run locally

From `frontend/`, use the project's declared npm 11.6.2 and a Node version accepted by the locked Angular packages:

```bash
npm ci
npm start
```

Open `http://localhost:4200`. `npm start` loads `proxy.conf.json`, forwarding `/api/**` to the separately running Spring backend at port 8080. Plain `ng serve` does not enable this proxy automatically.

## Build and minimal tests

```bash
npm test -- --watch=false
npm run build
```

Five focused tests in `src/app/auth/services/auth-flow.spec.ts` cover bearer attachment, external URL exclusion, protected 401 cleanup/redirect, cleanup after profile loading fails, and logout. There is no `app.spec.ts` in this checkpoint. No end-to-end runner is configured.

The user reports passing tests for the completed Day 4 checkpoint. This documentation review did not rerun tests or builds. Separate Day 5 frontend test/build/manual-check outcomes have not been recorded. Older review-environment npm failures are not confirmed current blockers.

## Authentication behavior

Registration and login use Material forms. Login loads `/api/v1/players/me` before establishing an authenticated session. Home is guarded. Logout clears the in-memory token/player and returns to login; a browser reload also loses the session.

The interceptor attaches bearer tokens to relative protected API requests. A protected-request 401 clears the matching session and redirects an established player to login; a 403 does not clear the session. Failed form submissions preserve passwords. Successful login clears the password; successful registration resets the form.

## Username editing

Home embeds the standalone `ProfileEditor` component. It initializes the form from the in-memory player, validates a nonblank username of 3–30 UTF-16 string units, and calls PATCH `/api/v1/players/me` through `PlayerApiService`.

The existing interceptor attaches the token. A successful response updates `AuthSessionStore`, immediately updating the welcome message. The form then resets to the server-returned username. Errors preserve the input and show validation, conflict, session or fallback feedback. A response for a changed session is ignored. No new frontend test suite was introduced for this increment.

Usernames may contain spaces and special characters. No trimming or case normalization is performed. Email/password changes are outside this form. Username changes do not require a new token.

Manual checks: successful rename and immediate header update; duplicate rejection with input retained; invalid input feedback; navigating back to Home with the updated profile. Use the browser network panel to inspect PATCH response statuses/codes listed in [API contracts](../docs/api-contracts.md).

## Matchmaking behavior

Home loads queue status when opened and provides Material join, leave and refresh controls with progress/error feedback. It displays `NOT_QUEUED`, the enqueue time for `WAITING`, or the game ID for `MATCHED`.

Pairing runs on the backend when players join. The first player manually refreshes to discover the match. Leaving removes waiting membership without cancelling a game; logout does not leave the queue.

There is no game API service, game route, chessboard or real-time subscription in this checkpoint.

See [development-progress.md](../docs/development-progress.md) for current coverage and limitations, [architecture.md](../docs/architecture.md) for engineering decisions, and [roadmap.md](../docs/roadmap.md) for planning status.
