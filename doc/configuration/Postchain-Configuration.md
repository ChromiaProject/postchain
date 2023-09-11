# Postchain Configuration Management

## Scope

Handling of blockchain consensus-critical configuration, as well as transient parts such as network topology.

## Problem

Currently Postchain configuration is provided in text files, which are then read using Apache Commons Configuration 2 API and supplied to classes which need configuration. This works quite well when you need to run one specific configuration.

However, the configuration might change over time -- e.g. functionality is enabled, blockchain code is changed, signer list is modified. Current Postchain configuration infrastructure does not support evolution of configuration over time.

It's not possible to add nodes to a cluster, do a hard fork, etc.

## Architecture overview

Blockchain behavior is described by `BlockchainConfiguration` object, which serves as a factory for other objects which work with blockchain. It's a stateless object which doesn't change over time. As such, it should be considered to be a blockchain configuration at a certain time.

E.g. say a hardfork is scheduled at block 1000. This means that there's configuration C1 which applies to blocks 0..999 which produces `BlockchainConfiguration` BC1. And there's configuration C2 which applies to blocks 1000 and onward, which produces BC2.

This means that upon reaching block 1000, Postchain node should stop using BC1 and start using BC2.

Thus:

1. Postchain node must be aware of consensus configurations at different heights.
2. Blockchain-consensus configuration should be split from network topology configuration.

## Configuration data

The general architecture described above does not prescribe any particular way of handling the configuration data. For example, it can be kept in properties file, one for each configuration, e.g.

```
<blockchainRID>/
       0.properties
    1000.properties
```

Then the node can load data from properties into the memory (e.g. Map) and then used by configuration manager.

However, there are several problems with it:

1. Text files are fragile, it is tempting for a system administrator to get into it and modify parameters.
2. We need configuration hashes e.g. for comparison reason (so that nodes can detect if they are using the same configuration), and that works better if there's a canonic serialization.
3. In some cases it is necessary to compute configuration dynamically or transmit it between nodes (e.g. one node proposes changes and other nodes vote for them), this works better with a precise format.
4. properties format is rather shitty:
   - few datatypes, no native binary data type
   - no way to describe a list with a single item or zero items
   - Configuration2 is quite bad too, e.g. it's not hierarchical
   - so it's worse than JSON/toml/XML, but JSON/toml/XML are problematic too

So it seems that the solution is to standardize an unambiguous binary format to use for configuration, one which would have actual binary data datatype. And, thankfully, we already have one -- GTXValue.

Also, configuration should be kept in the database, not in file system. Since the blockchain state itself is in the database, it makes sense that configuration is preserved together with it. This gives us an opportunity to manage configuration data programmatically without a hassle with file system.

## OO architecture

Let's recall how it works now:

1. `BlockchainConfiguration` defines the blockchain logic, that is, it what chain of blocks is considered valid and how the state is updated.
2. `BlockchainEngine` implements the block-building behavior.
3. `BlockDatabase` connects `BlockchainEngine` with consensus code.
4. `PostchainNode` builds object graph and runs update loop.

We need BlockchainConfiguration to be dynamic. To avoid re-making whole graph each time, we can wrap it into a new BlockchainConfigurationManager object, which would return a configuration for a current block.

Then BlockchainEngine and BlockDatabase can retrieve BlockchainConfiguration in context of a certain block.

`BlockchainConfiguraionManager` might have a single method:

```
 getBlockchainConfiguration(height: Long, ctx: EContext): BlockchainConfiguration
```

Configuration data will be kept in the database, thus connect context is necessary to retrieve it. It seems in most cases we have now we only need configuration at current height (that is, a configuration for a block which is being built), but there might be a case in future where we need to retrieve historic configuration.

### ConfigurationDataStore

```
interface ConfigurationDataStore {
    fun getConfigurationData(ctx: EContext, version: Long): ByteArray;
    fun getConfigurationVersion(ctx: EContext, height: Long): Long;
    fun addConfigurationData(ctx: EContext, height: Long, data: ByteArray);
}

class BaseBlockStore : ConfigurationDataStore
```

---

P.S. _Moved from_ _https://bitbucket.org/chromawallet/postchain/wiki/Postchain2Configuration_
_then from_ _https://gitlab.com/chromaway/postchain/-/wikis/configuration/Postchain-Configuration_
