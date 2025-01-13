package net.postchain.gtx

import net.postchain.common.BlockchainRid
import net.postchain.common.exception.TransactionIncorrect
import net.postchain.common.exception.UserMistake
import net.postchain.common.toHex
import net.postchain.crypto.CryptoSystem
import net.postchain.crypto.SigMaker
import net.postchain.crypto.Signature
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory
import net.postchain.gtv.merkle.GtvMerkleHashCalculatorBase
import java.time.Instant

// approximate conservative values since it is difficult to calculate tx size exactly
private const val OP_SIZE_OVERHEAD = 4
private const val TX_SIZE_OVERHEAD = 192

/**
 * @param maxTxSize maximal allowed transaction size in bytes, or -1 for no limit
 */
open class GtxBuilder(
        private val blockchainRid: BlockchainRid,
        private val signers: List<ByteArray>,
        private val cryptoSystem: CryptoSystem,
        private val calculator: GtvMerkleHashCalculatorBase,
        val maxTxSize: Int = -1,
        operations: List<GtxOp> = listOf()
) {
    companion object {
        val EMPTY_SIGNATURE = ByteArray(0)
    }

    private val operations = mutableListOf<GtxOp>()

    internal var totalSize: Int = TX_SIZE_OVERHEAD

    init {
        operations.forEach { op ->
            addOperation(op.opName, *op.args)
        }
    }

    fun isEmpty() = operations.isEmpty()

    /**
     * Adds an operation to this transaction
     *
     * @throws IllegalStateException if the operation does not fit
     */
    fun addOperation(name: String, vararg args: Gtv) = apply {
        val op = GtxOp(name, *args)
        if (maxTxSize > 0) {
            val opSize = op.calcSize() + OP_SIZE_OVERHEAD
            if (totalSize + opSize > maxTxSize) {
                throw IllegalStateException("Operation does not fit, tx would be ${totalSize + opSize} bytes, but maxTxSize is $maxTxSize bytes")
            } else {
                totalSize += opSize
            }
        }
        operations.add(op)
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

    /**
     * Marks this transaction as sign-able but the signatures won't be checked.
     */
    fun uncheckedSignBuilder(): GtxSignBuilder {
        val body = GtxBody(blockchainRid, operations, signers)
        return GtxSignBuilder(body,false)
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
            if (check) {
                check(signature)
            }
            signatures.add(signature)
        }

        private fun check(signature: Signature) {
            if (!cryptoSystem.verifyDigest(txRid, signature)) {
                throw TransactionIncorrect(txRid, "Signature by ${signature.subjectID.toHex()} is not valid")
            }
        }

        /**
         * Add empty signature for subjectID. Only successful if [check] = false
         */
        fun emptySign(subjectID: ByteArray) = apply {
            sign(Signature(subjectID, EMPTY_SIGNATURE))
        }

        /**
         * Replace empty signature.
         */
        fun signOverEmptySignature(sigMaker: SigMaker) = apply {
            val newSignature = sigMaker.signDigest(txRid)
            check(newSignature)
            val index = signatures.indexOfFirst { it.subjectID.contentEquals(newSignature.subjectID) && it.data.contentEquals(EMPTY_SIGNATURE) }

            if (index == -1) {
                throw UserMistake("No empty signature found for the given subject ID")
            }

            signatures[index] = newSignature
        }

        /**
         * Add signatures for [signers].
         *
         * @param signatures List of respective signatures for all [signers]
         */
        fun addSignatures(signatures: List<ByteArray>) = apply {
            signatures.forEachIndexed{index, signature -> sign(Signature(signers[index], signature))}
        }

        /**
         * Build a GTX
         */
        fun buildGtx() = Gtx(body, signatures.map { it.data })
    }
}
