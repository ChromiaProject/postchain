// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.gtx

import net.postchain.common.BlockchainRid
import net.postchain.common.exception.UserMistake
import net.postchain.core.Transaction
import net.postchain.core.TransactionFactory
import net.postchain.crypto.CryptoSystem
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvDecoder
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.merkle.GtvMerkleHashCalculatorBase
import net.postchain.gtv.merkleHash
import kotlin.time.Duration

/**
 * Idea is that we can build a [GTXTransaction] from different layers.
 * The most normal way would be to build from binary, but sometimes we might have deserialized the binary data already
 */
class GTXTransactionFactory(val blockchainRID: BlockchainRid, val module: GTXModule, val cs: CryptoSystem,
                            val gtvMerkleHashCalculator: GtvMerkleHashCalculatorBase,
                            val maxTransactionSize: Long = 1024 * 1024, val maxTransactionSignatures: Long = 100,
                            val slowOpThreshold: Duration = Duration.INFINITE) : TransactionFactory {

    override fun decodeTransaction(data: ByteArray): Transaction {
        if (data.size > maxTransactionSize) {
            throw UserMistake("Transaction size exceeds max transaction size $maxTransactionSize bytes")
        }
        val decoded = GtvDecoder.decodeGtv(data)
        return internalBuild(data, decoded)
    }

    override fun decodeAndValidateTransaction(data: ByteArray): Transaction {
        if (data.size > maxTransactionSize) {
            throw UserMistake("Transaction size exceeds max transaction size $maxTransactionSize bytes")
        }
        val decoded = GtvDecoder.decodeGtv(data)
        val reEncoded = GtvEncoder.encodeGtv(decoded)
        if (!data.contentEquals(reEncoded)) throw UserMistake("Transaction is not encoded with valid encoding.")
        return internalBuild(data, decoded)
    }

    // Meant to be used in tests, could be deleted if not needed
    fun build(gtx: Gtx) = internalMainBuild(null, gtx.toGtv(), gtx)

    // ----------------- Internal workings -------------------

    private fun internalBuild(rawData: ByteArray?, gtvData: Gtv): GTXTransaction {
        val gtxData = Gtx.fromGtv(gtvData)
        return internalMainBuild(rawData, gtvData, gtxData)
    }

    /**
     * Does the heavy lifting of creating the TX
     */
    private fun internalMainBuild(rawData: ByteArray?, gtvData: Gtv, gtxData: Gtx): GTXTransaction {
        val body = gtxData.gtxBody

        if (body.blockchainRid != blockchainRID) {
            throw UserMistake("Transaction has wrong blockchainRID: Should be: ${blockchainRID.toHex()}, but was: ${body.blockchainRid.toHex()}")
        }

        if (body.signers.size > maxTransactionSignatures || gtxData.signatures.size > maxTransactionSignatures) {
            throw UserMistake("Transaction contains too many signatures, only $maxTransactionSignatures allowed")
        }

        // We wait until after validation before doing (expensive) merkle root calculation
        val myHash = gtvData.merkleHash(gtvMerkleHashCalculator)
        val myRID = body.calculateTxRid(gtvMerkleHashCalculator)

        // Extract some stuff
        val signers = body.signers
        val signatures = gtxData.signatures
        val ops = body.getExtOpData().map { module.makeTransactor(it) }.toTypedArray()

        return GTXTransaction(rawData, gtvData, gtxData, signers.toTypedArray(), signatures.toTypedArray(),
                ops, myHash, myRID, cs, slowOpThreshold)
    }

}