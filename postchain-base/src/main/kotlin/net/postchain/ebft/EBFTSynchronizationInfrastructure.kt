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
import net.postchain.ebft.message.Message
import net.postchain.ebft.worker.HistoricChainWorker
import net.postchain.ebft.worker.ReadOnlyWorker
import net.postchain.ebft.worker.ValidatorWorker
import net.postchain.ebft.worker.WorkerContext
import net.postchain.network.CommunicationManager
import net.postchain.network.netty2.NettyConnectorFactory
import net.postchain.network.x.*

@Suppress("JoinDeclarationAndAssignment")
open class EBFTSynchronizationInfrastructure(
        val nodeConfigProvider: NodeConfigurationProvider,
        val nodeDiagnosticContext: NodeDiagnosticContext,
        val peersCommConfigFactory: PeersCommConfigFactory = DefaultPeersCommConfigFactory()
) : SynchronizationInfrastructure {

    protected val nodeConfig get() = nodeConfigProvider.getConfiguration()
    lateinit var connectionManager: XConnectionManager
    private val blockchainProcessesDiagnosticData = mutableMapOf<BlockchainRid, MutableMap<String, Any>>()

    init {
        this.init() // TODO: [POS-129]: Redesign this call
    }

    override fun init() {
        connectionManager = DefaultXConnectionManager(
                NettyConnectorFactory(),
                EbftPacketEncoderFactory(),
                EbftPacketDecoderFactory()
        )

        addBlockchainDiagnosticProperty()
    }

    override fun shutdown() {
        connectionManager.shutdown()
    }

    override fun makeBlockchainProcess(
            processName: BlockchainProcessName,
            engine: BlockchainEngine,
            historicBlockchain: HistoricBlockchain?
    ): BlockchainProcess {
        val blockchainConfig = engine.getConfiguration() as BaseBlockchainConfiguration // TODO: [et]: Resolve type cast
        val unregisterBlockchainDiagnosticData: () -> Unit = {
            blockchainProcessesDiagnosticData.remove(blockchainConfig.blockchainRid)
        }

        val peerCommConfiguration = peersCommConfigFactory.create(nodeConfig, blockchainConfig, historicBlockchain)
        val workerContext = WorkerContext(
                processName, blockchainConfig.signers, engine,
                blockchainConfig.configData.context.nodeID,
                buildXCommunicationManager(processName, blockchainConfig, peerCommConfiguration),
                peerCommConfiguration,
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
        return if (historicBlockchain != null) {
            historicBlockchain.contextCreator = {
                val historicPeerCommConfiguration = if (it == historicBlockchain.historicBrid) {
                    peersCommConfigFactory.create(nodeConfig, blockchainConfig, historicBlockchain)
                } else {
                    // It's an alias brid for historicBrid
                    buildPeerCommConfigurationForAlias(nodeConfig, historicBlockchain, it)
                }
                val histCommManager = buildXCommunicationManager(processName, blockchainConfig, historicPeerCommConfiguration, it)

                WorkerContext(processName, blockchainConfig.signers,
                        engine, blockchainConfig.configData.context.nodeID, histCommManager, historicPeerCommConfiguration,
                        nodeConfig, unregisterBlockchainDiagnosticData)

            }
            HistoricChainWorker(workerContext, historicBlockchain)
        } else if (blockchainConfig.configData.context.nodeID != NODE_ID_READ_ONLY) {
            registerBlockchainDiagnosticData(blockchainConfig.blockchainRid, DpNodeType.NODE_TYPE_VALIDATOR)
            ValidatorWorker(workerContext)
        } else {
            registerBlockchainDiagnosticData(blockchainConfig.blockchainRid, DpNodeType.NODE_TYPE_REPLICA)
            ReadOnlyWorker(workerContext)
        }
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
            blockchainConfig: BaseBlockchainConfiguration,
            relevantPeerCommConfig: PeerCommConfiguration,
            blockchainRid: BlockchainRid? = null
    ): CommunicationManager<Message> {
        val effectiveRid = blockchainRid ?: blockchainConfig.blockchainRid
        val packetEncoder = EbftPacketEncoder(relevantPeerCommConfig, effectiveRid)
        val packetDecoder = EbftPacketDecoder(relevantPeerCommConfig)

        return DefaultXCommunicationManager(
                connectionManager,
                relevantPeerCommConfig,
                blockchainConfig.chainID,
                effectiveRid,
                packetEncoder,
                packetDecoder,
                processName
        ).apply { init() }
    }

    // TODO: [POS-129] Merge: move it to [DefaultPeersCommConfigFactory]
    private fun buildPeerCommConfigurationForAlias(nodeConfig: NodeConfig, historicBlockchain: HistoricBlockchain, aliasBrid: BlockchainRid): PeerCommConfiguration {
        val myPeerID = XPeerID(nodeConfig.pubKeyByteArray)
        val peersThatServeAliasBrid = historicBlockchain.aliases[aliasBrid]!!

        val relevantPeerMap = nodeConfig.peerInfoMap.filterKeys {
            it in peersThatServeAliasBrid || it == myPeerID
        }

        return BasePeerCommConfiguration.build(
                relevantPeerMap.values,
                SECP256K1CryptoSystem(),
                nodeConfig.privKeyByteArray,
                nodeConfig.pubKeyByteArray)
    }

    private fun addBlockchainDiagnosticProperty() {
        nodeDiagnosticContext.addProperty(BLOCKCHAIN) {
            val diagnosticData = blockchainProcessesDiagnosticData.toMutableMap()

            connectionManager.getPeersTopology().forEach { (blockchainRid, topology) ->
                diagnosticData.computeIfPresent(BlockchainRid.buildFromHex(blockchainRid)) { _, properties ->
                    properties.apply {
                        put(DiagnosticProperty.BLOCKCHAIN_NODE_PEERS.prettyName, topology)
                    }
                }
            }

            diagnosticData.values.toTypedArray()
        }
    }

    private fun registerBlockchainDiagnosticData(blockchainRid: BlockchainRid, nodeType: DpNodeType) {
        blockchainProcessesDiagnosticData[blockchainRid] = mutableMapOf<String, Any>(
                DiagnosticProperty.BLOCKCHAIN_RID.prettyName to blockchainRid.toHex(),
                DiagnosticProperty.BLOCKCHAIN_NODE_TYPE.prettyName to nodeType.prettyName
        )
    }
}
