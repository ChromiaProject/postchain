package net.postchain.d1.anchor.integration

import net.postchain.base.data.DatabaseAccess
import net.postchain.base.withReadConnection
import net.postchain.common.BlockchainRid
import net.postchain.devtools.TxCache
import net.postchain.devtools.utils.GtxTxIntegrationTestSetup
import net.postchain.devtools.utils.configuration.SystemSetup
import org.apache.commons.dbutils.handlers.MapListHandler
import org.apache.commons.dbutils.QueryRunner
import org.junit.jupiter.api.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

private const val CHAIN_ID = 1
private const val ANCHOR_CHAIN_ID = 2 // Only for this test, we don't have a hard ID for anchoring.

/**
 * Main idea is to have one "source" blockchain that generates block, so that these blocks can be anchored by another
 * "anchor" blockchain. If this work the golden path of anchoring should work.
 *
 * It doesn't matter what the "source" blocks contain.
 * Produce blocks containing Special transactions using the simplest possible setup, but as a minimum we need a new
 * custom test module to give us the "__xxx" operations needed.
 */
class AnchorIntegrationTest : GtxTxIntegrationTestSetup() {

    /**
     * Simple happy test to see that we can run 3 nodes with:
     * - a normal chain and
     * - an anchor chain.
     */
    @Test
    fun happyAnchor() {
        val mapBcFiles: Map<Int, String> = mapOf(
            CHAIN_ID to "/net/postchain/anchor/integration/blockchain_config_1.xml",
            ANCHOR_CHAIN_ID to "/net/postchain/anchor/integration/blockchain_config_2_anchor.xml"
        )

        val sysSetup = SystemSetup.buildComplexSetup(mapBcFiles)
        sysSetup.confInfrastructure = net.postchain.d1.anchor.D1TestInfrastructureFactory::class.java.name

        // -----------------------------
        // Important!! We don't want to create any regular test transactions for the Anchor chain
        // -----------------------------
        sysSetup.blockchainMap[ANCHOR_CHAIN_ID]!!.setShouldHaveNormalTx(false)
        //sysSetup.blockchainMap[ANCHOR_CHAIN_ID]!!.setListenerChainLevel(LevelConnectionChecker.HIGH_LEVEL) // The usual Anchor level

        runXNodes(sysSetup) // Starts all chains on all nodes

        // --------------------
        // ChainId = 1: Create all blocks
        // --------------------
        val txCache = TxCache(mutableMapOf())

        // Only for chain 1 see "shouldHaveNormalTx" setting
        runXNodesWithYTxPerBlock(4, 5, sysSetup, txCache) // This is waiting for blocks to finish too
        // Only for chain 1 see "shouldHaveNormalTx" setting
        runXNodesAssertions(4, 5, sysSetup, txCache)

        val blockchainRID: BlockchainRid = sysSetup.blockchainMap[CHAIN_ID]!!.rid

        // --------------------
        // ChainId = 2: Check that we begin with nothing
        // --------------------
        val blockQueries = nodes[0].getBlockchainInstance(ANCHOR_CHAIN_ID.toLong()).blockchainEngine.getBlockQueries()

        // --------------------
        // ChainId = 2: Build first anchor block
        // --------------------

        val heightZero = 0
        buildBlocks(0, ANCHOR_CHAIN_ID.toLong(), heightZero)

        // --------------------
        // ChainId = 2: Actual test
        // --------------------
        val expectedNumberOfTxs = 1  // Only the first TX

        val blockDataFull = blockQueries.getBlockAtHeight(heightZero.toLong()).get()!!
        assertEquals(expectedNumberOfTxs, blockDataFull.transactions.size)

        withReadConnection(nodes[0].postchainContext.storage, ANCHOR_CHAIN_ID.toLong()) {
            val db = DatabaseAccess.of(it)
            val queryRunner = QueryRunner()

            val res = queryRunner.query(
                it.conn,
                "SELECT blockchain_rid, block_height, status FROM ${db.tableName(it, "anchor_blocks")}",
                MapListHandler()
            )
            assertEquals(4, res.size)
            assertContentEquals(blockchainRID.data, res[0]["blockchain_rid"] as ByteArray)
            assertEquals(0L, res[0]["block_height"])
            assertEquals(0L, res[0]["status"])
            assertContentEquals(blockchainRID.data, res[1]["blockchain_rid"] as ByteArray)
            assertEquals(1L, res[1]["block_height"])
            assertEquals(0L, res[1]["status"])
            assertContentEquals(blockchainRID.data, res[2]["blockchain_rid"] as ByteArray)
            assertEquals(2L, res[2]["block_height"])
            assertEquals(0L, res[2]["status"])
            assertContentEquals(blockchainRID.data, res[3]["blockchain_rid"] as ByteArray)
            assertEquals(3L, res[3]["block_height"])
            assertEquals(0L, res[3]["status"])
        }
    }
}
