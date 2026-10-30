# Day 21 — source review and closing notes

## Evidence

Reviewed on 30 October 2026 against the actual user chess-web-app-day21-end.zip, compared with chess-web-app-day20-end(1).zip including Day 20 documentation replacements. No assistant-generated production archive replaces that baseline. Inspected changed production/test sources, migrations, templates and models. No blocking defect was found in inspected changes.

The user reports all looks good; tests are treated as green under the standing agreement unless failures are reported. No assistant tests, builds, migrations, database upgrade runs or browser checks were run. Source inspection does not establish independent runtime acceptance.

## Confirmed behavior

- V7 baseline/completeness/count plus history table, foreign keys, structural checks and unique game/sequence constraint. Existing games remain incomplete; new games record from creation.
- GameService reuses the complete legal set for SAN before mutation; flushes the versioned game before history insertion; flushes history before terminal release/event publication. All writes share the existing transaction.
- Extracted updateGameForAcceptedMove, flushGameOrRejectConflict, releaseReservationsIfFinished and publishGameChanged retain transaction ownership in public service methods.
- Bounded participant-protected GET history with immutable list response. Active/finished reads use existing access errors. Game GET and POST result shapes remain unchanged.
- Pure Java SanFormatter, Bean wiring and real formatter spy in isolated command tests. V8 preserves null SAN for pre-SAN rows; new constructors require SAN.
- Coordinated board/history GETs, exact history-version acceptance, newer-history reconciliation, older-history rejection, cancellation across both stages and explicit post-command refresh.
- Separate coordinate and SAN columns, matching headers and null-SAN “—”. History-specific errors do not independently block moves. Visible finished games recover missing/failed history.

## Source-confirmed test evidence

| Class | Inspected additions |
| --- | --- |
| GameTest | New-game baseline/completeness/count, monotonic count and finished-game guard |
| GameMoveServiceTest | History repository mock, real SAN spy, optimistic conflict prevents insertion/release/event |
| GameMoveIntegrationTest | Ordinary/special/terminal history, timestamp agreement, rejection/rollback preservation, one recorded concurrency winner, simulated legacy continuity and bounded query |
| GameMoveHttpIntegrationTest | Empty/ordered history for participants, authentication/access/missing errors, legacy response, finished reads, SAN capture sequence and null-SAN compatibility |
| SanFormatterTest | Ordinary moves/captures, both castling colors/sides, en passant, promotion/underpromotion, check/mate/stalemate, file/rank/full-source disambiguation, pinned alternative and rejected proposal |

The bounded-query fixture is sequential; it does not force a concurrent history-read interleaving. Legacy fixtures simulate post-migration state rather than upgrading a real old database. No new frontend coordinator suite, full socket integration suite or WebSocketPropertiesTest is present. Existing frontend authentication tests remain unchanged. These are coverage limits, not reported failures.

## Nonblocking cleanup

The unchanged unused combineLatest import remains in game.ts and map in home.ts. The GameHistoryResponse inline comment “almost always true” could more precisely say “false for V7 legacy games; unrelated to completion”. These are optional source cleanups, not included in this documentation-only package.

## Scope limits

V7 cannot reconstruct prior moves. V8 leaves pre-SAN entries null and supplies no backfill. Recorded entries are individual moves relative to the baseline, not FEN fullmove numbers. Full history is returned without pagination. SAN is English notation; PGN export and replay controls are absent. Existing Day 20 transport/expiry/session and initial-read recovery limits remain. Closing a page never finishes a game or releases reservations.

## Suggested user checks

1. Two sessions: moves from both sides update coordinates and SAN after refresh/reconnect.
2. Capture/promotion/castling and terminal moves appear once with matching server state.
3. A legacy game retains its baseline and incomplete notice; null SAN displays “—”.
4. A history-only request failure leaves the accepted board usable and recovers on refresh or safety checks.
5. Navigate while history GET is pending; no old-game list appears. Submit while a permitted background history read runs; it is cancelled and history catches up afterward.
6. Finished games with failed history recover while visible; synchronized finished games cease safety reads.

These are suggested checks, not an assistant pass log.

## Documentation package

Contains documentation replacements only, a history/SAN companion, this review, next-session handoff and an archived Day 20 document snapshot. It changes no production code, tests, migrations or configuration.

Suggested documentation-only commit:

```text
docs: document persisted move history and SAN gameplay flow
```
