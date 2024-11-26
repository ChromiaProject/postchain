# Fast synchronizer

The purpose of fast synchronizer is to synchronize a node that is behind on a blockchain by fetching and loading blocks
as fast as possible. Signer nodes will always use fast synchronizer while replicas will only use it initially and once
it has synchronized to the tip of the blockchain it will switch to using [slow synchronizer](Slow-Synchronizer.md).

## Strategy

Fast synchronizer will begin by sending out a configurable amount of parallel requests for blocks to randomly
selected peers. E.g. if a node is synchronizing a blockchain from its beginning it will send out requests for block
heights 0 .. parallelism - 1. These requests are referred to as *jobs*.

Once a job is finished successfully its resulting block will be queued up to be committed on the node. However, since
blocks must be applied in order it has to wait until any jobs for earlier heights have been committed successfully.
Once the block has been committed a new job can be launched to fetch the next height that there is no current job
scheduled for yet.

Fast synchronizer will track the behavior of the different peers and apply appropriate statuses to them. Read more in
[peer states](Peer-States.md).

## Completion

Fast synchronizer will synchronize until no peers are considered syncable or the node or blockchain is stopped or
restarted.

This rule has two exceptions:

1. If `fastsync.exit_delay` amount of time has not passed since synchronization started. This delay gives the node some
   time to establish connections to all peers which may prevent a premature exit.
2. If node has not synchronized up to `fastsync.must_sync_until_height` yet. This is another way to prevent premature
   exits.

See [node configuration](../configuration/Node-Configuration-Properties.md) for more details.

## Legacy peers

Fast synchronizer can handle legacy peers by first attempting to send modern message types to the peer and if no
response is received it will try with legacy message types.

## Messages

For a detailed description about each message please see [EBFT messages](../ebft-protocol/EBFT-Messages.md).

Fast synchronizer is sending the following messages:

- `GetBlockHeaderAndBlock`
- `GetBlockAtHeight` - Used for legacy peers that do not support `GetBlockHeaderAndBlock`.

And expects to receive in response:

- `BlockHeader` - If the peer does not have the requested block it will send this message with height set to the latest
  height it has. Now we can mark this peer as drained at that height.
- `UnfinishedBlock` - If the peer has the block at requested height.
- `CompleteBlock` - From legacy peers. If the peer has the block at requested height.
