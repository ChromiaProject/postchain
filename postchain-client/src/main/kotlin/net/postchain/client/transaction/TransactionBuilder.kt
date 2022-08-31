package net.postchain.gtx

import net.postchain.common.BlockchainRid
import net.postchain.common.exception.UserMistake
import net.postchain.crypto.CryptoSystem
import net.postchain.crypto.Signature
import net.postchain.gtv.Gtv
import net.postchain.gtv.merkle.GtvMerkleHashCalculator

class GtxTransactionBuilder(
    private val blockchainRid: BlockchainRid,
    private val signers: List<ByteArray>,
    private val cryptoSystem: CryptoSystem,
) {
    private val calculator = GtvMerkleHashCalculator(cryptoSystem)
    val operations = mutableListOf<GtxOperation>()

    fun addOperation(name: String, vararg args: Gtv) = apply {
        operations.add(GtxOperation(name, *args))
    }

    fun finish(): SignatureBuilder {
        val body = GtxBody(blockchainRid, operations, signers)
        return SignatureBuilder(body)
    }

    fun sign(vararg signature: Signature): GtxTransaction {
        return finish().apply {
            signature.forEach { sign(it) }
        }.build()
    }

    inner class SignatureBuilder(private val body: GtxBody, private val check: Boolean = true) {

        val signatures = mutableListOf<Signature>()

        fun sign(signature: Signature) = apply {
            if (signatures.contains(signature)) throw UserMistake("Signature already exists")
            if (signers.find { it.contentEquals(signature.subjectID) } == null) throw UserMistake("Signature belongs to unknown signer")
            if (check && cryptoSystem.verifyDigest(body.calculateTxRid(calculator), signature)) {
                throw UserMistake("Signature is not valid")
            }
            signatures.add(signature)
        }

        fun build(): GtxTransaction {
            if (signatures.size != signers.size) throw UserMistake("${signatures.size} signatures found, expected ${signers.size}")
            return GtxTransaction(body, signatures.map { it.data })
        }
    }
}
