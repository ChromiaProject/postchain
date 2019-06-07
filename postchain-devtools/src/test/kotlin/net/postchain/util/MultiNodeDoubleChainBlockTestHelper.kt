package net.postchain.util

import mu.KLogging
import net.postchain.common.hexStringToByteArray
import net.postchain.common.toHex
import net.postchain.configurations.GTXTestModule
import net.postchain.core.BlockQueries
import net.postchain.devtools.IntegrationTest
import net.postchain.devtools.OnDemandBlockBuildingStrategy
import net.postchain.devtools.testinfra.TestOneOpGtxTransaction
import net.postchain.gtx.GTXTransaction
import net.postchain.gtx.GTXTransactionFactory
import net.postchain.integrationtest.assertChainStarted
import org.awaitility.Awaitility
import org.awaitility.Duration
import org.junit.Assert
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * A base class for tests where you want to run exactly 2 chains on each node.
 * (even with this simplification it gets a bit messy)
 */
open class MultiNodeDoubleChainBlockTestHelper: IntegrationTest()  {

    private val gtxTestModule =  GTXTestModule()
    private val factory1 = GTXTransactionFactory(blockchainRids[1L]!!.hexStringToByteArray(), gtxTestModule, cryptoSystem)
    private val factory2 = GTXTransactionFactory(blockchainRids[2L]!!.hexStringToByteArray(), gtxTestModule, cryptoSystem)
    private val factoryMap = mapOf(
            1L to factory1,
            2L to factory2)

    companion object: KLogging()


    private fun strategyOf(nodeId: Int, chainId: Long): OnDemandBlockBuildingStrategy {
        return nodes[nodeId].blockBuildingStrategy(chainId) as OnDemandBlockBuildingStrategy
    }

    fun runXNodes(
            nodesCount: Int,
            chainList: List<Long>,
            nodeConfigsFilenames: Array<String>,
            blockchainConfigsFilenames: Array<String>
    ) {
        configOverrides.setProperty("testpeerinfos", createPeerInfos(nodesCount))

        // Creating node with two chains
        logger.debug("---Creating node with two chains ----------------------------")
        createMultipleChainNodes(nodesCount, nodeConfigsFilenames, blockchainConfigsFilenames)

        // Asserting all chains are started
        logger.debug("---Asserting all chains are started -------------------------")
        Awaitility.await().atMost(Duration.TEN_SECONDS)
                .untilAsserted {
                    nodes.forEach { node ->
                        chainList.forEach(node::assertChainStarted)
                    }
                }

    }

    /**
     * Note that we are enqueing real GTX TXs here.
     *
     * @param blocksCount number of blocks to build
     * @param txPerBlock number of TX in each block
     * @param chainList all BC we will use
     * @param factoryMap a factory per BC
     */
    fun runXNodesWithYTxPerBlock(
            blocksCount: Int,
            txPerBlock: Int,
            chainList: List<Long>
    ): TxCache {

        // Enqueueing txs
        logger.debug("---Enqueueing txs --------------------------------------------")
        val cache = TxCache()
        var txId = 0

        // counters

        // Creates all the transactions in all the blocks
        for (block in 0 until blocksCount) {

            // Creates all TXs of the blocks of a specific height
            for (txIndex in 0 until txPerBlock) {
                val currentTxId = txId++
                logger.debug("+++++++++++++++++++++++++++++++++++++++++++")
                logger.debug("++++ block: $block, txId: $txId +++++++")
                logger.debug("+++++++++++++++++++++++++++++++++++++++++++")
                nodes.forEachIndexed {  nodeIndex, node ->
                    chainList.forEach { chain ->
                        logger.debug("++++ block: $block, txId: $txId, node: $nodeIndex, chain: $chain")
                        val tx = TestOneOpGtxTransaction(factoryMap[chain]!!, currentTxId).getGTXTransaction()
                        cache.add(nodeIndex, chain.toInt(), block, txIndex, tx)
                        node.transactionQueue(chain).enqueue(tx)
                    }
                }
            }

            // Tell the nodes to build all these blocks
            nodes.indices.forEach { nodeId ->
                chainList.forEach { chain ->
                    logger.debug("-------------------------------------------")
                    logger.info { "Node: $nodeId, chain: $chain -> Trigger block" }
                    logger.debug("-------------------------------------------")
                    strategyOf(nodeId, chain).buildBlocksUpTo(block.toLong())
                    logger.debug("-------------------------------------------")
                    logger.info { "Node: $nodeId, chain: $chain -> Await committed" }
                    logger.debug("-------------------------------------------")
                    strategyOf(nodeId, chain).awaitCommitted(block)
                }
            }
        }
        return cache
    }


