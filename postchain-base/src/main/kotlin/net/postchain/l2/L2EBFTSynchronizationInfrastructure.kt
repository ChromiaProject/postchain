package net.postchain.l2

import net.postchain.base.BasePeerCommConfiguration
import net.postchain.base.BlockchainRid
import net.postchain.base.PeerCommConfiguration
import net.postchain.base.SECP256K1CryptoSystem
import net.postchain.base.data.BaseBlockchainConfiguration
import net.postchain.base.l2.L2BlockchainConfiguration
import net.postchain.config.node.NodeConfig
import net.postchain.config.node.NodeConfigurationProvider
import net.postchain.core.BlockchainEngine
import net.postchain.core.BlockchainProcess
import net.postchain.core.NODE_ID_READ_ONLY
import net.postchain.core.SynchronizationInfrastructure
import net.postchain.debug.BlockchainProcessName
import net.postchain.debug.DiagnosticProperty
import net.postchain.debug.DpNodeType
import net.postchain.debug.NodeDiagnosticContext
import net.postchain.ebft.EbftPacketDecoder
import net.postchain.ebft.EbftPacketDecoderFactory
import net.postchain.ebft.EbftPacketEncoder
import net.postchain.ebft.EbftPacketEncoderFactory
import net.postchain.ebft.message.Message
import net.postchain.ebft.worker.ReadOnlyWorker
import net.postchain.ebft.worker.ValidatorWorker
import net.postchain.ebft.worker.WorkerContext
import net.postchain.network.CommunicationManager
import net.postchain.network.netty2.NettyConnectorFactory
import net.postchain.network.x.DefaultXCommunicationManager
import net.postchain.network.x.DefaultXConnectionManager
import net.postchain.network.x.XConnectionManager
import net.postchain.network.x.XPeerID

class L2EBFTSynchronizationInfrastructure(
    val nodeConfigProvider: NodeConfigurationProvider,
    val nodeDiagnosticContext: NodeDiagnosticContext
) : SynchronizationInfrastructure {
    private val nodeConfig get() = nodeConfigProvider.getConfiguration()
    val connectionManager: XConnectionManager
    private val blockchainProcessesDiagnosticData = mutableMapOf<BlockchainRid, MutableMap<String, Any>>()

    init {
        connectionManager = DefaultXConnectionManager(
            NettyConnectorFactory(),
            EbftPacketEncoderFactory(),
            EbftPacketDecoderFactory(),
            SECP256K1CryptoSystem()
        )

        addBlockchainDiagnosticProperty()
    }

    override fun makeBlockchainProcess(
        processName: BlockchainProcessName,
        engine: BlockchainEngine
    ): BlockchainProcess {
        val blockchainConfig = engine.getConfiguration() as L2BlockchainConfiguration // TODO: [et]: Resolve type cast
        val layer2 = blockchainConfig.configData.getLayer2()
        val unregisterBlockchainDiagnosticData: () -> Unit = {
            blockchainProcessesDiagnosticData.remove(blockchainConfig.blockchainRid)
        }
        val peerCommConfiguration = buildPeerCommConfiguration(nodeConfig, blockchainConfig)

        val url = layer2?.get("eth_rpc_api_node_url")?.asString() ?: "http://localhost:8545"
        val contractAddress = layer2?.get("contract_address")?.asString() ?: "0x0"
        val workerContext = WorkerContext(
            processName, blockchainConfig.signers, engine,
            blockchainConfig.configData.context.nodeID,
            buildXCommunicationManager(processName, blockchainConfig, peerCommConfiguration),
            peerCommConfiguration,
            nodeConfig,
            unregisterBlockchainDiagnosticData
        ).useWeb3Connector(url, contractAddress)

        return if (blockchainConfig.configData.context.nodeID != NODE_ID_READ_ONLY) {
            registerBlockchainDiagnosticData(blockchainConfig.blockchainRid, DpNodeType.NODE_TYPE_VALIDATOR)
            ValidatorWorker(workerContext)
        } else {
            registerBlockchainDiagnosticData(blockchainConfig.blockchainRid, DpNodeType.NODE_TYPE_REPLICA)
            ReadOnlyWorker(workerContext)
        }
    }

    override fun shutdown() {
        connectionManager.shutdown()
    }

    private fun addBlockchainDiagnosticProperty() {
        nodeDiagnosticContext.addProperty(DiagnosticProperty.BLOCKCHAIN) {
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

    private fun buildPeerCommConfiguration(
        nodeConfig: NodeConfig,
        blockchainConfig: BaseBlockchainConfiguration
    ): PeerCommConfiguration {
        val myPeerID = XPeerID(nodeConfig.pubKeyByteArray)
        val signers = blockchainConfig.signers.map { XPeerID(it) }
        val signersReplicas = signers.flatMap {
            nodeConfig.nodeReplicas[it] ?: listOf()
        }
        val blockchainReplicas = nodeConfig.blockchainReplicaNodes[blockchainConfig.blockchainRid] ?: listOf()
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

    private fun registerBlockchainDiagnosticData(blockchainRid: BlockchainRid, nodeType: DpNodeType) {
        blockchainProcessesDiagnosticData[blockchainRid] = mutableMapOf<String, Any>(
            DiagnosticProperty.BLOCKCHAIN_RID.prettyName to blockchainRid.toHex(),
            DiagnosticProperty.BLOCKCHAIN_NODE_TYPE.prettyName to nodeType.prettyName
        )
    }

    private fun buildXCommunicationManager(
        processName: BlockchainProcessName,
        blockchainConfig: BaseBlockchainConfiguration,
        relevantPeerCommConfig: PeerCommConfiguration
    ): CommunicationManager<Message> {

        val packetEncoder = EbftPacketEncoder(relevantPeerCommConfig, blockchainConfig.blockchainRid)
        val packetDecoder = EbftPacketDecoder(relevantPeerCommConfig)

        return DefaultXCommunicationManager(
            connectionManager,
            relevantPeerCommConfig,
            blockchainConfig.chainID,
            blockchainConfig.blockchainRid,
            packetEncoder,
            packetDecoder,
            processName
        ).apply { init() }
    }
}