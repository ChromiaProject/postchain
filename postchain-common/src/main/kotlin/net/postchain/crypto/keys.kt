package net.postchain.crypto

import net.postchain.common.hexStringToByteArray
import net.postchain.common.toHex
import net.postchain.common.types.WrappedByteArray

data class PubKey(val data: ByteArray) {
    @Deprecated(message = "Use 'data' accessor instead", replaceWith = ReplaceWith("data"))
    val key get() = data

    init {
        if (data.size != 33) throw IllegalArgumentException("Public key must be 33 bytes")
    }

    constructor(wrapped: WrappedByteArray) : this(wrapped.data)
    constructor(hex: String) : this(hex.hexStringToByteArray())

    fun hex() = data.toHex()

    override fun toString() = hex()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PubKey

        if (!data.contentEquals(other.data)) return false

        return true
    }

    override fun hashCode(): Int {
        return data.contentHashCode()
    }
}

data class PrivKey(val data: ByteArray) {
    @Deprecated(message = "Use 'data' accessor instead", replaceWith = ReplaceWith("data"))
    val key get() = data

    init {
        if (data.size != 32) throw IllegalArgumentException("Private key must be 32 bytes")
    }

    constructor(wrapped: WrappedByteArray) : this(wrapped.data)
    constructor(hex: String) : this(hex.hexStringToByteArray())

    fun hex() = data.toHex()

    override fun toString() = hex()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PrivKey

        if (!data.contentEquals(other.data)) return false

        return true
    }

    override fun hashCode(): Int {
        return data.contentHashCode()
    }
}

data class KeyPair(val pubKey: PubKey, val privKey: PrivKey) {

    fun sigMaker(cryptoSystem: CryptoSystem) = cryptoSystem.buildSigMaker(pubKey.data, privKey.data)

    companion object {
        @JvmStatic
        fun of(pubKey: String, privKey: String) = KeyPair(PubKey(pubKey), PrivKey(privKey))
    }
}
