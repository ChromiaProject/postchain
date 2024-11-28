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
        val pubKey = keyPair.pubKey
        val privKey = keyPair.privKey

        // Invalid compressed pubkeys
        assertThat(assertThrows<UserMistake> {
            Secp256k1SigMaker(pubKey.data.copyOfRange(0, 32), privKey.data, dummyDigestFun)
        }.message).isEqualTo("Compressed public key must be 33 bytes long")
        assertThat(assertThrows<UserMistake> {
            Secp256k1SigMaker(pubKey.data.plus(0), privKey.data, dummyDigestFun)
        }.message).isEqualTo("Compressed public key must be 33 bytes long")

        // Invalid uncompressed pubkeys
        assertThat(assertThrows<UserMistake> {
            Secp256k1SigMaker(ByteArray(64), privKey.data, dummyDigestFun)
        }.message).isEqualTo("Uncompressed public key must be 65 bytes long")
        assertThat(assertThrows<UserMistake> {
            Secp256k1SigMaker(ByteArray(66), privKey.data, dummyDigestFun)
        }.message).isEqualTo("Uncompressed public key must be 65 bytes long")

        // Invalid privkey
        assertThat(assertThrows<UserMistake> {
            Secp256k1SigMaker(pubKey.data, ByteArray(33), dummyDigestFun)
        }.message).isEqualTo("Private key must be 32 bytes long")

        // Valid compressed and uncompressed pubkeys
        Secp256k1SigMaker(pubKey.data, privKey.data, dummyDigestFun)
        Secp256k1SigMaker(ByteArray(65), privKey.data, dummyDigestFun)
    }
}