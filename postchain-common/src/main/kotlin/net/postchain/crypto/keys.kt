package net.postchain.crypto

import net.postchain.common.hexStringToByteArray
import net.postchain.common.toHex

data class PubKey(val key: ByteArray) {
    init {
        if (key.size != 33) throw IllegalArgumentException("Public key must be 33 bytes")
    }

    constructor(hex: String) : this(hex.hexStringToByteArray())

    fun hex() = key.toHex()

    override fun toString() = hex()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PubKey

        if (!key.contentEquals(other.key)) return false

        return true
    }

    override fun hashCode(): Int {
        return key.contentHashCode()
    }
}

data class PrivKey(val key: ByteArray) {
    init {
        if (key.size != 32) throw IllegalArgumentException("Private key must be 32 bytes")
    }

    constructor(hex: String) : this(hex.hexStringToByteArray())

    fun hex() = key.toHex()

    override fun toString() = hex()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PrivKey

        if (!key.contentEquals(other.key)) return false

        return true
    }

    override fun hashCode(): Int {
        return key.contentHashCode()
    }
}

data class KeyPair(val pubKey: PubKey, val privKey: PrivKey) {

    fun sigMaker(cryptoSystem: CryptoSystem) = cryptoSystem.buildSigMaker(pubKey.key, privKey.key)

    companion object {
        @JvmStatic
        fun of(pubKey: String, privKey: String) = KeyPair(PubKey(pubKey), PrivKey(privKey))
    }
}
