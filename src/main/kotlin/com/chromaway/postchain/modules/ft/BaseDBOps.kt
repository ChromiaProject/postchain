package com.chromaway.postchain.modules.ft

import com.chromaway.postchain.gtx.GTXValue
import com.chromaway.postchain.gtx.decodeGTXValue
import org.apache.commons.dbutils.QueryRunner
import org.apache.commons.dbutils.handlers.MapListHandler
import org.apache.commons.dbutils.handlers.ScalarHandler

open class BaseDBOps: FTDBOps {

    private val r = QueryRunner()
    private val byteArrayRes = ScalarHandler<ByteArray>()
    private val longHandler = ScalarHandler<Long>()
    private val unitHandler = ScalarHandler<Unit>()
    private val mapListHandler = MapListHandler()

    override fun update(ctx: OpEContext, accountID: ByteArray, assetID: String, amount: Long, allowNeg: Boolean) {
        r.query(ctx.txCtx.conn, "SELECT ft_update_raw(?, ?, ?, ?, ?, ?, ?)", unitHandler,
                ctx.txCtx.chainID,
                ctx.txCtx.txIID,
                ctx.opIndex,
                accountID, assetID, amount, allowNeg)
    }

    override fun getDescriptor(ctx: OpEContext, accountID: ByteArray): GTXValue {
        return decodeGTXValue(r.query(ctx.txCtx.conn, "SELECT ft_get_account_desc(?, ?)",
                byteArrayRes, ctx.txCtx.chainID, accountID))
    }

    override fun registerAccount(ctx: OpEContext, accountID: ByteArray, accountType: Int, accountDesc: ByteArray) {
        r.query(ctx.txCtx.conn, "SELECT ft_register_account(?, ?, ?, ?, ?, ?)", unitHandler,
                ctx.txCtx.chainID,
                ctx.txCtx.txIID,
                ctx.opIndex,
                accountID,
                accountType,
                accountDesc)
    }

    override fun getBalance(ctx: OpEContext, accountID: ByteArray, assetID: String): Long {
        return r.query(ctx.txCtx.conn, "SELECT ft_get_balance(?, ?, ?)", longHandler,
                ctx.txCtx.chainID,
                accountID,
                assetID)
    }

    override fun getHistory(ctx: OpEContext, accountID: ByteArray, assetID: String): List<HistoryEntry> {
        val res = r.query(ctx.txCtx.conn, "SELECT ft_get_history(?, ?, ?)", mapListHandler,
                ctx.txCtx.chainID,
                accountID,
                assetID)
        return res.map {
            HistoryEntry(
                    it.get("delta") as Long,
                    it.get("txRID") as ByteArray,
                    it.get("op_index") as Int
            )
        }
    }
}