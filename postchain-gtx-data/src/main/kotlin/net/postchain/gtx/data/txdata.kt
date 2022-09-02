// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.gtx.data

import net.postchain.common.BlockchainRid
import net.postchain.common.data.Hash
import net.postchain.gtv.*
import net.postchain.gtv.merkle.MerkleHashCalculator
import net.postchain.gtx.Gtx
import net.postchain.gtx.GtxBody
import net.postchain.gtx.GtxOp
import net.postchain.gtx.data.serializer.GtxTransactionBodyDataSerializer
import net.postchain.gtx.data.serializer.GtxTransactionDataSerializer
import java.util.*

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
                val args: Array<out Gtv>,
                val blockchainRID: BlockchainRid,
                val signers: Array<ByteArray>,
                val operations: Array<OpData> ) {

    companion object {

        /**
         * If we have [GTXTransactionBodyData] it will hold everything we need for extending the OpData
         */
        fun build(op: OpData, opIndex: Int, bodyData: GTXTransactionBodyData): ExtOpData {
            return ExtOpData(op.opName, opIndex, op.args, bodyData.blockchainRID, bodyData.signers, bodyData.operations)
        }

        fun build(op: GtxOp, opIndex: Int, body: GtxBody): ExtOpData {
            return ExtOpData(op.name, opIndex, op.args, body.blockchainRid, body.signers.toTypedArray(), body.operations.map { it.toOpData() }.toTypedArray())
        }
    }

}

val EMPTY_SIGNATURE: ByteArray = ByteArray(0)

data class GTXTransactionBodyData(
    val blockchainRID: BlockchainRid,
    val operations: Array<OpData>,
    val signers: Array<ByteArray>) {

    private var cachedRid: Hash? = null

    fun getExtOpData(): Array<ExtOpData> {
        return operations.mapIndexed { idx, op ->
            ExtOpData.build(op, idx, this)
        }.toTypedArray()
    }

    fun calculateRID(calculator: MerkleHashCalculator<Gtv>): Hash {
        if (cachedRid == null) {
            val txBodyGtvArr: GtvArray = GtxTransactionBodyDataSerializer.serializeToGtv(this)
            cachedRid = txBodyGtvArr.merkleHash(calculator)
        }

        return cachedRid!!
    }

    fun serialize(): ByteArray {
        val gtvArray =  GtxTransactionBodyDataSerializer.serializeToGtv(this)
        return GtvEncoder.encodeGtv(gtvArray)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as GTXTransactionBodyData

        if (blockchainRID != other.blockchainRID) return false
        if (!Arrays.deepEquals(signers, other.signers)) return false
        if (!Arrays.equals(operations, other.operations)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = blockchainRID.hashCode()
        result = 31 * result + Arrays.hashCode(signers)
        result = 31 * result + Arrays.hashCode(operations)
        return result
    }
}

data class GTXTransactionData(
    val transactionBodyData: GTXTransactionBodyData,
    val signatures: Array<ByteArray>) {

    fun serialize(): ByteArray {
        val gtvArray = GtxTransactionDataSerializer.serializeToGtv(this)
        return  GtvEncoder.encodeGtv(gtvArray)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as GTXTransactionData

        if (transactionBodyData != other.transactionBodyData) return false
        if (!Arrays.deepEquals(signatures, other.signatures)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = transactionBodyData.hashCode()
        result = 31 * result + Arrays.hashCode(signatures)
        return result
    }
}


fun decodeGTXTransactionData(_rawData: ByteArray): Gtx {
    // Decode to RawGTV
    val gtv: Gtv = GtvDecoder.decodeGtv(_rawData)

    // GTV -> GTXTransactionData
    return Gtx.fromGtv(gtv)
}
