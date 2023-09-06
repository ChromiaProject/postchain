package net.postchain.crypto.devtools

interface KeyPairCache {
    fun privKey(pubKey: ByteArray): ByteArray
    fun privKey(index: Int): ByteArray
    fun privKeyHex(index: Int): String
    fun pubKey(index: Int): ByteArray
    fun pubKeyHex(index: Int): String
    fun pubKeyFromByteArray(pubKeyHex: String): Int?
}
