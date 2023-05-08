// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.ebft

import mu.KLogging
import mu.withLoggingContext
import net.postchain.PostchainContext
import net.postchain.base.HistoricBlockchainContext
import net.postchain.base.PeerCommConfiguration
import net.postchain.base.configuration.BaseBlockchainConfiguration
import net.postchain.base.peerId
import net.postchain.common.BlockchainRid
import net.postchain.common.wrap
import net.postchain.config.blockchain.BlockchainConfigurationProvider
import net.postchain.config.node.NodeConfig
import net.postchain.core.BlockchainConfiguration
import net.postchain.core.BlockchainEngine
import net.postchain.core.BlockchainProcess
import net.postchain.core.BlockchainRestartNotifier
import net.postchain.core.NODE_ID_READ_ONLY
import net.postchain.core.NodeRid
import net.postchain.core.SynchronizationInfrastructure
import net.postchain.debug.BlockchainProcessName
import net.postchain.ebft.message.EbftMessage
import net.postchain.ebft.worker.HistoricBlockchainProcess
import net.postchain.ebft.worker.MessageProcessingLatch
import net.postchain.ebft.worker.ReadOnlyBlockchainProcess
import net.postchain.ebft.worker.ValidatorBlockchainProcess
import net.postchain.ebft.worker.WorkerContext
import net.postchain.logging.BLOCKCHAIN_RID_TAG
import net.postchain.logging.CHAIN_IID_TAG
import net.postchain.logging.NODE_PUBKEY_TAG
import net.postchain.network.CommunicationManager
import net.postchain.network.peer.DefaultPeerCommunicationManager
import net.postchain.network.peer.DefaultPeersCommConfigFactory
import net.postchain.network.peer.PeersCommConfigFactory

