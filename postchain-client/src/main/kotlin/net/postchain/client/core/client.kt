// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.client.core

import net.postchain.common.BlockchainRid
import net.postchain.crypto.Secp256K1CryptoSystem
import net.postchain.crypto.SigMaker
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvDictionary
import net.postchain.gtx.GTXDataBuilder
import net.postchain.common.tx.TransactionStatus
import nl.komponents.kovenant.Promise

class GTXTransactionBuilder(private val client: PostchainClient, blockchainRID: BlockchainRid, signers: Array<ByteArray>) {

    private val dataBuilder = GTXDataBuilder(blockchainRID, signers, Secp256K1CryptoSystem())

    fun addOperation(opName: String, vararg args: Gtv) {
        dataBuilder.addOperation(opName, arrayOf(*args))
    }

    fun sign(sigMaker: SigMaker) {
        if (!dataBuilder.finished) {
            dataBuilder.finish()
        }
        dataBuilder.addSignature(sigMaker.signDigest(dataBuilder.getDigestForSigning()))
    }

    fun post(confirmationLevel: ConfirmationLevel): Promise<TransactionAck, Exception> {
        return client.postTransaction(dataBuilder, confirmationLevel)
    }

    fun postSync(confirmationLevel: ConfirmationLevel): TransactionAck {
        return client.postTransactionSync(dataBuilder, confirmationLevel)
    }
}
/**
 * Holds the acknowledgement message from the Postchain Server
 */
interface TransactionAck {
    val status: TransactionStatus
}

interface PostchainClient {
    fun makeTransaction(): GTXTransactionBuilder
    fun makeTransaction(signers: Array<ByteArray>): GTXTransactionBuilder

    fun postTransaction(txBuilder: GTXDataBuilder, confirmationLevel: ConfirmationLevel): Promise<TransactionAck, Exception>
    fun postTransactionSync(txBuilder: GTXDataBuilder, confirmationLevel: ConfirmationLevel): TransactionAck

    fun query(name: String, gtv: Gtv = GtvDictionary.build(mapOf())): Promise<Gtv, Exception>
    fun querySync(name: String, gtv: Gtv = GtvDictionary.build(mapOf())): Gtv

}

interface PostchainNodeResolver {
    fun getNodeURL(blockchainRID: BlockchainRid): String
}

class DefaultSigner(val sigMaker: SigMaker, val pubkey: ByteArray)
