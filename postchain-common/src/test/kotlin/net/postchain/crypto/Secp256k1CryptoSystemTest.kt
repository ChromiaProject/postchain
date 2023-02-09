package net.postchain.crypto

import assertk.assert
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import net.postchain.common.hexStringToByteArray
import net.postchain.common.toHex
import org.junit.jupiter.api.Test

class Secp256k1CryptoSystemTest {
    val sut = Secp256K1CryptoSystem()

    @Test
    fun testSignVerify() {
        for (i in 0..39) {
            val keyPair = sut.generateKeyPair()
            val sigMaker = sut.buildSigMaker(keyPair)
            val data = "Hello".toByteArray()
            val signature = sigMaker.signMessage(data) // TODO: POS-04_sig ???
            val verifier = sut.makeVerifier()
            assert(verifier(data, signature), "Positive test failed for privkey ${keyPair.privKey.data.toHex()}").isTrue()
            assert(verifier("Hell0".toByteArray(), signature), "Negative test failed for privkey ${keyPair.privKey.data.toHex()}").isFalse()
        }
    }

    @Test
    fun validPubKey() {
        assert(sut.validatePubKey("02DBBD2B3466D1B65FD16DD7556DBF44C46ED0D20DE83F70C14C5A7733F923556A".hexStringToByteArray())).isTrue()
    }

    @Test
    fun invalidPubKey() {
        assert(sut.validatePubKey("025645654674545654645678786956745932557475856935675675699566774576".hexStringToByteArray())).isFalse()
    }

    @Test
    fun emptyPubKey() {
        assert(sut.validatePubKey("".hexStringToByteArray())).isFalse()
    }

    @Test
    fun tooShortPubKey() {
        assert(sut.validatePubKey("02030405".hexStringToByteArray())).isFalse()
    }
}
