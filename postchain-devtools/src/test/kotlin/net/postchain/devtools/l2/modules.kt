package net.postchain.devtools.l2

import net.postchain.base.data.DatabaseAccess
import net.postchain.base.l2.L2TxEContext
import net.postchain.core.EContext
import net.postchain.core.TxEContext
import net.postchain.gtv.GtvByteArray
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.GtvInteger
import net.postchain.gtx.ExtOpData
import net.postchain.gtx.GTXOperation
import net.postchain.gtx.GTXSchemaManager
import net.postchain.gtx.SimpleGTXModule
import org.apache.commons.dbutils.QueryRunner

private val r = QueryRunner()

private fun table_l2_test_tnx(ctx: EContext): String {
    val db = DatabaseAccess.of(ctx)
    return db.tableName(ctx, "l2_test_tnx")
}

class L2EventOp(u: Unit, opdata: ExtOpData) : GTXOperation(opdata) {

    override fun isCorrect(): Boolean {
        if (data.args.size != 2) return false
        return true
    }

    override fun apply(ctx: TxEContext): Boolean {
        L2TxEContext.emitL2Event(ctx,
            gtv(
                GtvInteger(data.args[0].asInteger()),
                GtvByteArray(data.args[1].asByteArray())
            )
        )
        return true
    }
}

class L2StateOp(u: Unit, opdata: ExtOpData) : GTXOperation(opdata) {

    override fun isCorrect(): Boolean {
        if (data.args.size != 3) return false
        return true
    }

    override fun apply(ctx: TxEContext): Boolean {
        L2TxEContext.emitL2AccountState(ctx, data.args[0].asInteger(),
            gtv(
                GtvInteger(data.args[1].asInteger()),
                GtvByteArray(data.args[2].asByteArray())
            )
        )
        return true
    }
}

class L2TransferOp(u: Unit, opdata: ExtOpData) : GTXOperation(opdata) {

    override fun isCorrect(): Boolean {
        if (data.args.size != 9) return false
        return true
    }

    override fun apply(ctx: TxEContext): Boolean {
        r.update(ctx.conn,
            """INSERT INTO ${table_l2_test_tnx(ctx)}(blockNumber, blockHash, tnxHash, logIndex, 
                |eventSignature, contractAddress, fromAddress, toAddress, value) 
                |VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)""".trimMargin(),
            data.args[0].asInteger(), data.args[1].asString(), data.args[2].asString(), data.args[3].asInteger(),
            data.args[4].asString(), data.args[5].asString(), data.args[6].asString(), data.args[7].asString(), data.args[8].asInteger())
        return true
    }
}

class L2TestModule : SimpleGTXModule<Unit>(Unit,
    mapOf(
        "l2_event" to ::L2EventOp,
        "l2_state" to ::L2StateOp
    ),
    mapOf()
) {
    override fun initializeDB(ctx: EContext) {}
}

class L2TransferTestModule : SimpleGTXModule<Unit>(Unit,
    mapOf(
        "l2_event" to ::L2EventOp,
        "l2_state" to ::L2StateOp,
        "__l2_transfer" to ::L2TransferOp
    ),
    mapOf()
) {
    override fun initializeDB(ctx: EContext) {
        val moduleName = this::class.qualifiedName!!
        val version = GTXSchemaManager.getModuleVersion(ctx, moduleName)
        if (version == null) {
            val sql = """CREATE TABLE ${table_l2_test_tnx(ctx)}(
                |tx_iid BIGSERIAL PRIMARY KEY, 
                |blockNumber BIGINT,
                |blockHash TEXT NOT NULL, 
                |tnxHash TEXT NOT NULL, 
                |logIndex BIGINT,
                |eventSignature TEXT NOT NULL,
                |contractAddress TEXT NOT NULL,
                |fromAddress TEXT NOT NULL, 
                |toAddress TEXT NOT NULL, 
                |value BIGINT)""".trimMargin()
            r.update(ctx.conn, sql)
            GTXSchemaManager.setModuleVersion(ctx, moduleName, 0)
        }
    }
}