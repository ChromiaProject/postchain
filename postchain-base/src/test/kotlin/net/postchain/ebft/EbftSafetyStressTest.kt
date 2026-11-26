// Copyright (c) 2026 ChromaWay AB. See README for license information.

package net.postchain.ebft

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import net.postchain.common.hexStringToByteArray
import net.postchain.core.BlockchainConfiguration
import net.postchain.core.block.BlockData
import net.postchain.core.block.BlockHeader
import net.postchain.crypto.Signature
import net.postchain.ebft.message.StateChangeTracker
import net.postchain.ebft.syncmanager.BlockDataDecoder
import io.micrometer.core.instrument.Counter
import net.postchain.metrics.NodeStatusMetrics
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * Multi-node EBFT safety, liveness, and backward-compatibility stress tests for:
 * - Finding #3: Prepared validator sync recovery without Byzantine hijacking
 * - Finding #4: Durable Prepared block lock with full BlockData persistence (cluster/quorum restart recovery)
 */
class EbftSafetyStressTest {

    private val clock = Clock.fixed(Instant.ofEpochMilli(BaseStatusManager.ZERO_SERIAL_TIME + 10_000L), ZoneOffset.UTC)

    private fun makeNodes(count: Int): List<ByteArray> =
            (1..count).map { i ->
                val hex = i.toString(16).padStart(2, '0').repeat(32)
                hex.hexStringToByteArray()
            }

    private fun sig(nodeIdx: Int, blockSeed: Int): Signature =
            Signature(ByteArray(33) { (nodeIdx + 1).toByte() }, ByteArray(64) { (nodeIdx * 17 + blockSeed).toByte() })

    private fun blockRid(seed: Int): ByteArray = ByteArray(32) { seed.toByte() }

    private fun fakeBlockBytes(seed: Int): ByteArray = "serialized-block-data-$seed".toByteArray()

    companion object {
        private fun mockMetrics(): NodeStatusMetrics = mock {
            on { revoltsByNode } doReturn mock<Counter>()
            on { revoltsOnNode } doReturn mock<Counter>()
            on { revoltsBetweenOthers } doReturn mock<Counter>()
        }
    }

    /**
     * Single-row in-memory PreparedBlockStore mirroring ValidatorPreparedBlockStore's single-row UPSERT contract.
     */
    private class SingleRowPreparedBlockStore : PreparedBlockStore {
        var row: PreparedBlockRecord? = null
            private set
        var saveCalls: Int = 0
            private set

        override fun initialize() = Unit

        override fun load(height: Long): PreparedBlockRecord? =
                row?.takeIf { it.height == height }

        override fun save(height: Long, blockRID: ByteArray, blockData: ByteArray?): Boolean {
            saveCalls++
            val existing = row
            if (existing != null) {
                if (existing.height > height) return false
                if (existing.height == height) {
                    if (!existing.blockRID.contentEquals(blockRID)) return false
                    if (existing.blockData == null && blockData != null) {
                        row = PreparedBlockRecord(height, blockRID, blockData)
                    }
                    return true
                }
            }
            row = PreparedBlockRecord(height, blockRID, blockData)
            return true
        }
    }

    private fun copyStatus(s: NodeStatus): NodeStatus =
            NodeStatus(s.height, s.serial).apply {
                round = s.round
                revolting = s.revolting
                state = s.state
                blockRID = s.blockRID?.copyOf()
                signature = s.signature
            }

    private data class SimNode(
            val index: Int,
            val nodes: List<ByteArray>,
            val store: SingleRowPreparedBlockStore?,
            var statusManager: BaseStatusManager,
            val clock: Clock,
            var currentBlockRid: ByteArray? = null,
            var currentBlockData: ByteArray? = null,
            val committedBlocks: MutableMap<Long, ByteArray> = mutableMapOf()
    ) {
        fun restart(nextHeight: Long = statusManager.myStatus.height) {
            currentBlockRid = null
            currentBlockData = null
            statusManager = BaseStatusManager(
                    nodes,
                    index,
                    nextHeight,
                    mockMetrics(),
                    mock<StateChangeTracker>(),
                    clock,
                    store
            )
        }
    }

