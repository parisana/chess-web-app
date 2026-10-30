# Apply Day 21 documentation replacements

Extract this package into the project root and overwrite the included Markdown files. It contains no production implementation, tests, migrations, dependencies or configuration. Keep the actual user Day 21 source as the implementation baseline.

The package synchronizes current main documents, preserves their Day 20 versions in docs/history/main-documents-through-day20.md, and adds move-history-flow.md, day21-history-review.md and day21-handoff.md. Existing historical Day documents remain unchanged.

Review evidence: source inspection on 30 October 2026; user reports all looks good; no assistant tests/builds/migrations/browser checks.

Suggested commit:

```text
docs: document persisted move history and SAN gameplay flow
```
