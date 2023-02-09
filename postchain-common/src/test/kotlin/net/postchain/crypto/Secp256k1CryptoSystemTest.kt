package net.postchain.crypto

import assertk.assert
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import net.postchain.common.hexStringToByteArray
import org.junit.jupiter.api.Test

class Secp256k1CryptoSystemTest {
    val sut = Secp256K1CryptoSystem()

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