    /**
     * Will assert common things, like:
     *
     * - chains have correct height
     * - blocks have correct TXs
     */
    fun runXNodesAssertions(
            blocksCount: Int,
            txPerBlock: Int,
            chainList: List<Long>,
            cache: TxCache
    ) {
        logger.debug("---Assertions -------------------------------------------------")
        // Assertions
        val expectedHeight = (blocksCount - 1).toLong()
        nodes.forEachIndexed { nodeIndex, node ->
            chainList.forEach { chain ->
                logger.info { "Assertions: node: $nodeIndex, chain: $chain, expectedHeight: $expectedHeight" }

                val queries = node.blockQueries(chain)

                // Asserting best height == (block count -1)
                Assert.assertEquals(expectedHeight, queries.getBestHeight().get())

                for (height in 0..expectedHeight) {
                    verifyBlock(nodeIndex, chain, height, queries, txPerBlock, cache)
                }
            }
        }
    }

    /**
     * Verify that a block on node = [nodeIndex] , chain = [chain] with height = [height] has the correct TXs.
     */
    private fun verifyBlock(nodeIndex: Int, chain: Long, height: Long, queries: BlockQueries, txPerBlock: Int, cache: TxCache) {
        logger.info { "Verifying height: $height" }

        // Asserting uniqueness of block at height
        val blockRid = queries.getBlockRids(height).get()
        assertNotNull(blockRid)

        // Asserting txs count
        val txs = queries.getBlockTransactionRids(blockRid!!).get()
        Assert.assertEquals(txPerBlock, txs.size)

        // Asserting txs content
        for (txIndex in 0 until txPerBlock) {
            val expectedTxRid = cache.getCachedTxRid(nodeIndex, chain.toInt(), height.toInt(), txIndex)
            val realTxRid = txs[txIndex]
            logger.debug("Real TX RID: ${realTxRid.toHex()} ")
            Assert.assertArrayEquals(expectedTxRid, realTxRid)
        }
    }

    /**
     * Here we verify that the dependencies are written correctly into the BLOCK_DEPENDENCIES table.
     *
     * @param blocksCount is number of blocks created
     * @param dependentChainList is a list of all chains that has dependencies (depends on some other chain)
     */
    fun runXNodesDependencyAssertions(
            blocksCount: Int,
            dependentChainList: List<Long>
    ) {
        logger.debug("---Dep Assertions -------------------------------------------------")
        // Assertions
        val expectedHeight = (blocksCount - 1).toLong()
        nodes.forEachIndexed { nodeId, node ->
            dependentChainList.forEach { chain ->
                logger.info { "Dep Assertions: node: $nodeId, chain: $chain, expectedHeight: $expectedHeight" }

                val queries = node.blockQueries(chain)

                for (height in 1..expectedHeight) { // We don't care about height = 0

                    val blockRid = queries.getBlockRids(height).get()
                    logger.info { "Verifying deps for height: $height (deps for block RID: ${blockRid!!.toHex()} )" }

                    // Asserting dependencies exist in DB
                    val deps = queries.getBlockDependencies(blockRid!!).get()
                    Assert.assertTrue(deps.all().size > 0) // Since we only look at chains with deps and blocks higher than 1.

                    // Check that the block we depend on is of correct height
                    for (dep in deps.all()) {
                        logger.debug { "Checking dependency $dep is of height $height" }
                        Assert.assertEquals(dep.heightDependency!!.height, height)
                    }

                }
            }
        }
    }
}
