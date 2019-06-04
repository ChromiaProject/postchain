package net.postchain.util

import mu.KLogging
import net.postchain.common.hexStringToByteArray
import net.postchain.common.toHex
import net.postchain.configurations.GTXTestModule
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
    ): List<GTXTransaction> {

        // Enqueueing txs
        logger.debug("---Enqueueing txs --------------------------------------------")
        val retList = mutableListOf<GTXTransaction>()
        var txId = 0
        for (block in 0 until blocksCount) {
            (0 until txPerBlock).forEach { _ ->
                val currentTxId = txId++
                logger.debug("+++++++++++++++++++++++++++++++++++++++++++")
                logger.debug("++++ block: $block, txId: $txId +++++++")
                logger.debug("+++++++++++++++++++++++++++++++++++++++++++")
                nodes.forEach { node ->
                    chainList.forEach { chain ->
                        logger.debug("++++ block: $block, txId: $txId, node: $node, chain: $chain")
                        val tx = TestOneOpGtxTransaction(factoryMap[chain]!!, currentTxId).getGTXTransaction()
                        retList.add(tx)
                        node.transactionQueue(chain).enqueue(tx)
                    }
                }
            }

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
        return retList
    }


    /**
     * Will assert common things, like:
     *
     * 1.
     */
    fun runXNodesAssertions(
            blocksCount: Int,
            txPerBlock: Int,
            chainList: List<Long>,
            txList: List<GTXTransaction>
    ) {
        logger.debug("---Assertions -------------------------------------------------")
        val txCache = TxCache(txList)
        // Assertions
        val expectedHeight = (blocksCount - 1).toLong()
        nodes.forEachIndexed { nodeId, node ->
            chainList.forEach { chain ->
                logger.info { "Assertions: node: $nodeId, chain: $chain, expectedHeight: $expectedHeight" }

                val queries = node.blockQueries(chain)

                // Asserting best height == (block count -1)
                Assert.assertEquals(expectedHeight, queries.getBestHeight().get())

                for (height in 0..expectedHeight) {
                    logger.info { "Verifying height: $height" }

                    // Asserting uniqueness of block at height
                    val blockRid = queries.getBlockRids(height).get()
                    assertNotNull(blockRid)

                    // Asserting txs count
                    val txs = queries.getBlockTransactionRids(blockRid!!).get()
                    Assert.assertEquals(txPerBlock, txs.size)

                    // Asserting txs content
                    for (tx in 0 until txPerBlock) {
                        val txPos = height.toInt() * txPerBlock + tx
                        val expectedTxRid = txCache.getCachedTxRid(chain.toInt(), chainList.size, height.toInt(), txPerBlock, tx)

                        //val expectedTx = TestTransaction(height.toInt() * txPerBlock + tx)
                        val realTxRid = txs[tx]
                        logger.debug("Real TX RID: ${realTxRid.toHex()}")
                        Assert.assertArrayEquals(expectedTxRid, realTxRid)
                    }
                }
            }
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

                    // Asserting




                }
            }
        }
    }
}
