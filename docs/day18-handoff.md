# After Day 18 — historical handoff

This file records the Day 18 HTTP gameplay boundary. For current continuation, use [the Day 19 handoff](day19-handoff.md).

Day 18 exposed POST /api/v1/games/{gameId}/moves using JWT-derived Player identity. SubmitMoveRequest validates a required nonnegative expectedVersion, lowercase distinct coordinates and optional explicit promotion. POST returns GameMoveResult. GameResponse.from(game) maps reads including version, result, terminationReason and finishedAt.

Game-owned failures are access 403, missing game 404, finished/stale/wrong-turn 409 and illegal move 422. Field validation remains 400/VALIDATION_FAILED. Real-JWT HTTP test sources cover committed moves, POST/GET agreement, validation, precedence, special moves and terminal results/release.

No migration, rules, runtime configuration or frontend implementation changed in Day 18. Tests followed the standing user instruction; no independent assistant tests/builds or final Day 18 archive review had occurred at that session's close.

Minimal frontend gameplay was the recommended next milestone. It was subsequently implemented and source-reviewed in Day 19. History, real-time delivery, clocks, extra draw rules and disconnect policy remained separate decisions.
