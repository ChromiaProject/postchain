package net.postchain.d1.icmf

import net.postchain.base.data.DatabaseAccess
import net.postchain.core.EContext
import net.postchain.gtx.SimpleGTXModule
import net.postchain.gtx.special.GTXSpecialTxExtension
import org.apache.commons.dbutils.QueryRunner

const val PREFIX: String = "sys.x.icmf" // This name should not clash with Rell
fun DatabaseAccess.tableAnchorHeight(ctx: EContext) = tableName(ctx, "${PREFIX}.anchor_height")

class IcmfReceiverGTXModule(private val topics: List<String>) : SimpleGTXModule<Unit>(
    Unit,
    mapOf(),
    mapOf()
) {

    override fun initializeDB(ctx: EContext) {
        DatabaseAccess.of(ctx).apply {
            val queryRunner = QueryRunner()
            queryRunner.update(
                ctx.conn,
                "CREATE TABLE IF NOT EXISTS ${
                    tableAnchorHeight(ctx)
                } (cluster text PRIMARY KEY, height BIGINT NOT NULL)"
            )
        }
    }

    override fun getSpecialTxExtensions(): List<GTXSpecialTxExtension> = listOf(IcmfRemoteSpecialTxExtension(topics))
}
