package net.postchain.crypto

import net.postchain.common.hexStringToByteArray
import net.postchain.common.toHex
import java.lang.IllegalArgumentException

@JvmInline
value class PubKey(val key: ByteArray) {
    init {
        if (key.size != 33) throw IllegalArgumentException("Public key must be 33 bytes")
    }

    constructor(hex: String) : this(hex.hexStringToByteArray())

    fun hex() = key.toHex()

    override fun toString() = hex()
}

@JvmInline
value class PrivKey(val key: ByteArray) {
    init {
        if (key.size != 32) throw IllegalArgumentException("Private key must be 32 bytes")
    }

    constructor(hex: String) : this(hex.hexStringToByteArray())

    fun hex() = key.toHex()

    override fun toString() = hex()
}

data class KeyPair(val pubKey: PubKey, val privKey: PrivKey) {

    fun sigMaker(cryptoSystem: CryptoSystem) = cryptoSystem.buildSigMaker(pubKey.key, privKey.key)

    companion object {
        @JvmStatic
        fun of(pubKey: String, privKey: String) = KeyPair(PubKey(pubKey), PrivKey(privKey))
    }
}
