# Day 20 — source review and closing notes

## Evidence

Reviewed on 30 October 2026 against the actual user `chess-web-app-day20-end.zip`, compared with `chess-web-app-day19-end(1).zip`. Inspected changed production/test sources, dependency/proxy/configuration changes, templates and the existing architecture/contracts. No blocking defect was found in the inspected changes.

The user reports that implementation looks good and follows the standing instruction that test failures will be reported. No assistant tests, builds, migrations or browser checks were run. Source inspection is not independent frontend compilation, measured coverage or comprehensive runtime acceptance.

The previously reported generated-chunk 404 was reported resolved. The later board question was clarified: the board does not automatically open after pairing. That is intended; Home reveals an Open game link. No navigation fix is required.

## Confirmed implementation

- Shared typed YAML WebSocket settings, derived destinations and HTTP upgrade matcher; configured heartbeat validation is 1–300 seconds.
- Native WebSocket/STOMP with existing-decoder bearer authentication, exact private subscription, rejected client publishing and strict expiry checks.
- In-process session-expiry registry and outbound checks before enqueueing and executor handling; disconnect cleanup is idempotent.
- Immutable game change events and private notifications to both participants only after outer commit. No new migration or change to pure chess rules.
- Root Angular connection lifecycle, subscription recreation, heartbeat/reconnect handling, stale-session guards and local expiry cleanup.
- Automatic queue discovery and game reconciliation, GET/POST coordination and no command replay.
- Home’s HTML/CSS extracted; user naming queueStatusResponse and realtimeService retained.
- Custom board remains unchanged. The Day 19 submission guard and successful-read message clear are included.

## Test evidence and gaps

Present: StompSecurityInterceptorTest, StompOutboundSecurityInterceptorTest and three new GameMoveIntegrationTest cases for creation recipients, outer-commit timing and rollback suppression. The byte[] disconnect fixture correction is present. GameMoveServiceTest adds the event-publisher mock. Existing rules/HTTP/authentication sources remain in place.

Not present: the previously proposed WebSocketPropertiesTest, a full socket delivery/security test and new Angular notification/game coordination tests. The existing five frontend authentication tests are unchanged. Do not describe the missing binding test as implemented or passing. These coverage limits do not establish a production defect.

## Nonblocking cleanup

The inspected game.ts still imports unused combineLatest; home.ts still imports unused map. Removing those imports is optional and does not change behavior. No production edits are included in this documentation package.

## Recovery limits to preserve

Notifications are best-effort hints. No durable replay, subscription receipt or exactly-once guarantee exists. The client’s CONNECTED label is not proof that broker subscription registration has finished. Periodic HTTP recovery covers missed hints subject to connectivity/browser scheduling.

Game periodic checks require a visible page and a loaded snapshot. An initial GET failure without a snapshot needs manual/focus/connection/hint-triggered recovery. Blocking synchronization can cancel promotion; same-version nonblocking safety reads preserve it. STOMP rejection stops automatic reconnect for the current lifecycle while leaving HTTP available.

Expired-session application messages are filtered; physical socket closure at exact expiry is not scheduled by the server. No disconnect outcome, abandoned-game release, history or clock policy is introduced.

## Suggested manual scenarios

1. Pair two authenticated sessions: waiting Home updates and both use Open game.
2. Submit ordinary/illegal moves and verify only server-accepted state changes the board.
3. Disconnect one session, move from the other, then reconnect or return focus and recover current state.
4. Open promotion and wait through an unchanged safety read; verify the choice persists. Cancel and select a new source.
5. Deliver a notification while POST is pending; verify the accepted response is retained and no POST is replayed.
6. Finish a game, observe both results and queue again after reservation release.
7. Log out or let the token expire; confirm session/connection cleanup and no old-session UI updates.

These are suggested user checks, not an assistant pass log.

## Documentation package and commit

The package replaces the current main documents, adds the real-time companion/review/handoff and archives previous main-document checkpoints. Existing Day documents remain historical. It contains no application source, tests, migrations, dependencies or runtime configuration.

Suggested documentation-only commit:

```text
docs: consolidate architecture and document realtime gameplay flows
```
