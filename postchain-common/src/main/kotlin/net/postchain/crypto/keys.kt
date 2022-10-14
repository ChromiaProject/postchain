package net.postchain.crypto

import net.postchain.common.hexStringToByteArray
import net.postchain.common.toHex
import net.postchain.common.types.WByteArray

data class PubKey(private val wData: WByteArray) {
    @Deprecated(message = "Use 'data' accessor in stead", replaceWith = ReplaceWith("data"))
    val key get() = wData.data
    val data get() = wData.data
    init {
        if (data.size != 33) throw IllegalArgumentException("Public key must be 33 bytes")
    }

    constructor(data: ByteArray) : this(WByteArray(data))
    constructor(hex: String) : this(hex.hexStringToByteArray())

    fun hex() = data.toHex()

    override fun toString() = hex()
}

data class PrivKey(private val wData: WByteArray) {
    @Deprecated(message = "Use 'data' accessor in stead", replaceWith = ReplaceWith("data"))
    val key get() = wData.data
    val data get() = wData.data
    init {
        if (key.size != 32) throw IllegalArgumentException("Private key must be 32 bytes")
    }

    constructor(data: ByteArray) : this(WByteArray(data))
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
