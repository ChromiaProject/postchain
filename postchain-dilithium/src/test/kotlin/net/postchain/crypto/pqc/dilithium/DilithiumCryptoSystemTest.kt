package net.postchain.crypto.pqc.dilithium

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import net.postchain.crypto.Signature
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
        assertThat(sut.verifyDigest(sut.digest(message), signature)).isTrue()

        // wrong signature
        val keyPair2 = sut.generateKeyPair()
        val sigMaker2 = sut.buildSigMaker(keyPair2)
        val signature2 = sigMaker2.signMessage(message)
        val wrongSignature = Signature(signature.subjectID, signature2.data)
        assertThat(verifier(message, wrongSignature)).isFalse()
        assertThat(sut.verifyDigest(sut.digest(message), wrongSignature)).isFalse()
    }

    @Test
    fun `should not accept signatures with redundant data`() {
        val sut = DilithiumCryptoSystem()

        val keyPair = sut.generateKeyPair()
        val sigMaker = sut.buildSigMaker(keyPair)
        val data = "Hello".toByteArray()
        val signature = sigMaker.signMessage(data)
        val bogusSignature = Signature(signature.subjectID, signature.data + byteArrayOf(1, 2, 3, 4))
        val verifier = sut.makeVerifier()
        assertThat(verifier(data, bogusSignature)).isFalse()
        assertThat(sut.verifyDigest(sut.digest(data), bogusSignature)).isFalse()
    }
}
