package net.postchain.modules.esplix_r4

import net.postchain.common.hexStringToByteArray
import net.postchain.core.EContext
import net.postchain.core.UserMistake
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory.gtv
import org.apache.commons.dbutils.QueryRunner
import org.apache.commons.dbutils.handlers.MapListHandler
import org.apache.commons.dbutils.handlers.ScalarHandler
import org.postgresql.jdbc.PgArray

class MessageEntry(val txData: ByteArray, val txRID: ByteArray, val opIndexes: Array<Int>)

fun getTxRIDQ(config: EsplixConfig, ctx: EContext, args: Gtv): Gtv {
    val r = QueryRunner()

    val messageID = args["messageID"]!!.asByteArray(true)
    val res = r.query(ctx.conn, "SELECT tx_rid FROM r4_messages m " +
            "INNER JOIN transactions t ON m.tx_iid = t.tx_iid " +
            "WHERE m.message_id = ?", ScalarHandler<ByteArray>(), messageID)
    return gtv("txRID" to gtv(res))
}

fun getMessagesQ(config: EsplixConfig, ctx: EContext, args: Gtv): Gtv {
    val r = QueryRunner()
    val mapListHandler = MapListHandler()

    val chainID = args["chainID"]?.asString()?.hexStringToByteArray()
    if (chainID == null)
        throw UserMistake("Invalid ChainID")

    var sinceMessageID : ByteArray? = null
    args["sinceMessageID"]?.let {
        if (!it.isNull())
            sinceMessageID = it.asString().hexStringToByteArray()
    }

    val maxHits = (args["maxHits"]?.asInteger() ?: 100).toInt()
    if (maxHits < 1 || maxHits > 1000) throw UserMistake("Invalid maxHits")

    fun getMessages(ctx: EContext, chainID: ByteArray, sinceMessageID: ByteArray?, maxHits: Int): List<MessageEntry> {
        val res = r.query(ctx.conn, "SELECT * FROM r4_getMessages(?, ?, ?)", mapListHandler,
                chainID,
                sinceMessageID,
                maxHits)
        return List<MessageEntry>(res.size, { index ->
            MessageEntry(
                    res[index]["tx_data"] as ByteArray,
                    res[index]["tx_rid"] as ByteArray,
                    (res[index]["op_indexes"] as PgArray).getArray() as Array<Int>
            )
        })
    }

    val messages = getMessages(ctx, chainID, sinceMessageID, maxHits)
    val result = messages.map {
        gtv("txData" to gtv(it.txData),
                "txRID" to gtv(it.txRID),
                "opIndexes" to gtv(*it.opIndexes.map {
                    gtv(it.toLong())
                }.toTypedArray()))
    }.toTypedArray()

    return gtv(*result)
}