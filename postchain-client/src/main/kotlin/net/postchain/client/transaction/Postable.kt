package net.postchain.client.transaction

import net.postchain.client.core.TransactionResult
import java.util.concurrent.CompletionStage

interface Postable {
    fun post(): CompletionStage<TransactionResult>
    fun postSync(): TransactionResult
    fun postSyncAwaitConfirmation(): TransactionResult
}
