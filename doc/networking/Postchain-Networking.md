# Postchain networking

This documentation is about network communication between nodes.
For documentation about specific EBFT (and other) messages that are sent between nodes please
see [EBFT messages](../ebft-protocol/EBFT-Messages.md).

Networking is implemented with Netty. The node that initiates the connection is referred to as the *client* and the
node receiving the connection request is referred to as the *server*. A node should only have one connection per peer
and blockchain.

## Peer information

Peer information refers to the host name and port that a node is listening on for incoming connections.

Below is a description of how Postchain finds peer information when running a blockchain. It differs depending on which
mode Postchain is running in. However, in both cases peer information is assembled when the blockchain is (re)starting.

### Manual mode

Peers must be manually added to the node's database via CLI commands when a node is running in manual mode.

#### Inferring relevant peers for a specific blockchain

In manual mode, Postchain considers blockchain signers as relevant peers as well as any replicas of the blockchain.
Note that information about replica peers must be manually added via CLI commands since that information is not
available in the blockchain configuration.

### Managed mode

In managed mode, nodes will primarily be served peer information via the directory chain Node Management (NM) API. When
registering a node in the network a provider must specify its peer information. This information is stored on the
directory chain.

It is also possible to add local peer information directly to the node just like in manual mode. This peer information
will be merged with the information from NM API. If there is conflicting peer information from NM API and local database
Postchain will pick the peer information that was added/updated last. Both directory chain and local database updates a
timestamp when peer information is added/modified.

For the directory chain itself, peer information of the genesis node must be configured in node configuration so that
a node can start syncing the first blocks of the directory chain. If the genesis node is no longer available, genesis
peer information can point to another live node but then that node also needs to be manually added as a peer and as a
blockchain replica via CLI.

#### Inferring relevant peers for a specific blockchain

In managed mode Postchain considers blockchain signers at node height, current blockchain signers as well as any
replicas of the blockchain as relevant peers. Note that any replicas that have not registered as blockchain replicas in
directory chain (or have been manually added to the node's local database) won't be considered. Considering current
blockchain signers as relevant peers is useful if a node starts syncing the chain from height 0 since the signers at
that time may no longer be available.

## Connection handling

### Connecting to relevant peers

When starting a blockchain Postchain will attempt to connect to all the relevant peers. This is done with a small
random delay. If the connection attempt fails or if a connection is established but later lost a re-connection attempt
will be made with a random exponentially increasing delay.

Despite some randomness in the connection strategies there is still a possibility that two nodes attempt to connect to
each other at the same time. When a duplicate connection is detected the nodes will drop one of the connections based
on each others public keys. The connection where the node with the higher public key is client will be kept.

Connections from peers that are not in the relevant peer list are allowed but re-connection attempts will not be made if
such connections are lost.

### Identification

When establishing a connection, nodes will perform a handshake with signed identification messages to prove that they
are who they claim to be.

### Versioning

Nodes will also negotiate about packet versions by sending a version message to each other upon connecting. If no
version message is received we assume that the peer uses a legacy version of Postchain from before versioning was
introduced. Versions are useful when new messages are added or when the message formats are modified.

### Ping

To identify broken connections nodes will ping each other on a regular interval if the connection is idle.

### Limits

Peers that are not in the relevant peer list are limited on how many connection attempts they can make during a day.
Current limit is 1000.

## Master and subnode connection handling

In a master-subnode setup the master node will handle connections on behalf of the subnode. The subnode only has one
connection per blockchain and that connection is toward the master node. The master node will send an updated view of
connected peers to the subnode on a regular interval. Whenever a subnode wants to send a message to a peer it will be
routed via the master node.

## Packet handling

Packets are received and decoded (signature is verified if message is signed). Incoming messages are placed on an
in-memory queue to be polled by the blockchain process.
