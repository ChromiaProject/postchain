package net.postchain.gtx

import net.postchain.common.BlockchainRid
import net.postchain.common.exception.UserMistake
import net.postchain.crypto.CryptoSystem
import net.postchain.crypto.SigMaker
import net.postchain.crypto.Signature
import net.postchain.gtv.Gtv
import net.postchain.gtv.merkle.GtvMerkleHashCalculator

open class GtxBuilder(
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
     * Marks this transaction as finished and ready to be signed
     */
    fun finish(): GtxSignBuilder {
        val body = GtxBody(blockchainRid, operations, signers)
        return GtxSignBuilder(body)
    }


    inner class GtxSignBuilder(private val body: GtxBody, private val check: Boolean = false) {

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
    }
}
