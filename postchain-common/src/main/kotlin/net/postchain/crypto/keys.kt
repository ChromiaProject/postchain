package net.postchain.crypto

import net.postchain.common.hexStringToByteArray
import net.postchain.common.toHex
import net.postchain.common.types.WrappedByteArray

interface Key {
    val wData: WrappedByteArray
    val data get() = wData.data
    @Deprecated(message = "Use 'data' accessor instead", replaceWith = ReplaceWith("data"))
    val key get() = data

    fun hex() = data.toHex()
    fun toShortHex() = hex().take(8)
}

data class PubKey(override val wData: WrappedByteArray): Key {
    init {
        require(data.isNotEmpty()) { "Public key must mot be empty" }
    }

    constructor(data: ByteArray) : this(WrappedByteArray(data))
    constructor(hex: String) : this(hex.hexStringToByteArray())

    override fun toString() = hex()
}

data class PrivKey(override val wData: WrappedByteArray): Key {
    init {
        require(data.isNotEmpty()) { "Private key must mot be empty" }
    }

    constructor(data: ByteArray) : this(WrappedByteArray(data))
    constructor(hex: String) : this(hex.hexStringToByteArray())

    override fun toString() = hex()
}

data class KeyPair(val pubKey: PubKey, val privKey: PrivKey) {

    constructor(pubKey: ByteArray, privKey: ByteArray) : this(PubKey(pubKey), PrivKey(privKey))

    fun sigMaker(cryptoSystem: CryptoSystem) = cryptoSystem.buildSigMaker(this)

    companion object {
        @JvmStatic
        fun of(pubKey: String, privKey: String) = KeyPair(PubKey(pubKey), PrivKey(privKey))
    }
}
