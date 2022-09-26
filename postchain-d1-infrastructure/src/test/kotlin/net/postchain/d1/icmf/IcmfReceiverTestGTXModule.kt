package net.postchain.d1.icmf

import net.postchain.base.data.DatabaseAccess
import net.postchain.core.EContext
import net.postchain.core.TxEContext
import net.postchain.d1.icmf.IcmfReceiverTestGTXModule.Companion.testMessageTable
import net.postchain.d1.icmf.IcmfRemoteSpecialTxExtension.Companion.OP_ICMF_MESSAGE
import net.postchain.gtv.GtvEncoder
import net.postchain.gtx.GTXOperation
import net.postchain.gtx.SimpleGTXModule
import net.postchain.gtx.data.ExtOpData
import org.apache.commons.dbutils.QueryRunner

class IcmfReceiverTestGTXModule : SimpleGTXModule<Unit>(
        Unit,
        mapOf(OP_ICMF_MESSAGE to ::IcmfMessageOp),
        mapOf()
) {

    companion object {
        const val testMessageTable = "test_messages"
    }

    override fun initializeDB(ctx: EContext) {
        DatabaseAccess.of(ctx).apply {
            val queryRunner = QueryRunner()
            queryRunner.update(
                    ctx.conn,
                    "CREATE TABLE IF NOT EXISTS ${tableName(ctx, testMessageTable)} (id BIGSERIAL PRIMARY KEY, sender BYTEA, topic TEXT, body BYTEA)"
            )
        }
    }

}

class IcmfMessageOp(u: Unit, private val opdata: ExtOpData) : GTXOperation(opdata) {
    override fun isCorrect(): Boolean {
        return true
    }

    override fun apply(ctx: TxEContext): Boolean {
        DatabaseAccess.of(ctx).apply {
            val queryRunner = QueryRunner()
            queryRunner.update(
                    ctx.conn,
                    "INSERT INTO ${tableName(ctx, testMessageTable)} (sender, topic, body) VALUES (?, ?, ?)",
                    opdata.args[0].asByteArray(),
                    opdata.args[1].asString(),
                    GtvEncoder.encodeGtv(opdata.args[2])
            )
        }
        return true
    }
}
