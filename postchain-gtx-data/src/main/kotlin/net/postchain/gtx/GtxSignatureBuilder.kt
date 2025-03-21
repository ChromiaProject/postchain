package net.postchain.gtx

import net.postchain.common.data.Hash
import net.postchain.common.exception.TransactionIncorrect
import net.postchain.common.exception.UserMistake
import net.postchain.common.toHex
import net.postchain.crypto.CryptoSystem
import net.postchain.crypto.KeyPair
import net.postchain.crypto.SigMaker
import net.postchain.crypto.Signature
import net.postchain.gtx.GtxBuilder.Companion.EMPTY_SIGNATURE

class GtxSignatureBuilder(
        val body: GtxBody,
        val txRid: Hash,
        val cryptoSystem: CryptoSystem,
        private val check: Boolean = true
) {
    private val signatures = Array<Signature>(body.signers.size) { Signature(body.signers[it], ByteArray(0)) }

    fun isFullySigned() = signatures.none { it.data.contentEquals(EMPTY_SIGNATURE) }

    /**
     * Sign this transaction
     */
    fun sign(keyPair: KeyPair, forceCheck: Boolean = false) = apply {
        sign(cryptoSystem.buildSigMaker(keyPair), forceCheck)
    }

    /**
     * Sign this transaction
     */
    fun sign(sigMaker: SigMaker, forceCheck: Boolean = false) = apply {
        sign(sigMaker.signDigest(txRid), forceCheck)
    }

    /**
     * Add a signature to this transaction
     */
    fun sign(signature: Signature, forceCheck: Boolean = false) = apply {
        val index = body.signers.indexOfFirst { it.contentEquals(signature.subjectID) }
        if (index == -1) throw UserMistake("Signature belongs to unknown signer")
        if (!signatures[index].data.contentEquals(EMPTY_SIGNATURE)) throw UserMistake("Signature for this signer already exists")
        if (check || forceCheck) {
            check(signature)
        }
        signatures[index] = signature
    }

    internal fun check(signature: Signature) {
        if (!cryptoSystem.verifyDigest(txRid, signature)) {
            throw TransactionIncorrect(txRid, "Signature by ${signature.subjectID.toHex()} is not valid")
        }
    }

    /**
     * Add signatures for signers.
     *
     * @param signatures List of respective signatures for all signers
     */
    fun addSignatures(signatures: List<ByteArray>) = apply {
        signatures.forEachIndexed { index, signature -> sign(Signature(body.signers[index], signature)) }
    }

    /**
     * Build a GTX
     */
    fun buildGtx() = Gtx(body, signatures.map { it.data })
}
