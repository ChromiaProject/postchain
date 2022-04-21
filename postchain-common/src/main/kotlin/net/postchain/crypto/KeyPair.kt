package net.postchain.crypto

data class KeyPair(val pubKey: PubKey, val privKey: PrivKey) {
    init {
        require(secp256k1_derivePubKey(privKey.byteArray).contentEquals(pubKey.byteArray)) { "Public key ${pubKey.asString} does not match private key" }
    }

    companion object {
        @JvmStatic
        fun fromStrings(pubKey: String, privKey: String) = KeyPair(PubKey.fromString(pubKey), PrivKey.fromString(privKey))
    }
}