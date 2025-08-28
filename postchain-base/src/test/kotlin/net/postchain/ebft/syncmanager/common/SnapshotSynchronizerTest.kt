package net.postchain.ebft.syncmanager.common

import assertk.assertThat
import assertk.assertions.isEqualTo
import net.postchain.base.BaseBlockHeader
import net.postchain.base.BlockWitnessProvider
import net.postchain.base.NetworkNodes
import net.postchain.base.PeerCommConfiguration
import net.postchain.base.data.DatabaseAccess
import net.postchain.base.gtv.BlockHeaderData
import net.postchain.base.snapshot.RangeProof
import net.postchain.base.snapshot.SNAPSHOT_ROOT_EXTRA_HEADER
import net.postchain.base.snapshot.SnapshotDatum
import net.postchain.base.snapshot.VerifyRangeProof
import net.postchain.common.BlockchainRid
import net.postchain.common.data.Hash
import net.postchain.common.exception.ProgrammerMistake
import net.postchain.core.BlockchainConfiguration
import net.postchain.core.BlockchainEngine
import net.postchain.core.EContext
import net.postchain.core.NodeRid
import net.postchain.core.Storage
import net.postchain.core.block.BlockQueries
import net.postchain.core.block.BlockWitness
import net.postchain.core.block.BlockWitnessBuilder
import net.postchain.ebft.BlockDatabase
import net.postchain.ebft.message.EbftMessage
import net.postchain.ebft.message.GetSnapshotData
import net.postchain.ebft.message.MessageDurationTracker
import net.postchain.ebft.message.SnapshotBlockHeader
import net.postchain.ebft.message.SnapshotBlockHeaderContextData
import net.postchain.ebft.message.SnapshotData
import net.postchain.ebft.message.SnapshotDatumData
import net.postchain.ebft.message.SnapshotRangeProof
import net.postchain.ebft.syncmanager.configuration.RateLimitConfiguration
import net.postchain.ebft.worker.WorkerContext
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.GtvNull
import net.postchain.gtv.merkle.makeMerkleHashCalculator
import net.postchain.gtx.SnapshotAware
import net.postchain.gtx.SnapshotContext
import net.postchain.network.CommunicationManager
import net.postchain.network.ReceivedPacket
import org.awaitility.Awaitility
import org.awaitility.Duration
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.Mockito.`when`
import org.mockito.kotlin.KInvocationOnMock
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.spy
import java.nio.ByteBuffer
import java.util.LinkedList
import java.util.concurrent.CompletableFuture

class SnapshotSynchronizerTest {

    private var isProcessRunning: Boolean = true
    private val brid = "3475C1EEC5836D9B38218F78C30D302DBC7CAAAFFAF0CC83AE054B7A208F71D4"
    private val blockRID = BlockchainRid.buildFromHex(brid)
    private val node = "0350FE40766BC0CE8D08B3F5B810E49A8352FDD458606BD5FAFE5ACDCDC8FF3F57"
    private val nodeRid = NodeRid.fromHex(node)
    private val height = 10L
    private val lastBlockHeight = 10L
    private val header: ByteArray = "header".toByteArray()
    private val witness: ByteArray = "witness".toByteArray()
    private val peerIds = mutableSetOf<NodeRid>()
    private val ourChainId = 0L

