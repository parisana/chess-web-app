# Development Progress

Reviewed: 20 October 2026. Source: attached `chess-web-app-day3.zip`, archive commit `d649893cba9bef693890fc86804328f714e959b6`. This revision updates documentation only; application code, tests, dependency files, migrations, and configuration are unchanged.

## Day 3 status

The checkpoint contains the backend authentication foundation and the Angular authentication flow. Architecture remains a modular monolith, package by feature with conventional layers, not DDD. Stack: Java 21, Spring Boot 4.1.1, Gradle wrapper 9.8.1, PostgreSQL, Flyway, Lombok, YAML, Angular 21, and Angular Material.

Implemented source and test presence have been reviewed. This is not certification of a passing build. Matchmaking, games, legal moves, and real-time delivery have not been implemented.

## Backend reviewed

- Transactional registration persists players and BCrypt credentials; V1/V2 own schema creation.
- Auth calls PlayerService using DTOs rather than importing player entities/repositories.
- Duplicate prechecks and named PostgreSQL constraints provide conflict handling.
- Structured validation errors omit rejected password values; registration and login enforce the 72-byte UTF-8 password maximum.
- Exact stored-email login uses a dummy hash for unknown/missing credentials and returns generic 401 errors.
- Login issues HS256 JWTs with player ID subject and a no-store header.
- JWT decoding checks algorithm, signature, issuer, required expiration, expiration/optional not-before validity with skew, and positive Long subject. Issued-at is emitted but there is no explicit future-issued-at validator.
- `/api/v1/players/me` loads current database state and rejects missing players.
- Unit, standalone MVC, PostgreSQL persistence/uniqueness/rollback, login, and JWT integration tests are present.

Two previous follow-ups are already resolved: PlayerExceptionHandlerTest is in `player.exception`, and the missing-local-credentials test initializes and verifies the dummy hash. The earlier Day 2 documentation describing them as pending was stale.

## Frontend reviewed

- Registration/login use reactive forms and Angular Material, with progress and error handling.
- Registration success redirects to login with feedback; failure preserves form values/password.
- Login obtains a token, loads `/players/me`, then establishes the player session.
- In-memory signals hold the token and player; reload loses the session.
- The interceptor attaches bearer headers to relative backend URLs, excludes public auth calls, and avoids sending tokens to external services.
- Protected-request 401 clears the matching session and redirects established sessions. 403 does not clear it.
- Home is guarded; logout clears client state and returns to login.
- Both failed form submissions preserve passwords. Success-only resets remain intentional.

## Verification results and blockers

| Check | Observed result |
| --- | --- |
| Backend source/test review | Completed |
| `bash gradlew --version` | Failed downloading Gradle 9.8.1: network unreachable |
| Java runtime available here | Java 17; project requires Java 21 |
| `npm ci --no-audit --no-fund` (with bounded fetch settings) | Failed with EUSAGE under Node 24.19.0/npm 11.9.0; missing `@emnapi/core@1.11.3` and `@emnapi/runtime@1.11.3` from lockfile |
| Frontend tests/build | Not executed; dependencies not installed |
| Backend tests/build | Not executed; Gradle/bootstrap and Java prerequisites unavailable |

The package manager declaration is npm 11.6.2. Reproduce installation with that version before attributing the lockfile failure to all environments. If needed, regenerate lock metadata using `npm install --package-lock-only`, inspect its diff, and rerun `npm ci`; do not start by upgrading Angular or deleting the lockfile.

### Minimal frontend tests: current source status

`auth-flow.spec.ts` contains five tests: protected bearer attachment, no token to an external service, protected 401 session clear/redirect, cleanup when profile loading fails after login, and logout. Their assertions match the reviewed flow, but they have not run in this review.

`app.spec.ts` has an app creation smoke test and an obsolete generated title test expecting `Hello, chess-frontend`. `app.html` now contains only `<router-outlet />`, so that title expectation cannot pass when executed. Small follow-up: remove the obsolete title test, retaining the creation smoke test and five auth tests. No broad component/E2E suite is proposed.

### Local verification commands

From `frontend/`, after resolving installation:

```bash
npm ci
npm test -- --watch=false --include='src/app/auth/services/auth-flow.spec.ts'
npm test -- --watch=false
npm run build
```

From `backend/`, with Java 21 and PostgreSQL `chess_test_db` available:

```bash
./gradlew --version
./gradlew test --tests com.chessapp.auth.service.AuthServiceTest --tests com.chessapp.auth.controller.AuthControllerTest
SPRING_PROFILES_ACTIVE=test ./gradlew clean test
```

Reports: `backend/build/reports/tests/test/index.html`. Test transactions roll back participating fixtures; earlier committed rows are not removed by adding `@Transactional`. Expected constraint errors in logs are not by themselves failed tests.

Manual frontend smoke check using `npm start`: register → sign in → home → logout; wrong-password and duplicate registration failures retain the entered password; refresh returns to login. These checks are suggested, not claimed as performed here.

## Accepted limitations and brief refactoring hints

Secrets remain user-managed and are not a prerequisite for this milestone. Identity matching has no trimming/case normalization policy. No refresh tokens, revocation, account disabling, rate limiting, or server logout exists. Security filter errors and MVC Problem Details have different body contracts. Only `/players/me` currently checks player existence after JWT validation.

Small opportunistic improvements:

- Replace the mocked PlayerResponse record in AuthServiceTest with a real fixture.
- Avoid narrowing IDs with `intValue()` in JWT test assertions.
- Use unique identity values in AuthRegistrationIntegrationTest if old committed fixtures cause conflicts.
- Keep transaction ownership explicit: PlayerService.createPlayer currently relies on the registration transaction or repository transaction. Give new multi-write use cases service-level transaction boundaries rather than assuming repository calls are atomic together.
- Add not-before/algorithm rejection cases when next extending backend token tests.
- Keep email-bearing PlayerResponse private to self-profile/auth flows; matchmaking responses should not expose an opponent's email.

## Matchmaking queue milestone — implemented

- [x] V3 queue migration, entity, and repository.
- [x] Transactional join, leave, and status operations.
- [x] Repeated joins preserve the original entry and timestamp.
- [x] Database-enforced uniqueness and concurrent-join handling.
- [x] Authenticated endpoints using JWT-derived player identity.
- [x] Missing-player Problem Detail response.
- [x] PostgreSQL integration tests for membership and concurrent joins.
- [x] HTTP integration tests for authentication and queue operations.
- [x] Minimal Angular queue controls with progress and error handling.

Current limitations: one queue, manual status refresh, no expiry, no pairing,
no active-game eligibility checks, and no automatic queue removal on logout.

Next milestone: minimal persisted game creation, followed by transactional
pairing of queued players.

## Suggested commit

```text
docs: reconcile day 3 auth checkpoint and plan matchmaking queue
```

No git commits were created in this review.
