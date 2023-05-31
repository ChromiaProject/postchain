package net.postchain.crypto.devtools

import net.postchain.common.data.Hash
import net.postchain.crypto.SigMaker
import net.postchain.crypto.Signature
import kotlin.experimental.xor

class MockSigMaker(val pubKey: ByteArray, val privKey: ByteArray, val digestFun: (ByteArray) -> Hash): SigMaker {
    override fun signMessage(msg: ByteArray): Signature {
        val digestMsg = digestFun(msg)
        return signDigest(digestMsg)
    }
    override fun signDigest(digest: Hash): Signature {
        digest.forEachIndexed { index, byte -> byte xor pubKey[index] }
        return Signature(pubKey, digest)
    }
}
