package net.postchain.crypto

import net.postchain.common.hexStringToByteArray
import net.postchain.common.toHex
import net.postchain.common.types.WrappedByteArray

@JvmInline
value class PubKey(private val wData: WrappedByteArray) {
    val data get() = wData.data
    @Deprecated(message = "Use 'data' accessor in stead", replaceWith = ReplaceWith("data"))
    val key get() = data
    init {
        if (data.size != 33) throw IllegalArgumentException("Public key must be 33 bytes")
    }

    constructor(data: ByteArray) : this(WrappedByteArray(data))
    constructor(hex: String) : this(hex.hexStringToByteArray())

    fun hex() = data.toHex()

    override fun toString() = hex()
}

@JvmInline
value class PrivKey(private val wData: WrappedByteArray) {
    val data get() = wData.data
    @Deprecated(message = "Use 'data' accessor in stead", replaceWith = ReplaceWith("data"))
    val key get() = data
    init {
        if (data.size != 32) throw IllegalArgumentException("Private key must be 32 bytes")
    }

    constructor(data: ByteArray) : this(WrappedByteArray(data))
    constructor(hex: String) : this(hex.hexStringToByteArray())

    fun hex() = data.toHex()

    override fun toString() = hex()
}

data class KeyPair(val pubKey: PubKey, val privKey: PrivKey) {

    fun sigMaker(cryptoSystem: CryptoSystem) = cryptoSystem.buildSigMaker(pubKey.data, privKey.data)

    companion object {
        @JvmStatic
        fun of(pubKey: String, privKey: String) = KeyPair(PubKey(pubKey), PrivKey(privKey))
    }
}
