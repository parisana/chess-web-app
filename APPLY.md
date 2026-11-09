# Apply the bounded stability increment

1. Add load-tests/websocket-stability.ts to the existing load-tests directory.
   It uses the existing websocket-fixture.ts and run-k6.mjs. Leave prior scripts.
2. Merge the package-scripts.json entries into package.json's scripts object.
3. Add docs/day29-websocket-stability.md to docs; link it from load-testing.md and
   the remaining Day 29 task entry. Do not overwrite user-managed config/docs.
4. Keep the backend on its isolated loadtest profile/database, pool 10, two delivery
   workers, queue capacity 256 and default ten-second heartbeat configuration.
5. Start vmstat and run npm run ws:stability:20 with console capture exactly as
   described in the guide. It includes the type check. Share the three outputs.

No backend source/configuration or dependencies change. This is the final selected
bounded local increment, not authorization for post-MVP experiments. No assistant
build, typecheck or load execution occurred. The k6 constructor/event typing fixes
from the earlier socket increment are already included in the new file.
