package net.postchain.crypto

import assertk.assert
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import org.junit.jupiter.api.Test

class DilithiumCryptoSystemTest {

    @Test
    fun `Can generate keypair and derive pubkey from privkey`() {
        val dilithiumCryptoSystem = DilithiumCryptoSystem()

        val keyPair = dilithiumCryptoSystem.generateKeyPair()

        assert(dilithiumCryptoSystem.validatePubKey(keyPair.pubKey.data)).isTrue()

        val derivedPubKey = dilithiumCryptoSystem.derivePubKey(keyPair.privKey)

        assert(derivedPubKey).isEqualTo(keyPair.pubKey)
    }

    @Test
    fun `Can sign and verify signature`() {
        val dilithiumCryptoSystem = DilithiumCryptoSystem()

        val keyPair = dilithiumCryptoSystem.generateKeyPair()
        val sigMaker = dilithiumCryptoSystem.buildSigMaker(keyPair)

        val digest = dilithiumCryptoSystem.digest("Hello!".toByteArray())
        val signature = sigMaker.signDigest(digest)

        assert(dilithiumCryptoSystem.verifyDigest(digest, signature)).isTrue()
    }
}
