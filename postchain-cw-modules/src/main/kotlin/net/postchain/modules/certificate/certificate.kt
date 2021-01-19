// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.modules.certificate

import net.postchain.core.TxEContext
import net.postchain.core.UserMistake
import net.postchain.gtx.ExtOpData
import net.postchain.gtx.GTXOperation
import org.apache.commons.dbutils.QueryRunner
import org.apache.commons.dbutils.handlers.ScalarHandler

class certificate_op (val config: CertificateConfig, data: ExtOpData): GTXOperation(data) {
    val id = data.args[0].asString()
    val name = data.args[1].asString()
    val pubkey = data.args[2].asByteArray()
    val expires = data.args[3].asInteger()
    val authority = data.args[4].asByteArray()
    val reason = data.args[5].asByteArray()

    private val r = QueryRunner()
    private val unitHandler = ScalarHandler<Unit>()

    override fun isCorrect(): Boolean {
        if (data.args.size != 6)
            return false
        if (!data.signers.any { signer ->
                    signer.contentEquals(authority)
                })
            return false

        if (pubkey.size != 33) return false
        if (expires < 0) return false
        if (authority.size != 33) return false
        return true
    }

    override fun apply(ctx: TxEContext): Boolean {
        if (ctx.timestamp >= expires)
            throw UserMistake("Certificate already expired")
        r.update(ctx.conn, "INSERT INTO certificate (id, name, pubkey, expires, authority, reason) " +
                "values(?, ?, ?, ?, ?, ?)",  id, name, pubkey, expires, authority, reason)
        return true
    }
}