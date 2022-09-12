package net.postchain.d1.icmf

import mu.KLogging
import net.postchain.base.BaseBlockBuilderExtension
import net.postchain.base.data.BaseBlockBuilder
import net.postchain.base.data.DatabaseAccess
import net.postchain.core.BlockEContext
import net.postchain.core.block.BlockBuilder
import net.postchain.crypto.CryptoSystem
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory.gtv
import org.apache.commons.dbutils.QueryRunner
import org.apache.commons.dbutils.handlers.MapListHandler
import org.apache.commons.dbutils.handlers.ScalarHandler

const val ICMF_BLOCK_HEADER_EXTRA = "icmf_send"

class IcmfBlockBuilderExtension : BaseBlockBuilderExtension {
    companion object : KLogging()

    private lateinit var cryptoSystem: CryptoSystem
    private lateinit var blockEContext: BlockEContext

    override fun init(blockEContext: BlockEContext, bb: BlockBuilder) {
        this.blockEContext = blockEContext
        val baseBB = bb as BaseBlockBuilder
        cryptoSystem = baseBB.cryptoSystem
    }

    /**
     * Called once at end of block building.
     *
     * @return extra data for block header
     */
    override fun finalize(): Map<String, Gtv> {
        DatabaseAccess.of(blockEContext).apply {
            // TODO: Try to implement this query in rell
            val queryRunner = QueryRunner()
            val prevMessageBlockHeight = queryRunner.query(
                blockEContext.conn,
                "SELECT MAX(block_height) FROM ${tableMessage(blockEContext)} WHERE block_height < ?",
                ScalarHandler<Long>(),
                blockEContext.height
            ) ?: -1

            val res = queryRunner.query(
                blockEContext.conn,
                "SELECT topic, body FROM ${tableMessage(blockEContext)} WHERE block_height = ?",
                MapListHandler(),
                blockEContext.height
            )

            val hashesByTopic = res
                .groupBy { it["topic"] as String }
                .mapValues { messages -> messages.value.map { message -> cryptoSystem.digest(message["body"] as ByteArray) } }
            val hashByTopic = hashesByTopic
                .mapValues {
                    gtv(mapOf("hash" to gtv(cryptoSystem.digest(it.value.fold(ByteArray(0)) { total, item ->
                        total.plus(
                            item
                        )
                    })), "prev_message_block_height" to gtv(prevMessageBlockHeight)))
                }
            return mapOf(ICMF_BLOCK_HEADER_EXTRA to gtv(hashByTopic))
        }
    }
}
