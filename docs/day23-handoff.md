# After Day 23 — next working session

Continue from actual user chess-web-app-day23-end.zip plus Day 23 documentation replacements and subsequent inline changes. No assistant-generated implementation archive replaces the user's source.

## Working agreement

User implements coherent inline increments. Preserve modular monolith/conventional feature layers, Player naming, cross-feature services/events, pure Java rules, useful comments, separate Angular HTML/CSS, minimal frontend tests and user-managed configuration. Do not run tests/builds without explicit request. Treat tests as green unless a failure is reported. Documentation replacement packages are welcome. Include a brief "what to understand" note with test increments; the user skims routine fixtures and deep-reads selected important tests.

## Reviewed checkpoint

Source-reviewed on 31 October 2026 against attached user Day 23 ZIP, compared with chess-web-app-day22-end(1).zip. Only three test sources were added; production, existing tests, migrations, configuration, dependencies and frontend are unchanged. No blocking issue found in inspected additions. User reports tests passing; no assistant tests/builds/migrations/browser checks were run.

WebSocketPropertiesTest exercises real binding with synthetic properties, constructor validation and duration boundaries. WebSocketIntegrationTest has nine invocations for bad CONNECT, forbidden subscriptions, SEND rejection, participant-only resignation after outer commit and rollback suppression. It uses real clients/JWTs/broker/listener/database. Private byte probes establish readiness for both participants and outsider. Client heartbeats are disabled; no-update checks are bounded to 300 ms and positive reads allow five seconds.

Both socket fixture corrections are present: SimpleMessageConverter and null-safe text for bodyless ERROR frames. Seven rejection cases had failed; converter replacement alone did not resolve them; null handling resolved the reported failures. Do not relax production security or remove ERROR assertions to address that historical issue.

GameMigrationIntegrationTest is declared in uploaded game/G.java. It applies real migrations to unique schemas, tests V6-to-V10 baselines/constraints and V7/V8/V9/V10 history/null SAN/resignation continuity. Restore/release the schema-bound connection and drop only the generated schema; Flyway.clean is disabled. These are SQL upgrade fixtures, not service/JPA/deployment rehearsals. V5 backfill with preexisting rows is not covered.

The two Day 22 draw HTTP tests previously reported as post-upload additions are present and source-reviewed. Do not ask the user to add them again.

## Application behavior retained

Day 22 versioned resignation and draw negotiation remain: either participant can offer/resign regardless of turn; recipient accepts/declines; sender withdraws. Accepted moves and completion clear offers, rejected moves preserve them. Public GameService methods own transactions; optimistic flush, required reservation release and event publication roll back atomically. AFTER_COMMIT hints are best-effort and cannot roll back a committed game on send failure.

Lifecycle versions advance independently of move count and insert no history rows. History must reconcile even when the list is unchanged. Angular shares one POST lock across moves/lifecycle commands; promotion resolves before lifecycle actions; resignation/acceptance use inline confirmation. Conflicts/uncertain outcomes reconcile through GET without replay. Home keeps explicit Open game. Disconnecting never finishes a game or releases reservations. Initial failed board GET without a snapshot still has no timer retry; visible finished games recover missing/outdated/failed history.

## Read first

README.md, docs/day23-reliability-review.md, docs/backend-reliability-testing.md, docs/development-progress.md, docs/roadmap.md and docs/architecture.md. Consult API, lifecycle, realtime, frontend-gameplay and move-history companions for the selected feature. Pure rules remain documented in movement-pipeline-architecture.md. Historical Day 22 reviews retain their old evidence and are superseded by Day 23 where coverage changed.

## Cleanup and remaining limits

The user subsequently reports the test naming cleanup completed; treat it as handled and do not ask again. The reviewed upload still records the old G.java filename. Removing the unused ByteArrayMessageConverter import from WebSocketIntegrationTest remains optional; no test source is changed by this documentation package. APPLY-DOCS.md is absent in the current upload. Other prior optional import/comment formatting cleanups remain optional.

No new frontend coordinator suite; no real-socket expiry/origin-denial/heartbeat/reconnect/multiple-session matrix; no durable delivery guarantee; no isolated upgraded-schema JPA validation or every prior-version fixture. History bounding is still sequential. These limits do not implicitly authorize another reliability increment.

## Next milestone

Day 23 is complete. Select a milestone in a new chat before implementation. Replay/PGN export is a reasonable candidate because baseline FEN, persisted history and SAN exist; legacy incomplete history and old null SAN need explicit handling. Ratings/statistics, clocks, extra rule-based draws, acceptance and abandonment remain separate choices. No next feature is authorized by this handoff.
