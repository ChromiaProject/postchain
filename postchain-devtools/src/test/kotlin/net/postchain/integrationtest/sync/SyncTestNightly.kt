package net.postchain.integrationtest.sync

import mu.KLogging
import net.postchain.concurrent.util.get
import net.postchain.devtools.AbstractSyncTest
import net.postchain.ebft.worker.ForceReadOnlyBlockchainProcess
import net.postchain.ebft.worker.ValidatorBlockchainProcess
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.util.concurrent.TimeUnit

class SyncTestNightly : AbstractSyncTest() {

    companion object : KLogging() {

        @JvmStatic
        fun testArguments() = listOf(
                // Empty blockchain
                arrayOf(1, 1, setOf(0), setOf<Int>(), 0),
                arrayOf(1, 1, setOf(1), setOf<Int>(), 0),

                // Single block test
                arrayOf(1, 1, setOf(0), setOf<Int>(), 1),
                arrayOf(1, 1, setOf(1), setOf<Int>(), 1),
                arrayOf(1, 2, setOf(1), setOf(0), 1),
                arrayOf(2, 0, setOf(1), setOf<Int>(), 1),

                // Multi block test
                arrayOf(1, 1, setOf(0), setOf<Int>(), 50),
                arrayOf(1, 1, setOf(1), setOf<Int>(), 50),
                arrayOf(1, 2, setOf(1), setOf(0), 50),
                arrayOf(2, 0, setOf(1), setOf<Int>(), 50),

                // Multi node multi blocks
                arrayOf(4, 4, setOf(0, 1, 2, 4, 5), setOf(3, 6), 50)

        )
    }

    @ParameterizedTest
    @MethodSource("testArguments")
    fun sync(signers: Int, replicas: Int, syncIndex: Set<Int>, stopIndex: Set<Int>, blocksToSync: Int) {
        println("++ Sync Nightly, -------- Signs: $signers Repls: $replicas SyncIdx: ${syncIndex.size} StopIdx: ${stopIndex.size} blocks: $blocksToSync ------------------")
        runSyncTest(signers, replicas, syncIndex, stopIndex, blocksToSync)
    }

    @Test
    fun fastsyncHandlesDisconnectedNodes() {
        val nodeSetup = runNodes(4, 0)
        buildBlock(0, 1)

        // Shutdown node 1 and build a block
        nodes[1].shutdown()
        buildBlock(listOf(nodes[0], nodes[2], nodes[3]), 0, 2)

        // Shutdown node 2 and start node 1 again -> node 1 will enter fast sync
        nodes[2].shutdown()
        val peerInfoMap = nodeSetup[1].configurationProvider!!.getConfiguration().peerInfoMap
        startOldNode(1, peerInfoMap, nodes[1].getBlockchainRid(0)!!)

        // Build another block, node 1 must consider node 2 as disconnected in order to exit fast sync
        Awaitility.await().atMost(15, TimeUnit.SECONDS).until {
            buildBlock(listOf(nodes[0], nodes[1], nodes[3]), 0, 3)
            true
        }
    }

    @Test
    fun canSyncFromForcedReadOnlyNode() {
        val nodeSetup = runNodes(4, 0)
        buildBlock(0, 1)
        // Shutdown the 4th node
        nodes[3].shutdown()

        // Build a block without 4th node and shutdown all nodes
        val liveNodes = nodes.subList(0, 3)
        buildBlock(liveNodes, 0, 2)
        liveNodes.forEach { it.shutdown() }

        // Restart 1st node as a force read only process
        configOverrides.setProperty("readOnly", true)
        val peerInfoMap0 = nodeSetup[0].configurationProvider!!.getConfiguration().peerInfoMap
        startOldNode(0, peerInfoMap0, nodes[0].getBlockchainRid(0)!!)
        assertThat(nodes[0].getBlockchainInstance(0)).isInstanceOf(ForceReadOnlyBlockchainProcess::class.java)

        // Restart 4th node as validator
        configOverrides.setProperty("readOnly", false)
        val peerInfoMap3 = nodeSetup[3].configurationProvider!!.getConfiguration().peerInfoMap
        startOldNode(3, peerInfoMap3, nodes[3].getBlockchainRid(0)!!)
        assertThat(nodes[3].blockQueries(0).getLastBlockHeight().get()).isEqualTo(1)
        assertThat(nodes[3].getBlockchainInstance(0)).isInstanceOf(ValidatorBlockchainProcess::class.java)

        // Assert that 4th node can sync the second block from force read only node
        Awaitility.await().atMost(5, TimeUnit.SECONDS).untilAsserted {
            assertThat(nodes[3].blockQueries(0).getLastBlockHeight().get()).isEqualTo(2)
        }
    }
}
