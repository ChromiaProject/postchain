package net.postchain.crypto

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.Test

class BaseCryptoSystemTest {

    @Test
    fun `Dilithium crypto system can verify secp256k1 signatures`() {
        val dilithiumCryptoSystem = DilithiumCryptoSystem()
        val secp256K1CryptoSystem = Secp256K1CryptoSystem()

        val secp256K1KeyPair = secp256K1CryptoSystem.generateKeyPair()
        val secp256K1SigMaker = secp256K1CryptoSystem.buildSigMaker(secp256K1KeyPair)

        val secp256K1Digest = secp256K1CryptoSystem.digest("Hello!".toByteArray())
        val secp256K1Signature = secp256K1SigMaker.signDigest(secp256K1Digest)

        assertThat(dilithiumCryptoSystem.verifyDigest(secp256K1Digest, secp256K1Signature)).isEqualTo(true)
    }

    @Test
    fun `Secp256k1 crypto system can verify dilithium signatures`() {
        val secp256K1CryptoSystem = Secp256K1CryptoSystem()
        val dilithiumCryptoSystem = DilithiumCryptoSystem()

        val dilithiumKeyPair = dilithiumCryptoSystem.generateKeyPair()
        val dilithiumSigMaker = dilithiumCryptoSystem.buildSigMaker(dilithiumKeyPair)

        val dilithiumDigest = dilithiumCryptoSystem.digest("Hello!".toByteArray())
        val dilithiumSignature = dilithiumSigMaker.signDigest(dilithiumDigest)

        assertThat(secp256K1CryptoSystem.verifyDigest(dilithiumDigest, dilithiumSignature)).isEqualTo(true)
    }
}
