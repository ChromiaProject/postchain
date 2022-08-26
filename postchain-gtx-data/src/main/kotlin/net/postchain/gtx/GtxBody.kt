package net.postchain.gtx

import com.beanit.jasn1.ber.types.BerOctetString
import net.postchain.common.BlockchainRid
import net.postchain.common.data.Hash
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.gtxmessages.GTXBody
import net.postchain.gtv.gtxmessages.GTXTransaction
import net.postchain.gtv.merkle.MerkleHashCalculator

class GtxBody(val blockchainRid: BlockchainRid, val operations: List<GtxOperation>, val signers: List<ByteArray>) {

    lateinit var rid: Hash

    fun calculateRid(calculator: MerkleHashCalculator<Gtv>): Hash {
        if (this::rid.isInitialized) return rid
        rid = calculator.calculateLeafHash(toGtv())
        return rid
    }

    fun asn() = GTXTransaction.Body(
        listOf(
            GTXBody(
                BerOctetString(blockchainRid.data),
                GTXBody.Operations(operations.map { it.asn() }),
                GTXBody.Signers(signers.map { BerOctetString(it) })
            )
        )
    )

    fun toGtv() = gtv(
        gtv(blockchainRid),
        gtv(operations.map { it.gtv() }),
        gtv(signers.map { gtv(it) })
    )
}