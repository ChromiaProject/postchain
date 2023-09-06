package net.postchain.crypto.pqc.dilithium

import assertk.assertThat
import assertk.assertions.isTrue
import net.postchain.crypto.Secp256K1CryptoSystem
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

        assertThat(dilithiumCryptoSystem.verifyDigest(secp256K1Digest, secp256K1Signature)).isTrue()
    }
}