    private fun createCluster(count: Int, startHeight: Long, withStore: (Int) -> Boolean): List<SimNode> {
        val nodes = makeNodes(count)
        return (0 until count).map { i ->
            val store = if (withStore(i)) SingleRowPreparedBlockStore() else null
            SimNode(
                    index = i,
                    nodes = nodes,
                    store = store,
                    statusManager = BaseStatusManager(
                            nodes,
                            i,
                            startHeight,
                            mockMetrics(),
                            mock<StateChangeTracker>(),
                            clock,
                            store
                    ),
                    clock = clock
            )
        }
    }

    private fun gossipStatuses(cluster: List<SimNode>) {
        val snapshots = cluster.map { copyStatus(it.statusManager.myStatus) }
        for (receiver in cluster) {
            for ((senderIdx, status) in snapshots.withIndex()) {
                if (senderIdx != receiver.index) {
                    receiver.statusManager.onStatusUpdate(senderIdx, copyStatus(status))
                }
            }
        }
    }

    private fun gossipSignatures(cluster: List<SimNode>) {
        for (sender in cluster) {
            val rid = sender.statusManager.myStatus.blockRID ?: continue
            val sig = sender.statusManager.getCommitSignature() ?: continue
            if (sender.statusManager.myStatus.state == NodeBlockState.Prepared) {
                for (receiver in cluster) {
                    if (receiver.index != sender.index) {
                        receiver.statusManager.onCommitSignature(sender.index, rid, sig)
                    }
                }
            }
        }
    }

    /**
     * Step all nodes' intents using the exact same local-reload-first rule as ValidatorSyncManager.fetchOrProcessProposedBlock.
     */
    private fun stepIntents(
            cluster: List<SimNode>,
            nextBuildSeed: () -> Int,
            allowCommit: Boolean = true
    ): Int {
        var progress = 0
        for (node in cluster) {
            when (val intent = node.statusManager.getBlockIntent()) {
                is BuildBlockIntent -> {
                    val seed = nextBuildSeed()
                    val rid = blockRid(seed)
                    val data = fakeBlockBytes(seed)
                    if (node.statusManager.onBuiltBlock(rid, sig(node.index, seed), data)) {
                        node.currentBlockRid = rid
                        node.currentBlockData = data
                        progress++
                    }
                }

                is FetchProposedBlockIntent -> {
                    // 1. Local reload from PreparedBlockStore (Finding #4 fix)
                    val localData = node.statusManager.getLockedBlockData(intent.blockRID)
                    if (localData != null) {
                        if (node.statusManager.onReceivedBlock(intent.blockRID, sig(node.index, intent.blockRID[0].toInt()), localData)) {
                            node.currentBlockRid = intent.blockRID
                            node.currentBlockData = localData
                            progress++
                        }
                    } else {
                        // 2. Peer fetch from any validator holding the proposed block in memory
                        val peer = cluster.firstOrNull {
                            it.currentBlockRid != null && it.currentBlockRid!!.contentEquals(intent.blockRID) && it.currentBlockData != null
                        }
                        if (peer != null) {
                            if (node.statusManager.onReceivedBlock(intent.blockRID, sig(node.index, intent.blockRID[0].toInt()), peer.currentBlockData)) {
                                node.currentBlockRid = intent.blockRID
                                node.currentBlockData = peer.currentBlockData
                                progress++
                            }
                        }
                    }
                }

                is CommitBlockIntent -> {
                    if (allowCommit) {
                        val rid = node.currentBlockRid ?: error("Node ${node.index} has CommitBlockIntent without currentBlock")
                        val height = node.statusManager.myStatus.height
                        node.committedBlocks[height] = rid
                        node.statusManager.onCommittedBlock(rid)
                        node.currentBlockRid = null
                        node.currentBlockData = null
                        progress++
                    }
                }

                is FetchBlockAtHeightIntent -> {
                    val peerWithBlock = cluster.firstOrNull { it.committedBlocks.containsKey(intent.height) }
                    if (peerWithBlock != null) {
                        node.committedBlocks[intent.height] = peerWithBlock.committedBlocks[intent.height]!!
                        node.statusManager.onHeightAdvance(intent.height + 1)
                        node.currentBlockRid = null
                        node.currentBlockData = null
                        progress++
                    }
                }

                else -> Unit
            }
        }
        return progress
    }

