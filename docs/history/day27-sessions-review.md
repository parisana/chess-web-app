# Day 27 — persistent session source review

## Evidence

Reviewed on 4 November 2026 against actual user `chess-web-app-day27-end(1).zip`, compared by file bytes with the actual Day 26 archive including its documentation replacements. Raw path counts: 15 added, 5 removed, 56 changed. This includes the BoardTestRenderer move into `com.chessapp.support` and associated import-only rule-test changes. Four new Java suites contain 18 ordinary test methods; the existing socket suite now has seven ordinary methods and two parameterized methods (13 source cases). Frontend has six auth-flow and three synchronization test cases.

The user reports the previously reported failures resolved, after shared CSRF, cookie-name, outbound mock and probe fixes, and reports browser login/flow working. This is user-reported execution, not an assistant run. No assistant tests, builds, migrations, database calls or browser checks were performed. No separate comprehensive frontend test execution or scenario-by-scenario browser acceptance is established. No production blocker was found in the inspected milestone scope; this is not an exhaustive security/concurrency audit.

## Inspected implementation

| Area | Source evidence |
| --- | --- |
| Session persistence | Spring Boot session-JDBC starter; V14 framework tables/indexes and cascading attributes; no custom session entities |
| Login | BCrypt verification reused; canonical Player-ID String principal; explicit SecurityContext save; existing session-ID change and CSRF reset |
| Cookies | Explicit SessionCookieConfig reading YAML; CHESS_SESSION, HttpOnly, Lax, 7d cookie max-age; Secure environment flag |
| HTTP security | IF_REQUIRED sessions; SPA CSRF; public csrf/register/login subject to CSRF on writes; authenticated other routes; API 401; POST logout 204 |
| Controllers | Principal.getName identifies the Player; game membership/version/transaction logic retained |
| WebSocket | Authenticated upgrade, origin restriction, inherited principal, handshake session/CSRF metadata, CONNECT header comparison and persisted-session binding checks |
| Angular | Startup restoration; in-memory profile/revision only; built-in XSRF handling; server logout; revision-guarded HTTP/socket callbacks |
| Tabs | SESSION_CHANGED broadcast; receiver clear/navigation/restore/Home flow; exact ordering and limits in tab companion |
| Removal | LoginResponse, JWT config/token service/resource-server starter and app.jwt settings removed; shared Clock moved to common.config.TimeConfig |

The actual migration is named `V14__create_auth_sessions.sql`; it creates SPRING_SESSION / SPRING_SESSION_ATTRIBUTES. Do not rename an applied migration. Production session cleanup runs every minute. The test profile now disables session cleanup and game timeout scanning; the Day 26 shared-profile follow-up is applied in this uploaded source. Session inactivity and cookie expiry are separate; there is no refresh-token feature or absolute server-session lifetime cap.

## Test-fixture corrections present

TestCsrf sends the actual cookie/header protocol without substituting the filter repository. TestSessions seeds persisted authenticated contexts and serializes their cookies with the configured CookieSerializer; it bypasses password verification, which dedicated login tests exercise. Its sessions require explicit cleanup even in transaction-managed feature tests because framework session transactions are independent.

SessionCookieConfig is present after tests observed default SESSION instead of configured CHESS_SESSION. The outbound executor test now permits preSend and denies beforeHandle. The socket readiness fixture tracks the exact set of generated probes: all same-Player sockets ignore fixture probes, but only the matching peer's probe releases its latch. Bodyless ERROR handling and rejected-peer stop behavior are retained. Registration-backed socket fixtures delete credentials and persisted sessions during cleanup.

Coverage includes persisted attributes/cascade/expiry; cookie-only authentication; missing/unknown/expired sessions and deleted Players; login validation/credentials; session-ID replacement; CSRF-protected profile writes/logout; feature HTTP authentication and error precedence; actual HTTP-upgrade 401/origin 403; missing/incorrect CONNECT CSRF; forbidden destinations/SEND; real commit/rollback hints; two sockets sharing a logged-out session; and delivery filtering after expiry. No new V13-to-V14 isolated migration-upgrade fixture was added. The proposed parameterized missing-CSRF test for every game command is not present; the dedicated HTTP authentication suite covers missing-CSRF login, profile PATCH and logout, while game/profile/queue fixtures supply valid cookie CSRF for their domain assertions.

Backend no-delivery assertions use bounded observations with ready receivers and a positive control. The three tab-sync tests mock channel events, restoration and navigation. They do not establish actual BroadcastChannel/browser behavior or all simultaneous auth schedules. Framework session cleanup's scheduled trigger is configured, not separately exercised by these tests.

## Limits and deferred work

- BroadcastChannel is best-effort and has no fallback/replay. Revisions do not serialize cookies or all cross-tab login/logout races. The receiver checks revision after navigation resolves but not the navigation boolean itself.
- WebSocket access rechecks query the database; outbound application delivery has two checks. No load/latency benchmark, expiry-event-driven socket closure or recall of transmitted messages is claimed.
- Preflight HTTP failure can leave live delivery unavailable until a new client lifecycle. Application startup restoration can time out and render Login with recovery feedback.
- Secure defaults false for local HTTP; deployment configuration remains user-managed. No deployed proxy/HTTPS acceptance is established.
- Password/OIDC/social login extensions, all-device logout, session management UI and account lifecycle changes are not introduced.
- Existing extra chess draws, timeout mating-possibility adjudication, acceptance/abandonment, PGN UI, promotion chooser legality gating and post-MVP replay polish remain deferred.

## Deferred game-action click investigation

User reports some Offer draw/Accept draw/other clicks occasionally require another attempt. No reproduction or runtime trace establishes the cause, and no fix was requested. Accept draw/resign intentionally require confirmation. Source guards can block commands during initial loading, submission, replay, pending promotion or needsRefresh; newer snapshots/blocking synchronization can clear confirmations. An ordinary nonblocking safety read alone does not disable actions. A 403 could also be CSRF rejection and a 409 stale version, but neither is established as the observed cause. Later investigation should first distinguish no POST, confirmation-only behavior, and POST rejection/status/code. Do not silently retry commands or claim this issue fixed.

## Deep reading

Read [session authentication](session-authentication-flow.md), then [tab synchronization](tab-synchronization-flow.md). Prioritize `anotherLoginChangesSessionIdAndRejectsOldCookie`, `logoutStopsDeliveryToBothTabsSharingTheSession`, `resignationReachesOnlyParticipantsAfterOuterCommit`, the outbound executor recheck, and the frontend stale-401/newer-local-login tests.
