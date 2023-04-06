// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.gtx

import net.postchain.common.data.Hash
import net.postchain.common.exception.TransactionIncorrect
import net.postchain.common.exception.UserMistake
import net.postchain.common.toHex
import net.postchain.core.Transaction
import net.postchain.core.Transactor
import net.postchain.core.TxEContext
import net.postchain.crypto.CryptoSystem
import net.postchain.crypto.Signature
import net.postchain.gtv.Gtv

/**
 * A transaction based on the GTX format.
 *
 * @property _rawData what the TX data looks like in binary form
 * @property txGtv what the TX data looks like in [Gtv]
 * @property rawGtx what the TX data looks like in GTX
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
        val signers: Array<ByteArray>,
        val signatures: Array<ByteArray>,
        val ops: Array<Transactor>,
        val myHash: Hash,
        val myRID: ByteArray,
        val cs: CryptoSystem
) : Transaction {

    var cachedRawData: ByteArray? = null // We are not sure we have the rawData, and if ever need to calculate it it will be cache here.
    var isChecked: Boolean = false

    override fun getHash(): ByteArray {
        return myHash
    }

    override fun isSpecial(): Boolean {
        return ops.any {
            it.isSpecial()
        }
    }

    override fun checkCorrectness() {
        if (isChecked) return

        if (signatures.size != signers.size) {
            throw TransactionIncorrect(myRID, "${signatures.size} signatures != ${signers.size} signers")
        }

        for ((idx, signer) in signers.withIndex()) {
            val signature = signatures[idx]
            if (!cs.verifyDigest(myRID, Signature(signer, signature))) {
                throw TransactionIncorrect(myRID, "Signature by ${signer.toHex()} is not valid")
            }
        }

        checkOperations()

        isChecked = true
    }

    /**
     * The business rules for a TX to be valid are here to prevent spam from entering the blockchain.
     * Ideally we want at least one operation where the module will validate the signer somehow, so it's not just
     * anyone sending TXs, and this is why we require a transaction to include at least one "custom" operation.
     * We still have one attack vector where the Dapp developer creates custom operation where no signer check is
     * included, b/c this opens up to anonymous attacks.
     */
    private fun checkOperations() {
        var hasCustomOperation = false
        var totalOps = 0
        var specialOps = 0
        // A transaction is permitted to have only one occurrence of each operation: nop, __nop, and time.
        // Otherwise, it is considered spam.
        var foundNop = false
        var foundSpecNop = false
        var foundTimeB = false

        for (op in ops) {

            totalOps++
            if (op.isSpecial()) specialOps++

            when (op) {
                is GtxSpecNop -> {
                    if (foundSpecNop) throw TransactionIncorrect(myRID, "contains more than one '__nop'")
                    foundSpecNop = true
                }
                is GtxNop -> {
                    if (foundNop) throw TransactionIncorrect(myRID, "contains more than one 'nop'")
                    foundNop = true
                }
                is GtxTimeB -> {
                    if (foundTimeB) throw TransactionIncorrect(myRID, "contains more than one 'timeb'")
                    foundTimeB = true
                }
                else -> {
                    hasCustomOperation = true
                }
            }

            op.checkCorrectness()
        }

        if (specialOps > 0 && specialOps == totalOps) {
            // The TX contains only special ops
            return // Pure special TX, and this should be valid
        }

        // "This transaction must have at least one operation (nop and timeb not counted) or be classed as spam."
        if (!hasCustomOperation) throw TransactionIncorrect(myRID, "contains no normal operation")
    }

    @Synchronized
    override fun getRawData(): ByteArray {
        if (_rawData != null) {
            return _rawData
        }
        if (cachedRawData == null) {
            cachedRawData = gtxData.encode()
        }
        return cachedRawData!!
    }

    override fun getRID(): ByteArray {
        return myRID
    }

    override fun apply(ctx: TxEContext): Boolean {
        checkCorrectness()
        for (op in ops) {
            if (!op.apply(ctx))
                throw UserMistake("Operation failed")
        }
        return true
    }
}
