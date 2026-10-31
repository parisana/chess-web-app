# Day 23 — backend reliability source review

## Evidence and scope

Reviewed on 31 October 2026 against actual user chess-web-app-day23-end.zip, compared with chess-web-app-day22-end(1).zip. File comparison finds exactly three added Java source files: notification/config/WebSocketPropertiesTest.java, notification/WebSocketIntegrationTest.java and game/G.java. G.java declares package-private GameMigrationIntegrationTest. All production, migration, configuration, dependency, frontend and existing test files are unchanged from that baseline.

The user reports the tests passing after fixture corrections. No assistant tests, builds, migrations, browser checks or database commands were run. Source review found no blocking issue in the inspected additions. Documentation generation/ZIP inspection is not application verification.

The prior Day 22 post-upload tests drawCommandsRequireAuthentication and drawCommandsValidateVersionAndPreserveFailurePrecedence are present in GameMoveHttpIntegrationTest and have now been inspected. They cover all four draw routes, unauthenticated requests, missing/null/negative versions and lookup/membership/lifecycle/version precedence. Historical Day 22 review statements retain their original evidence date.

## Inspected coverage

| Source | Actual scope |
| --- | --- |
| WebSocketPropertiesTest | Binding and derived/custom destinations; missing settings; literal paths; overlapping prefixes; invalid/valid origins; invalid, boundary and unparseable durations |
| WebSocketIntegrationTest | Nine invocations: two bad CONNECT cases, four forbidden subscription forms, client SEND rejection, private resignation after outer commit, rollback suppression |
| GameMigrationIntegrationTest, uploaded as G.java | Two fixtures: V6-to-V10 legacy games and constraints; V7-to-V8-to-V9-to-V10 recorded history/null SAN/resignation continuity |

Property tests register only configuration binding, so no database/server is needed. They supply synthetic property values rather than parsing the user's application.yaml and do not verify every consumer's wiring.

Socket tests start the real random-port server and use real JWT issuance/decoding, listener, messaging template, broker and PostgreSQL transactions. They call GameService directly for resignation; the HTTP-to-socket path is not combined in this fixture. Both participants and the outsider independently receive private readiness probes before application assertions. Commit tests compare PlayerUpdate type, game ID and version. Rollback verifies active state, original version, null outcome/time and two reservations.

Migration tests first apply real old migrations, insert rows, then advance using real classpath migrations. Every test owns a unique schema and migration history, uses a schema-bound JDBC connection, restores/releases it and drops only the generated schema. Flyway.clean is disabled. V6 fixtures compare old game columns and reservations, assert legacy baselines/incomplete history/zero counts and exercise V10 offer/completion constraints. V7 fixtures preserve a pre-SAN move, reject resignation at V8, permit it at V9 and preserve it through V10. These SQL fixtures do not exercise service authorization, version updates or atomic release.

## Socket fixture corrections

SimpleMessageConverter is used to preserve already-decoded raw bytes regardless of content type. Bodyless ERROR frames pass null to the callback; text(null) now returns an empty string so the protocol error can be recorded. The user reported seven failures out of nine; the converter replacement alone did not resolve them, and the null-handling change did. Both corrections are present in the upload. They affect only the test client, not production security.

## Limits

Socket absence assertions observe 300 ms per check; positive reads wait up to five seconds. Probe reception establishes current routing readiness, not future delivery. The client disables heartbeats. There are no new real-socket tests for expiry while connected, denied origins, heartbeat negotiation, reconnection, multiple sessions for one Player, every lifecycle action or browser/proxy behavior. Existing interceptor expiry tests remain useful separate coverage. The simple broker still offers no durable replay or browser acknowledgement guarantee.

Upgrade tests run on the configured PostgreSQL test database with CREATE SCHEMA permission. They are not production database clones, deployment rehearsals, old-application compatibility tests or Hibernate validation of the isolated upgraded schema. V5 executes before fixture rows are inserted, so its existing-game reservation backfill is not exercised. Pending pre-V10 offers cannot exist; agreement post-upgrade is checked with SQL. No concurrency/load, sequence-continuation or exhaustive constraint matrix is claimed.

There is no new Angular coordinator suite. Bounded history-read tests remain sequential. No production feature was added in Day 23.

## Nonblocking cleanup

The reviewed upload stores GameMigrationIntegrationTest in G.java. After the review, the user reports the naming cleanup completed; treat it as handled and do not request it again. The corrected local file was not re-uploaded for inspection. The original package-private class name was already correct, so the filename mismatch was not itself a Java compilation error. Remove the unused ByteArrayMessageConverter import from WebSocketIntegrationTest. Neither change is applied by this documentation package.

The prior optional combineLatest/map import cleanup, historyComplete comment precision and formatting notes remain outside this package. APPLY-DOCS.md is absent from the current upload; no removal is needed here.

## Documentation package

Replaces ten current main documents; adds this review, a reliability testing guide and Day 23 handoff; preserves the uploaded Day 22 main documents in docs/history/main-documents-through-day22.md. Historical review/handoff documents remain unchanged. No implementation archive, test replacement, migration, configuration or dependency change is included. Extract at the project root.

Suggested documentation commit:

```text
docs: record backend reliability coverage and Day 23 handoff
```
