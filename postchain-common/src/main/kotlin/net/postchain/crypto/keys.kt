package net.postchain.crypto

import net.postchain.common.hexStringToByteArray
import net.postchain.common.toHex

@JvmInline
value class PubKey(val key: ByteArray) {
    override fun toString() = key.toHex()
}

@JvmInline
value class PrivKey(val key: ByteArray) {
    override fun toString() = key.toHex()
}

data class KeyPair(val pubKey: PubKey, val privKey: PrivKey) {

    fun sigMaker(cryptoSystem: CryptoSystem) = cryptoSystem.buildSigMaker(pubKey.key, privKey.key)

    companion object {
        @JvmStatic
        fun of(pubkey: String, privKey: String): KeyPair {
            return KeyPair(
                PubKey(pubkey.hexStringToByteArray()),
                PrivKey(privKey.hexStringToByteArray())
            )
        }
    }
}