    private val headerRec: BlockHeaderData = mock {
        on { getHeight() } doReturn height
    }
    private val baseBlockHeader: BaseBlockHeader = mock {
        on { blockHeaderRec } doReturn headerRec
        on { rawData } doReturn header
    }
    private val commManager: CommunicationManager<EbftMessage> = mock()
    private val blockQueries: BlockQueries = mock {
        on { getLastBlockHeight() } doReturn CompletableFuture.completedStage(lastBlockHeight)
        on { getSnapshotContextMaxIds(anyLong()) } doAnswer { CompletableFuture.completedStage(snapshotModuleByContextMap.values.associate { it.contextId to 0L }) }
    }
    private val blockWitness: BlockWitness = mock {
        on { getRawData() } doReturn witness
    }
    private val blockWitnessBuilder: BlockWitnessBuilder = mock()
    private val blockWitnessProvider: BlockWitnessProvider = mock {
        on { createWitnessBuilderWithoutOwnSignature(baseBlockHeader) } doReturn blockWitnessBuilder
    }
    private val blockchainConfiguration: BlockchainConfiguration = mock {
        on { decodeBlockHeader(header) } doReturn baseBlockHeader
        on { decodeWitness(witness) } doReturn blockWitness
        on { getBlockHeaderValidator() } doReturn blockWitnessProvider
        on { blockchainRid } doReturn blockRID
        on { signers } doReturn listOf(nodeRid.data)
        on { chainID } doReturn ourChainId
        on { merkleHashCalculator } doReturn makeMerkleHashCalculator(2)
    }
    private val databaeAccess = mock<DatabaseAccess>()
    private val ctx = mock<EContext> {
        on { getInterface(eq(DatabaseAccess::class.java)) } doReturn databaeAccess
    }
    private val storage = mock<Storage> {
        on { openWriteConnection(anyLong()) } doReturn ctx
    }
    private val blockchainEngine: BlockchainEngine = mock {
        on { getBlockQueries() } doReturn blockQueries
        on { getConfiguration() } doReturn blockchainConfiguration
        on { blockBuilderStorage } doReturn storage
    }
    private val networkNodes: NetworkNodes = mock {
        on { getPeerIds() } doAnswer {
            peerIds
        }
    }
    private val peerCommConf: PeerCommConfiguration = mock {
        on { networkNodes } doReturn networkNodes
    }
    private val messageDurationTracker: MessageDurationTracker = mock()
    private val workerContext: WorkerContext = mock {
        on { engine } doReturn blockchainEngine
        on { communicationManager } doReturn commManager
        on { peerCommConfiguration } doReturn peerCommConf
        on { blockchainConfiguration } doReturn blockchainConfiguration
        on { messageDurationTracker } doReturn messageDurationTracker
    }
    private val blockDatabase: BlockDatabase = mock()
    private val peerStatuses: PeerStatuses = mock()
    private val params = SyncParameters()
    private val snapshotModuleByContextMap = mutableMapOf<Long, SnapshotAwareTestModule>()
    private val messageQueue = LinkedList<Pair<NodeRid, EbftMessage>>()
    private val node1 = NodeRid(1) { 1 }
    private val node2 = NodeRid(1) { 2 }
    private val node3 = NodeRid(1) { 3 }
    private val node4 = NodeRid(1) { 4 }

    private lateinit var ss: SnapshotSynchronizer

    @BeforeEach
    fun setup() {
        snapshotModuleByContextMap.clear()
        messageQueue.clear()
        val verifyRangeProof = mock<VerifyRangeProof> {
            on { verify(any<Hash>(), any<RangeProof>(), anyLong(), any<List<Hash>>()) } doReturn true
        }
        ss = spy(SnapshotSynchronizer(workerContext, blockDatabase, params, peerStatuses, { isProcessRunning },
                RateLimitConfiguration(100), verifyRangeProof))
    }

    @Test
    fun `basic flow until building snapshot`() {
        peerIds.addAll(listOf(node1, node2, node3, node4))
        addTestModules(listOf(3L, 5L))
        val snapshotBlockHeaderMsg = makeSnapshotBlockHeaderMessage(10)
        `when`(commManager.getPackets()).doAnswer {
            peerIds.map { ReceivedPacket(it, 1L, snapshotBlockHeaderMsg as EbftMessage) }.toMutableList()
        }.doAnswer(::provideQueuedPackets)
        whenGetSnapshotDataReplyWith { randomPeer, message ->
            if (message.datumIdFrom <= snapshotModuleByContextMap[message.contextId]!!.datumIdMax)
                listOf(SnapshotDatumData(gtv(true), false))
            else emptyList()
        }

        // Expected, everything is received but we are not interested in building the snapshot here
        val exception = assertThrows<ProgrammerMistake> {
            ss.trySnapshotSync()
        }
        assertThat(exception.message).isEqualTo("Snapshot root hashes do not match")

        // Assert constructed data
        assertThat(snapshotModuleByContextMap[0]!!.constructDatumInvocations.size).isEqualTo(3)
        assertThat(snapshotModuleByContextMap[1]!!.constructDatumInvocations.size).isEqualTo(5)
    }

