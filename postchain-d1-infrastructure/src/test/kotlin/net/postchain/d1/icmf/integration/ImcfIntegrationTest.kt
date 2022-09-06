package net.postchain.d1.icmf.integration

import net.postchain.base.data.DatabaseAccess
import net.postchain.base.withReadConnection
import net.postchain.d1.icmf.IcmfTestTransaction
import net.postchain.devtools.utils.GtxTxIntegrationTestSetup
import net.postchain.devtools.utils.configuration.SystemSetup
import net.postchain.base.gtv.BlockHeaderDataFactory
import net.postchain.d1.icmf.ICMF_BLOCK_HEADER_EXTRA
import org.apache.commons.dbutils.QueryRunner
import org.junit.jupiter.api.Test
import net.postchain.d1.icmf.tableMessages
import org.apache.commons.dbutils.handlers.MapListHandler
import kotlin.test.assertEquals

private const val CHAIN_ID = 1

class ImcfIntegrationTest : GtxTxIntegrationTestSetup() {

    @Test
    fun icmfDummy() {
        val mapBcFiles: Map<Int, String> = mapOf(
            CHAIN_ID to "/net/postchain/icmf/integration/blockchain_config_1.xml",
        )

        val sysSetup = SystemSetup.buildComplexSetup(mapBcFiles)

        runXNodes(sysSetup)

        buildBlock(CHAIN_ID.toLong(), 0, IcmfTestTransaction(0))

        for (node in nodes) {
            withReadConnection(node.postchainContext.storage, CHAIN_ID.toLong()) {
                val db = DatabaseAccess.of(it)
                val queryRunner = QueryRunner()

                val res1 = queryRunner.query(
                    it.conn,
                    "SELECT block_height, prev_message_block_height FROM ${db.tableMessages(it)}",
                    MapListHandler()
                )
                assertEquals(1, res1.size)
                assertEquals(0L, res1[0]["block_height"])
                assertEquals(-1L, res1[0]["prev_message_block_height"])

                val blockQueries = node.getBlockchainInstance(CHAIN_ID.toLong()).blockchainEngine.getBlockQueries()
                val blockRid = blockQueries.getBlockRid(0).get()
                val blockHeader = blockQueries.getBlockHeader(blockRid!!).get()
                val decodedHeader = BlockHeaderDataFactory.buildFromBinary(blockHeader.rawData)
                assertEquals("hashhash", decodedHeader.gtvExtra[ICMF_BLOCK_HEADER_EXTRA]!!.asString())
            }
        }
    }
}
