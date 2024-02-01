// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.ebft

import mu.KLogging
import net.postchain.PostchainContext
import net.postchain.base.HistoricBlockchainContext
import net.postchain.base.PeerCommConfiguration
import net.postchain.base.configuration.BaseBlockchainConfiguration
import net.postchain.common.BlockchainRid
import net.postchain.common.exception.ProgrammerMistake
import net.postchain.common.wrap
import net.postchain.config.blockchain.BlockchainConfigurationProvider
import net.postchain.config.node.NodeConfig
import net.postchain.core.BlockchainConfiguration
import net.postchain.core.BlockchainEngine
import net.postchain.core.BlockchainProcess
import net.postchain.core.BlockchainRestartNotifier
import net.postchain.core.BlockchainState
import net.postchain.core.NODE_ID_READ_ONLY
import net.postchain.core.NodeRid
import net.postchain.core.SynchronizationInfrastructure
import net.postchain.ebft.message.EbftMessage
import net.postchain.ebft.message.MessageDurationTracker
import net.postchain.ebft.message.ebftMessageToString
import net.postchain.ebft.worker.ForceReadOnlyBlockchainProcess
import net.postchain.ebft.worker.HistoricBlockchainProcess
import net.postchain.ebft.worker.ReadOnlyBlockchainProcess
import net.postchain.ebft.worker.ValidatorBlockchainProcess
import net.postchain.ebft.worker.WorkerContext
import net.postchain.managed.ManagedBlockchainConfigurationProvider
import net.postchain.managed.MigratingBlockchainNodeInfo
import net.postchain.metrics.MessageDurationTrackerMetricsFactory
import net.postchain.network.CommunicationManager
import net.postchain.network.peer.DefaultPeerCommunicationManager
import net.postchain.network.peer.DefaultPeersCommConfigFactory
import net.postchain.network.peer.PeersCommConfigFactory

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
            engine: BlockchainEngine,
            blockchainConfigurationProvider: BlockchainConfigurationProvider,
            restartNotifier: BlockchainRestartNotifier,
            blockchainState: BlockchainState
    ): BlockchainProcess {
        val blockchainConfig = engine.getConfiguration()
        val chainId = blockchainConfig.chainID
        val blockchainRid = blockchainConfig.blockchainRid
        val currentNodeConfig = nodeConfig

        // historic context
        val historicBrid = blockchainConfig.effectiveBlockchainRID
        val historicBlockchainContext = if (crossFetchingEnabled(blockchainConfig)) {
            HistoricBlockchainContext(
                    historicBrid, currentNodeConfig.blockchainAncestors[blockchainRid] ?: emptyMap()
            )
        } else null

        // peers
        val peerCommConfiguration = peersCommConfigFactory.create(postchainContext.appConfig, currentNodeConfig, blockchainConfig, historicBlockchainContext)
        val peers: Set<NodeRid> = peerCommConfiguration.networkNodes.getPeerIds()
        val signers: Set<NodeRid> = blockchainConfig.signers.map { NodeRid(it) }.toSet()
        val iAmASigner = blockchainConfig.blockchainContext.nodeID != NODE_ID_READ_ONLY
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
        val forceReadOnly = postchainContext.appConfig.readOnly
        if (forceReadOnly) logger.warn("I am running in forced read only mode")

        // worker context
        val buildWorkerContext = { brid: BlockchainRid, peerCommConfig: PeerCommConfiguration ->
            val communicationManager = buildXCommunicationManager(blockchainConfigurationProvider, blockchainConfig, peerCommConfig, brid)
            val messageDurationTracker = MessageDurationTracker(
                    postchainContext.appConfig,
                    communicationManager,
                    MessageDurationTrackerMetricsFactory(chainId, brid, currentNodeConfig.appConfig.pubKey),
                    ebftMessageToString(blockchainConfig))
            WorkerContext(
                    blockchainConfig,
                    engine,
                    communicationManager,
                    peerCommConfig,
                    postchainContext.appConfig,
                    currentNodeConfig,
                    restartNotifier,
                    blockchainConfigurationProvider,
                    postchainContext.nodeDiagnosticContext,
                    messageDurationTracker
            )
        }

        val workerContext = buildWorkerContext(blockchainRid, peerCommConfiguration)

        historicBlockchainContext?.contextCreator = { brid, historicBcContext ->
            val historicPeerCommConfig = if (brid == historicBrid) {
                peersCommConfigFactory.create(
                        postchainContext.appConfig, currentNodeConfig, blockchainConfig, historicBcContext)
            } else {
                // It's an ancestor brid for historicBrid
                peersCommConfigFactory.create(
                        postchainContext.appConfig, currentNodeConfig, brid, historicBcContext)
            }
            buildWorkerContext(brid, historicPeerCommConfig)
        }

        /*
            Block building is prohibited on FB if its current configuration has a historicBrid set.
            When starting a blockchain:
                If !hasHistoricBrid then do nothing special, proceed as we always did
                Otherwise:
                    1. Sync from local-OB (if available) until drained
                    2. Sync from remote-OB until drained or timeout
                    3. Sync from FB until drained or timeout
                    4. Goto 2
        */
        val migratingInfo = (blockchainConfigurationProvider as? ManagedBlockchainConfigurationProvider)
                ?.getMigratingBlockchainNodeInfo(blockchainConfig.blockchainRid)
        return when {
            forceReadOnly -> ForceReadOnlyBlockchainProcess(workerContext, blockchainState)

            // Must be before RUNNING | PAUSED b/c there is no explicit MOVING blockchain state
            canMovingBlockchainBeForceReadOnly(blockchainConfig, blockchainState, migratingInfo) -> createMovingForceReadOnlyBlockchainProcess(
                    workerContext, blockchainState, migratingInfo)

            blockchainState == BlockchainState.RUNNING -> createRunningBlockchainProcess(
                    workerContext, historicBlockchainContext, blockchainConfigurationProvider, blockchainConfig, blockchainState, iAmASigner)

            blockchainState == BlockchainState.PAUSED -> createPausedBlockchainProcess(
                    workerContext, blockchainConfigurationProvider, blockchainConfig, blockchainState)

            blockchainState == BlockchainState.IMPORTING -> ForceReadOnlyBlockchainProcess(workerContext, blockchainState)

            blockchainState == BlockchainState.UNARCHIVING -> createUnarchivingBlockchainProcess(
                    workerContext, blockchainConfigurationProvider, blockchainConfig, blockchainState, iAmASigner)

            else -> throw ProgrammerMistake("Unexpected blockchain state $blockchainState for blockchain $blockchainRid")
        }
    }

    protected open fun createRunningBlockchainProcess(
            workerContext: WorkerContext,
            historicBlockchainContext: HistoricBlockchainContext?,
            blockchainConfigProvider: BlockchainConfigurationProvider,
            blockchainConfig: BlockchainConfiguration,
            blockchainState: BlockchainState,
            iAmASigner: Boolean
    ): BlockchainProcess {
        return when {
            historicBlockchainContext != null -> HistoricBlockchainProcess(workerContext, historicBlockchainContext)
            iAmASigner -> ValidatorBlockchainProcess(workerContext, getStartWithFastSyncValue(blockchainConfig.chainID), blockchainState)
            else -> ReadOnlyBlockchainProcess(workerContext, blockchainState)
        }
    }

    protected open fun createPausedBlockchainProcess(workerContext: WorkerContext, blockchainConfigProvider: BlockchainConfigurationProvider, blockchainConfig: BlockchainConfiguration, blockchainState: BlockchainState): BlockchainProcess =
            ReadOnlyBlockchainProcess(workerContext, blockchainState)

    protected open fun canMovingBlockchainBeForceReadOnly(
            blockchainConfig: BlockchainConfiguration,
            blockchainState: BlockchainState,
            migratingInfo: MigratingBlockchainNodeInfo?
    ): Boolean = when {
        migratingInfo == null -> false // bc is not migrating
        blockchainState == BlockchainState.UNARCHIVING -> false // bc is migrating but is not moving
        migratingInfo.isSourceNode && migratingInfo.finalHeight != -1L -> true // bc is moving and finalHeight is fixed
        else -> false
    }

    protected open fun createMovingForceReadOnlyBlockchainProcess(
            workerContext: WorkerContext,
            blockchainState: BlockchainState,
            migratingInfo: MigratingBlockchainNodeInfo?
    ): BlockchainProcess {
        if (migratingInfo == null || blockchainState == BlockchainState.UNARCHIVING) {
            throw ProgrammerMistake("Can't create blockchain process for moving blockchain: ${workerContext.blockchainConfiguration.blockchainRid}")
        }
        return ForceReadOnlyBlockchainProcess(workerContext, blockchainState, migratingInfo.finalHeight)
    }

    protected open fun createUnarchivingBlockchainProcess(
            workerContext: WorkerContext,
            blockchainConfigProvider: BlockchainConfigurationProvider,
            blockchainConfig: BlockchainConfiguration,
            blockchainState: BlockchainState,
            iAmASigner: Boolean
    ): BlockchainProcess {
        val bcInfo = (blockchainConfigProvider as? ManagedBlockchainConfigurationProvider)
                ?.getMigratingBlockchainNodeInfo(blockchainConfig.blockchainRid)
        return when {
            bcInfo != null && bcInfo.isSourceNode && !bcInfo.isDestinationNode -> ForceReadOnlyBlockchainProcess(
                    workerContext, blockchainState, bcInfo.finalHeight)

            iAmASigner -> ValidatorBlockchainProcess(
                    workerContext, getStartWithFastSyncValue(blockchainConfig.chainID), blockchainState)

            else -> ReadOnlyBlockchainProcess(workerContext, blockchainState)
        }
    }

    /*
    Definition: cross-fetching is the process of downloading blocks from another blockchain
    over the peer-to-peer network. This is used when forking a chain when we don't have
    the old chain locally, and we haven't been able to sync using the new chain rid.

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

    protected open fun buildXCommunicationManager(
            blockchainConfigProvider: BlockchainConfigurationProvider,
            blockchainConfig: BlockchainConfiguration,
            relevantPeerCommConfig: PeerCommConfiguration,
            blockchainRid: BlockchainRid
    ): CommunicationManager<EbftMessage> {
        return DefaultPeerCommunicationManager(
                connectionManager,
                relevantPeerCommConfig,
                blockchainConfig.chainID,
                blockchainRid,
                EbftPacketCodec(relevantPeerCommConfig, blockchainRid),
                ebftMessageToString(blockchainConfig)
        ).apply { init() }
    }

    protected fun getStartWithFastSyncValue(chainId: Long): Boolean {
        return startWithFastSync[chainId] ?: true
    }
}