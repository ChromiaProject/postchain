# Snapshot modules

To be snapshot-compatible, a module must implement the
[SnapshotAware](../../postchain-spi/src/main/kotlin/net/postchain/gtx/SnapshotAware.kt)
interface and inform Postchain about every data change within the module’s state. This enables
Postchain to build and maintain snapshots, verify them via block-header commitments, and transfer
compact state to new nodes for fast reconstruction.

A snapshot-aware module must be able to:
- Emit created/updated state as datums to Postchain.
- Reconstruct its state from snapshot datums during import.
- Supply values of permanent datums to Postchain.

## Datums

Each module exposes its state as a sequence of datums. A datum consists of:

- Datum ID: A unique, dense sequence starting at 0 within the module context.
- GTV payload: The datum represented as GTV. It must be self-descriptive enough to rebuild on import.
  Besides the value, include any metadata needed to reconstruct the state (e.g., table reference etc.).
- Permanent flag: `true` for immutable (append-only) entries, `false` for dynamic entries that can be updated.

Notes:
- Datum ordering is not guaranteed during import. Do not assume IDs arrive in sequence.

## Emitting updates

A module must track all changes to its state and emit them to Postchain during block building.

- Format: The GTV payload is module-defined but must include all information required to rebuild.
  Keep it compact; snapshots may contain many datums.
- API: Use `SnapshotContext.emitDatum()` for each creation/update.
- Deletions: Datums are never deleted; instead, write an updated GTV that marks the item as removed for your module.

During import, the module must rebuild its content from calls to `SnapshotAware.constructDatum()` so
that the resulting database state exactly matches the state after replaying the corresponding blocks.

## Reconstructing state

A module must be able to reconstruct its database state from snapshot datums. When Postchain
initializes import, the module can assume its database schema already exists (module is already initialized)
and is empty.

The `SnapshotAware` lifecycle during import:

1. `initializeImport(ctx)` — Called once before any `constructDatum()`. Prepare caches or temp tables if needed.
2. `constructDatum(ctx, datumList)` — Called zero or more times with batches of one or more datums; order is unspecified. Insert/update your tables accordingly.
3. `finalizeImport(ctx)` — Called once after all datums are processed. Perform integrity checks, index builds, or cleanup.


## Examples

A minimal test implementation `SnapshotTestModule` can be found in
[SnapshotTest](../../postchain-devtools/src/test/kotlin/net/postchain/integrationtest/snapshot/SnapshotTest.kt).

## Tips and gotchas

- No hard deletes: use tombstones in GTV to mark removals.
- Mapping: A datum does not have to map 1:1 to a row. Reserve a range (e.g., datum 0) for metadata such as table schemas or name-to-ID mappings if helpful.
- Determinism: Import must be deterministic regardless of datum batch sizes and ordering.
- For performance consider remove table constraints such as keys and indexes and re-create them after the import.
