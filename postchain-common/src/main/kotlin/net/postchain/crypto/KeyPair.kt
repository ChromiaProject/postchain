package net.postchain.crypto

data class KeyPair(val pubKey: Key, val privKey: Key) {
    init {
        require(secp256k1_derivePubKey(privKey.byteArray).contentEquals(pubKey.byteArray)) { "Public key ${pubKey.asString} does not match private key" }
    }

    companion object {
        @JvmStatic
        fun fromStrings(pubKey: String, privKey: String) = KeyPair(Key.fromString(pubKey), Key.fromString(privKey))
    }
}