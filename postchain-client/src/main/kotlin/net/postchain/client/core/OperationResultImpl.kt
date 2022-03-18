package net.postchain.client.core

import net.postchain.common.toHex
import net.postchain.core.TransactionStatus

class OperationResultImpl(override val status: TransactionStatus, override val txId: ByteArray): OperationResult {
    override fun toString() = "OperationResult(status=$status, txId=${txId.toHex()})"
}
