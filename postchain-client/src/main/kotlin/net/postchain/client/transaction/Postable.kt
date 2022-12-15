package net.postchain.client.transaction

import net.postchain.client.core.TransactionResult

interface Postable {
    fun post(): TransactionResult
    fun postAwaitConfirmation(): TransactionResult
}
