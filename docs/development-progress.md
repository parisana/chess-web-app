# Development Progress

Last source review: 16 October 2026. This document separates code present in the reviewed snapshot from behavior verified by executing tests.

## Goal and conventions

Approximately 30 working days at eight hours per day. Build a portfolio-quality backend and Angular client using Java 21, Spring Boot, Gradle, PostgreSQL, Flyway, Lombok, and YAML.

Use a modular monolith, package by feature, and conventional layers. Do not adopt DDD. Keep `Player`. Explain decisions and work incrementally; include verification commands without pausing for routine test confirmations.

## Current milestone

Day 1 implementation is present; final testing improvements remain. Day 2 has not started. The uploaded snapshot declares Spring Boot 4.1.1 and Gradle wrapper 9.8.1, superseding older planning notes about Gradle 8.3. Angular is not scaffolded yet.

## Implemented in the snapshot

- [x] Repository, Gradle wrapper, and initial documentation.
- [x] Java 21 toolchain configuration and Spring Boot dependencies.
- [x] PostgreSQL datasource, Flyway, Hibernate schema validation, and disabled Open EntityManager in View.
- [x] V1 players migration and V2 local credentials migration.
- [x] `Player`, `AuthCredential`, and timestamp-only `BaseEntity`.
- [x] Repositories and player creation service.
- [x] Registration DTOs, service, and `POST /api/v1/auth/register` controller.
- [x] Transactional registration and encoded password storage.
- [x] Stateless HTTP security configuration with public registration.
- [x] Mockito service tests and standalone MockMvc controller tests.
- [x] PostgreSQL registration integration test with test profile and test rollback.
- [x] Rollback annotation committed; uploaded working tree was clean at source review.

These checks record implementation presence, not a claim that the test suite passed during review. The review environment could not download Gradle because network access was unavailable, and its default Java runtime was 17. No runtime test result was established here.

## Finish Day 1

- [ ] Add `@ActiveProfiles("test")` to `ChessBackendApplicationTests` so it does not use the development database.
- [ ] Flush and clear the persistence context before integration-test reads; assert email, credential linkage, and timestamps.
- [ ] Add a rollback integration test without test-level `@Transactional`: force a failure after player creation, then verify the player was not committed.
- [ ] Add `@Size(max = 255)` to registration email validation.
- [ ] Remove the duplicated implementation declaration of `flyway-database-postgresql`; retain its runtime declaration.
- [ ] Run the full suite twice against PostgreSQL to check repeatability.
- [ ] Review the diff and commit the changes.

The existing unit test for credential persistence failure verifies exception propagation only. The happy-path transactional integration test verifies persistence but cannot independently prove that the service transaction is required: the test supplies a transaction itself.

For the rollback integration test, a controlled password-encoder failure after player insertion is a small first test of the current transaction boundary. Keep database uniqueness and credential constraint checks as separate coverage.

## Verification commands

From `backend/`:

```bash
./gradlew --version
./gradlew test --tests com.chessapp.auth.service.AuthServiceTest --tests com.chessapp.auth.controller.AuthControllerTest
SPRING_PROFILES_ACTIVE=test ./gradlew clean test
SPRING_PROFILES_ACTIVE=test ./gradlew test --rerun-tasks
git diff --check
```

Use Java 21 and a running PostgreSQL instance with `chess_test_db` owned by the configured test user. The explicit profile on the complete suite protects the context-load test before its annotation is fixed. Test reports appear under `build/reports/tests/test/`.

Existing rows from earlier non-transactional runs are not removed by adding `@Transactional`. Inspect and clean only the intended test fixtures if necessary; the credential foreign key requires deleting dependent credentials before their players. Do not treat gaps in generated IDs as a test failure.

## Day 2 sequence

| Order | Work | Completion evidence |
| --- | --- | --- |
| 1 | Consistent registration errors; known duplicate identities return 409 | Controller/integration tests cover invalid and duplicate requests |
| 2 | Email/username identity rules and password byte-length validation | Boundary tests, including Unicode passwords, return controlled client errors |
| 3 | Replace the player entity crossing into auth with a focused service DTO | Auth no longer imports player persistence entities; registration tests still pass |
| 4 | Login with password verification | Valid credentials succeed; invalid credentials return a consistent authentication failure |
| 5 | JWT issuance and request authentication | Valid token authenticates; expired, malformed, and invalid-signature tokens are rejected |
| 6 | Current-player endpoint and real security-filter-chain tests | Public registration/login and protected-resource access are verified |

Keep Day 2 backend-focused. Complete registration correctness before layering token authentication on top. Select the JWT library and configuration during implementation rather than documenting an unimplemented API as final.

## Deferred follow-ups

- Enable Flyway migration-name validation in shared configuration.
- Document and implement the chosen normalization/case policy for identities.
- Keep actual deployment credentials outside version control; align local overrides with the documented profile convention.
- Expand registration validation coverage and verify public registration through the real security chain.
- Add future schema changes as new migrations rather than editing applied V1/V2.
- Update this file after each completed increment, distinguishing implemented behavior from proposed work.

## Suggested commits

For this documentation update:

```text
docs: align architecture and progress with registration implementation
```

For the next code increment:

```text
test: isolate Spring tests and verify persisted registration data
```

Use a separate commit for the service rollback integration test when added. Review staged changes before committing; no commits were created by this documentation update.
