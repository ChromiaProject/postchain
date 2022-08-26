package net.postchain.gtx

import com.beanit.jasn1.ber.ReverseByteArrayOutputStream
import com.beanit.jasn1.ber.types.BerOctetString
import net.postchain.common.BlockchainRid
import net.postchain.crypto.Signature
import net.postchain.gtv.Gtv
import net.postchain.gtv.gtxmessages.GTXTransaction

class GtxTransaction(
    private val blockchainRid: BlockchainRid,
) {

    val signers = mutableListOf<ByteArray>()
    val ops = mutableListOf<GtxOperation>()
    val signatures = mutableListOf<ByteArray>()
    val tx = GTXTransaction()

    fun addOperation(name: String, vararg args: Gtv) {
        ops.add(GtxOperation(name, *args))
    }

    fun addSignature(sign: Signature) = signers.add(sign.subjectID) && signatures.add(sign.data)

    fun encode(): ByteArray {
        val encoded = ReverseByteArrayOutputStream(1000, true)
        GTXTransaction(
            GtxBody(blockchainRid, ops, signers).asn(),
            GTXTransaction.Signatures(signatures.map { BerOctetString(it) })
        ).encode(encoded)
        return encoded.array
    }
}
