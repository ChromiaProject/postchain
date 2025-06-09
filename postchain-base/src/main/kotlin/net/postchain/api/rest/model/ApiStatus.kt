// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.api.rest.model

import net.postchain.common.tx.TransactionStatus
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.mapper.ToGtv

class ApiStatus(val txStatus: TransactionStatus, val rejectReason: String? = null, val rejectTimestamp: Long? = null) : ToGtv {

    val status: String = txStatus.status

    init {
        if (txStatus != TransactionStatus.REJECTED) {
            check(rejectReason == null) {
                "rejectReason field can only be used with status: REJECTED"
            }
            check(rejectTimestamp == null) {
                "rejectTimestamp field can only be used with status: REJECTED"
            }
        }
    }

    override fun toGtv(): Gtv = if (txStatus == TransactionStatus.REJECTED) {
        gtv(mapOf(
                "status" to gtv(status),
                "rejectReason" to gtv(rejectReason ?: ""),
                "rejectTimestamp" to gtv(rejectTimestamp ?: -1)
        ))
    } else {
        gtv(mapOf(
                "status" to gtv(status)
        ))
    }
}
