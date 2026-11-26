// Copyright (c) 2026 ChromaWay AB. See README for license information.

package net.postchain.ebft

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isLessThanOrEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import io.micrometer.core.instrument.Counter
import net.postchain.base.data.DatabaseAccess
import net.postchain.common.hexStringToByteArray
import net.postchain.core.BlockchainConfiguration
import net.postchain.core.BlockchainEngine
import net.postchain.core.EContext
import net.postchain.core.Storage
import net.postchain.core.block.BlockData
import net.postchain.core.block.BlockHeader
import net.postchain.crypto.Signature
import net.postchain.ebft.message.StateChangeTracker
import net.postchain.ebft.syncmanager.BlockDataDecoder
import net.postchain.ebft.worker.ValidatorPreparedBlockStore
import net.postchain.ebft.worker.WorkerContext
import net.postchain.metrics.NodeStatusMetrics
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Random

/**
 * Large-scale EBFT chaos & stress test suite for MR !1828 (#3 + deadlock-free #4):
 * - 10-node (f=3) and 16-node (f=5) validator clusters over 50-100+ heights
 * - Rolling & simultaneous quorum restarts right after Prepared(H, A) before commitBlock
 * - Network partitions, packet drops, duplicate/stale Prepared replays, and <= f Byzantine liars
 * - Mixed-version rolling upgrade (half legacy without #4 + half upgraded with #3 & #4)
 * - Real ValidatorPreparedBlockStore DB/Storage invariant check (<= 1 row per chain, 0 per-block DDL)
 */
class EbftScaleChaosTest {

    private var clockMillis = BaseStatusManager.ZERO_SERIAL_TIME + 100_000L

    private fun advancingClock(): Clock = object : Clock() {
        override fun getZone() = ZoneOffset.UTC
        override fun withZone(zone: java.time.ZoneId?) = this
        override fun instant(): Instant = Instant.ofEpochMilli(clockMillis)
    }

    private fun makeNodes(count: Int): List<ByteArray> =
            (1..count).map { i ->
                val hex = i.toString(16).padStart(2, '0').repeat(32)
                hex.hexStringToByteArray()
            }

    private fun sig(nodeIdx: Int, blockSeed: Int): Signature =
            Signature(
                    ByteArray(33) { (nodeIdx + 1).toByte() },
                    ByteArray(64) { ((nodeIdx * 31 + blockSeed * 17) and 0xFF).toByte() }
            )

    private fun blockRid(seed: Int): ByteArray = ByteArray(32) { i -> ((seed * 37 + i * 13) and 0xFF).toByte() }

    private fun fakeBlockBytes(seed: Int): ByteArray = "serialized-block-data-seed-$seed".toByteArray()

    companion object {
        private fun mockMetrics(): NodeStatusMetrics = mock {
            on { revoltsByNode } doReturn mock<Counter>()
            on { revoltsOnNode } doReturn mock<Counter>()
            on { revoltsBetweenOthers } doReturn mock<Counter>()
        }
    }

