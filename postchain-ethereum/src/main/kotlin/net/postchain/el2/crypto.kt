package net.postchain.el2

import net.postchain.base.decompressKey
import net.postchain.base.encodeSignature
import net.postchain.base.secp256k1_decodeSignature
import net.postchain.base.snapshot.DigestSystem
import net.postchain.common.data.EMPTY_HASH
import net.postchain.common.data.HASH_LENGTH
import net.postchain.common.data.Hash
import net.postchain.gtx.EMPTY_SIGNATURE
import org.spongycastle.util.Arrays
import java.security.InvalidParameterException

/**
 * @param hash signed message
 * @param pubKey compress public key
 * @param signature signature without v
 * @return signature with v to run ecrecover properly on ethereum solidity smart contract
 */
fun encodeSignatureWithV(hash: ByteArray, pubKey: ByteArray, signature: ByteArray): ByteArray {
    val pub = decompressKey(pubKey)
    val sig = secp256k1_decodeSignature(signature)
    val pub0 = SECP256K1Keccak.ecrecover(0, hash, sig[0], sig[1])
    if (Arrays.areEqual(pub0, pub)) {
        return encodeSignature(sig[0], sig[1], 27)
    }
    val pub1 = SECP256K1Keccak.ecrecover(1, hash, sig[0], sig[1])
    if (Arrays.areEqual(pub1, pub)) {
        return encodeSignature(sig[0], sig[1], 28)
    }
    return EMPTY_SIGNATURE
}


