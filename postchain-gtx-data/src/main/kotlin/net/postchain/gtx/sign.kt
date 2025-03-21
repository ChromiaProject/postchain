package net.postchain.gtx

import net.postchain.common.data.Hash
import net.postchain.crypto.CryptoSystem
import net.postchain.crypto.KeyPair
import net.postchain.crypto.Secp256K1CryptoSystem

/**
 * Rebuilds a partially signed transaction from GTX and sign it with provided signers.
 */
fun signTransaction(
        gtxByteArray: ByteArray,
        txRid: Hash,
        signers: List<KeyPair>,
        cryptoSystem: CryptoSystem = Secp256K1CryptoSystem(),
): ByteArray {
    val gtx = Gtx.decode(gtxByteArray)

    val signBuilder = GtxSignatureBuilder(gtx.gtxBody, txRid, cryptoSystem, check = false)
    signBuilder.addSignatures(gtx.signatures)
    signers.forEach { signBuilder.sign(cryptoSystem.buildSigMaker(it)) }
    return signBuilder.buildGtx().encode()
}
