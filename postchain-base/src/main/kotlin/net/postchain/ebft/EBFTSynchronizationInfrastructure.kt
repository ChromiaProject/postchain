// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.ebft

import net.postchain.base.*
import net.postchain.base.data.BaseBlockchainConfiguration
import net.postchain.config.node.NodeConfig
import net.postchain.config.node.NodeConfigurationProvider
import net.postchain.core.*
import net.postchain.debug.BlockchainProcessName
import net.postchain.debug.DiagnosticProperty
import net.postchain.debug.DiagnosticProperty.BLOCKCHAIN
import net.postchain.debug.DpNodeType
import net.postchain.debug.NodeDiagnosticContext
import net.postchain.ebft.heartbeat.HeartbeatListener
import net.postchain.ebft.message.Message
import net.postchain.ebft.worker.HistoricBlockchainProcess
import net.postchain.ebft.worker.ReadOnlyBlockchainProcess
import net.postchain.ebft.worker.ValidatorBlockchainProcess
import net.postchain.ebft.worker.WorkerContext
import net.postchain.network.CommunicationManager
import net.postchain.network.common.*
import net.postchain.network.peer.*

@Suppress("JoinDeclarationAndAssignment")
open class EBFTSynchronizationInfrastructure(
        val nodeConfigProvider: NodeConfigurationProvider,
        val nodeDiagnosticContext: NodeDiagnosticContext,
        val peersCommConfigFactory: PeersCommConfigFactory = DefaultPeersCommConfigFactory()
) : SynchronizationInfrastructure {

    val nodeConfig get() = nodeConfigProvider.getConfiguration()
    lateinit var connectionManager: ConnectionManager
    protected val blockchainProcessesDiagnosticData = mutableMapOf<BlockchainRid, MutableMap<DiagnosticProperty, () -> Any>>()
    private val startWithFastSync: MutableMap<Long, Boolean> = mutableMapOf() // { chainId -> true/false }

    init {
        this.init() // TODO: [POS-129]: Redesign this call
    }

    override fun init() {
        connectionManager = DefaultPeerConnectionManager(
                EbftPacketEncoderFactory(),
                EbftPacketDecoderFactory(),
                SECP256K1CryptoSystem()
        )

        fillDiagnosticContext()
    }

    override fun shutdown() {
        connectionManager.shutdown()
    }

    override fun makeBlockchainProcess(
            processName: BlockchainProcessName,
            engine: BlockchainEngine,
            heartbeatListener: HeartbeatListener?
    ): BlockchainProcess {
        val blockchainConfig = engine.getConfiguration()
        val unregisterBlockchainDiagnosticData: () -> Unit = {
            blockchainProcessesDiagnosticData.remove(blockchainConfig.blockchainRid)
        }

        val historicBrid = blockchainConfig.effectiveBlockchainRID
        val historicBlockchainContext = if (crossFetchingEnabled(blockchainConfig)) {
            HistoricBlockchainContext(
                    historicBrid, nodeConfig.blockchainAncestors[blockchainConfig.blockchainRid]
                    ?: emptyMap()
            )
        } else null

        val peerCommConfiguration = peersCommConfigFactory.create(nodeConfig, blockchainConfig, historicBlockchainContext)
        val workerContext = WorkerContext(
                processName,
                blockchainConfig,
                engine,
                buildXCommunicationManager(processName, blockchainConfig, peerCommConfiguration, blockchainConfig.blockchainRid),
                peerCommConfiguration,
                heartbeatListener,
                nodeConfig,
                unregisterBlockchainDiagnosticData
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
                    peersCommConfigFactory.create(nodeConfig, blockchainConfig, historicBlockchainContext)
                } else {
                    // It's an ancestor brid for historicBrid
                    buildPeerCommConfigurationForAncestor(nodeConfig, historicBlockchainContext, brid)
                }
                val histCommManager = buildXCommunicationManager(processName, blockchainConfig, historicPeerCommConfiguration, brid)

                WorkerContext(
                        processName,
                        blockchainConfig,
                        engine,
                        histCommManager,
                        historicPeerCommConfiguration,
                        heartbeatListener,
                        nodeConfig,
                        unregisterBlockchainDiagnosticData
                )

            }
            HistoricBlockchainProcess(workerContext, historicBlockchainContext).also { it.start() }
        } else if (blockchainConfig.blockchainContext.nodeID != NODE_ID_READ_ONLY) {
            ValidatorBlockchainProcess(workerContext, getStartWithFastSyncValue(blockchainConfig.chainID)).also { it.start() }
        } else {
            ReadOnlyBlockchainProcess(workerContext).also { it.start() }
        }.also { it.registerDiagnosticData(blockchainProcessesDiagnosticData.getOrPut(blockchainConfig.blockchainRid) { mutableMapOf() }) }
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
        val chainPeers = blockchainConfig.signers.map { it.byteArrayKeyOf() }

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
    ): CommunicationManager<Message> {
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

    // TODO: [POS-129] Merge: move it to [DefaultPeersCommConfigFactory]
    private fun buildPeerCommConfigurationForAncestor(
            nodeConfig: NodeConfig,
            historicBlockchainContext: HistoricBlockchainContext,
            ancBrid: BlockchainRid
    ): PeerCommConfiguration {
        val myPeerID = NodeRid(nodeConfig.pubKeyByteArray)
        val peersThatServeAncestorBrid = historicBlockchainContext.ancestors[ancBrid]!!

        val relevantPeerMap = nodeConfig.peerInfoMap.filterKeys {
            it in peersThatServeAncestorBrid || it == myPeerID
        }

        return BasePeerCommConfiguration.build(
                relevantPeerMap.values,
                SECP256K1CryptoSystem(),
                nodeConfig.privKeyByteArray,
                nodeConfig.pubKeyByteArray
        )
    }

    /**
     * To calculate the [relevantPeerMap] we need to:
     *
     * 1. begin with the signers (from the BC config)
     * 2. add all NODE replicas (from node config)
     * 3. add BC replicas (from node config)
     *
     * TODO: Could getRelevantPeers() be a method inside [NodeConfig]?
     */
    private fun buildPeerCommConfiguration(
            nodeConfig: NodeConfig,
            blockchainConfig: BaseBlockchainConfiguration,
            historicBlockchainContext: HistoricBlockchainContext? = null
    ): PeerCommConfiguration {
        val myPeerID = NodeRid(nodeConfig.pubKeyByteArray)
        val signers = blockchainConfig.signers.map { NodeRid(it) }
        val signersReplicas = signers.flatMap {
            nodeConfig.nodeReplicas[it] ?: listOf()
        }
        val blockchainReplicas = if (historicBlockchainContext != null) {
            (nodeConfig.blockchainReplicaNodes[historicBlockchainContext.historicBrid] ?: listOf()).union(
                    nodeConfig.blockchainReplicaNodes[blockchainConfig.blockchainRid] ?: listOf()
            )
        } else {
            nodeConfig.blockchainReplicaNodes[blockchainConfig.blockchainRid] ?: listOf()
        }

        val relevantPeerMap = nodeConfig.peerInfoMap.filterKeys {
            it in signers || it in signersReplicas || it in blockchainReplicas || it == myPeerID
        }

        return BasePeerCommConfiguration.build(
                relevantPeerMap.values,
                SECP256K1CryptoSystem(),
                nodeConfig.privKeyByteArray,
                nodeConfig.pubKeyByteArray
        )
    }

    protected fun fillDiagnosticContext() {
        nodeDiagnosticContext.addProperty(BLOCKCHAIN) {
            val diagnosticData = blockchainProcessesDiagnosticData.toMutableMap()

            connectionManager.getNodesTopology().forEach { (blockchainRid, topology) ->
                diagnosticData.computeIfPresent(BlockchainRid.buildFromHex(blockchainRid)) { _, properties ->
                    properties.apply {
                        put(DiagnosticProperty.BLOCKCHAIN_NODE_PEERS) { topology }
                    }
                }
            }

            diagnosticData
                    .mapValues { (_, v) ->
                        v.mapValues { (_, v2) -> v2() }
                    }
                    .values.toTypedArray()
        }
    }

    private fun getStartWithFastSyncValue(chainId: Long): Boolean {
        return startWithFastSync[chainId] ?: true
    }
}