    /**
     * Starting with only one node (#2) having the latest snapshot block, verify that the sync
     * eventually will ask all nodes for tha snapshot of the same height.
     */
    @Test
    fun `detect new nodes during snapshot syncing`() {
        peerIds.addAll(listOf(node1, node2, node3, node4))
        addTestModules(listOf(3L, 5L))

        // Node2 has latest only
        val snapshotBlockHeaderMsg = makeSnapshotBlockHeaderMessage(10)
        val notLatestSnapshotBlockHeaderMsg = makeSnapshotBlockHeaderMessage(8)
        `when`(commManager.getPackets()).doAnswer {
            mutableListOf(
                    ReceivedPacket(node1, 1L, notLatestSnapshotBlockHeaderMsg),
                    ReceivedPacket(node2, 1L, snapshotBlockHeaderMsg),
                    ReceivedPacket(node3, 1L, notLatestSnapshotBlockHeaderMsg),
                    ReceivedPacket(node4, 1L, notLatestSnapshotBlockHeaderMsg),
            )
        }.doAnswer(::provideQueuedPackets)
        val nodesReceivedGetSnapshotData = mutableSetOf<NodeRid>()
        whenGetSnapshotDataReplyWith { randomPeer, message ->
            nodesReceivedGetSnapshotData.add(randomPeer)
            if (nodesReceivedGetSnapshotData.size < peerIds.size)
                listOf(SnapshotDatumData(gtv(true), false))
            else
                emptyList()
        }

        // Expected, everything is received but we are not interested in building the snapshot here
        val exception = assertThrows<ProgrammerMistake> {
            params.snapshotSyncNodesUpdateIntervalTime = 10
            ss.trySnapshotSync()
        }
        assertThat(exception.message).isEqualTo("Snapshot root hashes do not match")

        // Assert all nodes eventually got the request
        Awaitility.await().atMost(Duration.ONE_MINUTE).untilAsserted {
            assertThat(nodesReceivedGetSnapshotData.size).isEqualTo(4)
        }
    }

    /**
     * 3 nodes out of 4 has the latest snapshot. Ignore all snapshot requests to verify that the timeout
     * moves on to the next node and eventually sends the request to all 3 of them.
     */
    @Test
    fun `retry sending snapshot request on timeout`() {
        peerIds.addAll(listOf(node1, node2, node3, node4))
        addTestModules(listOf(3L, 5L))

        // Node2 has latest only
        val snapshotBlockHeaderMsg = makeSnapshotBlockHeaderMessage(10)
        val notLatestSnapshotBlockHeaderMsg = makeSnapshotBlockHeaderMessage(8)
        `when`(commManager.getPackets()).doAnswer {
            mutableListOf(
                    ReceivedPacket(node1, 1L, snapshotBlockHeaderMsg),
                    ReceivedPacket(node2, 1L, snapshotBlockHeaderMsg),
                    ReceivedPacket(node3, 1L, snapshotBlockHeaderMsg),
                    ReceivedPacket(node4, 1L, notLatestSnapshotBlockHeaderMsg),
            )
        }.doAnswer(::provideQueuedPackets)
        val nodesReceivedGetSnapshotData = mutableSetOf<NodeRid>()
        whenGetSnapshotData { randomPeer, message ->
            // Record peers receiving this request and keep ignoring them until expected 3 nodes has received them
            nodesReceivedGetSnapshotData.add(randomPeer)
            if (nodesReceivedGetSnapshotData.size < 3) {
                null
            } else {
                SnapshotData(message.height, message.contextId, message.datumIdFrom, emptyList(), null)
            }
        }

        // Expected, everything is received but we are not interested in building the snapshot here
        val exception = assertThrows<ProgrammerMistake> {
            params.snapshotSyncNodesUpdateIntervalTime = -1
            params.jobTimeout = 100
            ss.trySnapshotSync()
        }
        assertThat(exception.message).isEqualTo("Snapshot root hashes do not match")

        // Assert all nodes eventually got the request
        Awaitility.await().atMost(Duration.ONE_MINUTE).untilAsserted {
            assertThat(nodesReceivedGetSnapshotData.size).isEqualTo(3)
        }
    }

