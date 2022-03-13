package net.postchain.d1.anchor

import mu.KLogging
import net.postchain.base.gtv.BlockHeaderDataFactory
import net.postchain.core.BlockRid
import net.postchain.core.BlockchainRid
import net.postchain.core.TxEContext
import net.postchain.gtx.ExtOpData
import net.postchain.gtx.GTXOperation
import org.apache.commons.dbutils.QueryRunner

private val r = QueryRunner()

/**
 * This operation corresponds to the "__anchor_block_header" special operation.
 *
 * NOTE:
 * This is for test only, IRL this will be in the Rell code.
 */
class AnchorTestOp(u: Unit, opdata: ExtOpData) : GTXOperation(opdata) {

    companion object : KLogging()

    override fun isSpecial(): Boolean = true

    override fun isCorrect(): Boolean {
        return if (data.args.size == 3) { // 1. BlockRid, 2. Header, 3. Witness-list
            true
        } else {
            logger.debug("Incorrect arguments to operation ${AnchorSpecialTxExtension.OP_BLOCK_HEADER}, expect 3 got ${data.args.size}")
            false
        }
    }

    /**
     * We will look into the header to find the block height and the BC RID.
     *
     * Insert these into the table.
     */
    override fun apply(ctx: TxEContext): Boolean {
        val blockRid = BlockRid(data.args[0].asByteArray())
        val header = BlockHeaderDataFactory.buildFromGtv(data.args[1])
        val realBcRid = BlockchainRid(header.getBlockchainRid())
        val height = header.gtvHeight.integer

        logger.debug("About to anchor block height: $height, bc: ${realBcRid.toShortHex()} to DB.")
        r.update(ctx.conn,
            """INSERT INTO ${table_anchor_blocks(ctx)} (blockchain_rid, block_height, block_rid, status)
                |VALUES (?, ?, ?, ?)""".trimMargin(),
            realBcRid.toHex(),
            height,
            blockRid.toHex(),
            0 // Not sure how to use this yet. Maybe update to 1 after event has been removed?
        )
        return true
    }
}
