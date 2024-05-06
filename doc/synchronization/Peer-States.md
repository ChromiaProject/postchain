# Synchronization states

This document describes the different synchronization states peers can have when a node is
running [fast synchronizer](Fast-Synchronizer.md) or [slow synchronizer](Slow-Synchronizer.md).

- `SYNCABLE` - This is the default state of a peer. It is assumed that it has more blocks to fetch.
- `DRAINED` - This means the peer does not have any more blocks to fetch. This state is associated with a height at
  which the node is drained.
- `UNRESPONSIVE` - This means the peer is not responding to requests in a timely manner.
- `BLACKLISTED` - A peer is blacklisted when it has provided too many invalid responses. This could be because the peer
  is buggy or even malicious. Any messages received from a blacklisted peer will be discarded.

All states that are not `SYNCABLE` are time-limited. Meaning we will give nodes second chances after a configurable
timeout.
