# Day 24 — replay and PGN source review

## Evidence and scope

Reviewed on 31 October 2026 against actual user chess-web-app-day24-end.zip, compared by file bytes with actual Day 23 source including its documentation replacements. Four files are added, nine changed and none removed. Of those thirteen paths, twelve are source/test files and one is roadmap.md. The migration test has its correct GameMigrationIntegrationTest.java filename in the current upload; no rename is outstanding.

The user reports increments working and tests follow the standing agreement to treat them as green unless failures are reported. Replay route switching was not manually verified. No assistant tests, builds, migrations, browser checks or database commands were run. Archive inspection/document checks are not application verification. No blocking issue was found in inspected changes.

## Source changes inspected

| Area | Observed scope |
| --- | --- |
| Backend export | New GameExportService, PgnFormatter and PgnExportUnavailableException |
| Backend transport/errors | GameController export endpoint; GameErrorCodes/handler add structured 409 |
| History DTO | Additional ordering comment; no signature or behavior change |
| Formatter tests | New PgnFormatterTest: eight methods, ten invocations including three results |
| HTTP tests | GameReadIntegrationTest: six added methods, seven invocations including both participants |
| Angular | Game page TS/HTML/CSS add replay; existing board/API service unchanged |
| Roadmap | User added explicit PGN download UI deferral |

Core rules, GameService, repositories, entities, migrations, dependencies and configuration are unchanged. No frontend PGN API method/button/Blob handling is present, matching the user's deferral. PgnFormatter.generateTokens extraction preserves the proposed behavior; Long-versus-long version comparison unboxes numerically.

## Inspected behavior

Export authorizes membership before reading/exposing availability, captures game result/version/count and reads only moves through that count. Player names use PlayerService. Complete/count/sequence/SAN guards reject unsupported history. Headers/results, UTC creation date, custom baseline FEN/SetUp, Black-first numbering, token wrapping, tag escaping and UTF-8/no-store attachment handling are present.

Replay separates authoritative game state from numeric display selection, blocks shared command permission, cancels pending local proposals and retains the cursor while live/history versions advance. Baseline and saved resulting positions permit legacy replay independent of SAN. Correct route initialization and history-clearing error resets are present in source. Latest explicitly resumes live mode; automatic Next-to-live was discussed only.

## Coverage and limits

Formatter tests cover exact standard headers/movetext, custom Black baseline, three finished results with zero moves, incomplete legacy history, null SAN, row-count/sequence failures, escaping and wrapping. Fixtures are formatting inputs rather than legality-replayed games. Snapshot mismatch/blank SAN have production guards but no dedicated new assertions in this test class.

HTTP additions cover either participant and attachment/no-store headers, missing authentication/game, outsider-before-legacy precedence, persisted e4 plus resignation result and persisted null-SAN rejection. They run inside existing rollback-managed transactions with persistence-context clearing for relevant fixtures. They do not prove commit delivery, draw-agreement export through the real HTTP lifecycle path, username-change export or a forced concurrent-export schedule. Result enum unit coverage is distinct from those workflow paths. Export bounding uses the same established append-only assumption as history reads.

There is no new frontend replay/coordinator suite, independent browser acceptance or build result. The user explicitly leaves route-switch navigation unverified. The backend formatter trusts internally stored SAN rather than parsing arbitrary notation or recomputing legality. Name reads are current, not historical or one cross-Player snapshot. No compatibility round-trip with an external PGN importer was run.

## Intentional deferrals and cleanup

PGN download UI is deferred; retain the implemented backend. Next-to-live-at-the-end remains optional and unimplemented. Premature promotion selection for a move failing king safety is a known UI limitation intentionally retained for the user to improve later. Existing backend legal membership still must reject self-check; accepting such a move would be a separate defect. No new reproduction or rules change is claimed.

The uploaded frontend companion lacks the promotion tracking section proposed in conversation. This documentation package supplies it. It also removes current-document statements saying replay/backend PGN are absent. The existing DTO comment historyComplete // almost always true remains optional cleanup; its precise meaning is complete recording since creation, not completed lifecycle. The moves ordering comment describes repository ordering, not constructor sorting. Existing unused ByteArrayMessageConverter/combineLatest imports and formatting remain optional; none is changed here.

## Documentation package

Replaces nine current main documents, adds this review, replay-pgn-flow.md and day24-handoff.md, and preserves the uploaded nine documents in history/main-documents-through-day23.md. Historical review/handoff files remain unchanged. Extract the ZIP at the project root. No APPLY-DOCS.md, production source, tests, configuration or migrations are included.

Suggested commit:

```text
docs: record Day 24 replay and PGN export checkpoint
```
