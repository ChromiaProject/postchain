// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.modules.esplix_r4

import net.postchain.base.CryptoSystem
import net.postchain.core.TxEContext
import net.postchain.gtx.EMPTY_SIGNATURE
import net.postchain.gtx.ExtOpData
import net.postchain.gtx.GTXOperation
import net.postchain.modules.esplix_r4.DbUtils.tableName
import org.apache.commons.dbutils.QueryRunner
import org.apache.commons.dbutils.handlers.ScalarHandler

fun computeMessageID(cryptoSystem: CryptoSystem,
                     prevID: ByteArray, payload: ByteArray, signers: Array<ByteArray>): ByteArray {
    val signersCombined = if (signers.size > 0) {
        signers.reduce { it, acc -> it + acc }
    } else {
        EMPTY_SIGNATURE
    }
    return cryptoSystem.digest(prevID + payload + signersCombined)
}

class post_message_op(val config: EsplixConfig, data: ExtOpData) : GTXOperation(data) {

    val prevID = data.args[0].asByteArray()
    val payload = data.args[1].asByteArray()
    val messageID = computeMessageID(config.cryptoSystem, prevID, payload, data.signers)

    private val r = QueryRunner()
    private val unitHandler = ScalarHandler<Unit>()

    override fun isCorrect(): Boolean {
        if (data.args.size != 2)
            return false
        return true
    }

    override fun apply(ctx: TxEContext): Boolean {
        r.query(ctx.conn, "SELECT ${tableName(ctx, "r4_postMessage")}(?, ?, ?, ?, ?)", unitHandler,
                ctx.txIID, data.opIndex, messageID, prevID, payload)
        return true
    }
}
