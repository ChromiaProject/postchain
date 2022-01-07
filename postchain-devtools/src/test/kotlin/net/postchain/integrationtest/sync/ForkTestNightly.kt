package net.postchain.integrationtest.sync

import net.postchain.devtools.KeyPairHelper
import net.postchain.devtools.currentHeight
import net.postchain.devtools.utils.configuration.NodeSetup
import net.postchain.network.x.XPeerID
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.lang.Thread.sleep
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ForkTestNightly : ManagedModeTest() {

    // If you need a specific node to have a specific property, add it in here
    val extraNodeProperties = mutableMapOf<Int, Map<String, Any>>()

    @Test
    fun testSyncManagedBlockchain() {
        basicSystem()

        val c2 = startNewBlockchain(setOf(1), setOf(2), null)
        buildBlock(c2, 0)

        restartNodeClean(2, c2, -1)
        awaitHeight(c2, 0)
    }

    // Local sync

    @Test
    fun testForkLocallySimple() {
        val (c1, c2) = makeFork()
        assertEqualAtHeight(c1, c2, 10)
        // Can't build block until configuration without historic brid deployed
        assertCantBuildBlock(c2, 11)
    }

    @Test
    fun testForkLocallyAddConfCantBuild() {
        val (c1, c2) = makeFork()

        val c2_11 = addBlockchainConfiguration(c2, 11, setOf(0), setOf(1), c1.chain)
        buildBlock(c1, 11)
        awaitHeight(c2_11, 11)
        assertEqualAtHeight(c1, c2_11, 11)
        // Can't build block until configuration without historic brid deployed
        assertCantBuildBlock(c2, 12)
    }

    @Test
    fun testForkLocallyAddConfSameSignerCanBuild() {
        val (c1, c2) = makeFork()
        // c1 and c2 are in sync at height 10.

        val c2_12 = addBlockchainConfiguration(c2, 12, setOf(0), setOf(1), null)
        buildBlock(c1, 12)
        // Unfortunately, we must wait a full cycle of "synclocally (quick), fetch (2s), cross-fetch (2s)"
        // to be sure we don't sync from c1 after height 11. This sucks.
        sleep(5000)
        // Assert that c2_12 isn't syncing from c1 after height 11
        assertEquals(11, c2_12.nodes()[0].currentHeight(c2_12.chain))
        // Assert that c2_12 can build blocks on its own now
        buildBlock(c2_12, 12)
        assertNotEqualAtHeight(c1, c2_12, 12)
    }


    @Test
    fun testForkLocallySwapSignerReplica() {
        val c1 = basicSystem()
        val c2 = startNewBlockchain(setOf(1), setOf(0), c1.chain)
        sleep(1000)
        c2.nodes().forEach {
            assertEquals(-1, it.blockQueries(c2.chain).getBestHeight().get())
        }
    }

    // Network sync

    @Test
    fun testForkSignerCrossFetch() {
        startManagedSystem(2, 0)

        val c1 = startNewBlockchain(setOf(0), setOf(1), null)
        buildBlock(c1, 10)

        val expectedBlockRid = nodes[c1.all().first()].blockQueries(c1.chain).getBlockRid(10).get()

        // Make sure that node <node> doesn't have c1 locally and that the other node
        // doesn't have c2, We want node <node> to cross-fetch c2 from c1 on the other node instead of from local db.
        val nodeDataSource = dataSources(c1)[0]!!
        nodeDataSource.delBlockchain(chainRidOf(c1.chain))
        // Set node 1 as replica for c1 so that node 0 will use node 1 to cross-fetch blocks.
        nodeDataSource.addExtraReplica(chainRidOf(c1.chain), XPeerID(KeyPairHelper.pubKey(1)))

        restartNodeClean(0, c0, -1)
        val c2 = startNewBlockchain(setOf(0), setOf(), c1.chain)
        awaitHeight(c2, 10)
        c2.all().forEach {
           assertArrayEquals(nodes[it].blockQueries(c2.chain).getBlockRid(10).get(), expectedBlockRid)
        }
        assertCantBuildBlock(c2, 11)
    }

    @Test
    fun testReplicaFetch() {
        startManagedSystem(2, 0)

        val c1 = startNewBlockchain(setOf(0), setOf(1), null)
        buildBlock(c1, 10)

        val expectedBlockRid = nodes[c1.all().first()].blockQueries(c1.chain).getBlockRid(10).get()

        // Make sure that node <node> doesn't have c1 locally and that the other node
        // doesn't have c2, We want node <node> to cross-fetch c2 from c1 on the other node instead of from local db.
        val nodeDataSource = dataSources(c1)[1]!!
        nodeDataSource.delBlockchain(chainRidOf(c1.chain))

        restartNodeClean(1, c0, -1)
        // We don't know if node 1 will fetch c1 or c2 from node 0, because
        // node 0 might sync parts of c2 locally from c1 before node 1 fetches
        // from node0. Se we can't be sure that both fetch and cross-fetch works for a replica,
        // only that either works.
        // If you can come up with a test that forces a replica to cross-fetch, it'd be great.
        val c2 = startNewBlockchain(setOf(0), setOf(1), c1.chain)
        awaitHeight(c2, 10)
        c2.all().forEach {
           assertArrayEquals(nodes[it].blockQueries(c2.chain).getBlockRid(10).get(), expectedBlockRid)
        }
        assertCantBuildBlock(c2, 11)
    }

    /**
     * The chart at doc/ForkTestNightly_recursiveFork.graphml (or .png) to understand how the
     * chain1's and chain2's configurations relate.
     * (chain3 isn't on the pic, but it starts out as a chain1 fork and moves to be a chain2 fork at height 19.
     *  Rather tricky IMO)
     */
    @Test
    fun testRecursiveFork() {
        val (c1, c2) = makeFork()
        val c2_15 = addBlockchainConfiguration(c2, 15, setOf(0), setOf(1), c1.chain)
        buildBlock(c1, 15)
        awaitChainRestarted(c2_15, 14)
        awaitHeight(c2_15, 15)
        val c3 = startNewBlockchain(setOf(0), setOf(), c1.chain)
        buildBlock(c1, 17)
        awaitHeight(c2_15, 17)
        awaitHeight(c3, 17)

        // From height 19 chain2 is standalone (=is no longer a fork of chain1).
        val c2_19 = addBlockchainConfiguration(c2_15, 19, setOf(2), setOf(0, 1), null)
        val c3_19 = addBlockchainConfiguration(c3, 19, setOf(2), setOf(0), c2_19.chain)
        buildBlock(c1, 20)
        awaitChainRestarted(c2_19, 18)
        awaitChainRestarted(c3_19, 18)
        buildBlock(c2_19, 19)
        awaitHeight(c3_19, 19)
        assertEqualAtHeight(c2_19, c3_19, 19)
        assertNotEqualAtHeight(c1, c3_19, 19)

        // From height 21 chain3 is standalone (=is no longer a fork of chain2).
        val c3_21 = addBlockchainConfiguration(c3_19, 21, setOf(0), setOf(), null)
        buildBlock(c2_19, 22)
        awaitChainRestarted(c3_21, 20)
        buildBlock(c3_21, 22)
        assertNotEqualAtHeight(c2_19, c3_21, 21)
    }

    @Test
    fun testAncestors() {
        extraNodeProperties[0] = mapOf("blockchain_ancestors.${chainRidOf(3)}"
                to listOf(ancestor(1,2)))
        val (c1, c2) = makeFork() // c1 and c2 both consist of node index 0 (signer) and 1 (replica)
        dataSources(c1).forEach {
            it.value.delBlockchain(chainRidOf(c1.chain))
        }
        buildBlock(c0) // trigger stopping of c1
        val c3 = startNewBlockchain(setOf(0), setOf(), c1.chain)
        awaitHeight(c3, 10)
        assertEqualAtHeight(c2, c3, 10)
    }

    /**
     * (This test originated from a discussion with alex)
     * What should happen if we at some point use an ancestor over the network, but later move it to the local node.
     * This test is three steps, and they are described graphically so look at the pictures:
     *
     * doc/blockchain_ancestor_usage_step1.png to ...step3
     *
     * ------- ------- -------------- ------- -----
     *                  Signing
     * NodeId  NodeHex  Chains        Replica Ancestor
     * ------- -------- ------------- ------- -----
     * 0       70       0
     * 1       8F       1             0
     * 2       94       2             0       Chain2 has Chain1 ancestor
     * 3       5D       3,(2),(4)
     * ------- -------- ------------- ------- ------
     *
     * Test
     * This tests that we can sync from a chain via ancestor (chain2 is ancestor for chain1) AND that we can
     * also run that chain locally and sync locally (from chain2 as an ancestor for chain1).
     *
     * Note:
     * To do this successfully we must do the different steps in succession, we cannot for example do step1 and step2
     * in parallel, since ConnMgr will not allow us to connect to the same chain  (chain2 on Node2) using different names.
     */
   @Disabled // Incomplete test, never worked and probably incorrect setup.
    @Test
    fun testAncestorNetworkThenLocally() {
        extraNodeProperties[0] = mapOf("blockchain_ancestors.${chainRidOf(3)}" to listOf(ancestor(2,2)))

        startManagedSystem(4, 0)

        awaitLog("++++++++++++++ Begin Ancestor Network then Locally ++++++++++++++")

        val c1 = startNewBlockchain(setOf(1), setOf(0), null)
        buildBlock(c1, 10)
        val chains = mutableMapOf(1 to c1)

        // Add chain2 on Node2
        val c2 = startNewBlockchain(setOf(2), setOf(0), c1.chain)
        buildBlock(c0) // trigger blockchain changes
        buildBlock(c2, 10)
        awaitHeight(c2, 10)
        chains[2] = c2

        // ============
        // Step 1 - sync Chain3 remote via Chain2 (=ancestor for Chain1)
        // ============
        // -- Shutdown Node1, so that it will be impossible to get chain1 blocks from Node1
        nodes[1].shutdown()

        // -- Create chain 3 on Node3
        val c3 = startNewBlockchain(setOf(3), setOf(), c1.chain)
        buildBlock(c0) // trigger blockchain changes
        buildBlock(c3, 10)
        awaitHeight(c3, 10)
        assertEqualAtHeight(c2, c3, 10)
        chains[3] = c3

        // ============
        // Step 2 - copy chain2 Node2 -> Node3
        // ============
        val c2_node3 = addBlockchainConfiguration(c2, 10, setOf(3), setOf(), null)
        buildBlock(c0) // trigger blockchain changes
        buildBlock(c2_node3, 20)
        awaitHeight(c2_node3,  20)
        assertEqualAtHeight(c2_node3, c2, 10)

        // ============
        // Step 3 - sync Chain4 locally via Chain2 (=ancestor for Chain1)
        // ============
        // -- Shutdown Node2, so that it will be impossible to get chain2 blocks from Node2
        nodes[2].shutdown()

        // -- Sync Chain4 from Chain2 locally on Node3
        val c4 = startNewBlockchain(setOf(3), setOf(), c1.chain)
        buildBlock(c0) // trigger blockchain changes
        buildBlock(c4, 10)
        awaitHeight(c4, 10)
        assertEqualAtHeight(c3, c4, 10)
        chains[4] = c4

        awaitLog("++++++++++++++ End Ancestor Network then Locally ++++++++++++++")
    }

    /**
     * This is a pretty brutal test, runs many nodes and chains.
     *
     * ============
     * Setup
     * ============
     * When reading logs you might find this map describing what chains run on what node useful:
     *
     * ------- ------- ------------- -----
     *                  Signing       Replica
     * NodeId  NodeHex  Chains        Chains
     * ------- -------- ------------- -----
     * 0       70       0
     * 1       8F       1,2,3,4,(5?)
     * 2       94       2,3,4,(5?)
     * 3       5D       3,4,(5?)
     * 4       D1       4,(5?)
     * 5       68                     5 (replica at all heights)
     * 6       E4
     * ------- -------- ------------- -----
     *
     * NOTE: Node 1-5 are all signers for chain 5, but at different heights.
     *
     * ============
     * Testing
     * ============
     * Chain 5 is started after all the other chains have been built, so
     * the actual test is to observe in chain 5 will manage to get blocks or not.
     *
     * The core idea of this test is to see if chain 5 can fetch the early blocks
     * (via the ancestors) despite node 1 and 2 being down.
     * (To be clear, node 3,4 and 5 must fetch blocks 1-19 from the ancestors on node 4.
     * since the original masters of chain 1 and 2 are down)
     *
     * ============
     * NOTE
     * ============
     * This test also tests that we won't get deadlock when we restart a chain that's being copied via [HistoricChainWorker].
     * This is a side effect of this test, that has been very valuable during debugging of deadlocks
     *
     */
    @Test
    fun testAncestorsManyLevels() {
        // ancestors for chain 5 are 3 and 4
        extraNodeProperties[5] = mapOf(
                "blockchain_ancestors.${chainRidOf(5)}" to listOf(ancestor(4, 4)))

        startManagedSystem(7, 0)

        awaitLog("++++++++++++++ Begin Ancestor Many Levels ++++++++++++++")
        val c1 = startNewBlockchain(setOf(1), setOf(), null)
        buildBlock(c1, 10)
        // Chain id is same as node id, for example node 3 is the final signer of chain 3
        val chains = mutableMapOf(1 to c1)
        for (node in 2..4) {
            val c = startNewBlockchain(setOf(1), setOf(), c1.chain)
            chains[node] = c
            for (config in 2..node) {
                // Node 4 will get chain 4, Node 3 will get chain 3,4, Node 2 will get all chains
                val configHeight = 10L * (config-1)
                val chainConfig = if (config == node) {
                    // For Node 2, BC 2 will be the "original" (unforked chain),
                    // Node 3 will have the "original" for BC 3 etc.
                    addBlockchainConfiguration(c, configHeight, setOf(config), setOf(), null)
                } else {
                    addBlockchainConfiguration(c, configHeight, setOf(config), setOf(), config.toLong())
                }
                buildBlock(c0) // Trigger all blockchain changes
                awaitChainRestarted(chainConfig, configHeight-1)
                chains[node] = chainConfig
            }
            val forkHeight = (node-1)*10L
            buildBlock(chains[node]!!, forkHeight+10)
            val chainOld = chains[node - 1]!!
            assertEqualAtHeight(chainOld, chains[node]!!, forkHeight-1)
            assertNotEqualAtHeight(chainOld, chains[node]!!, forkHeight)
        }

        // Now the actual test. We will test that a new node that forks chain 4, into chain 5
        // will be able to find all previous blocks of chain 4 even though the original (as depicted in
        // historicBrid) source for the blocks is unavailable
        nodes[1].shutdown()
        nodes[2].shutdown()
        awaitLog("++++++++++++++ Begin Ancestor Many Levels ACTUAL test ++++++++++++++")

        val c4 = chains[4]!!

        // -----------------
        // Chain5
        // -----------------
        // Chain5 will have different signers for every 10 blocks, AND new historic chain for every 10 blocks
        // so this is a really tricky test!
        val c5 = startNewBlockchain(setOf(1), setOf(5), c1.chain, setOf(1, 2), false)
        addBlockchainConfiguration(c5, 10, setOf(2), setOf(5), 2L, setOf(1, 2))
        addBlockchainConfiguration(c5, 20, setOf(3), setOf(5), 3L, setOf(1, 2))
        addBlockchainConfiguration(c5, 30, setOf(4), setOf(5), 4L, setOf(1, 2))
        // In the last step, where Node5 signs blocks above 40, we no longer consider this a fork
        val c5_5 = addBlockchainConfiguration(c5, 40, setOf(5), setOf(), null, setOf(1, 2))

        buildBlock(c0.remove(setOf(1, 2)))
        awaitChainRestarted(c5_5, 39)
        buildBlock(c5_5, 40)
        assertEqualAtHeight(c4, c5_5, 39)
        assertNotEqualAtHeight(c4, c5_5, 40)
    }

    @Test
    fun testForkReplicaFromNetInvalidSignerSet() {
        val c1 = basicSystem()
        val c2 = startNewBlockchain(setOf(1), setOf(2), c1.chain)
        sleep(1000)
        c2.nodes().forEach {
            assertEquals(-1, it.blockQueries(c2.chain).getBestHeight().get())
        }
    }

//    @Test
//    fun testNettyServerThreads() {
//        startManagedSystem(2, 0)
//        val c1 = startNewBlockchain(setOf(0, 1), setOf(), null)
//        val c1_10 = addBlockchainConfiguration(c1, 10, setOf(0, 1), setOf())
//        buildBlock(c0)
//        buildBlock(c1, 9)
//        awaitChainRestarted(c1, 9)
//        val c1_20 = addBlockchainConfiguration(c1, 20, setOf(0, 1), setOf())
//        buildBlock(c0)
//        buildBlock(c1, 19)
//        awaitChainRestarted(c1, 19)
//        val c1_30 = addBlockchainConfiguration(c1, 30, setOf(0, 1), setOf())
//        buildBlock(c0)
//        buildBlock(c1, 29)
//        awaitChainRestarted(c1, 29)
//
//        sleep(1000000000)
//    }

    private fun ancestor(index: Int, blockchain: Long): String {
        return "${XPeerID(KeyPairHelper.pubKey(index))}:${chainRidOf(blockchain)}"
    }

    /**
     * Here we want to set properties on unique nodes via a special map, just transfer the property to the
     * [NodeSetup] in question
     */

    override fun addNodeConfigurationOverrides(nodeSetup: NodeSetup) {
        super.addNodeConfigurationOverrides(nodeSetup) // Will jack into ManagedModeTest overrides (= set the specific "infrastructure" we need)
        val nodesExtra = extraNodeProperties[nodeSetup.sequenceNumber.nodeNumber]
        if (nodesExtra != null) {
            for (key in nodesExtra.keys) {
                nodeSetup.nodeSpecificConfigs.setProperty(key, nodesExtra[key])
            }
        }
    }


    private fun awaitChainRestarted(nodeSet: NodeSet, atLeastHeight: Long) {
        awaitLog("========= AWAIT ALL ${nodeSet.size} NODES RESTART chain:  ${nodeSet.chain}, at least height:  $atLeastHeight")
        nodeSet.all().forEach { awaitChainRunning(it, nodeSet.chain, atLeastHeight) }
        awaitLog("========= DONE WAITING ALL ${nodeSet.size} NODES RESTART chain:  ${nodeSet.chain}, at least height:  $atLeastHeight")
    }

    private fun makeFork(): Pair<NodeSet, NodeSet> {
        val c1 = basicSystem()
        val c2 = startNewBlockchain(setOf(0), setOf(1), c1.chain)
        awaitHeight(c2, 10)
        return Pair(c1, c2)
    }

    fun addBlockchainConfiguration(chain: NodeSet, atHeight: Long, signers: Set<Int>, replicas: Set<Int>,
                                   historicChain: Long? = null, excludeChain0Nodes: Set<Int> = setOf()): NodeSet {
        val newChain = NodeSet(chain.chain, signers, replicas)
        newBlockchainConfiguration(newChain, historicChain, atHeight, excludeChain0Nodes)
        return newChain
    }

    fun assertEqualAtHeight(chainOld: NodeSet, chainNew: NodeSet, height: Long) {
        val expectedBlockRid = nodes[chainOld.all().first()].blockQueries(chainOld.chain).getBlockRid(height).get()
        chainNew.all().forEach {
           assertArrayEquals(nodes[it].blockQueries(chainNew.chain).getBlockRid(height).get(), expectedBlockRid)
        }
    }


    fun assertNotEqualAtHeight(chainOld: NodeSet, chainNew: NodeSet, height: Long) {
        val expectedBlockRid = nodes[chainOld.all().first()].blockQueries(chainOld.chain).getBlockRid(height).get()
        chainNew.all().forEach {
           assertFalse(expectedBlockRid!!.contentEquals(nodes[it].blockQueries(chainNew.chain).getBlockRid(height).get()!!))
        }
    }

    private var chainId: Long = 1
    fun startNewBlockchain(signers: Set<Int>, replicas: Set<Int>, historicChain: Long? = null, excludeChain0Nodes: Set<Int> = setOf(), waitForRestart: Boolean = true): NodeSet {
        assertTrue(signers.intersect(replicas).isEmpty())
        val maxIndex = c0.all().size
        signers.forEach { assertTrue(it < maxIndex ) }
        replicas.forEach { assertTrue(it < maxIndex) }
        val c = NodeSet(chainId++, signers, replicas)
        newBlockchainConfiguration(c, historicChain, 0, excludeChain0Nodes)
        // Await blockchain started on all relevant nodes
        if (waitForRestart)
            awaitChainRestarted(c, -1)
        return c
    }

    /**
     * Starts a managed system with one managed blockchain with 11 blocks:
     *
     * * chain0 has two signers (indices 0 and 1) and one replica (2)
     * * chain1 has one signer (0) and one replica (1)
     */
    private fun basicSystem(): NodeSet {
        startManagedSystem(2, 1)

        val c1 = startNewBlockchain(setOf(0), setOf(1), null)
        buildBlock(c1, 10)
        return c1
    }
}