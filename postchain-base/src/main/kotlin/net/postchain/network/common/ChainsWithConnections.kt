package net.postchain.network.common


import net.postchain.common.exception.ProgrammerMistake
import net.postchain.core.NodeRid

/**
 * A collection of multiple [ChainWithConnections] objects, sorted by the Chain IID.
 *
 * Every Chain IID corresponds to multiple connection, one for each peer/node that communicates
 * with this chain.
 *
 * Note: Potentially this abstraction is premature (Currently only Peer connections use this)
 *
 * @property HandlerType is the message handler type
 * @property NodeConnectionType specifies the exact type of connection to be used
 * @property ChainType is the type of [ChainWithConnections] we store in this collection
 */
class ChainsWithConnections<
        HandlerType,
        NodeConnectionType,
        ChainType : ChainWithConnections<NodeConnectionType, HandlerType>> {

    private val chainsWithConnections: MutableMap<Long, ChainType> = mutableMapOf()

    // --------------
    // Accessors
    // --------------

    @Synchronized
    fun hasChain(chainIid: Long): Boolean {
        return chainIid in chainsWithConnections
    }

    @Synchronized
    fun get(chainIid: Long) = chainsWithConnections[chainIid]

    @Synchronized
    fun getOrThrow(chainIid: Long): ChainType {
        return chainsWithConnections[chainIid] ?: throw ProgrammerMistake("Chain ID not found: $chainIid")
    }


    // --------------
    // Mutators
    // --------------

    @Synchronized
    fun add(chain: ChainType) {
        chainsWithConnections[chain.getChainIid()] = chain
    }

    /**
     * We only remove the item here WITHOUT closing!!
     */
    @Synchronized
    fun remove(chainIid: Long): ChainType? {
        return chainsWithConnections.remove(chainIid)
    }

    /**
     * Loop everything and close it
     */
    @Synchronized
    fun removeAllAndClose() {
        chainsWithConnections.forEach { (_, chain) ->
            chain.closeConnections()
        }
        chainsWithConnections.clear()
    }

    // --------------
    //  Domain specific methods
    // --------------
    @Synchronized
    fun getNodeConnection(chainId: Long, nodeRid: NodeRid): NodeConnectionType? {
        val chWC = getOrThrow(chainId)
        return chWC.getConnection(nodeRid)
    }

    // -------------
    // Logging
    // -------------

    fun getNodesTopology(): Map<String, Map<String, String>> {
        return chainsWithConnections
                .mapKeys { (id, chain) -> id to chain.getBlockchainRid().toHex() }
                .mapValues { (idToRid, _) -> getNodesTopology(idToRid.first).mapKeys { (k, _) -> k.toString() } }
                .mapKeys { (idToRid, _) -> idToRid.second }
    }

    fun getNodesTopology(chainID: Long): Map<NodeRid, String> {
        val chain = get(chainID)
        return chain?.getNodeTopology() ?: emptyMap()
    }

    fun getStats(): String {
        return " size of chains: ${chainsWithConnections.size}"
    }

    // For testing only
    fun isEmpty() = chainsWithConnections.isEmpty()
}