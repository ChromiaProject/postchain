# Snapshots

Snapshots allow a new node to join an existing chain significantly faster. Instead of downloading and validating every historical transaction, the node requests a compact, proven snapshot of the chain state near the current height. This reduces bandwidth and, more importantly, the time to become operational.

A snapshot is constructed and maintained locally by every node as blocks are produced. Validator nodes include snapshot commitments in block headers so that other nodes can verify snapshot data by cryptographic proofs before accepting it.

## Scope and limitations

- Snapshots can only be enabled when a chain is created (height 0).
- Existing chains cannot enable snapshots retroactively.
- All modules used by the chain must be snapshot-compatible. See [Snapshot modules](snapshot-modules.md).

For the synchronization process and message flow, see [Snapshot synchronizer](../synchronization/Snapshot-Synchronizer.md).

## How it works (high level)

1. While producing blocks, each module emits state changes as "datums" to the snapshot store.
2. Periodically, the node seals these datums into a Merkle-like structure and commits the roots in the block header.
3. A joining node downloads block headers to the chosen snapshot height, fetches snapshot datums with proofs, verifies them against the committed roots, and reconstructs the state.
4. Once verification completes, the node switches to normal block synchronization.

## Configuration

Snapshots can currently only be enabled at genesis. See:
- Chain-level properties: [Blockchain-Configuration-Properties](../configuration/Blockchain-Configuration-Properties.md)
- Node-level properties: [Node-Configuration-Properties](../configuration/Node-Configuration-Properties.md)
