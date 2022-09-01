package net.postchain.client.transaction

import net.postchain.client.core.PostchainClient
import net.postchain.common.BlockchainRid
import net.postchain.common.exception.UserMistake
import net.postchain.crypto.CryptoSystem
import net.postchain.crypto.SigMaker
import net.postchain.crypto.Signature
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.merkle.GtvMerkleHashCalculator
import net.postchain.gtx.Gtx
import net.postchain.gtx.GtxBody
import net.postchain.gtx.GtxOperation
import java.time.Instant

class TransactionBuilder(
    private val client: PostchainClient,
    private val blockchainRid: BlockchainRid,
    private val signers: List<ByteArray>,
    private val cryptoSystem: CryptoSystem,
) {
    private val calculator = GtvMerkleHashCalculator(cryptoSystem)
    private val operations = mutableListOf<GtxOperation>()

    /**
     * Adds an operation to this transaction
     */
    fun addOperation(name: String, vararg args: Gtv) = apply {
        operations.add(GtxOperation(name, *args))
    }
    /**
     * Adds a null operation to make the transaction unique
     */
    fun addNop() = addOperation("nop", gtv(Instant.now().toEpochMilli()))


    /**
     * Marks this transaction as finished and ready to be signed
     */
    fun finish(): SignatureBuilder {
        val body = GtxBody(blockchainRid, operations, signers)
        return SignatureBuilder(body)
    }

    /**
     * Sign this transaction and prepare it to be posted
     */
    fun sign(vararg sigMaker: SigMaker): PostableTransaction {
        return finish().apply {
            sigMaker.forEach { sign(it) }
        }.build()
    }

    inner class SignatureBuilder(private val body: GtxBody, private val check: Boolean = false) {

        private val signatures = mutableListOf<Signature>()
        private val txRid = body.calculateTxRid(calculator)

        /**
         * Sign this transaction
         */
        fun sign(sigMaker: SigMaker) = apply {
            sign(sigMaker.signDigest(txRid))
        }

        /**
         * Add a signature to this transaction
         */
        fun sign(signature: Signature) = apply {
            if (signatures.contains(signature)) throw UserMistake("Signature already exists")
            if (signers.find { it.contentEquals(signature.subjectID) } == null) throw UserMistake("Signature belongs to unknown signer")
            if (check && cryptoSystem.verifyDigest(txRid, signature)) {
                throw UserMistake("Signature ${signature.subjectID} is not valid")
            }
            signatures.add(signature)
        }

        /**
         * Build a GTX
         */
        fun buildGtx() = Gtx(body, signatures.map { it.data })

        /**
         * Build a transaction that can be posted
         */
        fun build(): PostableTransaction {
            return PostableTransaction(buildGtx())
        }
    }

    inner class PostableTransaction(private val tx: Gtx) {

        fun post() = client.postTransaction(tx)

        fun postSync() = client.postTransactionSync(tx)

        fun postSyncAwaitConfirmation() = client.postTransactionSyncAwaitConfirmation(tx)
    }
}
