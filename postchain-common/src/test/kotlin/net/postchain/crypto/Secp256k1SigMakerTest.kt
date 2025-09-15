package net.postchain.crypto

import net.postchain.common.data.Hash
import net.postchain.common.exception.UserMistake
import org.assertj.core.api.AssertionsForClassTypes.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class Secp256k1SigMakerTest {

    @Test
    fun `Simple key length verification`() {

        val cryptoSystem = Secp256K1CryptoSystem()
        val dummyDigestFun: (ByteArray) -> Hash = { _ -> ByteArray(0) }

        val keyPair = cryptoSystem.generateKeyPair()
        val pubKey = keyPair.pubKey.data
        val pubKeyUncompressed = CURVE.curve.decodePoint(pubKey).getEncoded(false)
        val privKey = keyPair.privKey.data

        // Invalid compressed pubkeys
        assertThat(assertThrows<UserMistake> {
            Secp256k1SigMaker(cryptoSystem.id, pubKey.copyOfRange(0, 32), privKey, dummyDigestFun)
        }.message).isEqualTo("Invalid public key: Incorrect length for compressed encoding")
        assertThat(assertThrows<UserMistake> {
            Secp256k1SigMaker(cryptoSystem.id, pubKey.plus(0), privKey, dummyDigestFun)
        }.message).isEqualTo("Invalid public key: Incorrect length for compressed encoding")

        // Accidentally swap public key with the private key
        assertThat(assertThrows<UserMistake> {
            Secp256k1SigMaker(cryptoSystem.id, privKey, privKey, dummyDigestFun)
        }.message).startsWith("Invalid public key")

        // Invalid uncompressed pubkeys
        assertThat(assertThrows<UserMistake> {
            Secp256k1SigMaker(cryptoSystem.id, pubKeyUncompressed.copyOfRange(0, 64), privKey, dummyDigestFun)
        }.message).isEqualTo("Invalid public key: Incorrect length for uncompressed encoding")
        assertThat(assertThrows<UserMistake> {
            Secp256k1SigMaker(cryptoSystem.id, pubKeyUncompressed.plus(0), privKey, dummyDigestFun)
        }.message).isEqualTo("Invalid public key: Incorrect length for uncompressed encoding")

        // Invalid privkey
        assertThat(assertThrows<UserMistake> {
            Secp256k1SigMaker(cryptoSystem.id, pubKey, ByteArray(33), dummyDigestFun)
        }.message).isEqualTo("Private key must be 32 bytes long")
        assertThat(assertThrows<UserMistake> {
            Secp256k1SigMaker(cryptoSystem.id, pubKey, pubKey, dummyDigestFun)
        }.message).isEqualTo("Private key must be 32 bytes long")
        assertThat(assertThrows<UserMistake> {
            Secp256k1SigMaker(cryptoSystem.id, pubKey, ByteArray(32), dummyDigestFun)
        }.message).isEqualTo("Invalid private key: Scalar is not in the interval [1, n - 1]")

        // Valid compressed and uncompressed pubkeys
        Secp256k1SigMaker(cryptoSystem.id, pubKey, privKey, dummyDigestFun)
        Secp256k1SigMaker(cryptoSystem.id, pubKeyUncompressed, privKey, dummyDigestFun)
    }
}