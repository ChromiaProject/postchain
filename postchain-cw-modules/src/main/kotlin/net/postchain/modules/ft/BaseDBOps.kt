// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.modules.ft

import net.postchain.core.EContext
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvDecoder.decodeGtv
import net.postchain.modules.esplix_r4.DbUtils.tableName
import org.apache.commons.dbutils.QueryRunner
import org.apache.commons.dbutils.handlers.MapListHandler
import org.apache.commons.dbutils.handlers.ScalarHandler

open class BaseDBOps : FTDBOps {

    private val r = QueryRunner()
    private val nullableByteArrayRes = ScalarHandler<ByteArray?>()
    private val longHandler = ScalarHandler<Long>()
    private val nullableLongHandler = ScalarHandler<Long?>()
    private val unitHandler = ScalarHandler<Unit>()
    private val mapListHandler = MapListHandler()

    override fun update(ctx: OpEContext, accountID: ByteArray, assetID: String, amount: Long, memo: String?, allowNeg: Boolean) {
        r.query(ctx.txCtx.conn, "SELECT ${tableName(ctx.txCtx, "ft_update")}(?, ?, ?, ?, ?, ?, ?, ?)", unitHandler,
                ctx.txCtx.chainID,
                ctx.txCtx.txIID,
                ctx.opIndex,
                accountID, assetID, amount,
                memo,
                allowNeg)
    }

    override fun getDescriptor(ctx: EContext, accountID: ByteArray): Gtv? {
        val res = r.query(ctx.conn, "SELECT ${tableName(ctx, "ft_get_account_desc")}(?, ?)",
                nullableByteArrayRes, ctx.chainID, accountID)
        return if (res == null) null else decodeGtv(res)
    }

    override fun registerAccount(ctx: OpEContext, accountID: ByteArray, accountType: Int, accountDesc: ByteArray) {
        r.query(ctx.txCtx.conn, "SELECT ${tableName(ctx.txCtx, "ft_register_account")}(?, ?, ?, ?, ?, ?)", unitHandler,
                ctx.txCtx.chainID,
                ctx.txCtx.txIID,
                ctx.opIndex,
                accountID,
                accountType,
                accountDesc)
    }

    override fun getBalance(ctx: EContext, accountID: ByteArray, assetID: String): Long {
        return r.query(ctx.conn, "SELECT ${tableName(ctx, "ft_get_balance")}(?, ?, ?)", longHandler,
                ctx.chainID,
                accountID,
                assetID)
    }

    override fun getHistory(ctx: EContext, accountID: ByteArray, assetID: String): List<HistoryEntry> {
        val res = r.query(ctx.conn, "SELECT * FROM ${tableName(ctx, "ft_get_history")}(?, ?, ?)", mapListHandler,
                ctx.chainID,
                accountID,
                assetID)
        return res.map {
            HistoryEntry(
                    it["delta"] as Long,
                    it["tx_rid"] as ByteArray,
                    it["op_index"] as Int,
                    it["memo"]?.toString()
            )
        }
    }

    override fun registerAsset(ctx: OpEContext, assetID: String) {
        if (r.query(ctx.txCtx.conn,
                        "SELECT ${tableName(ctx.txCtx, "ft_find_asset")}(?, ?)",
                        nullableLongHandler,
                        ctx.txCtx.chainID,
                        assetID) == null) {
            r.query(ctx.txCtx.conn,
                    "SELECT ${tableName(ctx.txCtx, "ft_register_asset")}(?, ?)",
                    unitHandler,
                    ctx.txCtx.chainID, assetID)
        }
    }
}