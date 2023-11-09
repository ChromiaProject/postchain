// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.configurations

import mu.KLogging
import net.postchain.base.data.DatabaseAccess
import net.postchain.common.exception.UserMistake
import net.postchain.core.EContext
import net.postchain.core.TxEContext
import net.postchain.gtv.GtvDictionary
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.GtvNull
import net.postchain.gtx.GTXOperation
import net.postchain.gtx.GTXSchemaManager
import net.postchain.gtx.SimpleGTXModule
import net.postchain.gtx.data.ExtOpData
import org.apache.commons.dbutils.QueryRunner
import org.apache.commons.dbutils.handlers.ColumnListHandler
import org.apache.commons.dbutils.handlers.ScalarHandler

// TODO: [POS-128]: Refactor this

const val GTX_TEST_OP_NAME = "gtx_test"
const val GTX_TEST_QUERY_NAME = "gtx_test_get_value"

private val r = QueryRunner()
private val nullableStringReader = ScalarHandler<String?>()

internal fun table_gtx_test_value(ctx: EContext): String {
    val db = DatabaseAccess.of(ctx)
    return db.tableName(ctx, "gtx_test_value")
}

private fun table_transactions(ctx: EContext): String {
    val db = DatabaseAccess.of(ctx)
    return db.tableName(ctx, "transactions")
}

class GTXTestOp(@Suppress("UNUSED_PARAMETER") u: Unit, opdata: ExtOpData) : GTXOperation(opdata) {

    /**
     * The only way for the [GTXTestOp] to be considered correct is if first argument is "1" and the second is a string.
     */
    override fun checkCorrectness() {
        if (data.args.size != 2) throw UserMistake("expected 2 arguments")
        data.args[1].asString()
        if (data.args[0].asInteger() != 1L) throw UserMistake("expected first argument to be integer 1")
    }

    override fun apply(ctx: TxEContext): Boolean {
        if (data.args[1].asString() == "rejectMe")
            throw UserMistake("You were asking for it")

        try {
            r.update(
                    ctx.conn,
                    """INSERT INTO ${table_gtx_test_value(ctx)}(tx_iid, op_idx, value) VALUES (?,?, ?)""",
                    ctx.txIID, data.opIndex, data.args[1].asString()
            )
        } catch (e: Exception) {
            throw e // Just a good spot to place breakpoint
        }
        return true
    }
}

/**
 * A simple module that has its own table where it can store and read things. Useful for testing all the way down to DB.
 */
class GTXTestModule : SimpleGTXModule<Unit>(Unit,
        mapOf(GTX_TEST_OP_NAME to ::GTXTestOp),
        mapOf(GTX_TEST_QUERY_NAME to { _, ctxt, args ->
            val txRID = (args as GtvDictionary).get("txRID")
                    ?: throw UserMistake("No txRID property supplied")

            val sql = """
                SELECT value FROM ${table_gtx_test_value(ctxt)} g
                INNER JOIN ${table_transactions(ctxt)} t ON g.tx_iid=t.tx_iid
                WHERE t.tx_rid = ?
            """.trimIndent()
            val value = r.query(ctxt.conn, sql, ColumnListHandler<String>(), txRID.asByteArray(true))
            gtv( value.map { gtv(it) })
        })
) {
    companion object : KLogging()

    override fun initializeDB(ctx: EContext) {
        val moduleName = this::class.qualifiedName!!
        val version = GTXSchemaManager.getModuleVersion(ctx, moduleName)
        logger.info("initializeDB version = $version")
        if (version == null) {
            val sql = "CREATE TABLE ${table_gtx_test_value(ctx)}(tx_iid BIGINT, op_idx INTEGER, value TEXT NOT NULL," +
                    "PRIMARY KEY (tx_iid, op_idx))"
            r.update(ctx.conn, sql)
            GTXSchemaManager.setModuleVersion(ctx, moduleName, 0)
        }
    }
}
