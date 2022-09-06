package net.postchain.d1.icmf

import net.postchain.base.BaseBlockBuilderExtension
import net.postchain.base.data.DatabaseAccess
import net.postchain.core.EContext
import net.postchain.gtx.SimpleGTXModule
import net.postchain.gtx.special.GTXSpecialTxExtension
import org.apache.commons.dbutils.QueryRunner

class IcmfGTXModule : SimpleGTXModule<Unit>(Unit, mapOf(), mapOf()) {

    companion object {
        const val PREFIX: String = "sys.x.icmf" // This name should not clash with rell
    }

    override fun initializeDB(ctx: EContext) {
        DatabaseAccess.of(ctx).apply {
            val queryRunner = QueryRunner()
            val createMessageTableSql = "CREATE TABLE ${tableName(ctx, "${PREFIX}.messages")}" +
                    " (message_iid BIGSERIAL PRIMARY KEY," +
                    " block_height BIGINT NOT NULL, " +
                    " prev_message_block_height BIGINT NOT NULL, " +
                    " tx_iid BIGINT NOT NULL REFERENCES ${tableName(ctx, "transactions")}(tx_iid), " +
                    " body BYTEA NOT NULL)"
            queryRunner.update(ctx.conn, createMessageTableSql)
        }
    }

    override fun makeBlockBuilderExtensions(): List<BaseBlockBuilderExtension> {
        return listOf(IcmfBlockBuilderExtension())
    }

    /**
     * We need to write our own special type of operation for each header message we get.
     * That's the responsibility of [AnchorSpecialTxExtension]
     */
    override fun getSpecialTxExtensions(): List<GTXSpecialTxExtension> {
        return listOf()
    }
}