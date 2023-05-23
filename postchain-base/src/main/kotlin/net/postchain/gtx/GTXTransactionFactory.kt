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
import net.postchain.gtv.GtvFactory
import net.postchain.gtv.merkle.GtvMerkleHashCalculator
import net.postchain.gtv.merkleHash

/**
 * Idea is that we can build a [GTXTransaction] from different layers.
 * The most normal way would be to build from binary, but sometimes we might have deserialized the binary data already
 */
class GTXTransactionFactory(val blockchainRID: BlockchainRid, val module: GTXModule, val cs: CryptoSystem, val maxTransactionSize: Long = 1024 * 1024) : TransactionFactory {

    val gtvMerkleHashCalculator = GtvMerkleHashCalculator(cs) // Here we are using the standard cache

    override fun decodeTransaction(data: ByteArray): Transaction {
        if (data.size > maxTransactionSize) {
            throw UserMistake("Transaction size exceeds max transaction size $maxTransactionSize bytes")
        }
        return internalBuild(data)
    }

    override fun validateTransaction(data: ByteArray) {
        if (data.size > maxTransactionSize) {
            throw UserMistake("Transaction size exceeds max transaction size $maxTransactionSize bytes")
        }
        val encoded = GtvEncoder.encodeGtv(GtvDecoder.decodeGtv(data))
        if (!data.contentEquals(encoded)) throw UserMistake("Transaction is not encoded with valid encoding.")
    }

    // Meant to be used in tests, could be deleted if not needed
    fun build(gtx: Gtx) = internalMainBuild(null, gtx.toGtv(), gtx)

    // ----------------- Internal workings -------------------

    private fun internalBuild(rawData: ByteArray): GTXTransaction {
        val gtvData = GtvFactory.decodeGtv(rawData)
        return internalBuild(rawData, gtvData)
    }

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
            throw UserMistake("Transaction has wrong blockchainRID: Should be: ${blockchainRID.toHex()}, but was: ${body.blockchainRid.toHex()} ")
        }

        // We wait until after validation before doing (expensive) merkle root calculation
        val myHash = gtvData.merkleHash(gtvMerkleHashCalculator)
        val myRID = body.calculateTxRid(gtvMerkleHashCalculator)

        // Extract some stuff
        val signers = body.signers
        val signatures = gtxData.signatures
        val ops = body.getExtOpData().map { module.makeTransactor(it) }.toTypedArray()

        return GTXTransaction(rawData, gtvData, gtxData, signers.toTypedArray(), signatures.toTypedArray(), ops, myHash, myRID, cs)
    }

}