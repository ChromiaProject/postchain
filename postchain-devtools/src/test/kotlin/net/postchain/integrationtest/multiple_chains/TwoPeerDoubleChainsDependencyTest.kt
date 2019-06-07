package net.postchain.integrationtest.multiple_chains

import mu.KLogging
import net.postchain.util.MultiNodeDoubleChainBlockTestHelper
import org.junit.Test

/**
 *
 * This test is for the case when we have 2 nodes with 2 BCs each,
 * where Blockchain 2 depends on Blockchain 1.
 */
class TwoPeerDoubleChainsDependencyTest: MultiNodeDoubleChainBlockTestHelper() {

    companion object : KLogging()

    val nodeCount = 2


    /**
     * One BC depend on another BC. Build 2 blocks on both chains.
     */
    @Test
    fun testHappyDependency_BuildTwoBlocks() {
        val chainList = listOf(1L, 2L)
        val depChainList = listOf(2L) // These are the chains with a dependency to another chain
        val blocksToBuild = 2
        val txPerBlock = 10
        configOverrides.setProperty("testpeerinfos", createPeerInfos(nodeCount))
        configOverrides.setProperty("api.port", 0)

        runXNodes(
                nodeCount,
                chainList,
                arrayOf(
                        "classpath:/net/postchain/multiple_chains/dependent_bcs/two_nodes/node0bc2dep.properties",
                        "classpath:/net/postchain/multiple_chains/dependent_bcs/two_nodes/node1bc2dep.properties"
                ),
                arrayOf(
                        "/net/postchain/multiple_chains/dependent_bcs/two_nodes/blockchain_config_1.xml",
                        "/net/postchain/multiple_chains/dependent_bcs/two_nodes/blockchain_config_2_depends_on_1.xml"
                )
        )

        val txList = runXNodesWithYTxPerBlock( blocksToBuild, txPerBlock, chainList)
        runXNodesAssertions( blocksToBuild, txPerBlock, chainList, txList)
        runXNodesDependencyAssertions(blocksToBuild, depChainList)
    }


    /**
     * One BC depend on another BC, where we build 4 blocks on both chains.
     */
    @Test
    fun testHappyDependencyWithOldDepsOfHeight4() {
        val chainList = listOf(1L, 2L)
        val depChainList = listOf(2L) // These are the chains with a dependency to another chain
        val blocksToBuild = 4
        val txPerBlock = 10
        configOverrides.setProperty("testpeerinfos", createPeerInfos(nodeCount))
        configOverrides.setProperty("api.port", 0)

        runXNodes(
                nodeCount,
                chainList,
                arrayOf(
                        "classpath:/net/postchain/multiple_chains/dependent_bcs/two_nodes/node0bc2dep.properties",
                        "classpath:/net/postchain/multiple_chains/dependent_bcs/two_nodes/node1bc2dep.properties"
                ),
                arrayOf(
                        "/net/postchain/multiple_chains/dependent_bcs/two_nodes/blockchain_config_1.xml",
                        "/net/postchain/multiple_chains/dependent_bcs/two_nodes/blockchain_config_2_depends_on_1.xml"
                )
        )

        val txList = runXNodesWithYTxPerBlock( blocksToBuild, txPerBlock, chainList)
        runXNodesAssertions( blocksToBuild, txPerBlock, chainList, txList)
        runXNodesDependencyAssertions(blocksToBuild, depChainList)
    }



}
