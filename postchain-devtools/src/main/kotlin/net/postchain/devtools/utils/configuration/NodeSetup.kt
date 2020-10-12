package net.postchain.devtools.utils.configuration

import mu.KLogging
import net.postchain.config.node.NodeConfigurationProvider
import net.postchain.devtools.KeyPairHelper
import net.postchain.devtools.PostchainTestNode


/**
 * Represents a single test node
 * (The "setup" classes are data holders/builders for test configuration used to generate the "real" classes at a later stage)
 *
 * @property sequenceNumber is the node's number. Must be unique.
 * @property chainsToSign the blockchains this node should be a signer for (SET because no duplicates allowed)
 * @property chainsToRead the blockchains this node should have a read only copy of (SET because no duplicates allowed)
 * @property pubKeyHex is the pub key
 * @property privKeyHex is the private key
 * @property configurationProvider is the configuration provider for the node
 */
data class NodeSetup(
        val sequenceNumber: NodeSeqNumber,
        val chainsToSign: Set<Int>,
        val chainsToRead: Set<Int>,
        val pubKeyHex: String,
        val privKeyHex: String,
        var configurationProvider: NodeConfigurationProvider? = null, // We might not set this at first
        var netPortNum: Int = 0, // Use any available port
        var apiPortNum: Int = 0  // Use any available port
) {

    companion object : KLogging() {

        fun buildSimple(nodeNr: NodeSeqNumber,
                        signerChains: Set<Int>,
                        replicaChains: Set<Int>
        ): NodeSetup {

            return NodeSetup(
                    nodeNr,
                    signerChains,
                    replicaChains,
                    KeyPairHelper.pubKeyHex(nodeNr.nodeNumber),
                    KeyPairHelper.privKeyHex(nodeNr.nodeNumber)
            )
        }
    }

    fun getAllBlockchains() = this.chainsToSign.plus(this.chainsToRead)

    fun getPortNumber() = netPortNum
    fun getApiPortNumber() = apiPortNum

    /**
     * It can be pretty hard to figure out if a node needs to know about some other node,
     * but we have done most of the work already since we know all the BC's this node is holding.
     *
     * @param systemSetup
     * @return all nodes this node should know about
     */
    fun calculateAllNodeConnections(systemSetup: SystemSetup): Set<NodeSeqNumber> {
        val retSet = mutableSetOf<NodeSeqNumber>()

        val totalBcs = this.chainsToRead.plus(this.chainsToRead)
        for (chainId in totalBcs) {
            val bc = systemSetup.blockchainMap[chainId]!!
            retSet.addAll(bc.signerNodeList) //  should connect to all others
        }

        // Just remove ourselves and return
        return retSet.filter { it != this.sequenceNumber }.toSet()
    }

    /**
     * Turns this [NodeSetup] to a [PostchainTestNode] and adds and starts all blockchains on it
     */
    fun toTestNodeAndStartAllChains(
            systemSetup: SystemSetup,
            preWipeDatabase: Boolean = true
    ): PostchainTestNode {

        require(configurationProvider != null) { "Cannot build a PostchainTestNode without a NodeConfigurationProvider set" }

        val node = PostchainTestNode(configurationProvider!!, preWipeDatabase)

        if (chainsToRead.isNotEmpty()) {
            logger.debug("Node ${sequenceNumber.nodeNumber}: Start all read only blockchains (dependencies must be installed first)")
            // TODO: These chains can in turn be depending on each other, so they should be "sorted" first
            chainsToRead.forEach { chainId ->
                val chainSetup = systemSetup.blockchainMap[chainId]
                        ?: error("Incorrect SystemSetup")
                startChain(node, chainSetup, "read only")
            }
        }

        logger.debug("Node ${sequenceNumber.nodeNumber}: Start all blockchains we should sign")
        chainsToSign.forEach { chainId ->
            val chainSetup = systemSetup.blockchainMap[chainId]
                    ?: error("Incorrect SystemSetup")
            startChain(node, chainSetup, "")
        }

        return node
    }

    private fun startChain(node: PostchainTestNode, chain: BlockchainSetup, chainLogType: String) {
        val launched = node.retrieveBlockchain(chain.chainId.toLong()) != null
        if (launched) {
            logger.debug("Node ${sequenceNumber.nodeNumber}: Chain is already running: chainId: ${chain.chainId}")
        } else {
            logger.debug("Node ${sequenceNumber.nodeNumber}: Begin starting $chainLogType chainId: ${chain.chainId}")
            node.addBlockchain(chain)
            node.mapBlockchainRID(chain.chainId.toLong(), chain.rid)
            node.startBlockchain(chain.chainId.toLong())
            logger.debug("Node ${sequenceNumber.nodeNumber}: Finished starting $chainLogType chainId: ${chain.chainId}")
        }
    }

}

