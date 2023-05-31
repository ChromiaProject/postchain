package net.postchain.crypto

import assertk.assertThat
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
            assertThat(verifier(data, signature), "Positive test failed for privkey ${keyPair.privKey.data.toHex()}").isTrue()
            assertThat(verifier("Hell0".toByteArray(), signature), "Negative test failed for privkey ${keyPair.privKey.data.toHex()}").isFalse()
        }
    }

    @Test
    fun validPubKey() {
        assertThat(sut.validatePubKey("02DBBD2B3466D1B65FD16DD7556DBF44C46ED0D20DE83F70C14C5A7733F923556A".hexStringToByteArray())).isTrue()
    }

    @Test
    fun invalidPubKey() {
        assertThat(sut.validatePubKey("025645654674545654645678786956745932557475856935675675699566774576".hexStringToByteArray())).isFalse()
    }

    @Test
    fun emptyPubKey() {
        assertThat(sut.validatePubKey("".hexStringToByteArray())).isFalse()
    }

    @Test
    fun tooShortPubKey() {
        assertThat(sut.validatePubKey("02030405".hexStringToByteArray())).isFalse()
    }

    @Test
    fun validLongPubKey() {
        assertThat(sut.validatePubKey(
                "041B84C5567B126440995D3ED5AABA0565D71E1834604819FF9C17F5E9D5DD078F70BEAF8F588B541507FED6A642C5AB42DFDF8120A7F639DE5122D47A69A8E8D1".hexStringToByteArray()
        )).isTrue()
    }

    @Test
    fun invalidLongPubKey() {
        assertThat(sut.validatePubKey(
                "042B84C5567B126440995D3ED5AABA0565D71E1834604819FF9C17F5E9D5DD078F70BEAF8F588B541507FED6A642C5AB42DFDF8120A7F639DE5122D47A69A8E8D1".hexStringToByteArray()
        )).isFalse()
    }
}
