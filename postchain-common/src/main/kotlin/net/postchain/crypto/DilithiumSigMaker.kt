package net.postchain.crypto

import net.postchain.common.data.Hash
import net.postchain.crypto.DilithiumCryptoSystem.Companion.decodePrivateKey

class DilithiumSigMaker(val keyPair: KeyPair, val digestFun: (ByteArray) -> Hash) : SigMaker {

    override fun signMessage(msg: ByteArray): Signature {
        val digestedMsg = digestFun(msg)
        return signDigest(digestedMsg)
    }

    override fun signDigest(digest: Hash): Signature {
        val signer = java.security.Signature.getInstance(DilithiumCryptoSystem.algorithm, DilithiumCryptoSystem.provider).apply {
            initSign(decodePrivateKey(keyPair.privKey))
            update(digest)
        }

        return Signature(keyPair.pubKey.data, signer.sign())
    }
}