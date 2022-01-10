package net.postchain.network.common

import net.postchain.core.BlockchainRid
import net.postchain.core.ProgrammerMistake

/**
 * This is a collection of mappings from Chain to NodeConnection
 *
 * NOTE: It's differs from the [ChainsWithConnections] since it uses BC RID as key internally.
 *       (I assume this is an attempt to get free of the problems with the BC RID <-> BC IID caching?)
 *       We hide the internal workings as best we can in here
 */
class ChainsWithOneConnection<HandlerType, NodeConnectionType, ChainWCType>(
    // Nothing
) where ChainWCType : ChainWithOneConnection<NodeConnectionType, HandlerType> // Had to look this up. Is "where" the idiomatic way?
{

    private val chainsWithOneConnection: MutableMap<BlockchainRid, ChainWCType> = mutableMapOf()

    // --------------
    // Accessors
    // --------------

    @Synchronized
    fun hasChain(chainIid: Long): Boolean {
        return get(chainIid) != null
    }

    @Synchronized
    fun hasChain(bcRid: BlockchainRid): Boolean {
        return bcRid in chainsWithOneConnection
    }

    @Synchronized
    fun get(chainIid: Long): ChainWCType? {
        for (ch in chainsWithOneConnection.values) {
            if (ch.getChainIid() == chainIid) {
                return ch
            }
        }
        return null
    }

    fun get(bcRid: BlockchainRid) = chainsWithOneConnection[bcRid]

    @Synchronized
    fun getOrThrow(chainIid: Long): ChainWCType {
        return get(chainIid) ?: throw ProgrammerMistake("Chain ID not found: $chainIid")
    }

    @Synchronized
    fun getOrThrow(bcRid: BlockchainRid): ChainWCType {
        return chainsWithOneConnection[bcRid] ?: throw ProgrammerMistake("Chain RID not found: ${bcRid.toHex()}")
    }

    // --------------
    // Mutators
    // --------------

    @Synchronized
    fun add(chain: ChainWCType) {
        chainsWithOneConnection[chain.getBlockchainRid()] = chain
    }

    /**
     * We only remove the item here WITHOUT closing!!
     */
    @Synchronized
    fun remove(chainIid: Long): ChainWCType? {
        val ch = get(chainIid)
        return if (ch != null) {
            removeAndClose(ch!!.getBlockchainRid())
        } else {
            null
        }
    }

    /**
     * We only remove the item here WITHOUT closing!!
     */
    @Synchronized
    fun removeAndClose(bcRid: BlockchainRid): ChainWCType? {
        val chain = chainsWithOneConnection.remove(bcRid)
        chain?.removeAndCloseConnection()
        return chain
    }

    /**
     * Loop everything and close it
     */
    @Synchronized
    fun removeAllAndClose() {
        chainsWithOneConnection.forEach { (_, chain) ->
            chain.closeConnection()
        }
        chainsWithOneConnection.clear()
    }

    fun getStats(): String {
        return " size of chains: ${chainsWithOneConnection.size}"
    }

}