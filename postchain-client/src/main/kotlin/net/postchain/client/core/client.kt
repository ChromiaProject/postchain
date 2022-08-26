// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.client.core

import net.postchain.common.BlockchainRid
import net.postchain.common.tx.TransactionStatus
import net.postchain.crypto.Secp256K1CryptoSystem
import net.postchain.crypto.SigMaker
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvDictionary
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtx.data.GTXDataBuilder
import java.time.Instant
import java.util.concurrent.CompletionStage

class GTXTransactionBuilder(private val client: PostchainClient, blockchainRID: BlockchainRid, signers: Array<ByteArray>) {

    private val dataBuilder = GTXDataBuilder(blockchainRID, signers, Secp256K1CryptoSystem())

    fun addOperation(opName: String, vararg args: Gtv) = apply {
        dataBuilder.addOperation(opName, arrayOf(*args))
    }

    /** Add a "nop" operation with timestamp to make the transaction unique. */
    fun addNop() = apply {
        addOperation("nop", gtv(Instant.now().toEpochMilli()))
    }

    fun sign(sigMaker: SigMaker) = apply {
        if (!dataBuilder.finished) {
            dataBuilder.finish()
        }
        dataBuilder.addSignature(sigMaker.signDigest(dataBuilder.getDigestForSigning()))
    }

    fun finish() = apply { dataBuilder.finish() }

    fun post() = client.postTransaction(dataBuilder)

    fun postSync() = client.postTransactionSync(dataBuilder)

    fun postSyncAwaitConfirmation() = client.postTransactionSyncAwaitConfirmation(dataBuilder)
}

/**
 * Holds the acknowledgement message from the Postchain Server
 */
interface TransactionResult {
    val status: TransactionStatus
    val httpStatusCode: Int?
    val rejectReason: String? // Undefined if (status != TransactionStatus.REJECTED)
}


interface PostchainClient {
    fun makeTransaction(): GTXTransactionBuilder
    fun makeTransaction(signers: Array<ByteArray>): GTXTransactionBuilder

    fun postTransaction(txBuilder: GTXDataBuilder): CompletionStage<TransactionResult>
    fun postTransactionSync(txBuilder: GTXDataBuilder): TransactionResult
    fun postTransactionSyncAwaitConfirmation(txBuilder: GTXDataBuilder): TransactionResult

    fun query(name: String, gtv: Gtv = GtvDictionary.build(mapOf())): CompletionStage<Gtv>
    fun querySync(name: String, gtv: Gtv = GtvDictionary.build(mapOf())): Gtv

}

interface PostchainNodeResolver {
    fun getNodeURL(blockchainRID: BlockchainRid): String
}

class DefaultSigner(val sigMaker: SigMaker, val pubkey: ByteArray)
