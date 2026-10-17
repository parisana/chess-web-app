# Development Progress

Reviewed: 17 October 2026. Source checkpoint: `55b452213142012f9b06826a802f937ccd407afd`, supplied through `git archive`. This documentation update changes no application code.

## Status

Day 1 foundation and Day 2 authentication implementation are present. Day 2 is ready for local verification and configuration closeout; this review does not certify a passing build. The review runtime defaults to Java 17, and Gradle 9.8.1 could not download because network access was blocked.

Architecture remains a modular monolith, package by feature with conventional layers, not DDD. Stack: Java 21, Spring Boot 4.1.1, Gradle wrapper 9.8.1, PostgreSQL, Flyway, Lombok, YAML. Angular and real-time chess features remain planned.

## Implemented and reviewed

- [x] Players/local credentials, Flyway V1/V2, minimal timestamp base entity.
- [x] Transactional registration, password hashing, HTTP DTOs.
- [x] Player service result DTO instead of cross-feature entity exposure.
- [x] Duplicate prechecks and named PostgreSQL constraint fallback.
- [x] Shared field-validation Problem Details and feature-owned error constants.
- [x] Email maximum length and BCrypt UTF-8 byte-length validation.
- [x] Exact-email credential verification and dummy-hash failure path.
- [x] Login token issuance and no-store response header.
- [x] HS256 bearer authentication, issuer/timestamp/required-expiration/subject checks.
- [x] Database-backed `/api/v1/players/me` returning id, username, email.
- [x] Test profile on the context-load test and other current Spring integration classes.
- [x] Registration integration flush/clear, timestamps and credential-link assertions.
- [x] Separate service-rollback integration test without test-level transaction.
- [x] Actual PostgreSQL uniqueness/translation tests.
- [x] Full registration → login → protected-profile integration test.
- [x] Invalid credentials and invalid login email coverage.
- [x] JWT valid/missing/malformed/expired/wrong-issuer/altered-signature cases.
- [x] Missing/invalid subject, Long overflow, missing expiration, and deleted-player tests.
- [x] Duplicate Flyway runtime/implementation dependency declaration removed.

Checked boxes mean code/tests exist in the reviewed snapshot, not that they executed successfully here.

## Required closeout

1. Remove the tracked `app.jwt.secret` from shared YAML and generate a replacement in ignored `application-local.yml`. The archived signing key is disclosed; do not reuse it for deployment. Keep the test-only key confined to the test profile.
2. Run focused authentication tests and the complete suite on Java 21/PostgreSQL.
3. Run the suite again with `--rerun-tasks` to verify repeatability.
4. Review and commit documentation/configuration changes. No commits were created during this review.

The implemented login response contains token fields only. Including `PlayerResponse` alongside the token was discussed but is not implemented or required for this closeout.

## Verification

From `backend/`:

```bash
./gradlew --version
./gradlew test --tests com.chessapp.auth.LoginIntegrationTest --tests com.chessapp.security.JwtSecurityIntegrationTest --tests com.chessapp.auth.service.AuthServiceTest
SPRING_PROFILES_ACTIVE=test ./gradlew clean test
SPRING_PROFILES_ACTIVE=test ./gradlew test --rerun-tasks
```

Use PostgreSQL `chess_test_db`, not development data. Test reports appear in `build/reports/tests/test/index.html`. Expected constraint failures in negative tests are not by themselves failures. Existing committed fixtures may still need targeted cleanup; never truncate development tables to fix tests.

## Review notes and small follow-ups

- `PlayerExceptionHandlerTest` is still under `common.exception`. Move its file/package to `player.exception` for ownership consistency; behavior is unaffected.
- `AuthServiceTest.authenticate_rejectsMissingLocalCredentials` does not initialize the dummy hash, so its mocked encoder sees a null hash. Initialize it and verify `matches(password, "dummy-hash")` as the unknown-email test already does.
- `AuthServiceTest` mocks `PlayerResponse` in one test. A real record fixture is simpler and removes unnecessary getter stubbing.
- The valid-token test narrows the ID with `intValue()`. Prefer JsonPath's explicit Long target type if fixtures ever exceed integer range.
- Flyway migration-name validation remains test-only; enable it in shared configuration.
- The registration integration fixture uses fixed identity values. Unique per-test values would make it more resilient to old committed fixtures.
- Add not-before-future and unexpected-algorithm rejection coverage when extending token tests. Current required claims are already covered.
- The repository does not declare Lombok test annotation processing. Add test Lombok dependencies only if tests start using Lombok annotations.

## Accepted limitations

Email and username use stored-value comparisons without normalization. JWT lifetime is 15 minutes plus 60 seconds of clock tolerance. No refresh tokens, revocation, logout endpoint, account disabling, or rate limiting exists. Missing/invalid bearer responses use Spring Security's format, while MVC failures use Problem Details. `/players/me` checks account existence; bearer validation alone does not perform that check for every endpoint.

## Next development increment

After closeout, review the remaining 30-day plan before choosing Day 3 implementation. Matchmaking/game/WebSocket/Angular work has not started. Preserve the current feature boundaries and avoid adding Redis/Kafka or abstractions without a concrete requirement.

Guidance stays incremental: explain tradeoffs, add brief refactoring hints, provide verification commands, and do not pause for routine confirmation.

## Suggested documentation commit

```text
docs: document day 2 authentication and verification status
```

Commit the signing-key configuration correction separately. Once intended changes are committed, create the next review archive from the repository root with `git archive --format=zip --output=../chess-web-app.zip HEAD`.
