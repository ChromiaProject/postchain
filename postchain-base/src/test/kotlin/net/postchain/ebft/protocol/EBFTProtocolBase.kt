package net.postchain.ebft.protocol

import assertk.assertThat
import assertk.assertions.isEqualTo
import io.micrometer.core.instrument.Counter
import net.postchain.base.BaseBlockHeader
import net.postchain.base.NetworkNodes
import net.postchain.base.PeerCommConfiguration
import net.postchain.common.BlockchainRid
import net.postchain.common.hexStringToByteArray
import net.postchain.config.app.AppConfig
import net.postchain.config.node.NodeConfig
import net.postchain.core.BlockchainConfiguration
import net.postchain.core.BlockchainEngine
import net.postchain.core.EContext
import net.postchain.core.NodeRid
import net.postchain.core.Storage
import net.postchain.core.TransactionFactory
import net.postchain.core.TransactionQueue
import net.postchain.core.block.BlockBuildingStrategy
import net.postchain.core.block.BlockHeader
import net.postchain.core.block.BlockQueries
import net.postchain.core.block.InitialBlockData
import net.postchain.crypto.Secp256K1CryptoSystem
import net.postchain.crypto.Signature
import net.postchain.ebft.BaseBlockManager
import net.postchain.ebft.BaseStatusManager
import net.postchain.ebft.BlockDatabase
import net.postchain.ebft.BlockIntent
import net.postchain.ebft.BuildBlockIntent
import net.postchain.ebft.NodeBlockState
import net.postchain.ebft.NodeStateTracker
import net.postchain.ebft.message.EbftMessage
import net.postchain.ebft.message.Status
import net.postchain.ebft.syncmanager.validator.RevoltTracker
import net.postchain.ebft.syncmanager.validator.ValidatorSyncManager
import net.postchain.ebft.worker.WorkerContext
import net.postchain.gtv.merkle.GtvMerkleHashCalculator
import net.postchain.metrics.NodeStatusMetrics
import net.postchain.network.CommunicationManager
import org.apache.commons.configuration2.PropertiesConfiguration
import org.junit.jupiter.api.BeforeEach
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Clock
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

abstract class EBFTProtocolBase {

    protected val bridHex = "3475C1EEC5836D9B38218F78C30D302DBC7CAAAFFAF0CC83AE054B7A208F71D4"
    protected val blockchainRid = BlockchainRid.buildFromHex(bridHex)
    protected val node0Hex = "03A301697BDFCD704313BA48E51D567543F2A182031EFD6915DDC07BBCC4E16070"
    protected val node1Hex = "031B84C5567B126440995D3ED5AABA0565D71E1834604819FF9C17F5E9D5DD078F"
    protected val node2Hex = "03B2EF623E7EC933C478135D1763853CBB91FC31BA909AEC1411CA253FDCC1AC94"
    protected val node3Hex = "0203C6150397F7E4197FF784A8D74357EF20DAF1D09D823FFF8D3FC9150CBAE85D"
    protected val node0 = node0Hex.hexStringToByteArray()
    protected val node1 = node1Hex.hexStringToByteArray()
    protected val node2 = node2Hex.hexStringToByteArray()
    protected val node3 = node3Hex.hexStringToByteArray()
    protected val nodeRid0 = NodeRid.fromHex(node0Hex)
    protected val nodeRid1 = NodeRid.fromHex(node1Hex)
    protected val nodeRid2 = NodeRid.fromHex(node2Hex)
    protected val nodeRid3 = NodeRid.fromHex(node3Hex)
    protected val nodes = listOf(node0, node1, node2, node3)
    protected val myNodeId = 1
    protected var lastBlockHeight = 0L
    protected val peerIds = setOf(nodeRid0, nodeRid1, nodeRid2, nodeRid3)
    protected val cryptoSystem = Secp256K1CryptoSystem()
    protected val merkleHashCalculator = GtvMerkleHashCalculator(cryptoSystem)
    protected val prevBlockRid = "2222222222222222222222222222222222222222222222222222222222222222".hexStringToByteArray()
    protected val header0 = createBlockHeader(blockchainRid, 2L, 0, prevBlockRid, 1)
    protected val blockRid0 = header0.blockRID

