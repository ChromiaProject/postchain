package net.postchain.devtools.utils.configuration

import mu.KLogging
import net.postchain.api.rest.infra.RestApiConfig
import net.postchain.config.app.AppConfig
import net.postchain.config.node.NodeConfigurationProvider
import net.postchain.crypto.devtools.KeyPairCache
import net.postchain.devtools.PostchainTestNode
import org.apache.commons.configuration2.Configuration
import org.apache.commons.configuration2.PropertiesConfiguration
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap


/**
 * Represents a single test node
 * (The "setup" classes are data holders/builders for test configuration used to generate the "real" classes at a later stage)
 *
 * @property sequenceNumber is the node's number. Must be unique.
 * @property initialChainsToSign the blockchains this node should be a signer for (SET because no duplicates allowed)
 * @property initialChainsToRead the blockchains this node should have a read only copy of (SET because no duplicates allowed)
 * @property pubKeyHex is the pub key
 * @property privKeyHex is the private key
 * @property nodeSpecificConfigs are configurations that will be only for this node (usually nodes share config most
 *                       settings, but this can be useful sometimes)
 * @property configurationProvider is the configuration provider for the node
 */
data class NodeSetup(
        val sequenceNumber: NodeSeqNumber,
        val initialChainsToSign: Set<Int>,
        val initialChainsToRead: Set<Int>,
        val pubKeyHex: String,
        val privKeyHex: String,
        val nodeSpecificConfigs: Configuration = PropertiesConfiguration(),
        var configurationProvider: NodeConfigurationProvider? = null // We might not set this at first
) {
    // Internal thread safe sets
    private val _chainsToSign = Collections.newSetFromMap(ConcurrentHashMap<Int, Boolean>())
    private val _chainsToRead = Collections.newSetFromMap(ConcurrentHashMap<Int, Boolean>())
    val chainsToSign: Set<Int>
        get() = _chainsToSign.toSet()
    val chainsToRead: Set<Int>
        get() = _chainsToRead.toSet()

    init {
        _chainsToSign.addAll(initialChainsToSign)
        _chainsToRead.addAll(initialChainsToRead)
    }

    companion object : KLogging() {

        fun buildSimple(nodeNr: NodeSeqNumber,
                        signerChains: Set<Int>,
                        replicaChains: Set<Int>,
                        keyPairCache: KeyPairCache
        ): NodeSetup {

            return NodeSetup(
                    nodeNr,
                    signerChains,
                    replicaChains,
                    keyPairCache.pubKeyHex(nodeNr.nodeNumber),
                    keyPairCache.privKeyHex(nodeNr.nodeNumber)
            )
        }
    }

    fun addChainToSign(chainId: Int) {
        _chainsToSign.add(chainId)
    }

    fun removeChainToSign(chainId: Int) {
        _chainsToSign.remove(chainId)
    }

    fun addChainToRead(chainId: Int) {
        _chainsToRead.add(chainId)
    }

    fun removeChainToRead(chainId: Int) {
        _chainsToRead.remove(chainId)
    }

    fun getAllInitialBlockchains() = this.initialChainsToSign.plus(this.initialChainsToRead)

    /**
     * The nodes have ports depending on the order of their sequence number
     */
    fun getPortNumber() = getPortNumber(AppConfig.DEFAULT_PORT)
    fun getPortNumber(portBase: Int) = sequenceNumber.nodeNumber + portBase

    fun getApiPortNumber() = getApiPortNumber(RestApiConfig.DEFAULT_REST_API_PORT)
    fun getApiPortNumber(portBase: Int) = sequenceNumber.nodeNumber + portBase // Must be different from "normal" port base

    fun getDebugPortNumber() = getDebugPortNumber(RestApiConfig.DEFAULT_DEBUG_API_PORT)
    fun getDebugPortNumber(portBase: Int) = sequenceNumber.nodeNumber + portBase // Must be different from "normal" port base

    /**
     * It can be pretty hard to figure out if a node needs to know about some other node,
     * but we have done most of the work already since we know all the BC's this node is holding.
     *
     * @param systemSetup
     * @return all nodes this node should know about
     */
    fun calculateAllNodeConnections(systemSetup: SystemSetup): Set<NodeSeqNumber> {
        val retSet = mutableSetOf<NodeSeqNumber>()

        val totalBcs = this.chainsToSign.plus(this.chainsToRead)
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
        val node = PostchainTestNode(configurationProvider!!.getConfiguration().appConfig, preWipeDatabase)

        if (initialChainsToRead.isNotEmpty()) {
            logger.debug("Node ${sequenceNumber.nodeNumber}: Start all read only blockchains (dependencies must be installed first)")
            // TODO: These chains can in turn be depending on each other, so they should be "sorted" first
            initialChainsToRead.forEach { chainId ->
                val chainSetup = systemSetup.blockchainMap[chainId]
                        ?: error("Incorrect SystemSetup")
                try {
                    startChain(node, chainSetup, "read only")
                } catch (e: Exception) {
                    node.shutdown()
                    throw e
                }
            }
        }

        logger.debug("Node ${sequenceNumber.nodeNumber}: Start all blockchains we should sign")
        initialChainsToSign.forEach { chainId ->
            val chainSetup = systemSetup.blockchainMap[chainId]
                    ?: error("Incorrect SystemSetup")
            try {
                startChain(node, chainSetup, "")
            } catch (e: Exception) {
                node.shutdown()
                throw e
            }
        }

        return node
    }

    private fun startChain(node: PostchainTestNode, chain: BlockchainSetup, chainLogType: String) {
        val launched = node.retrieveBlockchain(chain.chainId.toLong()) != null
        if (launched) {
            logger.debug("Node ${sequenceNumber.nodeNumber}: Chain is already running: chainId: ${chain.chainId}")
        } else {
            logger.debug("Node ${sequenceNumber.nodeNumber}: Begin starting $chainLogType chainId: ${chain.chainId}")
            chain.prepareBlockchainOnNode(
                    chain,
                    node
            )  // Don't think this is needed, since we could have put the setting in the setup
            node.startBlockchain(chain.chainId.toLong())
            logger.debug("Node ${sequenceNumber.nodeNumber}: Finished starting $chainLogType chainId: ${chain.chainId}")
        }
    }

}