@Suppress("JoinDeclarationAndAssignment")
open class EBFTSynchronizationInfrastructure(
        protected val postchainContext: PostchainContext,
        val peersCommConfigFactory: PeersCommConfigFactory = DefaultPeersCommConfigFactory()
) : SynchronizationInfrastructure {

    companion object : KLogging()

    val nodeConfig get() = postchainContext.nodeConfigProvider.getConfiguration()
    val nodeDiagnosticContext = postchainContext.nodeDiagnosticContext
    val connectionManager = postchainContext.connectionManager
    private val startWithFastSync: MutableMap<Long, Boolean> = mutableMapOf() // { chainId -> true/false }

    override fun shutdown() {}

    override fun makeBlockchainProcess(
            processName: BlockchainProcessName,
            engine: BlockchainEngine,
            messageProcessingLatch: MessageProcessingLatch,
            blockchainConfigurationProvider: BlockchainConfigurationProvider,
            restartNotifier: BlockchainRestartNotifier,
    ): BlockchainProcess {
        val blockchainConfig = engine.getConfiguration()
        val currentNodeConfig = nodeConfig

        withLoggingContext(
                NODE_PUBKEY_TAG to nodeConfig.appConfig.pubKey,
                CHAIN_IID_TAG to blockchainConfig.chainID.toString(),
                BLOCKCHAIN_RID_TAG to blockchainConfig.blockchainRid.toHex()
        ) {
            val historicBrid = blockchainConfig.effectiveBlockchainRID
            val historicBlockchainContext = if (crossFetchingEnabled(blockchainConfig)) {
                HistoricBlockchainContext(
                        historicBrid, currentNodeConfig.blockchainAncestors[blockchainConfig.blockchainRid]
                        ?: emptyMap()
                )
            } else null

            val peerCommConfiguration = peersCommConfigFactory.create(postchainContext.appConfig, currentNodeConfig, blockchainConfig, historicBlockchainContext)

            val peers: Set<NodeRid> = peerCommConfiguration.networkNodes.getPeerIds()
            val signers: Set<NodeRid> = blockchainConfig.signers.map { NodeRid(it) }.toSet()
            val iAmASigner = signers.contains(peerCommConfiguration.networkNodes.myself.peerId())
            if (iAmASigner) {
                if (signers.size == 1) {
                    logger.info("I am alone signer")
                } else if (peers.intersect(signers).isEmpty()) {
                    logger.warn("I am a signer, but there is no overlap between peers and signers: peers=$peers signers=$signers")
                }
            } else {
                if (peers.isEmpty()) {
                    logger.warn("I am a replica, but I have no peers")
                }
            }

            val workerContext = WorkerContext(
                    processName,
                    blockchainConfig,
                    engine,
                    buildXCommunicationManager(processName, blockchainConfig, peerCommConfiguration, blockchainConfig.blockchainRid),
                    peerCommConfiguration,
                    postchainContext.appConfig,
                    currentNodeConfig,
                    messageProcessingLatch,
                    restartNotifier,
                    blockchainConfigurationProvider,
                    postchainContext.nodeDiagnosticContext
            )

            /*
            Block building is prohibited on FB if its current configuration has a historicBrid set.

            When starting a blockchain:

            If !hasHistoricBrid then do nothing special, proceed as we always did

            Otherwise:

            1 Sync from local-OB (if available) until drained
            2 Sync from remote-OB until drained or timeout
            3 Sync from FB until drained or timeout
            4 Goto 2
            */
            return if (historicBlockchainContext != null) {

                historicBlockchainContext.contextCreator = { brid ->
                    val historicPeerCommConfiguration = if (brid == historicBrid) {
                        peersCommConfigFactory.create(
                                postchainContext.appConfig, currentNodeConfig, blockchainConfig, historicBlockchainContext)
                    } else {
                        // It's an ancestor brid for historicBrid
                        peersCommConfigFactory.create(
                                postchainContext.appConfig, currentNodeConfig, brid, historicBlockchainContext)
                    }
                    val histCommManager = buildXCommunicationManager(processName, blockchainConfig, historicPeerCommConfiguration, brid)

                    WorkerContext(
                            processName,
                            blockchainConfig,
                            engine,
                            histCommManager,
                            historicPeerCommConfiguration,
                            postchainContext.appConfig,
                            currentNodeConfig,
                            messageProcessingLatch,
                            restartNotifier,
                            blockchainConfigurationProvider,
                            postchainContext.nodeDiagnosticContext
                    )

                }
                HistoricBlockchainProcess(workerContext, historicBlockchainContext)
            } else if (blockchainConfig.blockchainContext.nodeID != NODE_ID_READ_ONLY) {
                ValidatorBlockchainProcess(workerContext, getStartWithFastSyncValue(blockchainConfig.chainID))
            } else {
                ReadOnlyBlockchainProcess(workerContext, engine.getBlockQueries())
            }
        }
    }

    /*
    Definition: cross-fetching is the process of downloading blocks from another blockchain
    over the peer-to-peer network. This is used when forking a chain when we don't have
    the old chain locally and we haven't been able to sync using the new chain rid.

    Problem: in order to cross-fetch blocks, we'd like to get the old blockchain's
    configuration (to find nodes to connect to). But that's difficult. We don't always
    have it, and we might not have the most recent configuration.

    If we don't have that, we can use the current blockchain's configuration to
    find nodes to sync from, since at least a quorum of the signers from old chain
    must also be signers of the new chain.

    To simplify things, we will always use current blockchain configuration to find
    nodes to cross-fetch from. We'll also use sync-nodes.
     */
    private fun crossFetchingEnabled(blockchainConfig: BlockchainConfiguration) =
            blockchainConfig.effectiveBlockchainRID != blockchainConfig.blockchainRid

    override fun exitBlockchainProcess(process: BlockchainProcess) {
        val chainID = process.blockchainEngine.getConfiguration().chainID
        startWithFastSync.remove(chainID) // remove status when process is gone
    }

    override fun restartBlockchainProcess(process: BlockchainProcess) {
        var fastSyncStatus = true
        val chainID = process.blockchainEngine.getConfiguration().chainID
        if (process is ValidatorBlockchainProcess) {
            fastSyncStatus = process.isInFastSyncMode()
        }
        startWithFastSync[chainID] = fastSyncStatus
    }

    @Deprecated("POS-90")
    private fun validateConfigurations(nodeConfig: NodeConfig, blockchainConfig: BaseBlockchainConfiguration) {
        val chainPeers = blockchainConfig.signers.map { it.wrap() }

        val unreachableSigners = chainPeers.filter { !nodeConfig.peerInfoMap.contains(it) }
        require(unreachableSigners.isEmpty()) {
            "Invalid blockchain config: unreachable signers have been detected: " +
                    chainPeers.toTypedArray().contentToString()
        }
    }

    private fun buildXCommunicationManager(
            processName: BlockchainProcessName,
            blockchainConfig: BlockchainConfiguration,
            relevantPeerCommConfig: PeerCommConfiguration,
            blockchainRid: BlockchainRid
    ): CommunicationManager<EbftMessage> {
        val packetEncoder = EbftPacketEncoder(relevantPeerCommConfig, blockchainRid)
        val packetDecoder = EbftPacketDecoder(relevantPeerCommConfig)

        return DefaultPeerCommunicationManager(
                connectionManager,
                relevantPeerCommConfig,
                blockchainConfig.chainID,
                blockchainRid,
                packetEncoder,
                packetDecoder,
                processName
        ).apply { init() }
    }

    private fun getStartWithFastSyncValue(chainId: Long): Boolean {
        return startWithFastSync[chainId] ?: true
    }
}