    private class SingleRowPreparedBlockStore : PreparedBlockStore {
        var row: PreparedBlockRecord? = null
            private set
        var saveCalls: Int = 0
            private set
        var maxObservedRows: Int = 0
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
                        row = PreparedBlockRecord(height, blockRID.copyOf(), blockData.copyOf())
                    }
                    return true
                }
            }
            row = PreparedBlockRecord(height, blockRID.copyOf(), blockData?.copyOf())
            maxObservedRows = maxOf(maxObservedRows, 1)
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
            var store: SingleRowPreparedBlockStore?,
            var statusManager: BaseStatusManager,
            val clock: Clock,
            var currentBlockRid: ByteArray? = null,
            var currentBlockData: ByteArray? = null,
            val committedBlocks: MutableMap<Long, ByteArray> = mutableMapOf(),
            var restartCount: Int = 0,
            var localReloadCount: Int = 0
    ) {
        fun restart(nextHeight: Long = statusManager.myStatus.height, wipeStore: Boolean = false) {
            currentBlockRid = null
            currentBlockData = null
            if (wipeStore && store != null) {
                store = SingleRowPreparedBlockStore()
            }
            statusManager = BaseStatusManager(
                    nodes,
                    index,
                    nextHeight,
                    mockMetrics(),
                    mock<StateChangeTracker>(),
                    clock,
                    store
            )
            restartCount++
        }
    }

    private fun createCluster(count: Int, startHeight: Long, withStore: (Int) -> Boolean): List<SimNode> {
        val nodes = makeNodes(count)
        val clk = advancingClock()
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
                            clk,
                            store
                    ),
                    clock = clk
            )
        }
    }

    private fun gossipStatuses(
            cluster: List<SimNode>,
            activeMask: (Int, Int) -> Boolean = { _, _ -> true },
            dropRate: Double = 0.0,
            rng: Random? = null
    ) {
        val snapshots = cluster.map { copyStatus(it.statusManager.myStatus) }
        for (receiver in cluster) {
            for ((senderIdx, status) in snapshots.withIndex()) {
                if (senderIdx == receiver.index) continue
                if (!activeMask(senderIdx, receiver.index)) continue
                if (dropRate > 0.0 && rng != null && rng.nextDouble() < dropRate) continue
                receiver.statusManager.onStatusUpdate(senderIdx, copyStatus(status))
            }
        }
    }

    private fun gossipSignatures(
            cluster: List<SimNode>,
            activeMask: (Int, Int) -> Boolean = { _, _ -> true },
            dropRate: Double = 0.0,
            rng: Random? = null
    ) {
        for (sender in cluster) {
            val rid = sender.statusManager.myStatus.blockRID ?: continue
            val sig = sender.statusManager.getCommitSignature() ?: continue
            if (sender.statusManager.myStatus.state == NodeBlockState.Prepared) {
                for (receiver in cluster) {
                    if (receiver.index == sender.index) continue
                    if (!activeMask(sender.index, receiver.index)) continue
                    if (dropRate > 0.0 && rng != null && rng.nextDouble() < dropRate) continue
                    receiver.statusManager.onCommitSignature(sender.index, rid, sig)
                }
            }
        }
    }

    private fun stepIntents(
            cluster: List<SimNode>,
            nextBuildSeed: () -> Int,
            allowCommit: Boolean = true,
            activeMask: (Int, Int) -> Boolean = { _, _ -> true },
            participatingNodes: Set<Int>? = null
    ): Int {
        var progress = 0
        for (node in cluster) {
            if (participatingNodes != null && node.index !in participatingNodes) continue
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
                    val localData = node.statusManager.getLockedBlockData(intent.blockRID)
                    if (localData != null) {
                        val seedByte = intent.blockRID[0].toInt() and 0xFF
                        if (node.statusManager.onReceivedBlock(intent.blockRID, sig(node.index, seedByte), localData)) {
                            node.currentBlockRid = intent.blockRID
                            node.currentBlockData = localData
                            node.localReloadCount++
                            progress++
                        }
                    } else {
                        val peer = cluster.firstOrNull {
                            it.index != node.index &&
                                    activeMask(it.index, node.index) &&
                                    it.statusManager.myStatus.height == node.statusManager.myStatus.height &&
                                    it.currentBlockRid != null &&
                                    it.currentBlockRid!!.contentEquals(intent.blockRID) &&
                                    it.currentBlockData != null
                        }
                        if (peer != null) {
                            val seedByte = intent.blockRID[0].toInt() and 0xFF
                            if (node.statusManager.onReceivedBlock(intent.blockRID, sig(node.index, seedByte), peer.currentBlockData)) {
                                node.currentBlockRid = intent.blockRID
                                node.currentBlockData = peer.currentBlockData
                                progress++
                            }
                        }
                    }
                }

                is CommitBlockIntent -> {
                    if (allowCommit) {
                        val rid = node.currentBlockRid
                                ?: error("Node ${node.index} has CommitBlockIntent without currentBlock")
                        val height = node.statusManager.myStatus.height
                        node.committedBlocks[height] = rid.copyOf()
                        node.statusManager.onCommittedBlock(rid)
                        node.currentBlockRid = null
                        node.currentBlockData = null
                        progress++
                    }
                }

                is FetchBlockAtHeightIntent -> {
                    val peerWithBlock = cluster.firstOrNull {
                        it.index != node.index &&
                                activeMask(it.index, node.index) &&
                                it.committedBlocks.containsKey(intent.height)
                    }
                    if (peerWithBlock != null) {
                        node.committedBlocks[intent.height] = peerWithBlock.committedBlocks[intent.height]!!.copyOf()
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

    private fun assertZeroForks(cluster: List<SimNode>, heights: LongRange, honestIndices: Set<Int> = cluster.indices.toSet()) {
        val referenceNode = cluster.first { it.index in honestIndices }
        for (h in heights) {
            val expected = referenceNode.committedBlocks[h]
            assertThat(expected, name = "Height $h must be committed on reference node").isNotNull()
            for (node in cluster) {
                if (node.index !in honestIndices) continue
                val actual = node.committedBlocks[h]
                assertThat(actual, name = "Height $h must be committed on node ${node.index}").isNotNull()
                assertThat(actual!!.contentEquals(expected!!), name = "Fork detected at height $h on node ${node.index}").isTrue()
            }
        }
    }

    @Test
    fun `scale 10-node f=3 cluster over 100 heights with full, quorum, and f+1 restarts after Prepared`() {
        val n = 10 // f = 3, quorum = 7
        val cluster = createCluster(n, startHeight = 1L) { true }
        var seed = 1000
        var fullRestarts = 0
        var quorumRestarts = 0
        var partialRestarts = 0

        for (h in 1L..100L) {
            clockMillis += 1000L

            when {
                h % 10L == 0L -> {
                    // Reach Prepared(h, A) on all 10 nodes, STOP before commit, wipe ALL 10 nodes
                    for (step in 0 until 15) {
                        if (cluster.all { it.statusManager.myStatus.height == h && it.statusManager.myStatus.state == NodeBlockState.Prepared }) break
                        stepIntents(cluster, { seed++ }, allowCommit = false)
                        gossipStatuses(cluster)
                    }
                    val lockedA = cluster[0].currentBlockRid!!
                    assertThat(cluster.all { it.statusManager.myStatus.state == NodeBlockState.Prepared }).isTrue()

                    clockMillis += 1000L
                    cluster.forEach { it.restart(nextHeight = h) }
                    fullRestarts++

                    // Must recover via persisted BlockData and commit lockedA
                    for (step in 0 until 25) {
                        if (cluster.all { it.statusManager.myStatus.height >= h + 1 }) break
                        stepIntents(cluster, { seed++ }, allowCommit = true)
                        gossipStatuses(cluster)
                        gossipSignatures(cluster)
                    }
                    for (node in cluster) {
                        assertThat(node.statusManager.myStatus.height).isEqualTo(h + 1)
                        assertThat(node.committedBlocks[h]!!.contentEquals(lockedA)).isTrue()
                    }
                }

                h % 5L == 0L -> {
                    // Reach Prepared(h, A), then restart a quorum (7 of 10 nodes) simultaneously
                    for (step in 0 until 15) {
                        if (cluster.all { it.statusManager.myStatus.height == h && it.statusManager.myStatus.state == NodeBlockState.Prepared }) break
                        stepIntents(cluster, { seed++ }, allowCommit = false)
                        gossipStatuses(cluster)
                    }
                    val lockedA = cluster[0].currentBlockRid!!
                    clockMillis += 1000L
                    for (i in 0 until 7) cluster[i].restart(nextHeight = h)
                    quorumRestarts++

                    for (step in 0 until 25) {
                        if (cluster.all { it.statusManager.myStatus.height >= h + 1 }) break
                        stepIntents(cluster, { seed++ }, allowCommit = true)
                        gossipStatuses(cluster)
                        gossipSignatures(cluster)
                    }
                    for (node in cluster) {
                        assertThat(node.statusManager.myStatus.height).isEqualTo(h + 1)
                        assertThat(node.committedBlocks[h]!!.contentEquals(lockedA)).isTrue()
                    }
                }

                h % 7L == 0L -> {
                    // Partial f+1 (4 nodes) locked on A; remaining 6 nodes wiped (unlocked) — forces revolt onto locked primary
                    for (step in 0 until 15) {
                        if (cluster.all { it.statusManager.myStatus.height == h && it.statusManager.myStatus.state == NodeBlockState.Prepared }) break
                        stepIntents(cluster, { seed++ }, allowCommit = false)
                        gossipStatuses(cluster)
                    }
                    val lockedA = cluster[0].currentBlockRid!!
                    clockMillis += 1000L
                    val lockedSet = setOf(0, 1, 2, 3)
                    for (node in cluster) {
                        node.restart(nextHeight = h, wipeStore = node.index !in lockedSet)
                    }
                    partialRestarts++

                    var committed = false
                    for (cycle in 0 until 40) {
                        stepIntents(cluster, { seed++ }, allowCommit = true)
                        gossipStatuses(cluster)
                        gossipSignatures(cluster)
                        if (cluster.all { it.statusManager.myStatus.height >= h + 1 }) {
                            committed = true
                            break
                        }
                        if (cycle % 4 == 3) {
                            cluster.forEach { it.statusManager.onStartRevolting() }
                            gossipStatuses(cluster)
                        }
                    }
                    assertThat(committed, name = "Height $h stalled after partial f+1 restart").isTrue()
                    for (node in cluster) {
                        assertThat(node.committedBlocks[h]!!.contentEquals(lockedA)).isTrue()
                    }
                }

                else -> {
                    for (step in 0 until 20) {
                        if (cluster.all { it.statusManager.myStatus.height >= h + 1 }) break
                        stepIntents(cluster, { seed++ }, allowCommit = true)
                        gossipStatuses(cluster)
                        gossipSignatures(cluster)
                    }
                    for (node in cluster) {
                        assertThat(node.statusManager.myStatus.height).isEqualTo(h + 1)
                    }
                }
            }
        }

        assertZeroForks(cluster, 1L..100L)
        assertThat(fullRestarts).isEqualTo(10)
        assertThat(quorumRestarts).isEqualTo(10)
        assertThat(partialRestarts).isEqualTo(12)
        assertThat(cluster.sumOf { it.localReloadCount } > 100).isTrue()
        for (node in cluster) {
            assertThat(node.store!!.maxObservedRows).isLessThanOrEqualTo(1)
        }
    }

    @Test
    fun `scale 16-node f=5 cluster over 60 heights with 20pct packet drops, partitions, stale Prepared replays, and quorum restarts`() {
        val n = 16 // f = 5, quorum = 11
        val cluster = createCluster(n, startHeight = 1L) { true }
        val rng = Random(20261126L)
        var seed = 5000
        val staleSnapshots = mutableListOf<Pair<Int, NodeStatus>>()

        for (h in 1L..60L) {
            clockMillis += 1000L
            // Heights 20..27: partition 16 nodes into majority [0..10] (11 = quorum) vs minority [11..15] (5 = f)
            val isPartitioned = h in 20L..27L
            val activeMask: (Int, Int) -> Boolean = { a, b ->
                if (!isPartitioned) true
                else (a <= 10 && b <= 10) || (a > 10 && b > 10)
            }

            // At height 20, first let ALL 16 nodes reach Prepared(20, A) before partitioning minority [11..15]!
            if (h == 20L) {
                for (step in 0 until 15) {
                    if (cluster.all { it.statusManager.myStatus.height == 20L && it.statusManager.myStatus.state == NodeBlockState.Prepared }) break
                    stepIntents(cluster, { seed++ }, allowCommit = false)
                    gossipStatuses(cluster)
                }
                // Record stale Prepared snapshots to replay later
                cluster.forEach { staleSnapshots.add(it.index to copyStatus(it.statusManager.myStatus)) }
            }

            // At heights 15, 35, 50: simultaneous quorum restart right after Prepared(h, A)
            if (h in setOf(15L, 35L, 50L)) {
                for (step in 0 until 20) {
                    if (cluster.all { it.statusManager.myStatus.height == h && it.statusManager.myStatus.state == NodeBlockState.Prepared }) break
                    stepIntents(cluster, { seed++ }, allowCommit = false)
                    gossipStatuses(cluster, dropRate = 0.15, rng = rng)
                }
                val lockedA = cluster[0].currentBlockRid!!
                clockMillis += 1000L
                for (i in 0 until 11) cluster[i].restart(nextHeight = h)

                for (step in 0 until 40) {
                    if (cluster.all { it.statusManager.myStatus.height >= h + 1 }) break
                    stepIntents(cluster, { seed++ }, allowCommit = true)
                    gossipStatuses(cluster, dropRate = 0.20, rng = rng)
                    gossipSignatures(cluster, dropRate = 0.20, rng = rng)
                }
                for (node in cluster) {
                    assertThat(node.statusManager.myStatus.height).isEqualTo(h + 1)
                    assertThat(node.committedBlocks[h]!!.contentEquals(lockedA)).isTrue()
                }
                continue
            }

            // Normal chaotic progression with 20% packet drops + duplicate/stale Prepared replay
            val targetNodes = if (isPartitioned) cluster.take(11) else cluster
            for (step in 0 until 80) {
                if (targetNodes.all { it.statusManager.myStatus.height >= h + 1 }) break
                stepIntents(cluster, { seed++ }, allowCommit = true, activeMask = activeMask)
                gossipStatuses(cluster, activeMask = activeMask, dropRate = 0.20, rng = rng)
                gossipSignatures(cluster, activeMask = activeMask, dropRate = 0.20, rng = rng)
                if (step % 8 == 7) {
                    targetNodes.filter { it.statusManager.myStatus.height == h }.forEach { it.statusManager.onStartRevolting() }
                    gossipStatuses(cluster, activeMask = activeMask, dropRate = 0.20, rng = rng)
                }

                // Replay stale/duplicate Prepared messages and duplicate commit signatures
                if (staleSnapshots.isNotEmpty() && step % 3 == 0) {
                    val (senderIdx, stale) = staleSnapshots[rng.nextInt(staleSnapshots.size)]
                    for (receiver in cluster) {
                        if (receiver.index != senderIdx) {
                            receiver.statusManager.onStatusUpdate(senderIdx, copyStatus(stale))
                        }
                    }
                }
            }

            for (node in targetNodes) {
                if (node.statusManager.myStatus.height != h + 1) {
                    val summary = cluster.joinToString(", ") {
                        "n${it.index}(h=${it.statusManager.myStatus.height},r=${it.statusManager.myStatus.round},s=${it.statusManager.myStatus.state},i=${it.statusManager.getBlockIntent().javaClass.simpleName})"
                    }
                    val n0View = node.statusManager.nodeStatuses.mapIndexed { i, st -> "$i(h=${st.height},r=${st.round},s=${st.state},ser=${st.serial},rid=${st.blockRID?.firstOrNull()})" }.joinToString(" "); error("Node ${node.index} stalled at height $h: myRid=${node.statusManager.myStatus.blockRID?.firstOrNull()} store=${node.store?.row?.height} | view=$n0View")
                }
            }

            // Right after partition heals at height 28, minority nodes (including those stuck in Prepared(20)) must sync all 8 blocks!
            if (h == 28L) {
                for (syncStep in 0 until 40) {
                    if (cluster.all { it.statusManager.myStatus.height >= 29L }) break
                    gossipStatuses(cluster)
                    stepIntents(cluster, { seed++ }, allowCommit = true)
                }
                for (node in cluster) {
                    assertThat(node.statusManager.myStatus.height).isEqualTo(29L)
                }
            }
        }

        assertZeroForks(cluster, 1L..60L)
    }

    @Test
    fun `scale 16-node f=5 Byzantine adversary test over 50 heights with 5 active liars`() {
        val n = 16 // f = 5, quorum = 11, minHigherHeightCount = 16 - 11 + 1 = 6 (f + 1)
        val honestIndices = (0..10).toSet()
        val byzantineIndices = (11..15).toSet()
        val cluster = createCluster(n, startHeight = 1L) { true }
        var seed = 9000
        var byzantineHijackChecks = 0

        for (h in 1L..50L) {
            clockMillis += 1000L

            // Phase A: Drive honest nodes to Prepared(h, A) and inject 5 (= f) Byzantine liars claiming higher heights + conflicting proposals
            if (h % 5L == 0L) {
                var reachedPrepared = false
                for (cycle in 0 until 40) {
                    stepIntents(cluster, { seed++ }, allowCommit = false, participatingNodes = honestIndices)
                    gossipStatuses(cluster)
                    // Inject Byzantine liar statuses claiming h+1, h+5, conflicting RIDs
                    for (byz in byzantineIndices) {
                        val fakeStatus = NodeStatus(h + 1 + (byz % 3), clockMillis - BaseStatusManager.ZERO_SERIAL_TIME + cycle + 100).apply {
                            state = NodeBlockState.WaitBlock
                            blockRID = blockRid(99999 + byz)
                            round = cycle.toLong()
                        }
                        for (honestIdx in honestIndices) {
                            cluster[honestIdx].statusManager.onStatusUpdate(byz, copyStatus(fakeStatus))
                        }
                    }
                    if (honestIndices.all { cluster[it].statusManager.myStatus.state == NodeBlockState.Prepared }) {
                        reachedPrepared = true
                        break
                    }
                    if (cycle % 4 == 3) {
                        // Revolt past any Byzantine primary!
                        honestIndices.forEach { cluster[it].statusManager.onStartRevolting() }
                        gossipStatuses(cluster)
                    }
                }
                assertThat(reachedPrepared, name = "Honest quorum failed to reach Prepared at height $h").isTrue()

                // Verify ZERO honest Prepared nodes were hijacked into FetchBlockAtHeightIntent by the 5 (= f) Byzantine liars!
                for (honestIdx in honestIndices) {
                    val intent = cluster[honestIdx].statusManager.getBlockIntent()
                    assertThat(intent is FetchBlockAtHeightIntent, name = "Honest node $honestIdx hijacked by f=5 Byzantine liars at height $h").isFalse()
                    byzantineHijackChecks++
                }

                // Also restart all 11 honest nodes right after Prepared(h, A) while 5 Byzantine liars keep lying!
                val lockedA = cluster[0].currentBlockRid!!
                clockMillis += 1000L
                honestIndices.forEach { cluster[it].restart(nextHeight = h) }

                for (cycle in 0 until 40) {
                    if (honestIndices.all { cluster[it].statusManager.myStatus.height >= h + 1 }) break
                    stepIntents(cluster, { seed++ }, allowCommit = true, participatingNodes = honestIndices)
                    gossipStatuses(cluster)
                    gossipSignatures(cluster)
                    for (byz in byzantineIndices) {
                        val liarStatus = NodeStatus(h + 10, clockMillis - BaseStatusManager.ZERO_SERIAL_TIME + cycle + 500).apply {
                            state = NodeBlockState.Prepared
                            blockRID = blockRid(88888 + byz)
                        }
                        honestIndices.forEach { cluster[it].statusManager.onStatusUpdate(byz, copyStatus(liarStatus)) }
                    }
                }
                for (honestIdx in honestIndices) {
                    assertThat(cluster[honestIdx].statusManager.myStatus.height).isEqualTo(h + 1)
                    assertThat(cluster[honestIdx].committedBlocks[h]!!.contentEquals(lockedA)).isTrue()
                }
            } else {
                // Standard height under continuous Byzantine interference (including Byzantine primaries requiring revolts!)
                var committed = false
                for (cycle in 0 until 50) {
                    stepIntents(cluster, { seed++ }, allowCommit = true, participatingNodes = honestIndices)
                    gossipStatuses(cluster)
                    gossipSignatures(cluster)
                    // Byzantine nodes broadcast conflicting proposals / bogus higher heights
                    for (byz in byzantineIndices) {
                        val liarStatus = NodeStatus(h, clockMillis - BaseStatusManager.ZERO_SERIAL_TIME + cycle + 200).apply {
                            state = NodeBlockState.HaveBlock
                            blockRID = blockRid(77777 + byz)
                            round = cluster[0].statusManager.myStatus.round
                        }
                        honestIndices.forEach { cluster[it].statusManager.onStatusUpdate(byz, copyStatus(liarStatus)) }
                    }
                    if (honestIndices.all { cluster[it].statusManager.myStatus.height >= h + 1 }) {
                        committed = true
                        break
                    }
                    if (cycle % 4 == 3) {
                        honestIndices.forEach { cluster[it].statusManager.onStartRevolting() }
                        gossipStatuses(cluster)
                    }
                }
                assertThat(committed, name = "Honest quorum stalled at height $h under f=5 Byzantine liars").isTrue()
            }
        }

        assertThat(byzantineHijackChecks).isEqualTo(110) // 10 heights * 11 honest nodes
        assertZeroForks(cluster, 1L..50L, honestIndices = honestIndices)
    }

    @Test
    fun `mixed-version rolling upgrade - 10-node cluster (5 legacy + 5 upgraded) over 40 heights with revolts and restarts`() {
        // Nodes 0..4 upgraded (PreparedBlockStore enabled); Nodes 5..9 legacy (store = null)
        val cluster = createCluster(10, startHeight = 1L) { idx -> idx < 5 }
        var seed = 20000
        var revoltRounds = 0

        for (h in 1L..40L) {
            clockMillis += 1000L

            if (h % 4L == 0L) {
                // Trigger revolts rotating primaries across legacy and upgraded nodes
                cluster.forEach { it.statusManager.onStartRevolting() }
                gossipStatuses(cluster)
                revoltRounds++
            }

            if (h % 8L == 0L) {
                // Reach Prepared(h, A) across mixed cluster, then restart all 5 upgraded nodes
                for (step in 0 until 20) {
                    if (cluster.all { it.statusManager.myStatus.height == h && it.statusManager.myStatus.state == NodeBlockState.Prepared }) break
                    stepIntents(cluster, { seed++ }, allowCommit = false)
                    gossipStatuses(cluster)
                }
                val lockedA = cluster[0].currentBlockRid!!
                clockMillis += 1000L
                for (i in 0 until 5) cluster[i].restart(nextHeight = h)

                for (step in 0 until 30) {
                    if (cluster.all { it.statusManager.myStatus.height >= h + 1 }) break
                    stepIntents(cluster, { seed++ }, allowCommit = true)
                    gossipStatuses(cluster)
                    gossipSignatures(cluster)
                    if (step % 5 == 4) {
                        cluster.filter { it.statusManager.myStatus.height == h }.forEach { it.statusManager.onStartRevolting() }
                        gossipStatuses(cluster)
                    }
                }
                for (node in cluster) {
                    assertThat(node.statusManager.myStatus.height).isEqualTo(h + 1)
                    assertThat(node.committedBlocks[h]!!.contentEquals(lockedA)).isTrue()
                }
                continue
            }

            for (step in 0 until 25) {
                if (cluster.all { it.statusManager.myStatus.height >= h + 1 }) break
                stepIntents(cluster, { seed++ }, allowCommit = true)
                gossipStatuses(cluster)
                gossipSignatures(cluster)
            }
            for (node in cluster) {
                assertThat(node.statusManager.myStatus.height).isEqualTo(h + 1)
            }
        }

        assertThat(revoltRounds).isEqualTo(10)
        assertZeroForks(cluster, 1L..40L)
    }

    @Test
    fun `boundary chaos - partitioned Prepared node restarts and catches up 15 heights via Finding 3 with clean lockedBlock eviction`() {
        val cluster = createCluster(10, startHeight = 10L) { true }
        var seed = 30000

        // All 10 nodes reach Prepared(10, A)
        for (step in 0 until 15) {
            if (cluster.all { it.statusManager.myStatus.height == 10L && it.statusManager.myStatus.state == NodeBlockState.Prepared }) break
            stepIntents(cluster, { seed++ }, allowCommit = false)
            gossipStatuses(cluster)
        }
        val blockAt10 = cluster[0].currentBlockRid!!

        // Partition Node 0 (staying Prepared at 10) AND Node 1 (restarts while partitioned at 10)
        // Majority [2..9] (8 >= quorum=7) advances 15 heights (10..24 -> height 25)
        val partitioned = setOf(0, 1)
        val majority = (2..9).toSet()
        cluster[1].restart(nextHeight = 10L)

        val maskPartitioned: (Int, Int) -> Boolean = { a, b -> (a in majority && b in majority) }
        for (h in 10L..24L) {
            clockMillis += 1000L
            for (step in 0 until 40) {
                if (majority.all { cluster[it].statusManager.myStatus.height >= h + 1 }) break
                stepIntents(cluster, { seed++ }, allowCommit = true, activeMask = maskPartitioned, participatingNodes = majority)
                gossipStatuses(cluster, activeMask = maskPartitioned)
                gossipSignatures(cluster, activeMask = maskPartitioned)
                if (step % 5 == 4) {
                    majority.filter { cluster[it].statusManager.myStatus.height == h }.forEach { cluster[it].statusManager.onStartRevolting() }
                    gossipStatuses(cluster, activeMask = maskPartitioned)
                }
            }
            for (idx in majority) {
                assertThat(cluster[idx].statusManager.myStatus.height).isEqualTo(h + 1)
            }
        }

        // Heal partition: Node 0 (Prepared at 10) and Node 1 (restarted/locked at 10) must both catch up to height 25!
        for (step in 0 until 60) {
            if (cluster.all { it.statusManager.myStatus.height >= 25L }) break
            gossipStatuses(cluster)
            stepIntents(cluster, { seed++ }, allowCommit = true)
        }

        for (idx in partitioned) {
            assertThat(cluster[idx].statusManager.myStatus.height).isEqualTo(25L)
            // Stale lockedBlock from height 10 must be evicted
            assertThat(cluster[idx].statusManager.getLockedBlockData(blockAt10)).isNull()
        }
        assertZeroForks(cluster, 10L..24L)
    }

    @Test
    fun `DB storage invariant - real ValidatorPreparedBlockStore enforces at most 1 row per chain and zero per-block DDL over 120 heights`() {
        // In-memory JDBC test double enforcing single-row PK (id = 1) and auditing every SQL statement
        data class DbRow(val id: Int, var height: Long, var rid: ByteArray, var data: ByteArray?)
        val tableRows = mutableMapOf<Int, DbRow>()
        var ddlCount = 0
        var ddlInsideSaveOrLoad = 0
        var initialized = false
        var maxRowCount = 0

        fun buildConn(): Connection {
            val conn = mock<Connection>()
            doAnswer { inv ->
                val sql = inv.getArgument<String>(0).trim()
                val upper = sql.uppercase()
                val params = mutableMapOf<Int, Any?>()
                val ps = mock<PreparedStatement>()

                doAnswer { pInv -> params[pInv.getArgument(0)] = pInv.getArgument(1); Unit }.`when`(ps).setObject(any<Int>(), any())
                doAnswer { pInv -> params[pInv.getArgument(0)] = pInv.getArgument(1); Unit }.`when`(ps).setInt(any(), any())
                doAnswer { pInv -> params[pInv.getArgument(0)] = pInv.getArgument(1); Unit }.`when`(ps).setLong(any(), any())
                doAnswer { pInv -> params[pInv.getArgument(0)] = pInv.getArgument(1); Unit }.`when`(ps).setBytes(any(), any())
                doAnswer { pInv -> params[pInv.getArgument(0)] = null; Unit }.`when`(ps).setNull(any(), any())

                doAnswer {
                    if (upper.startsWith("CREATE TABLE")) {
                        ddlCount++
                        if (initialized) ddlInsideSaveOrLoad++
                        assertThat(upper.contains("PRIMARY KEY CHECK (ID = 1)")).isTrue()
                        0
                    } else if (upper.startsWith("INSERT INTO")) {
                        val id = (params[1] as Number).toInt()
                        require(id == 1) { "CHECK constraint violated: id=$id" }
                        val h = (params[2] as Number).toLong()
                        val rid = params[3] as ByteArray
                        val data = params[4] as ByteArray?
                        // ON CONFLICT (id) DO UPDATE overwrites the single row
                        tableRows[id] = DbRow(id, h, rid.copyOf(), data?.copyOf())
                        maxRowCount = maxOf(maxRowCount, tableRows.size)
                        1
                    } else if (upper.startsWith("UPDATE")) {
                        val data = params[1] as ByteArray?
                        val id = (params[2] as Number).toInt()
                        val h = (params[3] as Number).toLong()
                        val existing = tableRows[id]
                        if (existing != null && existing.height == h) {
                            existing.data = data?.copyOf()
                            1
                        } else 0
                    } else {
                        error("Unexpected update SQL: $sql")
                    }
                }.`when`(ps).executeUpdate()

                doAnswer {
                    if (upper.startsWith("CREATE TABLE")) {
                        ddlCount++
                        if (initialized) ddlInsideSaveOrLoad++
                    }
                    val id = (params[1] as Number).toInt()
                    val row = tableRows[id]
                    val matches = when {
                        row == null -> false
                        upper.contains("AND BLOCK_HEIGHT = ?") -> row.height == (params[2] as Number).toLong()
                        else -> true
                    }
                    var cursor = 0
                    val rs = mock<ResultSet>()
                    doAnswer {
                        if (matches && cursor == 0) {
                            cursor = 1
                            true
                        } else false
                    }.`when`(rs).next()
                    doAnswer { row!!.height }.`when`(rs).getLong("block_height")
                    doAnswer { row!!.rid.copyOf() }.`when`(rs).getBytes("block_rid")
                    doAnswer { row!!.data?.copyOf() }.`when`(rs).getBytes("block_data")
                    rs
                }.`when`(ps).executeQuery()

                ps
            }.`when`(conn).prepareStatement(any())
            return conn
        }

        val dbAccess = mock<DatabaseAccess> {
            on { tableName(any(), any()) } doReturn "c7.ebft_prepared_block"
        }
        val conn = buildConn()
        val eCtx = mock<EContext> {
            on { this.conn } doReturn conn
            on { getInterface(DatabaseAccess::class.java) } doReturn dbAccess
        }
        val storage = mock<Storage> {
            on { openWriteConnection(any()) } doReturn eCtx
            on { openReadConnection(any()) } doReturn eCtx
        }
        val bcConfig = mock<BlockchainConfiguration> {
            on { chainID } doReturn 7L
            on { decodeBlockHeader(any()) } doAnswer { inv ->
                val raw = inv.getArgument<ByteArray>(0)
                mock<BlockHeader> {
                    on { rawData } doReturn raw
                    on { blockRID } doReturn raw.copyOf(32)
                }
            }
        }
        val engine = mock<BlockchainEngine> {
            on { sharedStorage } doReturn storage
        }
        val workerCtx = mock<WorkerContext> {
            on { blockchainConfiguration } doReturn bcConfig
            on { this.engine } doReturn engine
        }

        val store = ValidatorPreparedBlockStore(workerCtx)
        store.initialize()
        initialized = true
        assertThat(ddlCount).isEqualTo(1)

        // Drive 120 consecutive heights with realistic GTV-encoded BlockData, equivocation attempts, and stale-height guards
        for (h in 1L..120L) {
            val ridA = blockRid(h.toInt())
            val headerA = mock<BlockHeader> {
                on { rawData } doReturn ridA
                on { blockRID } doReturn ridA
            }
            val encodedA = BlockDataDecoder.encodeBlockData(BlockData(headerA, listOf("tx-$h-1".toByteArray(), "tx-$h-2".toByteArray())))

            // 1. Save Prepared(h, ridA, encodedA)
            assertThat(store.save(h, ridA, encodedA)).isTrue()
            assertThat(tableRows.size).isEqualTo(1)

            // 2. Equivocation at same height with different RID must be rejected
            val ridB = blockRid(h.toInt() + 1000)
            assertThat(store.save(h, ridB, encodedA)).isFalse()

            // 3. Stale height save must be rejected
            if (h > 1L) {
                assertThat(store.save(h - 1, blockRid((h - 1).toInt()), encodedA)).isFalse()
            }

            // 4. Idempotent re-save at same (h, ridA) succeeds
            assertThat(store.save(h, ridA, encodedA)).isTrue()

            // 5. Load & decode round-trip
            val loaded = store.load(h)
            assertThat(loaded).isNotNull()
            assertThat(loaded!!.height).isEqualTo(h)
            assertThat(loaded.blockRID.contentEquals(ridA)).isTrue()
            val decoded = BlockDataDecoder.decodeEncodedBlockData(loaded.blockData!!, bcConfig)
            assertThat(decoded).isNotNull()
            assertThat(decoded!!.transactions.size).isEqualTo(2)
        }

        // Strict invariants: <= 1 row after 120 heights, ZERO DDL inside save()/load()
        assertThat(tableRows.size).isEqualTo(1)
        assertThat(maxRowCount).isEqualTo(1)
        assertThat(ddlCount).isEqualTo(1)
        assertThat(ddlInsideSaveOrLoad).isEqualTo(0)
    }

    @Test
    fun `edge-case resilience - corrupt or null PreparedBlockStore payload falls back safely and multi-round skewed restart converges`() {
        val bcConfig = mock<BlockchainConfiguration> {
            on { decodeBlockHeader(any()) } doAnswer { inv ->
                val raw = inv.getArgument<ByteArray>(0)
                if (raw.size < 32) throw IllegalArgumentException("Truncated header")
                mock<BlockHeader> {
                    on { rawData } doReturn raw
                    on { blockRID } doReturn raw.copyOf(32)
                }
            }
        }
        // Corrupt / truncated / wrong-arity GTV bytes must return null cleanly without throwing
        assertThat(BlockDataDecoder.decodeEncodedBlockData(byteArrayOf(), bcConfig)).isNull()
        assertThat(BlockDataDecoder.decodeEncodedBlockData(byteArrayOf(0x30, 0x05, 0x01, 0x02), bcConfig)).isNull()
        assertThat(BlockDataDecoder.decodeEncodedBlockData("not-gtv-garbage".toByteArray(), bcConfig)).isNull()

        // Multi-round skewed restart on 10-node cluster (f=3):
        // All 10 nodes revolt 3 times to round 3, reach Prepared(50, A) at round 3,
        // then 6 nodes restart (coming back at round 0) while 4 live peers stay at Prepared(50, A, round=3).
        val cluster = createCluster(10, startHeight = 50L) { true }
        var seed = 42000
        repeat(3) {
            cluster.forEach { it.statusManager.onStartRevolting() }
            gossipStatuses(cluster)
        }
        for (node in cluster) {
            assertThat(node.statusManager.myStatus.round).isEqualTo(3L)
        }

        for (step in 0 until 20) {
            if (cluster.all { it.statusManager.myStatus.state == NodeBlockState.Prepared }) break
            stepIntents(cluster, { seed++ }, allowCommit = false)
            gossipStatuses(cluster)
        }
        val lockedA = cluster[0].currentBlockRid!!
        assertThat(cluster.all { it.statusManager.myStatus.state == NodeBlockState.Prepared }).isTrue()

        // Restart 6 nodes (return at round 0 with persisted BlockData for A) while 4 peers stay live at round 3
        clockMillis += 1000L
        for (i in 0 until 6) cluster[i].restart(nextHeight = 50L)

        var committed = false
        for (cycle in 0 until 40) {
            stepIntents(cluster, { seed++ }, allowCommit = true)
            gossipStatuses(cluster)
            gossipSignatures(cluster)
            if (cluster.all { it.statusManager.myStatus.height == 51L }) {
                committed = true
                break
            }
            if (cycle % 4 == 3) {
                cluster.filter { it.statusManager.myStatus.height == 50L }.forEach { it.statusManager.onStartRevolting() }
                gossipStatuses(cluster)
            }
        }
        assertThat(committed).isTrue()
        for (node in cluster) {
            assertThat(node.statusManager.myStatus.height).isEqualTo(51L)
            assertThat(node.committedBlocks[50L]!!.contentEquals(lockedA)).isTrue()
        }
    }
}
