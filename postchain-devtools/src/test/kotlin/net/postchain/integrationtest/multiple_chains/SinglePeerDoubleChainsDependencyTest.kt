package net.postchain.integrationtest.multiple_chains

import mu.KLogging
import net.postchain.core.BadDataMistake
import net.postchain.core.BadDataType
import net.postchain.util.MultiNodeDoubleChainBlockTestHelper
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.fail

class SinglePeerDoubleChainsDependencyTest: MultiNodeDoubleChainBlockTestHelper() {

    companion object : KLogging()

    /**
     * Begin with a simple happy test to see that we can start/stop a node with 2 chains.
     */
    @Test
    fun startingAndStoppingSingleChainSuccessfully() {
        val chainList = listOf(1L, 2L)
        runXNodes(
                1,
                chainList,
                arrayOf(
                        "classpath:/net/postchain/multiple_chains/dependent_bcs/single_peer/node0bc2.properties"
                ),
                arrayOf(
                        "/net/postchain/multiple_chains/dependent_bcs/single_peer/blockchain_config_1.xml",
                        "/net/postchain/multiple_chains/dependent_bcs/single_peer/blockchain_config_2.xml"
                )
        )
        val txList = runXNodesWithYTxPerBlock( 1, 1, chainList)
        runXNodesAssertions( 1, 1, chainList, txList)
    }

    /**
     * What if our configuration tells us we should have a dependency, but we haven't got it?
     */
    @Test
    fun testBreakIfDependencyNotFound() {
        val chainList = listOf(1L)
        try {
            // It will break immediately
            runXNodes(
                    1,
                    chainList,
                    arrayOf(
                            "classpath:/net/postchain/multiple_chains/dependent_bcs/single_peer/node0bc1dep.properties"
                    ),
                    arrayOf(
                            "/net/postchain/multiple_chains/dependent_bcs/single_peer/blockchain_config_bad_dependency.xml"
                    )
            )

            fail("This is not allowed since we don't have the blockchain we depend on")
        } catch (e: BadDataMistake) {
            assertEquals(BadDataType.BAD_CONFIGURATION, e.type)
        }

    }

    /**
     * One BC depend on another BC. Build 2 blocks on both chains.
     */
    @Test
    fun testHappyDependency() {
        val chainList = listOf(1L, 2L)
        val depChainList = listOf(2L) // These are the chains with a dependency to another chain

        runXNodes(
                1,
                chainList,
                arrayOf(
                        "classpath:/net/postchain/multiple_chains/dependent_bcs/single_peer/node0bc2dep.properties"
                ),
                arrayOf(
                        "/net/postchain/multiple_chains/dependent_bcs/single_peer/blockchain_config_1.xml",
                        "/net/postchain/multiple_chains/dependent_bcs/single_peer/blockchain_config_2_depends_on_1.xml"
                )
        )

        val txList = runXNodesWithYTxPerBlock( 2, 10, chainList)
        runXNodesAssertions( 2, 10, chainList, txList)
        runXNodesDependencyAssertions(2, depChainList)
    }


    /**
     * One BC depend on another BC, where we build 4 blocks on both chains.
     */
    @Test
    fun testHappyDependencyWithOldDepsOfHeight4() {
        val chainList = listOf(1L, 2L)
        val depChainList = listOf(2L) // These are the chains with a dependency to another chain

        runXNodes(
                1,
                chainList,
                arrayOf(
                        "classpath:/net/postchain/multiple_chains/dependent_bcs/single_peer/node0bc2dep.properties"
                ),
                arrayOf(
                        "/net/postchain/multiple_chains/dependent_bcs/single_peer/blockchain_config_1.xml",
                        "/net/postchain/multiple_chains/dependent_bcs/single_peer/blockchain_config_2_depends_on_1.xml"
                )
        )


        val txList = runXNodesWithYTxPerBlock( 4, 10, chainList)
        runXNodesAssertions( 4, 10, chainList, txList)
        runXNodesDependencyAssertions(4, depChainList)

    }



}

