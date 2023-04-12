package net.postchain.gtx

import net.postchain.common.BlockchainRid
import net.postchain.common.exception.TransactionIncorrect
import net.postchain.common.exception.UserMistake
import net.postchain.common.toHex
import net.postchain.crypto.CryptoSystem
import net.postchain.crypto.Secp256K1CryptoSystem
import net.postchain.crypto.SigMaker
import net.postchain.crypto.Signature
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory
import net.postchain.gtv.merkle.GtvMerkleHashCalculator
import java.time.Instant

open class GtxBuilder(
    private val blockchainRid: BlockchainRid,
    private val signers: List<ByteArray>,
    private val cryptoSystem: CryptoSystem = Secp256K1CryptoSystem(),
) {
    private val calculator = GtvMerkleHashCalculator(cryptoSystem)
    private val operations = mutableListOf<GtxOp>()

    fun isEmpty() = operations.isEmpty()

    /**
     * Adds an operation to this transaction
     */
    fun addOperation(name: String, vararg args: Gtv) = apply {
        operations.add(GtxOp(name, *args))
    }

    /**
     * Adds a nop operation to make the transaction unique
     */
    fun addNop() = addOperation("nop", GtvFactory.gtv(Instant.now().toEpochMilli()))


    /**
     * Marks this transaction as finished and ready to be signed
     */
    fun finish(): GtxSignBuilder {
        val body = GtxBody(blockchainRid, operations, signers)
        return GtxSignBuilder(body)
    }


    inner class GtxSignBuilder(private val body: GtxBody, private val check: Boolean = true) {

        private val signatures = mutableListOf<Signature>()
        val txRid = body.calculateTxRid(calculator)

        fun isFullySigned() = signatures.size == body.signers.size

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
            if (check && !cryptoSystem.verifyDigest(txRid, signature)) {
                throw TransactionIncorrect(txRid, "Signature by ${signature.subjectID.toHex()} is not valid")
            }
            signatures.add(signature)
        }

        /**
         * Build a GTX
         */
        fun buildGtx() = Gtx(body, signatures.map { it.data })
    }
}
