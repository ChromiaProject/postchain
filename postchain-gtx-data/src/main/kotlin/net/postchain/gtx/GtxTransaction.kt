package net.postchain.gtx

import com.beanit.jasn1.ber.ReverseByteArrayOutputStream
import com.beanit.jasn1.ber.types.BerOctetString
import net.postchain.common.exception.UserMistake
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvArray
import net.postchain.gtv.gtxmessages.GTXTransaction
import net.postchain.gtv.merkle.MerkleHashCalculator
import java.io.ByteArrayInputStream

class GtxTransaction(
    val gtxBody: GtxBody,
    val signatures: List<ByteArray>
) {

    fun calculateTxRid(calculator: MerkleHashCalculator<Gtv>) = gtxBody.calculateTxRid(calculator)

    fun encode(): ByteArray {
        if (signatures.size != gtxBody.signers.size) throw UserMistake("Not fully signed")
        val encoded = ReverseByteArrayOutputStream(1000, true)
        GTXTransaction(
            gtxBody.asn(),
            GTXTransaction.Signatures(signatures.map { BerOctetString(it) })
        ).encode(encoded)
        return encoded.array
    }

    companion object {
        @JvmStatic
        fun decode(b: ByteArray): GtxTransaction {
            val bytes = ByteArrayInputStream(b)
            val decoded = GTXTransaction().apply { decode(bytes) }
            decoded.body
            return GtxTransaction(
                GtxBody.fromAsn(decoded.body),
                decoded.signatures.seqOf.map { it.value }
            )
        }

        @JvmStatic
        fun fromGtv(gtv: Gtv): GtxTransaction {
            if ((gtv !is GtvArray) && gtv.asArray().size != 2) throw IllegalArgumentException("Gtv must be an array of size 2")
            gtv.asArray().let { array ->
                if (array[0] !is GtvArray) throw IllegalArgumentException("First element must be an array")
                if (array[1] !is GtvArray) throw IllegalArgumentException("Second element must be an array")
                return GtxTransaction(
                    GtxBody.fromGtv(array[0]),
                    array[1].asArray().map { it.asByteArray() }
                )
            }
        }
    }
}
