package net.postchain.network.common

import net.postchain.common.BlockchainRid
import net.postchain.common.exception.ProgrammerMistake
import java.util.concurrent.ConcurrentHashMap

/**
 * This is a collection of mappings from Chain to NodeConnection
 *
 * NOTE: It's differs from the [ChainsWithConnections] since it uses BC RID as key internally.
 *       (I assume this is an attempt to get free of the problems with the BC RID <-> BC IID caching?)
 *       We hide the internal workings as best we can in here
 *
 * @property HandlerType is the message handler type
 * @property NodeConnectionType specifies the exact type of connection to be used
 * @property ChainType is the type of [ChainWithOneConnection] we store in this collection
 */
class ChainsWithOneConnection<
        HandlerType,
        NodeConnectionType,
        ChainType : ChainWithOneConnection<NodeConnectionType, HandlerType>> {

    private val chainsWithOneConnection = ConcurrentHashMap<BlockchainRid, ChainType>()

    // --------------
    // Accessors
    // --------------

    fun hasChain(chainIid: Long): Boolean {
        return get(chainIid) != null
    }

    fun hasChain(bcRid: BlockchainRid): Boolean {
        return chainsWithOneConnection.containsKey(bcRid)
    }

    fun get(chainIid: Long): ChainType? {
        for (ch in chainsWithOneConnection.values) {
            if (ch.getChainIid() == chainIid) {
                return ch
            }
        }
        return null
    }

    fun get(bcRid: BlockchainRid) = chainsWithOneConnection[bcRid]

    fun getOrThrow(chainIid: Long): ChainType {
        return get(chainIid) ?: throw ProgrammerMistake("Chain ID not found: $chainIid")
    }

    fun getOrThrow(bcRid: BlockchainRid): ChainType {
        return chainsWithOneConnection[bcRid] ?: throw ProgrammerMistake("Chain RID not found: ${bcRid.toHex()}")
    }

    fun getBlockchainRids() = chainsWithOneConnection.keys.toSet()

    // --------------
    // Mutators
    // --------------

    fun add(chain: ChainType) {
        chainsWithOneConnection[chain.getBlockchainRid()] = chain
    }

    /**
     * We only remove the item here WITHOUT closing!!
     */
    fun remove(chainIid: Long): ChainType? {
        val ch = get(chainIid)
        return if (ch != null) {
            removeAndClose(ch.getBlockchainRid())
        } else {
            null
        }
    }

    /**
     * We only remove the item here WITHOUT closing!!
     */
    fun removeAndClose(bcRid: BlockchainRid): ChainType? {
        val chain = chainsWithOneConnection.remove(bcRid)
        chain?.removeAndCloseConnection()
        return chain
    }

    /**
     * Loop everything and close it
     */
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