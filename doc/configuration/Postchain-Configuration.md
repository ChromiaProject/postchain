# Postchain Configuration Management

## Scope

Handling of blockchain consensus-critical configuration, as well as transient parts such as network topology.

## Architecture overview

Blockchain behavior is described by `BlockchainConfiguration` object, which serves as a factory for other objects which
work with blockchain.
It's a stateless object which doesn't change over time. As such, it should be considered to be a blockchain
configuration at a certain time.

E.g. say a hardfork is scheduled at block 1000. This means that there's configuration C1 which applies to blocks 0..999
which produces `BlockchainConfiguration` BC1.
And there's configuration C2 which applies to blocks 1000 and onward, which produces BC2.

This means that upon reaching block 1000, Postchain node should stop using BC1 and start using BC2.

Thus:

1. Postchain node must be aware of consensus configurations at different heights.
2. Blockchain-consensus configuration should be split from network topology configuration.

## OO architecture

Let's recall how it works now:

1. `BlockchainConfiguration` defines the blockchain logic, that is, it what chain of blocks is considered valid and how
   the state is updated.
2. `BlockchainEngine` implements the block-building behavior.
3. `BlockDatabase` connects `BlockchainEngine` with consensus code.
4. `PostchainNode` builds object graph and runs update loop.

We need BlockchainConfiguration to be dynamic.

Configuration data will be kept in the database, thus connect context is necessary to retrieve it. It seems in most
cases we have now we only need configuration at current height (that is, a configuration for a block which is being
built), but there might be a case in future where we need to retrieve historic configuration.

---

P.S. _Moved from_ _https://bitbucket.org/chromawallet/postchain/wiki/Postchain2Configuration_
_then from_ _https://gitlab.com/chromaway/postchain/-/wikis/configuration/Postchain-Configuration_
