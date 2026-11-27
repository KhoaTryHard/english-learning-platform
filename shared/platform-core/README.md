# Infrastructure SDK snapshot

Canonical security/error/validation/owner-HTTP helpers; no business entities, repositories or databases. Snapshot version: 0.1.0.

Each deployable folder vendors these sources/resources to build in isolation without an unpublished Maven dependency. Consumers can version/deploy independently. Update snapshots explicitly with `python scripts/sync-sdk.py`, then review/test affected consumers. CI uses `python scripts/sync-sdk.py --check` to detect accidental drift. The canonical library is not part of the application's deployment image.
