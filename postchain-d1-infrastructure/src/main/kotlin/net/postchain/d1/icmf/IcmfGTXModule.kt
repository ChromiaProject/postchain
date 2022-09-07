package net.postchain.d1.icmf

import net.postchain.base.BaseBlockBuilderExtension
import net.postchain.base.data.DatabaseAccess
import net.postchain.common.exception.UserMistake
import net.postchain.core.EContext
import net.postchain.crypto.Secp256K1CryptoSystem
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvDecoder
import net.postchain.gtv.GtvDictionary
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtx.SimpleGTXModule
import net.postchain.gtx.special.GTXSpecialTxExtension
import org.apache.commons.dbutils.QueryRunner
import org.apache.commons.dbutils.handlers.MapListHandler

fun DatabaseAccess.tableMessages(ctx: EContext) = tableName(ctx, "${IcmfGTXModule.PREFIX}.messages")

class IcmfGTXModule : SimpleGTXModule<Unit>(
    Unit,
    mapOf(),
    mapOf("icmf_get_all_messages" to ::getAllMessages, "icmf_get_messages" to ::getMessages)
) {

    companion object {
        const val PREFIX: String = "sys.x.icmf" // This name should not clash with Rell

        fun getAllMessages(u: Unit, ctx: EContext, args: Gtv): Gtv =
            fetchMessages(args, ctx, "topic = ? AND block_height >= ?")

        fun getMessages(u: Unit, ctx: EContext, args: Gtv): Gtv =
            fetchMessages(args, ctx, "topic = ? AND block_height = ?")

        private fun fetchMessages(args: Gtv, ctx: EContext, whereClause: String): Gtv {
            val topic = (args as GtvDictionary)["topic"]?.asString() ?: throw UserMistake("No topic supplied")
            val height = args["height"]?.asInteger() ?: throw UserMistake("No height supplied")
            DatabaseAccess.of(ctx).apply {
                val queryRunner = QueryRunner()
                val res = queryRunner.query(
                    ctx.conn,
                    "SELECT body FROM ${tableMessages(ctx)} WHERE $whereClause ORDER BY message_iid",
                    MapListHandler(), topic, height
                )
                return gtv(res.map { GtvDecoder.decodeGtv(it["body"] as ByteArray) })
            }
        }
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