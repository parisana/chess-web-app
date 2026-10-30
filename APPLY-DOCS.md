# Day 20 documentation replacements

Extract this package into the project root, preserving README.md, frontend/ and docs/ paths and replacing matching documentation files. It contains documentation only. It does not alter application code, tests, migrations, package manifests, lockfiles or runtime configuration.

The original main documents included in the Day 20 source archive are preserved in docs/history/main-documents-through-day19.md. Existing Day documents in your project remain in place as historical references.

Main files now describe current Day 20 behavior. The new real-time guide adds routing, transaction and connection diagrams. The review distinguishes inspected implementation from missing tests and from user-reported verification.

The intended navigation remains: pairing updates Home, then the Player clicks Open game.

Suggested commit:

```text
docs: consolidate architecture and document realtime gameplay flows
```
