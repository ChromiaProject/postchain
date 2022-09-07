package net.postchain.d1.icmf

import net.postchain.base.BaseBlockBuilderExtension
import net.postchain.base.data.DatabaseAccess
import net.postchain.core.EContext
import net.postchain.crypto.Secp256K1CryptoSystem
import net.postchain.gtx.SimpleGTXModule
import net.postchain.gtx.special.GTXSpecialTxExtension
import org.apache.commons.dbutils.QueryRunner

fun DatabaseAccess.tableMessages(ctx: EContext) = tableName(ctx, "${IcmfGTXModule.PREFIX}.messages")

class IcmfGTXModule : SimpleGTXModule<Unit>(Unit, mapOf(), mapOf()) {

    companion object {
        const val PREFIX: String = "sys.x.icmf" // This name should not clash with rell
    }

    override fun initializeDB(ctx: EContext) {
        DatabaseAccess.of(ctx).apply {
            val queryRunner = QueryRunner()
            val createMessageTableSql = "CREATE TABLE ${tableMessages(ctx)}" +
                    " (message_iid BIGSERIAL PRIMARY KEY," +
                    " block_height BIGINT NOT NULL, " +
                    " prev_message_block_height BIGINT NOT NULL, " +
                    " tx_iid BIGINT NOT NULL REFERENCES ${tableName(ctx, "transactions")}(tx_iid), " +
                    " topic TEXT NOT NULL, " +
                    " body BYTEA NOT NULL)"
            queryRunner.update(ctx.conn, createMessageTableSql)
        }
    }

    override fun makeBlockBuilderExtensions(): List<BaseBlockBuilderExtension> {
        return listOf(IcmfBlockBuilderExtension(Secp256K1CryptoSystem()))
    }

    override fun getSpecialTxExtensions(): List<GTXSpecialTxExtension> {
        return listOf()
    }
}