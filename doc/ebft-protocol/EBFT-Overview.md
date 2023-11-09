# EBFT

The general idea is a more robust and easier to implement BFT. By sacrificing some efficiency and using polling instead 
of notifications, we make it both simpler and more robust. Pulling the complete state of nodes removes the need for
retransmission of messages, which is highly complex and entails a lot of potential corner cases.

## Minimum number of nodes for consensus

3f + 1 is the minimum number of nodes that allow an asynchronous system to provide the safety and liveness
properties when up to f nodes are faulty. (See [PBFT](https://pmg.csail.mit.edu/papers/osdi99.pdf))

## Basic structure

- A node is characterized by its state, which indicates where it is in the consensus process. Consensus is based on
PBFT, and involves three stages of communication between nodes. Nodes attempt to keep track of the status of other
nodes at all times. The states are as follows:
  - __WaitBlock__: Node is waiting for the primary to build a block.
  - __HaveBlock__: Node has fetched the current block from the primary and has checked that it is valid.
  - __Prepared__: Node has received status messages from 2f other nodes indicating that they are in the __HaveBlock__
    state, and that the block that they have is the same. This is verified with a ‘RID’, a hash of the block header.
    Collision resistant hash functions make the possibility of two different blocks bearing the same RID astronomically
    unlikely. Once 2f other nodes have moved to __Prepared__ state (with the same block) the node will fetch and verify
    commit signatures from those nodes and commit the block once it has done so. This decouples the commit signatures
    (which must be recorded in the database) from the status messages, and also means that nodes that are behind, and
    miss the short window in which 2f+1 nodes are in the __Prepared__ state, but have not yet committed and reverted to
    __WaitBlock__ state, can provide their commit signatures in a more straightforward way.
- The messages which are needed for this system are:
  - Status messages which are sent between nodes and include the round in which a node is participating (__round__),
  the RID of the current block (__RID__, can be null), the current height of the blockchain (__height__), and whether
  it is revolting (__revolting__).
  - Requests for the current unfinished block, for nodes that are synchronized to the last complete block, and are
  joining the consensus process.
  - Requests for blocks at a specific height in the blockchain. A node that is out of sync must be able to fetch
  complete blocks and bring itself back up to speed.
  - Requests for the signatures of a given block without requesting the block in its entirety.

## Basic functionality

1. Node periodically receives __Status__ of all other signer nodes.
2. If the status of any other node is updated, it recomputes its own state using previous state and the state of
other nodes.
3. If it doesn't currently have a block, and the primary node status indicates that it has a block, it tries to obtain
it from the primary. The primary is determined with _(height + round) % number of nodes_.
4. If node is primary, then it tries to build a block.
5. If a node detects that it is behind the other nodes, it tries to download the missing blocks
6. If a node suspects a faulty primary for whatever reason, it sets a __revolting__ flag. If a node detects that 2f
nodes are revolting, it increments the round number, which elects a different primary node.

## Consensus decision layer

The intent is to make the consensus layer fully abstract of the communication method, so it can be used for both
streaming (what we have now) and batch/easy modes.

Currently, the consensus layer is organized into three modules: StatusManager, BlockManager, and sync.

The status manager is the authoritative source: it updates the state of the node in response to events in the network.
Nodes also have _intent_, which describes what it is the node ought to do given its own state, and the state of the
network.

BlockManager queries this intent from StatusManager and coordinates activities at the block level. Database operations
are abstracted from the consensus layer, and are called by BlockManager.

Code relating to synchronization, i.e., what happens when an out of sync node re/joins the network is handled by the
sync module. 

## Reasons for using EBFT

### Message retransmission

If messages fail in a stateful asynchronous protocol like PBFT, the idea is that all failed messages will be
retransmitted to the desynchronized node once connectivity is restored, and normal operation will be resumed. However,
it is impossible for one node to know what the other nodes have received, which is the whole point of BFT. You cannot
simply retransmit all relevant messages because the protocol is stateful and messages rely on persistent state
information at the receiving end. So you need another message, let's say __reset__, which deletes all messages from
that node in log and resets the state, so they can start afresh. In practice, the protocol is very complex and has a
lot of corner cases.

Message retransmission in EBFT is much simpler. There are fewer messages to care about. __Status__ messages are sent
on a state change, or if __MAX_STATUS_INTERVAL__ (default 1 second) has passed since the last message, i.e., as a
heartbeat. All other messages are sent as a response to __Status__ messages from other nodes and/or the current state
of the node, or upon requests from other nodes.

When a node updates its state in any way it will increment the status serial value. This way other nodes can determine
if a status message they receive is just a resubmission or an actual state change. When the chain is started or
restarted the node sets the serial to a value based on the current time in milliseconds, this way it ensures that other
nodes will consider the first status it sends as fresh (the assumption is that we send less than 1000 status messages
per second).

### Simplicity

If the consensus behavior depends on a large amount of code, there is a large probability that the system will get
stuck due to some corner case not being handled somewhere. EBFT code is more linear: it should work completely or not
at all. In EBFT, things which are actually conditional should be concentrated in few parts. This makes it easier to
closely review those parts which handle possible cases and rely on "linear" code working properly.

Fewer messages means that the decision logic becomes much more concentrated, and thus easier to review. Basically, we
need just two cases:

- what do we do when we receive status messages?
- what do we do when we receive responses on requests the node has sent?

All consensus code is concentrated in these two functions.

### Security

A Byzantine node might flood the network with repeated messages, so we also need to avoid keeping messages which have
the same meaning. E.g., a node might send many __prepare__ messages. EBFT can be seen as a measure to harden the
implementation against Byzantine nodes: only one state is stored for every node, so repeated messages do not consume
memory.

Messages can be strictly filtered, reducing the risk of DoS attacks. The risk of a network-level DoS attack is no
different from any other implementation and can be managed accordingly.

### View changes

PBFT view changes are like:

> And then you send everything you have to everyone, and everyone decides what to do next.
 
Not exactly simple... The EBFT method is simpler, if a node suspects a faulty __primary node__ it sets a revolting flag.
Revolt flags indicate an intention to increment round number, if supported by others. If a node detects a byzantine
majority of revolt flags, it will increment the round number and designate a new __primary node__.
