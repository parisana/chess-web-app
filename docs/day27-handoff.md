# After Day 27 — next working session

Continue from actual user `chess-web-app-day27-end(1).zip`, these documentation replacements and any subsequent user-applied changes. Never substitute an assistant-generated implementation archive. Reviewed on 4 November 2026.

## Working agreement

User implements coherent inline increments. Preserve modular monolith/conventional feature layers, Player naming, cross-feature services/events, pure Java rules, YAML/user-managed configuration, useful comments, separate Angular HTML/CSS and minimal frontend tests. Do not run tests/builds without explicit request. Treat tests as green unless failures are reported. Include what-to-understand notes and selected deep-read tests. Documentation replacements are welcome; new major milestones start in a new chat.

## Current source checkpoint

Day 27 replaces JWT authentication with Spring Security server-side sessions backed by Spring Session JDBC. The exact V14 filename is V14__create_auth_sessions.sql and its contents create framework session/attribute tables. JWT classes, token DTO, resource-server starter and settings are gone; TimeConfig retains Clock. Principal names remain canonical Player IDs. Registration/BCrypt and game/rules/rating semantics remain.

Login returns PlayerResponse, explicitly saves SecurityContext and changes an existing session ID. GET /api/v1/auth/csrf returns 204/no-store and initializes XSRF-TOKEN; state-changing HTTP requests require the corresponding X-XSRF-TOKEN. Login/register are anonymous-access routes but still CSRF-protected. POST logout invalidates only the current session and returns 204/no-store. Angular gets CSRF before login/register/logout and after successful login/logout, with best-effort handling of post-logout CSRF initialization.

SessionCookieConfig explicitly binds CHESS_SESSION/path/HttpOnly/Lax/Secure/max-age from YAML after the default SESSION mismatch. Configured cookie max-age and inactivity are both 7d but are independent timers; no absolute server cap/sliding cookie renewal is added. Secure defaults false locally via SESSION_COOKIE_SECURE. Framework cleanup runs every minute in production; test cleanup is disabled. Shared test timeout scanning is now false: Day 26's follow-up is applied.

Authenticated WebSocket upgrade inherits Player authentication; handshake copies HTTP session ID, Player name and raw CSRF cookie. CONNECT compares one native X-XSRF-TOKEN, rechecks database session identity, then binds the socket to that exact HTTP session. Outbound MESSAGE checks query session access before enqueue and before handler; inbound operations/heartbeats recheck too; DISCONNECT remains allowed. No proactive server socket closing, cached access guarantee, session-destruction events or distributed broker support.

Angular startup initializer restores csrf/profile with 10s timeout. AuthSessionStore holds profile/revision only. Protected 401 clears only a matching revision, not 403; no command retry. RealtimeService uses revision/authentication, HTTP csrf/profile preflight and native CSRF CONNECT header; JWT expiry timer is removed. A preflight/STOMP failure can stop that lifecycle until renewed; transport errors otherwise reconnect.

Tab events publish after login POST success before its final CSRF read, or after logout POST success/local clearing. AuthSessionEventsService sends only SESSION_CHANGED over chess.auth.session. Receiver switchMap begins a revision/clears Player, awaits Login navigation, checks revision, calls restore (another begin), and navigates Home only on true. Restore never broadcasts. Local revisions do not serialize browser cookies/server mutations; navigation promise booleans are not checked. Unsupported/suspended channels may miss hints. Detailed diagrams are in docs/tab-synchronization-flow.md; WebSocket diagrams are in docs/session-authentication-flow.md.

## Evidence

User reports reported test failures resolved and browser login/flow working. No assistant test/build/migration/database/browser execution; no comprehensive frontend or process-restart acceptance claim. Raw comparison: 15 added, 5 removed, 56 changed paths including BoardTestRenderer relocation/import updates. Four new Java suites have 18 methods; socket suite has 13 source cases; frontend auth/sync suites have 6+3 cases. See review for boundaries.

Fixes present: TestCsrf actual cookie/header fixture; explicit cookie serializer; missing CSRF on expected-401 writes and queue DELETEs; true-then-false outbound mock; registry of exact readiness probes for multiple sockets of one Player. Session fixtures are cleaned independently of rollback-managed domain fixtures. V13-to-V14 isolated upgrade, scheduled cleanup, actual browser BroadcastChannel and complete auth-race schedules are not demonstrated.

## Deferred observation

Some game-action clicks reportedly require another attempt. Source hypotheses: intentional Accept draw/resign confirmation, needsRefresh/other guards, confirmation invalidation by a newer snapshot, or an HTTP rejection. No runtime cause or fix established; user explicitly deferred it. Distinguish no POST from confirmation and 403/409 before changing behavior. No silent command retry.

## Next scope

Milestone is complete in inspected scope with documented limits. Read README, day27-sessions-review, session-authentication-flow, tab-synchronization-flow, roadmap and architecture first. Select the next major milestone separately. Portfolio preparation, extra draws and match acceptance/abandonment are options, not authorization. Preserve existing PGN UI/promotion/replay/timeout/rating deferrals and server-authoritative clocks, including calculatedAt naming.
