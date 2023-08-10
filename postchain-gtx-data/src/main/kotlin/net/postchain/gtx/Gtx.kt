package net.postchain.gtx

import net.postchain.common.exception.UserMistake
import net.postchain.common.toHex
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvArray
import net.postchain.gtv.GtvByteArray
import net.postchain.gtv.GtvDecoder
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.mapper.ToGtv
import net.postchain.gtv.merkle.MerkleHashCalculator

class Gtx(
    val gtxBody: GtxBody,
    val signatures: List<ByteArray>
) : ToGtv {

    constructor(gtxBody: GtxBody, signatures: Array<ByteArray>) : this(gtxBody, signatures.toList())

    init {
        require(gtxBody.signers.size == signatures.size) { "Expected ${gtxBody.signers.size} signatures, found ${signatures.size}" }
    }

    fun calculateTxRid(calculator: MerkleHashCalculator<Gtv>) = gtxBody.calculateTxRid(calculator)

    fun encodeHex() = encode().toHex()
    fun encode(): ByteArray {
        if (signatures.size != gtxBody.signers.size) throw UserMistake("Not fully signed")
        return GtvEncoder.encodeGtv(toGtv())
    }

    /**
     * Elements are structured like an ordered array with elements:
     * 1. transaction data body [GtvByteArray]
     * 2. signatures [GtvArray]
     */
    override fun toGtv() = gtv(
        gtxBody.toGtv(),
        gtv(signatures.map { gtv(it) })
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Gtx

        if (gtxBody != other.gtxBody) return false
        if (signatures.size != other.signatures.size) return false
        signatures.forEachIndexed { i, signature ->
            if (!signature.contentEquals(other.signatures[i])) return false
        }

        return true
    }

    override fun hashCode(): Int {
        var result = gtxBody.hashCode()
        signatures.forEach {
            result = 31 * result + it.contentHashCode()
        }
        return result
    }

    companion object {
        @JvmStatic
        fun decode(b: ByteArray) = fromGtv(GtvDecoder.decodeGtv(b))

        @JvmStatic
        fun fromGtv(gtv: Gtv): Gtx {
            if ((gtv !is GtvArray) && gtv.asArray().size != 2) throw IllegalArgumentException("Gtv must be an array of size 2")
            gtv.asArray().let { array ->
                if (array[0] !is GtvArray) throw IllegalArgumentException("First element must be an array")
                if (array[1] !is GtvArray) throw IllegalArgumentException("Second element must be an array")
                return Gtx(
                    GtxBody.fromGtv(array[0]),
                    array[1].asArray().map { it.asByteArray() }
                )
            }
        }
    }
}
