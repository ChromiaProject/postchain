// Copyright (c) 2017 ChromaWay Inc. See README for license information.

package net.postchain.gtx

import net.postchain.base.CryptoSystem
import net.postchain.base.Signer
import net.postchain.core.ProgrammerMistake
import net.postchain.core.Signature
import net.postchain.core.UserMistake
import java.util.*
import net.postchain.gtv.*
import net.postchain.gtx.factory.GtxDataFactory
import net.postchain.gtx.serializer.GtxDataSerializer

object GtxBase {
    const val NR_FIELDS_TRANSACTION = 2
    const val NR_FIELDS_TRANSACTION_BODY = 3
    const val NR_FIELDS_OPERATION = 2
}

data class OpData(val opName: String, val args: Array<Gtv>) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as OpData

        if (opName != other.opName) return false
        if (!Arrays.equals(args, other.args)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = opName.hashCode()
        result = 31 * result + Arrays.hashCode(args)
        return result
    }
}

class ExtOpData(val opName: String,
                val opIndex: Int,
                val blockchainRID: ByteArray,
                val signers: Array<ByteArray>,
                val args: Array<Gtv>)

val EMPTY_SIGNATURE: ByteArray = ByteArray(0)

data class GTXTransactionBodyData(
        val blockchainRID: ByteArray,
        val operations: Array<OpData>,
        val signers: Array<ByteArray>)

data class GTXTransactionData(
        val transactionBodyData: GTXTransactionBodyData,
        val signatures: Array<ByteArray>)

data class GTXData(
        val blockchainRID: ByteArray,
        val signers: Array<ByteArray>,
        val signatures: Array<ByteArray>,
        val operations: Array<OpData>) {

    fun getExtOpData(): Array<ExtOpData> {
        return operations.mapIndexed {
            idx, op -> ExtOpData(op.opName, idx, blockchainRID, signers, op.args)
        }.toTypedArray()
    }

    fun serialize(): ByteArray {
        val gtvArray = GtxDataSerializer.serializeToGtv(this)
        return GtvEncoder.encodeGtv(gtvArray)
    }

    fun serializeWithoutSignatures(): ByteArray {
        return GTXData(
                blockchainRID,
                signers,
                arrayOf(),
                operations).serialize()
    }

    fun getDigestForSigning(crypto: CryptoSystem): ByteArray {
        return crypto.digest(serializeWithoutSignatures())
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as GTXData

        if (!Arrays.equals(blockchainRID, other.blockchainRID)) return false
        if (!Arrays.deepEquals(signers, other.signers)) return false
        if (!Arrays.deepEquals(signatures, other.signatures)) return false
        if (!Arrays.equals(operations, other.operations)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = Arrays.hashCode(blockchainRID)
        result = 31 * result + Arrays.hashCode(signers)
        result = 31 * result + Arrays.hashCode(signatures)
        result = 31 * result + Arrays.hashCode(operations)
        return result
    }
}


fun decodeGTXData(_rawData: ByteArray): GTXData {
    // Decode to RawGTV
    val gtv: Gtv = GtvDecoder.decodeGtv(_rawData)

    // GTV -> GTXData
    return GtxDataFactory.deserializeFromGtv(gtv)
}

// TODO: cache data for signing and digest

class GTXDataBuilder(val blockchainRID: ByteArray,
                     val signers: Array<ByteArray>,
                     val crypto: CryptoSystem,
                     val signatures: Array<ByteArray>,
                     val operations: MutableList<OpData>,
                     private var finished: Boolean) {

    // construct empty builder
    constructor(blockchainRID: ByteArray,
                signers: Array<ByteArray>,
                crypto: CryptoSystem) :
            this(
                    blockchainRID,
                    signers,
                    crypto,
                    Array(signers.size, { EMPTY_SIGNATURE }),
                    mutableListOf<OpData>(),
                    false)

    companion object {
        fun decode(bytes: ByteArray, crypto: CryptoSystem, finished: Boolean = true): GTXDataBuilder {
            val data = decodeGTXData(bytes)
            return GTXDataBuilder(
                    data.blockchainRID,
                    data.signers, crypto, data.signatures,
                    data.operations.toMutableList(), finished)
        }
    }

    fun finish() {
        finished = true
    }

    fun isFullySigned(): Boolean {
        return signatures.all { !it.contentEquals(EMPTY_SIGNATURE) }
    }

    fun addOperation(opName: String, args: Array<Gtv>) {
        if (finished) throw ProgrammerMistake("Already finished")
        operations.add(OpData(opName, args))
    }

    fun verifySignature(s: Signature): Boolean {
        return crypto.verifyDigest(getDigestForSigning(), s)
    }

    fun addSignature(s: Signature, check: Boolean = true) {
        if (!finished) throw ProgrammerMistake("Must be finished before signing")

        if (check) {
            if (!verifySignature(s)) {
                throw UserMistake("Signature is not valid")
            }
        }

        val idx = signers.indexOfFirst { it.contentEquals(s.subjectID) }
        if (idx != -1) {
            if (signatures[idx].contentEquals(EMPTY_SIGNATURE)) {
                signatures[idx] = s.data
            } else throw UserMistake("Signature already exists")
        } else throw UserMistake("Singer not found")
    }

    fun getDataForSigning(): ByteArray {
        if (!finished) throw ProgrammerMistake("Must be finished before signing")

        return getGTXData().serializeWithoutSignatures()
    }

    fun getDigestForSigning(): ByteArray {
        if (!finished) throw ProgrammerMistake("Must be finished before signing")

        return getGTXData().getDigestForSigning(crypto)
    }

    fun sign(signer: Signer) {
        addSignature(signer(getDataForSigning()), false)
    }

    fun getGTXData(): GTXData {
        return GTXData(
                blockchainRID,
                signers,
                signatures,
                operations.toTypedArray()
        )
    }

    fun serialize(): ByteArray {
        return getGTXData().serialize()
    }
}
