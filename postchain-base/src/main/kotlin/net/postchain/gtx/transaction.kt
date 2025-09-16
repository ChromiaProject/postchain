// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.gtx

import mu.KLogging
import net.postchain.common.data.Hash
import net.postchain.common.exception.TransactionIncorrect
import net.postchain.common.exception.UserMistake
import net.postchain.common.toHex
import net.postchain.common.types.WrappedByteArray
import net.postchain.common.wrap
import net.postchain.core.SignableTransaction
import net.postchain.core.Transactor
import net.postchain.core.TxEContext
import net.postchain.crypto.CryptoSystem
import net.postchain.crypto.Signature
import net.postchain.gtv.Gtv
import kotlin.system.measureNanoTime
import kotlin.time.Duration

/**
 * A transaction based on the GTX format.
 *
 * @property _rawData what the TX data looks like in binary form
 * @property gtvData what the TX data looks like in [Gtv]
 * @property gtxData what the TX data looks like in GTX
 * @property signers are the public keys that should sign the TX
 * @property signatures are the actual signatures
 * @property ops are the operations of the TX
 * @property myHash is the merkle root of the TX
 * @property myRID  is the merkle root of the TX body
 * @property cs is the [CryptoSystem] we use
 */
class GTXTransaction(
        val _rawData: ByteArray?,
        val gtvData: Gtv,
        val gtxData: Gtx,
        override val signers: Array<ByteArray>,
        val signatures: Array<ByteArray>,
        val ops: Array<Transactor>,
        val myHash: Hash,
        val myRID: ByteArray,
        val cs: CryptoSystem,
        slowOpThreshold: Duration = Duration.INFINITE,
) : SignableTransaction {

    companion object : KLogging()

    private val slowOpThresholdNanos = slowOpThreshold.inWholeNanoseconds

    private val cachedRawData by lazy { gtxData.encode() } // We are not sure if we have the rawData, and if we ever need to calculate it, it will be cached here.
    var isChecked: Boolean = false
    var isCheckedWhileSyncing: Boolean = false

    override fun getHash(): ByteArray {
        return myHash
    }

    override fun isSpecial(): Boolean {
        return ops.any {
            it.isSpecial()
        }
    }

    override fun checkCorrectnessWhileSyncing() {
        if (isChecked || isCheckedWhileSyncing) return

        checkSignatures(parallel = false)
        checkOperations(true)

        isCheckedWhileSyncing = true
    }

    override fun checkCorrectness() {
        if (isChecked) return

        if (!isCheckedWhileSyncing) {
            checkSignatures(parallel = true)
        }

        checkOperations(false)

        isChecked = true
    }

    private fun checkSignatures(parallel: Boolean) {
        if (signatures.size != signers.size) {
            throw TransactionIncorrect(myRID, "${signatures.size} signatures != ${signers.size} signers")
        }

        if (signers.size > 1) {
            val set = HashSet<WrappedByteArray>(signers.size)
            for (signer in signers) {
                set.add(signer.wrap())
            }
            if (set.size != signers.size) {
                throw TransactionIncorrect(myRID, "Duplicate signers")
            }
        }

        if (signers.size == 1) {
            if (!cs.verifyDigest(myRID, Signature(signers.first(), signatures.first()))) {
                throw TransactionIncorrect(myRID, "Signature by ${signers.first().toHex()} is not valid")
            }
        } else if (signers.size > 1) {
            val signersAndSignatures = if (parallel)
                signers.zip(signatures).parallelStream()
            else
                signers.zip(signatures).stream()

            signersAndSignatures.forEach { (signer, signature) ->
                if (!cs.verifyDigest(myRID, Signature(signer, signature))) {
                    throw TransactionIncorrect(myRID, "Signature by ${signer.toHex()} is not valid")
                }
            }
        }
    }

    /**
     * The business rules for a TX to be valid are here to prevent spam from entering the blockchain.
     * Ideally we want at least one operation where the module will validate the signer somehow, so it's not just
     * anyone sending TXs, and this is why we require a transaction to include at least one "custom" operation.
     * We still have one attack vector where the Dapp developer creates custom operation where no signer check is
     * included, b/c this opens up to anonymous attacks.
     */
    private fun checkOperations(isSyncing: Boolean) {
        val hasNormalOperation = ops.any { !it.isCompound() }
        var totalOps = 0
        var specialOps = 0
        val foundSingleOps = mutableSetOf<String>()

        for (op in ops) {

            totalOps++
            if (op.isSpecial()) specialOps++

            if (!isSyncing && op is GTXOperation && op.isSinglePerTransaction()) {
                if (!foundSingleOps.add(op.data.opName)) {
                    throw TransactionIncorrect(myRID, "contains more than one '${op.data.opName}'")
                }
            }

            try {
                if (isSyncing) op.checkCorrectnessWhileSyncing() else op.checkCorrectness()
            } catch (e: UserMistake) {
                throw TransactionIncorrect(myRID, e.message)
            }
        }

        if (specialOps > 0 && specialOps == totalOps) {
            // The TX contains only special ops
            return // Pure special TX, and this should be valid
        }

        // This transaction must have at least one normal operation (non compound) or be classed as spam
        if (!hasNormalOperation && !isSyncing) throw TransactionIncorrect(myRID, "contains no normal operation")
    }

    override fun getRawData(): ByteArray {
        return _rawData ?: cachedRawData
    }

    override fun getRID(): ByteArray {
        return myRID
    }

    override fun apply(ctx: TxEContext): Boolean {
        checkCorrectness()
        for (op in ops) {
            val opSignature = (op as? GTXOperation)?.shortSignature() ?: "<unknown>"
            val opTimeNanos = measureNanoTime {
                if (!op.apply(ctx))
                    throw UserMistake("Operation $opSignature failed")
            }
            maybeLogSlowOp(opTimeNanos, opSignature, "apply")
        }
        return true
    }

    override fun applyWhileSyncing(ctx: TxEContext): Boolean {
        checkCorrectnessWhileSyncing()
        for (op in ops) {
            val opSignature = (op as? GTXOperation)?.shortSignature() ?: "<unknown>"
            val opTimeNanos = measureNanoTime {
                if (!op.applyWhileSyncing(ctx))
                    throw UserMistake("Operation $opSignature failed")
            }
            maybeLogSlowOp(opTimeNanos, opSignature, "apply while syncing")
        }
        return true
    }

    private fun maybeLogSlowOp(opTimeNanos: Long, opSignature: String, msg: String) {
        if (opTimeNanos > slowOpThresholdNanos) {
            logger.info("Operation $opSignature is slow, took ${opTimeNanos / 1000} ms to $msg")
        }
    }

    override fun toString(): String = "GTXTransaction(RID=${myRID.toHex()})"
}
