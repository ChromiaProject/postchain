package net.postchain.d1.anchor.integration

import net.postchain.base.data.DatabaseAccess
import net.postchain.base.withReadConnection
import net.postchain.common.BlockchainRid
import net.postchain.core.EContext
import net.postchain.devtools.PostchainTestNode
import net.postchain.devtools.TxCache
import net.postchain.devtools.getModules
import net.postchain.devtools.utils.GtxTxIntegrationTestSetup
import net.postchain.devtools.utils.configuration.SystemSetup
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory.gtv
import org.apache.commons.dbutils.QueryRunner
import org.apache.commons.dbutils.handlers.MapListHandler
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
    companion object {
        val messagesHash = ByteArray(32) { i -> i.toByte() }
    }

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
                "SELECT blockchain_rid, block_height, status FROM ${db.tableName(it, "anchor_block")}",
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

            val hash =
                query(
                    nodes[0],
                    it,
                    "icmf_get_messages_hash",
                    gtv(
                        mapOf(
                            "topic" to gtv("my-topic"),
                            "sender" to gtv(blockchainRID.data),
                            "sender_height" to gtv(0)
                        )
                    )
                ).asByteArray()
            assertContentEquals(messagesHash, hash)

            val hashes =
                query(
                    nodes[0],
                    it,
                    "icmf_get_messages_hash_since_height",
                    gtv(
                        mapOf(
                            "topic" to gtv("my-topic"),
                            "anchor_height" to gtv(0)
                        )
                    )
                ).asArray()
            assertEquals(4, hashes.size)
            assertContentEquals(blockchainRID.data, hashes[0]["sender"]!!.asByteArray())
            assertEquals(0L, hashes[0]["sender_height"]!!.asInteger())
            assertContentEquals(messagesHash, hashes[0]["hash"]!!.asByteArray())
            assertContentEquals(blockchainRID.data, hashes[1]["sender"]!!.asByteArray())
            assertEquals(1L, hashes[1]["sender_height"]!!.asInteger())
            assertContentEquals(messagesHash, hashes[1]["hash"]!!.asByteArray())
            assertContentEquals(blockchainRID.data, hashes[2]["sender"]!!.asByteArray())
            assertEquals(2L, hashes[2]["sender_height"]!!.asInteger())
            assertContentEquals(messagesHash, hashes[3]["hash"]!!.asByteArray())
            assertContentEquals(blockchainRID.data, hashes[3]["sender"]!!.asByteArray())
            assertEquals(3L, hashes[3]["sender_height"]!!.asInteger())
            assertContentEquals(messagesHash, hashes[3]["hash"]!!.asByteArray())
        }
    }

    private fun query(node: PostchainTestNode, ctxt: EContext, name: String, args: Gtv): Gtv =
        node.getModules(ANCHOR_CHAIN_ID.toLong()).find { it.javaClass.simpleName.startsWith("Rell") }!!
            .query(ctxt, name, args)
}