    @Test
    fun `finding 4 - full 4-node cluster restart after Prepared recovers cleanly and commits without halting`() {
        val cluster = createCluster(4, startHeight = 10L) { true }
        var seed = 42

        // Phase 1: Reach Prepared(10, A) on all 4 nodes, stopping BEFORE commitBlock
        repeat(8) {
            stepIntents(cluster, { seed++ }, allowCommit = false)
            gossipStatuses(cluster)
        }
        val expectedBlockA = cluster[0].currentBlockRid!!
        for (node in cluster) {
            assertThat(node.statusManager.myStatus.state).isEqualTo(NodeBlockState.Prepared)
            assertThat(node.statusManager.myStatus.blockRID!!.contentEquals(expectedBlockA)).isTrue()
            assertThat(node.store!!.row).isNotNull()
            assertThat(node.store.row!!.blockData).isNotNull()
        }

        // Phase 2: Simultaneous cluster restart — ALL in-memory currentBlocks are wiped!
        cluster.forEach { it.restart(nextHeight = 10L) }
        for (node in cluster) {
            assertThat(node.currentBlockRid).isNull()
            assertThat(node.statusManager.myStatus.state).isEqualTo(NodeBlockState.WaitBlock)
            assertThat(node.statusManager.getLockedBlockData(expectedBlockA)).isNotNull()
        }

        // Phase 3: Cluster resumes — MUST reload A from persisted BlockData, re-reach Prepared, and commit to height 11!
        for (step in 0 until 20) {
            if (cluster.all { it.statusManager.myStatus.height >= 11L }) break
            stepIntents(cluster, { seed++ }, allowCommit = true)
            gossipStatuses(cluster)
            gossipSignatures(cluster)
        }

        for (node in cluster) {
            assertThat(node.statusManager.myStatus.height).isEqualTo(11L)
            assertThat(node.committedBlocks[10L]!!.contentEquals(expectedBlockA)).isTrue()
        }
    }

    @Test
    fun `finding 4 - partial quorum restart (f+1 locked, remaining unlocked) converges on locked block A without halting`() {
        val cluster = createCluster(4, startHeight = 10L) { true }
        var seed = 77

        // Reach Prepared(10, A) on all 4 nodes
        repeat(8) {
            stepIntents(cluster, { seed++ }, allowCommit = false)
            gossipStatuses(cluster)
        }
        val blockA = cluster[0].currentBlockRid!!

        // Simulate only f+1 (nodes 0 and 1) having persisted Prepared(10, A), while nodes 2 and 3 lost state/never prepared
        val lockedNodes = setOf(0, 1)
        for (node in cluster) {
            if (node.index !in lockedNodes) {
                // Create fresh node without a lock at height 10
                val freshStore = SingleRowPreparedBlockStore()
                node.statusManager = BaseStatusManager(
                        node.nodes,
                        node.index,
                        10L,
                        mockMetrics(),
                        mock<StateChangeTracker>(),
                        clock,
                        freshStore
                )
                node.currentBlockRid = null
                node.currentBlockData = null
            } else {
                node.restart(nextHeight = 10L)
            }
        }

        // Round 0 primary for height 10 is index (10 + 0) % 4 = 2 (an UNLOCKED node!),
        // which will build a conflicting block B. Locked nodes 0 and 1 must refuse B;
        // once a revolt moves to a locked primary, locked primary serves A and all 4 commit A!
        var committed = false
        for (cycle in 0 until 20) {
            stepIntents(cluster, { seed++ }, allowCommit = true)
            gossipStatuses(cluster)
            gossipSignatures(cluster)
            if (cluster.all { it.statusManager.myStatus.height == 11L }) {
                committed = true
                break
            }
            if (cycle % 4 == 3) {
                // Trigger coordinated revolt when stalled on unlocked primary's conflicting block B
                cluster.forEach { it.statusManager.onStartRevolting() }
                gossipStatuses(cluster)
            }
        }

        assertThat(committed).isTrue()
        for (node in cluster) {
            assertThat(node.statusManager.myStatus.height).isEqualTo(11L)
            assertThat(node.committedBlocks[10L]!!.contentEquals(blockA)).isTrue()
        }
    }

