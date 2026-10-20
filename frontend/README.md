# Chess Frontend

Angular 21 standalone application with Angular Material, reactive forms, and in-memory authentication. Backend APIs use relative `/api/v1` URLs.

## Run locally

From this directory, use the Node engine range recorded in `package-lock.json` and the declared npm 11.6.2:

```bash
npm ci
npm start
```

Open `http://localhost:4200`. `npm start` loads `proxy.conf.json` and forwards `/api/**` to the Spring backend on port 8080. Start the backend separately. Plain `ng serve` does not enable this proxy automatically.

## Build and minimal tests

```bash
npm run build
npm test -- --watch=false --include='src/app/auth/services/auth-flow.spec.ts'
npm test -- --watch=false
```

Five focused auth tests cover header attachment, external URL exclusion, protected 401 handling, profile-error cleanup, and logout. `app.spec.ts` also contains an obsolete generated heading assertion: remove that title test because the root template now only renders the router outlet. Keep the app creation smoke test. No end-to-end runner is configured.

Review on 20 October 2026: `npm ci` failed under Node 24.19.0/npm 11.9.0 with missing `@emnapi/core@1.11.3` and `@emnapi/runtime@1.11.3` lockfile entries, so tests/build were not executed. Reproduce with the declared npm version; if needed, run `npm install --package-lock-only`, review the diff, and retry `npm ci`. This review changed no dependencies or tests.

## Behavior

Register, then sign in. A successful login loads `/players/me` before opening the guarded home page. Logout clears the token/player. Browser reload loses the in-memory session. Protected-request 401 clears the matching session and redirects an established player to login; 403 does not clear it. Failed form submissions retain passwords; successful submissions reset them as appropriate.

See `../docs/development-progress.md` for review findings and the next backend proposal.
