package net.postchain.ebft.worker

import net.postchain.base.PeerCommConfiguration
import net.postchain.config.node.NodeConfig
import net.postchain.core.BlockchainEngine
import net.postchain.debug.BlockchainProcessName
import net.postchain.ebft.message.Message
import net.postchain.l2.Web3Connector
import net.postchain.network.CommunicationManager
import org.web3j.protocol.Web3j
import org.web3j.protocol.http.HttpService

/**
 * This is a transitional class to hide away clutter from EBFTSynchronizationInfrastructure. There's
 * room for improvement here, for example, peerCommConfiguration is already part of the
 * communicationManager (If it's a DefaultXCommunicationManager, and so on).
 */
class WorkerContext(val processName: BlockchainProcessName,
                    val signers: List<ByteArray>,
                    val engine: BlockchainEngine,
                    val nodeId: Int, // Rename to signerIndex
                    val communicationManager: CommunicationManager<Message>,
                    val peerCommConfiguration: PeerCommConfiguration,
                    val nodeConfig: NodeConfig,
                    val onShutdown: () -> Unit) {

    private var web3c: Web3Connector? = null

    fun shutdown() {
        web3c?.shutdown()
        engine.shutdown()
        communicationManager.shutdown()
        onShutdown()
    }

    fun useWeb3Connector(url: String, contractAddress: String): WorkerContext {
        val web3j = Web3j.build(HttpService(url))
        web3c = Web3Connector(web3j, contractAddress)
        engine.setWeb3Connector(web3c)
        return this
    }
}