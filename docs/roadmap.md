# Roadmap

## Verified

- Local WebSocket delivery passed twice at 500 concurrent games with 1,000 sockets.
  See [load testing](load-testing.md) for the workload and latency.
- The two-browser reconnect check confirmed board, clock and move-history recovery.

## MVP acceptance

- [x] Verified a two-browser game from matchmaking through completion. After
  refreshing both browsers, confirmed the result, SAN move history, replay,
  and both players’ ratings and statistics.
- Review the README and the two screenshots in [screenshots](screenshots/) as a
  portfolio walkthrough.

## Later work

- Gameplay polish: gate the promotion chooser with legal hints, add an
  active-clock hourglass, improve replay controls and expose PGN download.
- Optional features: chat, rematches, public player history, additional draw
  rules and configurable time controls.
- If deploying: measure sustained gameplay through the deployment proxy with
  a separate load generator. Use observed latency, CPU and connection use to
  guide sizing. Add slow-client, reconnect-surge and admission-limit checks
  when deployment usage warrants them.
