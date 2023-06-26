package net.postchain.crypto

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import org.junit.jupiter.api.Test

class DilithiumCryptoSystemTest {

    @Test
    fun `Can generate keypair and derive pubkey from privkey`() {
        val sut = DilithiumCryptoSystem()

        val keyPair = sut.generateKeyPair()
        assertThat(sut.validatePubKey(keyPair.pubKey.data)).isTrue()

        val derivedPubKey = sut.derivePubKey(keyPair.privKey)
        assertThat(derivedPubKey).isEqualTo(keyPair.pubKey)
    }

    @Test
    fun `Can sign and verify signature`() {
        val sut = DilithiumCryptoSystem()
        val message = "Hello!".toByteArray()

        // correct signature
        val keyPair = sut.generateKeyPair()
        val sigMaker = sut.buildSigMaker(keyPair)
        val signature = sigMaker.signMessage(message)
        val verifier = sut.makeVerifier()
        assertThat(verifier(message, signature)).isTrue()

        // wrong signature
        val keyPair2 = sut.generateKeyPair()
        val sigMaker2 = sut.buildSigMaker(keyPair2)
        val signature2 = sigMaker2.signMessage(message)
        val wrongSignature = Signature(signature.subjectID, signature2.data)
        assertThat(verifier(message, wrongSignature)).isFalse()
    }
}
