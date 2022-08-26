package net.postchain.client.core

import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvDictionary
import net.postchain.gtx.data.GTXDataBuilder
import nl.komponents.kovenant.Promise

interface PostchainClient {
    fun makeTransaction(): GTXTransactionBuilder
    fun makeTransaction(signers: Array<ByteArray>): GTXTransactionBuilder

    fun postTransaction(txBuilder: GTXDataBuilder): Promise<TransactionResult, Exception>
    fun postTransactionSync(txBuilder: GTXDataBuilder): TransactionResult
    fun postTransactionSyncAwaitConfirmation(txBuilder: GTXDataBuilder): TransactionResult

    fun query(name: String, gtv: Gtv = GtvDictionary.build(mapOf())): Promise<Gtv, Exception>
    fun querySync(name: String, gtv: Gtv = GtvDictionary.build(mapOf())): Gtv

}