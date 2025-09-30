# Snapshot synchronizer

The snapshot synchronizer lets a node join an existing chain much faster by downloading a compact,
proven snapshot of the chain state instead of validating every historical transaction. After the
snapshot is verified and persisted locally, the node switches to regular block synchronization to
catch up to the tip.

This feature reduces initial sync time and bandwidth while preserving integrity through cryptographic
proofs that are committed in block headers.

## When is the snapshot synchronizer used?

Snapshot sync is attempted only if all of the following are true:
- Snapshots are enabled for the chain.
- The chain is new on this node (no prior blocks or state).
- The number of historical blocks to sync exceeds the configured threshold for snapshot sync.
- At least one peer advertises an available snapshot at or beyond the target height.

If any of these conditions fails, the node falls back to normal block synchronization.

## High-level strategy

1. Discover snapshot-capable peers
   - Query peers for their latest snapshot height and the associated context root hashes and top root hash.
   - Select a target snapshot height and a set of peers to fetch from.

2. Download block headers
   - Download and minimally verify block headers up to the target snapshot height.
   - Persist headers to the database for proof verification context.

3. Request snapshot data
   - Request snapshot data from peers. Requests are serialized per peer but run in parallel per context ID.
   - For each received message:
     - Verify the included proof against the expected context root.
     - Store verified datums in the local snapshot store.
     - Request the next range until the peer signals end-of-stream.

4. Finalize local snapshot
   - Once all contexts have been received and verified, build the local root hash.
   - Verify the root hash against the one in the block header.
   - Switch to standard block synchronization from that height.

## Configuration

See [node configuration](../configuration/Node-Configuration-Properties.md) for snapshot sync
settings such as:
- Enabling snapshot synchronization
- Snapshot sync threshold (block count)
- Maximum message size and time budget for snapshot data
- Concurrency settings (per-context parallelism)

## Messages

The snapshot synchronizer uses dedicated EBFT topics for advertising and transferring snapshot
metadata, proofs, and data ranges. Snapshot data is streamed in parallel per context ID. A sender
fills each message up to a size/time limit and attaches a proof so the receiver can verify
integrity. The end of a stream is identified by the proof and/or explicit end markers.

## Peer handling and states

- Snapshot sync integrates with the standard peer status framework. Peers are scored based on responsiveness, correctness, and timeliness.
- Misbehaving or slow peers are deprioritized or temporarily blacklisted. See [Peer states](Peer-States.md) for status semantics.

