package net.postchain.eif

import net.postchain.base.data.DatabaseAccess
import net.postchain.core.EContext
import net.postchain.core.TxEContext
import net.postchain.gtv.GtvByteArray
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.GtvInteger
import net.postchain.gtv.GtvNull
import net.postchain.gtx.ExtOpData
import net.postchain.gtx.GTXOperation
import net.postchain.gtx.GTXSchemaManager
import net.postchain.gtx.SimpleGTXModule
import org.apache.commons.dbutils.QueryRunner
import org.apache.commons.dbutils.handlers.MapListHandler
import java.math.BigInteger

private val r = QueryRunner()

private val mapListHandler = MapListHandler()

private fun table_eth_event(ctx: EContext): String {
    val db = DatabaseAccess.of(ctx)
    return db.tableName(ctx, "eth_events")
}

class EifEventOp(u: Unit, opdata: ExtOpData) : GTXOperation(opdata) {

    override fun isCorrect(): Boolean {
        if (data.args.size != 2) return false
        return true
    }

    override fun apply(ctx: TxEContext): Boolean {
        ctx.emitEvent(
            "eif_event",
            gtv(
                GtvInteger(data.args[0].asInteger()),
                GtvByteArray(data.args[1].asByteArray())
            )
        )

        return true
    }
}

class EifStateOp(u: Unit, opdata: ExtOpData) : GTXOperation(opdata) {

    override fun isCorrect(): Boolean {
        if (data.args.size != 3) return false
        return true
    }

    override fun apply(ctx: TxEContext): Boolean {
        ctx.emitEvent(
            "eif_state",
            gtv(
                gtv(data.args[0].asInteger()),
                gtv(
                    GtvInteger(data.args[1].asInteger()),
                    GtvByteArray(data.args[2].asByteArray())
                )
            )
        )

        return true
    }
}

class EifTransferOp(u: Unit, opdata: ExtOpData) : GTXOperation(opdata) {

    override fun isCorrect(): Boolean {
        if (data.args.size != 9) return false
        return true
    }

    override fun apply(ctx: TxEContext): Boolean {
        r.update(ctx.conn,
            """INSERT INTO ${table_eth_event(ctx)}(block_number, block_hash, tnx_hash, log_index, 
                |event_signature, contract_address, from_address, to_address, value) 
                |VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)""".trimMargin(),
            data.args[0].asInteger(), data.args[1].asString(), data.args[2].asString(), data.args[3].asInteger(),
            data.args[4].asString(), data.args[5].asString(), data.args[6].asString(), data.args[7].asString(), data.args[8].asBigInteger())
        return true
    }
}

class EifTestModule : SimpleGTXModule<Unit>(Unit,
    mapOf(
        "eif_event" to ::EifEventOp,
        "eif_state" to ::EifStateOp
    ),
    mapOf()
) {
    override fun initializeDB(ctx: EContext) {}
}

class EifTransferTestModule : SimpleGTXModule<Unit>(Unit,
    mapOf(
        "eif_event" to ::EifEventOp,
        "eif_state" to ::EifStateOp,
        "__eth_event" to ::EifTransferOp
    ),
    mapOf("get_last_eth_block" to { _, ctx, _ ->
        val sql = "SELECT LIMIT 1 block_number, block_hash FROM ${table_eth_event(ctx)} ORDER BY block_number DESC"
        val res = r.query(ctx.conn, sql, mapListHandler)
        when (res.size) {
            1 -> gtv(mutableMapOf(
                "eth_block_height" to gtv(res[0]["block_number"] as BigInteger),
                "eth_block_hash" to gtv(res[0]["block_hash"] as String)
            ))
            else -> GtvNull
        }
    })
) {
    override fun initializeDB(ctx: EContext) {
        val moduleName = this::class.qualifiedName!!
        val version = GTXSchemaManager.getModuleVersion(ctx, moduleName)
        if (version == null) {
            val sql = """CREATE TABLE ${table_eth_event(ctx)}(
                |tx_iid BIGSERIAL PRIMARY KEY, 
                |block_number BIGINT,
                |block_hash TEXT NOT NULL, 
                |tnx_hash TEXT NOT NULL, 
                |log_index BIGINT,
                |event_signature TEXT NOT NULL,
                |contract_address TEXT NOT NULL,
                |from_address TEXT NOT NULL, 
                |to_address TEXT NOT NULL, 
                |value BIGINT)""".trimMargin()
            r.update(ctx.conn, sql)
            GTXSchemaManager.setModuleVersion(ctx, moduleName, 0)
        }
    }
}