// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.gtx

import io.micrometer.core.instrument.Metrics
import io.micrometer.core.instrument.Timer
import mu.KLogging
import net.postchain.common.data.Hash
import net.postchain.common.exception.TransactionIncorrect
import net.postchain.common.exception.UserMistake
import net.postchain.common.toHex
import net.postchain.common.types.WrappedByteArray
import net.postchain.common.wrap
import net.postchain.core.EContext
import net.postchain.core.SignableTransaction
import net.postchain.core.Transactor
import net.postchain.core.TxEContext
import net.postchain.crypto.CryptoSystem
import net.postchain.crypto.Signature
import net.postchain.gtv.Gtv
import net.postchain.logging.BLOCKCHAIN_RID_TAG
import net.postchain.logging.CHAIN_IID_TAG
import net.postchain.logging.FAILURE_RESULT
import net.postchain.logging.OPERATIONS_METRIC_DESCRIPTION
import net.postchain.logging.OPERATIONS_METRIC_NAME
import net.postchain.logging.OPERATIONS_NAME_TAG
import net.postchain.logging.OPERATION_CORRECTNESS_METRIC_DESCRIPTION
import net.postchain.logging.OPERATION_CORRECTNESS_METRIC_NAME
import net.postchain.logging.RESULT_TAG
import net.postchain.logging.SUCCESS_RESULT
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

    override fun checkCorrectnessWhileSyncing() = throw NotImplementedError("call checkCorrectnessWhileSyncing(EContext) instead")

    override fun checkCorrectnessWhileSyncing(ctxt: EContext) {
        if (isChecked || isCheckedWhileSyncing) return

        checkSignatures(parallel = false)
        checkOperations(true, ctxt)

        isCheckedWhileSyncing = true
    }

    override fun checkCorrectness() = throw NotImplementedError("call checkCorrectness(EContext) instead")

    override fun checkCorrectness(ctxt: EContext) {
        if (isChecked) return

        if (!isCheckedWhileSyncing) {
            checkSignatures(parallel = true)
        }

        checkOperations(false, ctxt)

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
    private fun checkOperations(isSyncing: Boolean, ctxt: EContext) {
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

            val opName = (op as? GTXOperation)?.data?.opName ?: "<unknown>"
            val opSignature = (op as? GTXOperation)?.shortSignature() ?: "<unknown>"

            try {
                measureOperation(
                    opName = opName,
                    opSignature = opSignature,
                    chainId = ctxt.chainID,
                    metricName = OPERATION_CORRECTNESS_METRIC_NAME,
                    metricDescription = OPERATION_CORRECTNESS_METRIC_DESCRIPTION,
                    successMsg = "check correctness",
                    failureMsg = "fail correctness check",
                ) {
                    if (isSyncing) op.checkCorrectnessWhileSyncing(ctxt) else op.checkCorrectness(ctxt)
                }
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
        checkCorrectness(ctx)
        for (op in ops) {
            val opName = (op as? GTXOperation)?.data?.opName ?: "<unknown>"
            val opSignature = (op as? GTXOperation)?.shortSignature() ?: "<unknown>"

            measureOperation(
                opName = opName,
                opSignature = opSignature,
                chainId = ctx.chainID,
                metricName = OPERATIONS_METRIC_NAME,
                metricDescription = OPERATIONS_METRIC_DESCRIPTION,
                successMsg = "apply",
                failureMsg = "fail to be applied",
            ) {
                if (!op.apply(ctx)) {
                    throw UserMistake("Operation $opSignature failed")
                }
            }
        }
        return true
    }

    override fun applyWhileSyncing(ctx: TxEContext): Boolean {
        checkCorrectnessWhileSyncing(ctx)
        for (op in ops) {
            val opName = (op as? GTXOperation)?.data?.opName ?: "<unknown>"
            val opSignature = (op as? GTXOperation)?.shortSignature() ?: "<unknown>"

            measureOperation(
                opName = opName,
                opSignature = opSignature,
                chainId = ctx.chainID,
                metricName = OPERATIONS_METRIC_NAME,
                metricDescription = OPERATIONS_METRIC_DESCRIPTION,
                successMsg = "apply while syncing",
                failureMsg = "fail to be applied while syncing",
            ) {
                if (!op.applyWhileSyncing(ctx)) {
                    throw UserMistake("Operation $opSignature failed")
                }
            }
        }
        return true
    }

    private fun measureOperation(
            opName: String,
            opSignature: String,
            chainId: Long,
            metricName: String,
            metricDescription: String,
            successMsg: String,
            failureMsg: String,
            operationExecution: () -> Unit,
    ) {
        val timerBuilder = Timer.builder(metricName)
                .description(metricDescription)
                .tag(CHAIN_IID_TAG, chainId.toString())
                .tag(BLOCKCHAIN_RID_TAG, gtxData.gtxBody.blockchainRid.toHex())
                .tag(OPERATIONS_NAME_TAG, opName)
        val sample = Timer.start(Metrics.globalRegistry)
        return try {
            operationExecution()
            val opTimeNanos = sample.stop(
                    timerBuilder.tag(RESULT_TAG, SUCCESS_RESULT).register(Metrics.globalRegistry)
            )
            maybeLogSlowOp(opTimeNanos, opSignature, successMsg)
        } catch (e: Exception) {
            val opTimeNanos = sample.stop(
                    timerBuilder.tag(RESULT_TAG, FAILURE_RESULT).register(Metrics.globalRegistry)
            )
            maybeLogSlowOp(opTimeNanos, opSignature, failureMsg)
            throw e
        }
    }

    private fun maybeLogSlowOp(opTimeNanos: Long, opSignature: String, msg: String) {
        if (opTimeNanos > slowOpThresholdNanos) {
            logger.info("Operation $opSignature is slow, took ${opTimeNanos / 1000} ms to $msg")
        }
    }

    override fun toString(): String = "GTXTransaction(RID=${myRID.toHex()})"
}
