# Slow synchronizer

Slow synchronizer is only used by replica nodes once they have synchronized to the tip of the blockchain with
[fast synchronizer](Fast-Synchronizer.md). The intention with the slow synchronizer is to reduce spam from replica nodes
by fetching new blocks at an adaptive rate that is based on the block building rate of the blockchain.

## Strategy

Slow synchronizer will ask a single random peer for a range of ten blocks at a time. The peer will reply with as many
blocks as it can. Slow synchronizer will commit the blocks (if any) and sleep a bit until asking another random peer for
a new range. After making a few requests slow synchronizer will adjust the sleep time depending on how many blocks it
has gotten in response per request.

Slow synchronizer will track the behavior of the different peers and apply appropriate statuses to them. Read more in
[peer states](Peer-States.md).

## Sleep time adjustments

Sleep time is adjusted every 20th request using the formula below.

The acceptable range of amount of blocks received per request is getting no blocks back every third request up to
getting two or more blocks back every third request.

If the amount of blocks received with current sleep time is:

- Within the acceptable range -> Do nothing
- Lower than the acceptable range -> Increase sleep time
  with: <pre>current_sleep * (1 + (number_of_empty_responses / total_number_of_responses))</pre>
- Higher than the acceptable range -> Decrease sleep time with (where 0.5 is a dampening factor):
  <pre>current_sleep * (1 - (0.5 * (1 - (1 / average_number_of_blocks_per_response))))</pre>

## Completion

Slow synchronizer will continue synchronizing until the node or blockchain is stopped or restarted.

## Legacy peers

Slow synchronizer does not handle legacy peers that do not support block range messages. In order to synchronize from
those legacy peers you can disable slow synchronizer. See `slowsync.enabled` property
in [node configuration](../configuration/Node-Configuration-Properties.md).

## Messages

For a detailed description about each message please see [EBFT messages](../ebft-protocol/EBFT-Messages.md).

Slow synchronizer is sending the following message:

- `GetBlockRange`

And expects to receive in response:

- `BlockRange`
