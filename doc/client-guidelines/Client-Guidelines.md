# Client Guidelines

This document is aimed at client users of and developers for the Postchain REST API.

## Node discovery

### Trusted node setup

If you run your own node that you trust, you don't need to consider node discovery as long as it signs or replicates
the blockchains you want to interact with.

### Manual mode

There is no sophisticated node discovery mechanism for Postchain run in manual mode. You need to manually find and
configure the nodes to connect to.

### Managed mode

In managed mode it is possible to perform node discovery by doing lookups in the directory chain, see
relevant [docs](https://chromaway.gitlab.io/core/directory-chain/-directory%20chain/cm_api/index.html). To find the
blockchain RID of the directory chain you can call `/brid/iid_0` on any network node.

However, you still need some initial node(s) to connect to. It is recommended to use official sources such as
the [Block Explorer](https://explorer.chromia.com/mainnet/cluster/system) to get an initial list of system cluster
nodes.

## Retry strategies

### Transactions

If a transaction is reported as `REJECTED` or `UNKNOWN` when status is checked it may be resubmitted. A transaction can
only be included once on the blockchain so a resubmission is always safe.

There is one potential pitfall for cases where a `nop` or other type of nonce is added to create transaction uniqueness.
If the transaction is resubmitted with new nonce and is not idempotent it can be good to use timeout mechanisms such as
the time bound `timeb` operation in Postchain to be sure that the previous transaction is permanently invalidated before
resubmitting.

### Queries

If a node returns a retryable error that does not indicate that something is wrong with the client, it is advisable to
retry the request on another node. Possibly marking the node as malfunctioning and to be avoided for future requests.

See the section below for information about the different types of errors.

### HTTP errors

Errors that typically need to be resolved on the client side are:

* HTTP 400 Bad Request
* HTTP 404 Not Found
* HTTP 409 Conflict
* HTTP 413 Request Entity Too Large

Errors that can be resolved by retrying the request on another node are:

* Inability to resolve hostname in DNS
* Connection refused
* HTTP 500 Internal Server Error
* HTTP 503 Service Unavailable
* Timeout
* All other responses

## Consensus

### Transactions

To ensure that a transaction is actually committed, it is possible to request a confirmation proof by calling
`/tx/{blockchainRid}/{txRid}/confirmationProof` on a node that claims that it is. This proof can be verified given that
you know the blockchain signers. Signers can be obtained from the blockchain configuration at the height the transaction
was confirmed at.

### Queries

For queries, it is more complicated to determine consensus. It is possible to do it by asking all nodes and checking if
a BFT majority of them agree. However, this strategy is not suitable if the query returns mutable values, at least if
they are mutated often.

## Sticky nodes

For a more decentralized experience, it can be attractive to select a new random node for each request. This can be fine
for one-off request and similar, but for more complex flows it can be advisable to stick to a node throughout the whole
interaction. This is since the blockchain is eventually consistent in nature and transactions confirmed on a majority of
nodes may not yet have been applied on all nodes.

It is always possible to check if a node is aware of a transaction by calling `/tx/{blockchainRid}/{txRid}/status`.