    @Test
    fun `finding 3 - 7-node f=2 cluster resists f Byzantine liars and syncs on f+1 honest peers`() {
        val nodes = makeNodes(7) // n=7, quorum=5, f=2, minHigherHeightCount = 7 - 5 + 1 = 3 (f+1)
        val store = SingleRowPreparedBlockStore()
        val manager = BaseStatusManager(
                nodes,
                0,
                20L,
                mockMetrics(),
                mock<StateChangeTracker>(),
                clock,
                store
        )
        val rid = blockRid(9)
        manager.myStatus.height = 20L
        manager.myStatus.state = NodeBlockState.Prepared
        manager.myStatus.blockRID = rid
        manager.myStatus.round = 0
        manager.commitSignatures[0] = sig(0, 9)

        // 2 Byzantine peers (<= f) claim height 21 -> MUST NOT trigger FetchBlockAtHeightIntent
        for (byz in 1..2) {
            manager.nodeStatuses[byz].height = 21L
            manager.nodeStatuses[byz].state = NodeBlockState.WaitBlock
        }
        manager.recomputeStatus()
        assertThat(manager.intent is FetchBlockAtHeightIntent).isFalse()

        // 3rd peer (f+1 = 3) moves to height 21 -> MUST trigger FetchBlockAtHeightIntent(20)
        manager.nodeStatuses[3].height = 21L
        manager.nodeStatuses[3].state = NodeBlockState.WaitBlock
        manager.recomputeStatus()
        assertThat(manager.intent).isEqualTo(FetchBlockAtHeightIntent(20L))
    }

    @Test
    fun `finding 3 - Prepared node with quorum commit signatures keeps CommitBlockIntent precedence`() {
        val nodes = makeNodes(4)
        val manager = BaseStatusManager(
                nodes,
                0,
                30L,
                mockMetrics(),
                mock<StateChangeTracker>(),
                clock,
                SingleRowPreparedBlockStore()
        )
        val rid = blockRid(11)
        manager.myStatus.height = 30L
        manager.myStatus.state = NodeBlockState.Prepared
        manager.myStatus.blockRID = rid
        manager.myStatus.round = 0
        // Quorum (3) commit signatures already present
        for (i in 0..2) manager.commitSignatures[i] = sig(i, 11)
        // Even if f+1 peers are already at height 31
        for (i in 1..2) manager.nodeStatuses[i].height = 31L

        manager.recomputeStatus()
        assertThat(manager.intent).isEqualTo(CommitBlockIntent)
    }

    @Test
    fun `mixed upgraded and legacy 4-node cluster advances cleanly across 5 heights including revolts`() {
        // Nodes 0, 1 upgraded (with PreparedBlockStore); Nodes 2, 3 legacy (store = null)
        val cluster = createCluster(4, startHeight = 1L) { idx -> idx < 2 }
        var seed = 1

        for (targetHeight in 2L..6L) {
            if (targetHeight == 4L) {
                // Inject a revolt mid-run before block 3 completes
                cluster.forEach { it.statusManager.onStartRevolting() }
                gossipStatuses(cluster)
            }
            for (step in 0 until 20) {
                if (cluster.all { it.statusManager.myStatus.height >= targetHeight }) break
                stepIntents(cluster, { seed++ }, allowCommit = true)
                gossipStatuses(cluster)
                gossipSignatures(cluster)
            }
            for (node in cluster) {
                assertThat(node.statusManager.myStatus.height).isEqualTo(targetHeight)
            }
        }
        // Verify all 4 nodes committed identical blockRIDs at every height 1..5
        for (h in 1L..5L) {
            val expected = cluster[0].committedBlocks[h]
            assertThat(expected).isNotNull()
            for (node in cluster) {
                assertThat(node.committedBlocks[h]!!.contentEquals(expected!!)).isTrue()
            }
        }
    }

    @Test
    fun `BlockDataDecoder encodes and decodes Prepared BlockData round-trip`() {
        val rawHeader = byteArrayOf(10, 20, 30, 40)
        val txs = listOf(byteArrayOf(1, 2, 3), byteArrayOf(4, 5))
        val header = mock<BlockHeader> {
            on { rawData } doReturn rawHeader
            on { blockRID } doReturn blockRid(55)
        }
        val bcConfig = mock<BlockchainConfiguration> {
            on { decodeBlockHeader(any()) } doReturn header
        }
        val original = BlockData(header, txs)

        val encoded = BlockDataDecoder.encodeBlockData(original)
        val decoded = BlockDataDecoder.decodeEncodedBlockData(encoded, bcConfig)

        assertThat(decoded).isNotNull()
        assertThat(decoded!!.header.blockRID).isEqualTo(blockRid(55))
        assertThat(decoded.transactions.size).isEqualTo(2)
        assertThat(decoded.transactions[0]).isEqualTo(txs[0])
        assertThat(decoded.transactions[1]).isEqualTo(txs[1])
    }
}