    private fun provideQueuedPackets(mock: KInvocationOnMock): MutableList<ReceivedPacket<EbftMessage>> {
        return if (messageQueue.isNotEmpty()) {
            val message = messageQueue.poll()
            mutableListOf(ReceivedPacket(message.first, 1L, message.second))
        } else {
            mutableListOf()
        }
    }

    private fun whenGetSnapshotData(op: (peer: NodeRid, message: GetSnapshotData) -> SnapshotData?) {
        `when`(commManager.sendToRandomPeer(any<GetSnapshotData>(), any<Set<NodeRid>>())).doAnswer { it ->
            val message = it.arguments[0] as GetSnapshotData
            val peers = (it.arguments[1] as Set<*>).filterIsInstance<NodeRid>()
            if (peers.isEmpty()) {
                null to setOf()
            } else {
                val randomPeer = peers.random()
                op(randomPeer, message)?.let {
                    messageQueue.add(randomPeer to it)
                }
                randomPeer to setOf()
            }
        }
    }

    private fun whenGetSnapshotDataReplyWith(op: (peer: NodeRid, message: GetSnapshotData) -> List<SnapshotDatumData>?) {
        whenGetSnapshotData { peer, message ->
            SnapshotData(message.height, message.contextId, message.datumIdFrom,
                    op(peer, message), SnapshotRangeProof(emptyList(), emptyList(), emptyList()))
        }
    }

    private fun makeSnapshotBlockHeaderMessage(height: Long, snapshotRootHash: Hash = Hash(10) { 5 }, datumIdMax: Long = 20L): SnapshotBlockHeader {
        val blockHeaderData = BlockHeaderData(
                gtv(BlockchainRid.ZERO_RID.data),
                gtv(BlockchainRid.ZERO_RID.data),
                gtv(ByteArray(0)),
                gtv(1000000),
                gtv(height),
                GtvNull,
                gtv(mapOf(SNAPSHOT_ROOT_EXTRA_HEADER to gtv(snapshotRootHash)))
        )
        val witnessBytes = ByteArray(10)
        val witnessBuffer = ByteBuffer.wrap(witnessBytes)
        witnessBuffer.putInt(0)
        val snapshotBlockHeaderMsg = SnapshotBlockHeader(GtvEncoder.encodeGtv(blockHeaderData.toGtv()), witnessBytes,
                listOf(
                        SnapshotBlockHeaderContextData(0, ByteArray(0), datumIdMax),
                        SnapshotBlockHeaderContextData(1, ByteArray(0), datumIdMax))
        )
        return snapshotBlockHeaderMsg
    }

    private fun addTestModules(datumIdMaxList: List<Long>) {
        datumIdMaxList.mapIndexed { contextId, datumIdMax -> SnapshotAwareTestModule(contextId.toLong(), datumIdMax) }
                .forEach { snapshotModuleByContextMap[it.contextId] = it }
        ss.snapshotModuleByContextMap.putAll(snapshotModuleByContextMap)
    }

    class SnapshotAwareTestModule(val contextId: Long, val datumIdMax: Long) : SnapshotAware {

        val constructDatumInvocations = mutableListOf<SnapshotDatum>()

        override fun initializeSnapshotContext(context: SnapshotContext) {}

        override fun getPermanentDatumIdMax(ctx: EContext): Long? = null

        override fun getPermanentDatum(ctx: EContext, datumId: Long): Gtv? = null

        override fun constructDatum(ctx: EContext, datumId: Long, datum: Gtv, isPermanent: Boolean) {
            // Capture data sent to module for verification
            constructDatumInvocations.add(SnapshotDatum(datumId, datum, isPermanent))
        }

        override fun constructDatum(ctx: EContext, datumList: List<SnapshotDatum>) {
            datumList.forEach(constructDatumInvocations::add)
        }
    }
}
