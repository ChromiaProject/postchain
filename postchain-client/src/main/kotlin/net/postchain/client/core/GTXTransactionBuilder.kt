package net.postchain.client.core

import net.postchain.common.BlockchainRid
import net.postchain.crypto.Secp256K1CryptoSystem
import net.postchain.crypto.SigMaker
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory
import net.postchain.gtx.data.GTXDataBuilder
import java.time.Instant

class GTXTransactionBuilder(private val client: PostchainClient, blockchainRID: BlockchainRid, signers: Array<ByteArray>) {

    private val dataBuilder = GTXDataBuilder(blockchainRID, signers, Secp256K1CryptoSystem())

    fun addOperation(opName: String, vararg args: Gtv) = apply {
        dataBuilder.addOperation(opName, arrayOf(*args))
    }

    /** Add a "nop" operation with timestamp to make the transaction unique. */
    fun addNop() = apply {
        addOperation("nop", GtvFactory.gtv(Instant.now().toEpochMilli()))
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