package net.postchain.client.transaction

import net.postchain.client.core.PostchainClient
import net.postchain.common.BlockchainRid
import net.postchain.crypto.CryptoSystem
import net.postchain.crypto.Secp256K1CryptoSystem
import net.postchain.crypto.SigMaker
import net.postchain.gtv.Gtv
import net.postchain.gtx.Gtx
import net.postchain.gtx.GtxBuilder

class TransactionBuilder(
    private val client: PostchainClient,
    blockchainRid: BlockchainRid,
    signers: List<ByteArray>,
    private val defaultSigners: List<SigMaker> = listOf(),
    cryptoSystem: CryptoSystem = Secp256K1CryptoSystem(),
) {
    private val gtxBuilder = GtxBuilder(blockchainRid, signers, cryptoSystem)

    /**
     * Adds an operation to this transaction
     */
    fun addOperation(name: String, vararg args: Gtv) = apply {
        gtxBuilder.addOperation(name, *args)
    }

    /**
     * Adds a nop operation to make the transaction unique
     */
    fun addNop() = apply { gtxBuilder.addNop() }

    /**
     * Sign this transaction with default signers and [PostchainClient.postTransaction]
     */
    fun post() = sign().post()

    /**
     * Sign this transaction with default signers and [PostchainClient.postTransactionSync]
     */
    fun postSync() = sign().postSync()

    /**
     * Sign this transaction with default signers and [PostchainClient.postTransactionSyncAwaitConfirmation]
     */
    fun postSyncAwaitConfirmation() = sign().postSyncAwaitConfirmation()

    /**
     * Sign this transaction with the [defaultSigners] and prepare it to be posted
     */
    fun sign() = sign(*defaultSigners.toTypedArray())

    /**
     * Sign this transaction and prepare it to be posted
     */
    fun sign(vararg sigMaker: SigMaker): PostableTransaction {
        return finish().apply {
            sigMaker.forEach { sign(it) }
        }.build()
    }

    /**
     * Marks this transaction as finished and ready to be signed
     */
    fun finish(): SignatureBuilder {
        return SignatureBuilder(gtxBuilder.finish())
    }

    inner class SignatureBuilder(private val signBuilder: GtxBuilder.GtxSignBuilder) {

        /**
         * Sign this transaction
         */
        fun sign(sigMaker: SigMaker) = apply {
            signBuilder.sign(sigMaker)
        }

        /**
         * Build a transaction that can be posted
         */
        fun build(): PostableTransaction {
            return PostableTransaction(buildGtx())
        }

        /**
         * Build Gtx
         */
        fun buildGtx() = signBuilder.buildGtx()
    }

    inner class PostableTransaction(private val tx: Gtx) {

        /**
         * [PostchainClient.postTransaction]
         */
        fun post() = client.postTransaction(tx)

        /**
         * [PostchainClient.postTransactionSync]
         */
        fun postSync() = client.postTransactionSync(tx)

        /**
         * [PostchainClient.postTransactionSyncAwaitConfirmation]
         */
        fun postSyncAwaitConfirmation() = client.postTransactionSyncAwaitConfirmation(tx)
    }
}
