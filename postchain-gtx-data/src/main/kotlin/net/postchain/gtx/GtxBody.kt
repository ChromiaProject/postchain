package net.postchain.gtx

import com.beanit.jasn1.ber.types.BerOctetString
import net.postchain.common.BlockchainRid
import net.postchain.common.data.Hash
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvArray
import net.postchain.gtv.GtvByteArray
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.gtxmessages.GTXBody
import net.postchain.gtv.merkle.MerkleHashCalculator

class GtxBody(
    val blockchainRid: BlockchainRid,
    val operations: List<GtxOperation>,
    val signers: List<ByteArray>
) {

    lateinit var rid: Hash

    fun calculateTxRid(calculator: MerkleHashCalculator<Gtv>): Hash {
        if (this::rid.isInitialized) return rid
        rid = calculator.calculateLeafHash(toGtv())
        return rid
    }

    fun asn() = GTXBody(
        BerOctetString(blockchainRid.data),
        GTXBody.Operations(operations.map { it.asn() }),
        GTXBody.Signers(signers.map { BerOctetString(it) })
    )

    fun toGtv() = gtv(
        gtv(blockchainRid),
        gtv(operations.map { it.gtv() }),
        gtv(signers.map { gtv(it) })
    )

    companion object {
        @JvmStatic
        fun fromAsn(body: GTXBody): GtxBody {
            return GtxBody(
                BlockchainRid(body.blockchainRid.value),
                body.operations.seqOf.map { GtxOperation.fromAsn(it) },
                body.signers.seqOf.map { it.value })
        }

        @JvmStatic
        fun fromGtv(gtv: Gtv): GtxBody {
            if ((gtv !is GtvArray) && gtv.asArray().size != 3) throw IllegalArgumentException("Gtv must be an array of size 3")
            gtv.asArray().let { array ->
                if (array[0] !is GtvByteArray) throw IllegalArgumentException("First element must be a byte array")
                if (array[1] !is GtvArray) throw IllegalArgumentException("Second element must be an array")
                if (array[2] !is GtvArray) throw IllegalArgumentException("Third element must be an array")
                return GtxBody(
                    BlockchainRid(array[0].asByteArray()),
                    array[1].asArray().map { GtxOperation.fromGtv(it) },
                    array[2].asArray().map { it.asByteArray() }
                )
            }
        }
    }
}