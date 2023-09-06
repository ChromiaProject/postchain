package net.postchain.crypto.pqc.dilithium

import net.postchain.common.data.Hash
import net.postchain.crypto.KeyPair
import net.postchain.crypto.SigMaker
import net.postchain.crypto.Signature
import net.postchain.crypto.pqc.dilithium.DilithiumCryptoSystem.Companion.decodePrivateKey

class DilithiumSigMaker(val keyPair: KeyPair, val digestFun: (ByteArray) -> Hash) : SigMaker {

    override fun signMessage(msg: ByteArray): Signature {
        val digestedMsg = digestFun(msg)
        return signDigest(digestedMsg)
    }

    override fun signDigest(digest: Hash): Signature {
        val signer = java.security.Signature.getInstance(DilithiumCryptoSystem.ALGORITHM, DilithiumCryptoSystem.PROVIDER).apply {
            initSign(decodePrivateKey(keyPair.privKey))
            update(digest)
        }

        return Signature(keyPair.pubKey.data, signer.sign())
    }
}