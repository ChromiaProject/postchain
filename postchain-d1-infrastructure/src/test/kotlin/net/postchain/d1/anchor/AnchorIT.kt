package net.postchain.d1.anchor

import net.postchain.base.BaseBlockWitness
import net.postchain.base.data.DatabaseAccess
import net.postchain.base.gtv.BlockHeaderData
import net.postchain.base.withReadConnection
import net.postchain.common.BlockchainRid
import net.postchain.core.EContext
import net.postchain.devtools.ManagedModeTest
import net.postchain.devtools.PostchainTestNode
import net.postchain.devtools.getModules
import net.postchain.devtools.utils.ChainUtil
import net.postchain.devtools.utils.configuration.NodeSetup
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.gtvml.GtvMLParser
import net.postchain.gtv.merkle.GtvMerkleHashCalculator
import net.postchain.gtv.merkleHash
import org.apache.commons.dbutils.QueryRunner
import org.apache.commons.dbutils.handlers.MapListHandler
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Main idea is to have one "source" blockchain that generates block, so that these blocks can be anchored by another
 * "anchor" blockchain. If this work the golden path of anchoring should work.
 *
 * It doesn't matter what the "source" blocks contain.
 * Produce blocks containing Special transactions using the simplest possible setup, but as a minimum we need a new
 * custom test module to give us the "__xxx" operations needed.
 */
class AnchorIT : ManagedModeTest() {
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
        startManagedSystem(3, 0)

        val dappGtvConfig = GtvMLParser.parseGtvML(
                javaClass.getResource("/net/postchain/d1/anchor/blockchain_config_1.xml")!!.readText())

        val dappChain = startNewBlockchain(setOf(0, 1, 2), setOf(), rawBlockchainConfiguration = GtvEncoder.encodeGtv(dappGtvConfig))

        val moduleRellCode = File("postchain-d1-infrastructure/src/main/rell/anchor/module.rell").readText()
        val icmfRellCode = File("postchain-d1-infrastructure/src/main/rell/anchor/icmf.rell").readText()
        val anchorGtvConfig = GtvMLParser.parseGtvML(
                javaClass.getResource("/net/postchain/d1/anchor/blockchain_config_2_anchor.xml")!!.readText(),
                mapOf("rell" to gtv(moduleRellCode + icmfRellCode)))

        val anchorChain = startNewBlockchain(setOf(0, 1, 2), setOf(), rawBlockchainConfiguration = GtvEncoder.encodeGtv(anchorGtvConfig))

        // --------------------
        // Dapp chain: Build 4 blocks
        // --------------------
        for (height in 0..3) {
            buildBlock(dappChain, height.toLong())
        }

        val blockchainRID: BlockchainRid = ChainUtil.ridOf(dappChain.chain)

        // --------------------
        // Anchor chain: Check that we begin with nothing
        // --------------------
        val blockQueries = anchorChain.nodes()[0].getBlockchainInstance(anchorChain.chain).blockchainEngine.getBlockQueries()

        // --------------------
        // Anchor chain: Build first anchor block
        // --------------------

        val heightZero = 0
        buildBlock(anchorChain, 0)

        // --------------------
        // Anchor chain: Actual test
        // --------------------
        val expectedNumberOfTxs = 1  // Only the first TX

        val blockDataFull = blockQueries.getBlockAtHeight(heightZero.toLong()).get()!!
        assertEquals(expectedNumberOfTxs, blockDataFull.transactions.size)

        withReadConnection(anchorChain.nodes()[0].postchainContext.storage, anchorChain.chain) {
            val db = DatabaseAccess.of(it)
            val queryRunner = QueryRunner()

            val res = queryRunner.query(
                    it.conn,
                    "SELECT blockchain_rid, block_height FROM ${db.tableName(it, "anchor_block")}",
                    MapListHandler()
            )
            assertEquals(4, res.size)
            assertContentEquals(blockchainRID.data, res[0]["blockchain_rid"] as ByteArray)
            assertEquals(0L, res[0]["block_height"])
            assertContentEquals(blockchainRID.data, res[1]["blockchain_rid"] as ByteArray)
            assertEquals(1L, res[1]["block_height"])
            assertContentEquals(blockchainRID.data, res[2]["blockchain_rid"] as ByteArray)
            assertEquals(2L, res[2]["block_height"])
            assertContentEquals(blockchainRID.data, res[3]["blockchain_rid"] as ByteArray)
            assertEquals(3L, res[3]["block_height"])

            val headers =
                    query(
                            anchorChain.nodes()[0],
                            it,
                            "icmf_get_headers_with_messages_between_heights",
                            gtv(
                                    mapOf(
                                            "topic" to gtv("my-topic"),
                                            "from_anchor_height" to gtv(0),
                                            "to_anchor_height" to gtv(1)
                                    )
                            ),
                            anchorChain.chain
                    ).asArray()
            assertEquals(4, headers.size)
            headers.forEachIndexed { index, header ->
                val rawHeader = header["block_header"]!!.asByteArray()
                val decodedHeader = BlockHeaderData.fromBinary(rawHeader)
                assertContentEquals(blockchainRID.data, decodedHeader.getBlockchainRid())
                assertEquals(index.toLong(), decodedHeader.getHeight())
                assertContentEquals(messagesHash, decodedHeader.getExtra()["icmf_send"]!!["my-topic"]!!["hash"]!!.asByteArray())

                val witness = BaseBlockWitness.fromBytes(header["witness"]!!.asByteArray())
                val digest = decodedHeader.toGtv().merkleHash(GtvMerkleHashCalculator(cryptoSystem))
                witness.getSignatures().forEach { signature ->
                    assertTrue(cryptoSystem.verifyDigest(digest, signature))
                }
            }
        }
    }

    override fun addNodeConfigurationOverrides(nodeSetup: NodeSetup) {
        super.addNodeConfigurationOverrides(nodeSetup)
        nodeSetup.nodeSpecificConfigs.setProperty("infrastructure", D1TestInfrastructureFactory::class.qualifiedName)
    }

    private fun query(node: PostchainTestNode, ctxt: EContext, name: String, args: Gtv, anchorChainId: Long): Gtv =
            node.getModules(anchorChainId).find { it.javaClass.simpleName.startsWith("Rell") }!!
                    .query(ctxt, name, args)
}