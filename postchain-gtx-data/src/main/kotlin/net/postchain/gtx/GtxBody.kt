package net.postchain.gtx

import com.beanit.jasn1.ber.types.BerOctetString
import net.postchain.common.BlockchainRid
import net.postchain.common.data.Hash
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvArray
import net.postchain.gtv.GtvByteArray
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.gtxmessages.RawGtxBody
import net.postchain.gtv.gtxmessages.RawGtxBody.Signers
import net.postchain.gtv.merkle.MerkleHashCalculator
import net.postchain.gtv.merkleHash
import net.postchain.gtx.data.ExtOpData

class GtxBody(
    val blockchainRid: BlockchainRid,
    val operations: List<GtxOp>,
    val signers: List<ByteArray>
) {

    constructor(blockchainRid: BlockchainRid, operations: Array<GtxOp>, signers: Array<ByteArray>) :
            this(blockchainRid, operations.toList(), signers.toList())


    lateinit var rid: Hash

    fun calculateTxRid(calculator: MerkleHashCalculator<Gtv>): Hash {
        if (!this::rid.isInitialized) rid = toGtv().merkleHash(calculator)
        return rid
    }

    // Extended OpData
    fun getExtOpData(): Array<ExtOpData> {
        return operations.mapIndexed { idx, op ->
            ExtOpData.build(op, idx, this)
        }.toTypedArray()
    }

    internal fun toRaw() = RawGtxBody(
        BerOctetString(blockchainRid.data),
        RawGtxBody.Operations(operations.map { it.toRaw() }),
        Signers(signers.map { BerOctetString(it) })
    )

    /**
     * Elements are structured like an ordered array with elements:
     * 1. blockchainRId [GtvByteArray]
     * 2. operations [GtvArray]
     * 3. signers [GtvArray]
     */
    fun toGtv() = gtv(
        gtv(blockchainRid),
        gtv(operations.map { it.toGtv() }),
        gtv(signers.map { gtv(it) })
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as GtxBody

        if (blockchainRid != other.blockchainRid) return false
        if (operations != other.operations) return false
        if (!signers.toTypedArray().contentDeepEquals(other.signers.toTypedArray())) return false

        return true
    }

    override fun hashCode(): Int {
        var result = blockchainRid.hashCode()
        result = 31 * result + operations.hashCode()
        result = 31 * result + signers.hashCode()
        return result
    }

    companion object {
        @JvmStatic
        internal fun fromAsn(body: RawGtxBody): GtxBody {
            return GtxBody(
                BlockchainRid(body.blockchainRid.value),
                body.operations.seqOf.map { GtxOp.fromAsn(it) },
                body.signers.seqOf.map { it.value }
            )
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
                    array[1].asArray().map { GtxOp.fromGtv(it) },
                    array[2].asArray().map { it.asByteArray() }
                )
            }
        }
    }
}