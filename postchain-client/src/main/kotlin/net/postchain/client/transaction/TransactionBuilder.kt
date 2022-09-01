package net.postchain.client.transaction

import net.postchain.client.core.PostchainClient
import net.postchain.common.BlockchainRid
import net.postchain.common.exception.UserMistake
import net.postchain.crypto.CryptoSystem
import net.postchain.crypto.SigMaker
import net.postchain.crypto.Signature
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory
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
    val operations = mutableListOf<GtxOperation>()

    fun addNop() = apply {
        addOperation("nop", GtvFactory.gtv(Instant.now().toEpochMilli()))
    }

    fun addOperation(name: String, vararg args: Gtv) = apply {
        operations.add(GtxOperation(name, *args))
    }

    fun finish(): SignatureBuilder {
        val body = GtxBody(blockchainRid, operations, signers)
        return SignatureBuilder(body)
    }

    fun sign(vararg sigMaker: SigMaker): PostableTransaction {
        return finish().apply {
            sigMaker.forEach { sign(it) }
        }.build()
    }

    inner class SignatureBuilder(private val body: GtxBody, private val check: Boolean = false) {

        val signatures = mutableListOf<Signature>()
        private val txRid = body.calculateTxRid(calculator)

        fun sign(sigMaker: SigMaker) = apply {
            val signature = sigMaker.signDigest(txRid)
            sign(signature)
        }

        fun sign(signature: Signature) = apply {
            if (signatures.contains(signature)) throw UserMistake("Signature already exists")
            if (signers.find { it.contentEquals(signature.subjectID) } == null) throw UserMistake("Signature belongs to unknown signer")
            if (check && cryptoSystem.verifyDigest(txRid, signature)) {
                throw UserMistake("Signature is not valid")
            }
            signatures.add(signature)
        }

        fun buildTx(): Gtx {
            if (signatures.size != signers.size) throw UserMistake("${signatures.size} signatures found, expected ${signers.size}")
            return Gtx(body, signatures.map { it.data })
        }

        fun build(): PostableTransaction {
            return PostableTransaction(buildTx())
        }
    }

    inner class PostableTransaction(private val tx: Gtx) {

        fun post() = client.postTransaction(tx)

        fun postSync() = client.postTransactionSync(tx)

        fun postSyncAwaitConfirmation() = client.postTransactionSyncAwaitConfirmation(tx)
    }
}