    protected val blockDatabase: BlockDatabase = mock()
    protected val blockStrategy: BlockBuildingStrategy = mock()
    protected val nodeStateTracker: NodeStateTracker = mock()
    protected val counter: Counter = mock()
    protected val nodeStatusMetrics: NodeStatusMetrics = mock {
        on { revoltsByNode } doReturn counter
        on { revoltsOnNode } doReturn counter
        on { revoltsBetweenOthers } doReturn counter
    }
    protected val appConfig = AppConfig(PropertiesConfiguration().apply {
    })
    protected val nodeConfig = NodeConfig(appConfig)
    protected val blockQueries: BlockQueries = mock {
        val completionStage: CompletionStage<Long> = CompletableFuture.completedStage(lastBlockHeight)
        on { getLastBlockHeight() } doReturn completionStage
    }
    protected val transactionFactory: TransactionFactory = mock()
    protected val blockchainConfiguration: BlockchainConfiguration = mock {
        on { blockchainRid } doReturn blockchainRid
        on { signers } doReturn nodes
        on { getTransactionFactory() } doReturn transactionFactory
    }
    protected val transactionQueue: TransactionQueue = mock()
    protected val eContext: EContext = mock {
        on { chainID } doReturn 0
    }
    protected val storage: Storage = mock {
        on { openReadConnection(anyLong()) } doReturn eContext
    }
    protected val blockchainEngine: BlockchainEngine = mock {
        on { getBlockQueries() } doReturn blockQueries
        on { getConfiguration() } doReturn blockchainConfiguration
        on { getTransactionQueue() } doReturn transactionQueue
        on { blockBuilderStorage } doReturn storage
    }
    protected val commManager: CommunicationManager<EbftMessage> = mock()
    protected val networkNodes: NetworkNodes = mock {
        on { getPeerIds() } doReturn peerIds
    }
    protected val peerCommConf: PeerCommConfiguration = mock {
        on { networkNodes } doReturn networkNodes
    }
    protected val workerContext: WorkerContext = mock {
        on { appConfig } doReturn appConfig
        on { nodeConfig } doReturn nodeConfig
        on { engine } doReturn blockchainEngine
        on { communicationManager } doReturn commManager
        on { peerCommConfiguration } doReturn peerCommConf
        on { blockchainConfiguration } doReturn blockchainConfiguration
    }
    protected val clock: Clock = mock()
    protected val revoltTracker: RevoltTracker = mock()
    protected val signature: Signature = mock()

    protected lateinit var statusManager: BaseStatusManager
    protected lateinit var blockManager: BaseBlockManager
    protected lateinit var syncManager: ValidatorSyncManager

    @BeforeEach
    fun setup() {
        doReturn(BaseStatusManager.ZERO_SERIAL_TIME).whenever(clock).millis()
        statusManager = BaseStatusManager(nodes, myNodeId, 0, nodeStatusMetrics, clock)
        blockManager = BaseBlockManager(blockDatabase, statusManager, blockStrategy, workerContext)
        syncManager = ValidatorSyncManager(workerContext, emptyMap(), statusManager, blockManager, blockDatabase, nodeStateTracker, revoltTracker, { true }, false, clock)
        statusManager.recomputeStatus()
    }

    protected fun verifyStatus(blockRID: ByteArray?, height: Long, serial: Long, round: Long, revolting: Boolean, state: NodeBlockState) {
        argumentCaptor<Status> {
            verify(commManager).broadcastPacket(capture())
            assertThat(firstValue.blockRID).isEqualTo(blockRID)
            assertThat(firstValue.height).isEqualTo(height)
            assertThat(firstValue.serial).isEqualTo(serial)
            assertThat(firstValue.round).isEqualTo(round)
            assertThat(firstValue.revolting).isEqualTo(revolting)
            assertThat(firstValue.state).isEqualTo(state.ordinal)
        }
    }

    protected fun verifyIntent(intent: BlockIntent) = assertThat(statusManager.intent).isEqualTo(intent)

    protected fun messagesToReceive(vararg messages: Pair<NodeRid, EbftMessage>) {
        doReturn(messages.toList()).whenever(commManager).getPackets()
    }

    protected fun becomePrimary() {
        statusManager.myStatus.apply {
            blockRID = null
            height = 1
            serial = 3
            revolting = false
            round = 0
            state = NodeBlockState.WaitBlock
        }
        statusManager.recomputeStatus()
        verifyIntent(BuildBlockIntent)
    }

    private fun createBlockHeader(blockchainRid: BlockchainRid, blockIID: Long, chainId: Long, prevBlockRid: ByteArray, height: Long): BlockHeader {
        val rootHash = ByteArray(32) { 0 }
        val timestamp = 10000L + height
        val blockData = InitialBlockData(blockchainRid, blockIID, chainId, prevBlockRid, height, timestamp, arrayOf())
        return BaseBlockHeader.make(merkleHashCalculator, blockData, rootHash, timestamp, mapOf())
    }
}