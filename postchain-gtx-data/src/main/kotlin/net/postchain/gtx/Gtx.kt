package net.postchain.gtx

import com.beanit.jasn1.ber.ReverseByteArrayOutputStream
import com.beanit.jasn1.ber.types.BerOctetString
import net.postchain.common.exception.UserMistake
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvArray
import net.postchain.gtv.GtvByteArray
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.gtxmessages.RawGtx
import net.postchain.gtv.merkle.MerkleHashCalculator
import java.io.ByteArrayInputStream

class Gtx(
    val gtxBody: GtxBody,
    val signatures: List<ByteArray>
) {

    constructor(gtxBody: GtxBody, signatures: Array<ByteArray>) : this(gtxBody, signatures.toList())

    init {
        require(gtxBody.signers.size == signatures.size) { "Expected ${gtxBody.signers.size} signatures, found ${signatures.size}" }
    }

    fun calculateTxRid(calculator: MerkleHashCalculator<Gtv>) = gtxBody.calculateTxRid(calculator)

    fun encode(): ByteArray {
        if (signatures.size != gtxBody.signers.size) throw UserMistake("Not fully signed")
        val encoded = ReverseByteArrayOutputStream(1000, true)
        RawGtx(
            gtxBody.toRaw(),
            RawGtx.Signatures(signatures.map { BerOctetString(it) })
        ).encode(encoded)
        return encoded.array
    }
    /**
     * Elements are structured like an ordered array with elements:
     * 1. transaction data body [GtvByteArray]
     * 2. signatures [GtvArray]
     */
    fun toGtv() = gtv(
        gtxBody.toGtv(),
        gtv(signatures.map { gtv(it) })
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Gtx

        if (gtxBody != other.gtxBody) return false
        if (!signatures.toTypedArray().contentDeepEquals(other.signatures.toTypedArray())) return false

        return true
    }

    override fun hashCode(): Int {
        var result = gtxBody.hashCode()
        result = 31 * result + signatures.hashCode()
        return result
    }

    companion object {
        @JvmStatic
        fun decode(b: ByteArray): Gtx {
            val bytes = ByteArrayInputStream(b)
            val decoded = RawGtx().apply { decode(bytes) }
            return Gtx(
                GtxBody.fromAsn(decoded.body),
                decoded.signatures.seqOf.map { it.value }
            )
        }

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
