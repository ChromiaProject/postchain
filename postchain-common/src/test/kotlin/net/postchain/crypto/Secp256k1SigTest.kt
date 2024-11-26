package net.postchain.crypto

import assertk.assertThat
import assertk.assertions.isEqualTo
import net.postchain.common.exception.UserMistake
import net.postchain.common.hexStringToByteArray
import net.postchain.common.toHex
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.security.MessageDigest

/**
 * Test values fetched from here (DER encoding is removed in the test cases):
 * https://crypto.stackexchange.com/a/54222
 */
class Secp256k1SigTest {

    @Test
    fun testSign() {
        val privKey = PrivKey("01".hexStringToByteArray())
        val cryptoSystem = Secp256K1CryptoSystem()
        val pubKey = cryptoSystem.derivePubKey(privKey)
        val sha256 = MessageDigest.getInstance("SHA-256")
        val digest = sha256.digest("Absence makes the heart grow fonder.".toByteArray())
        val sigMaker = cryptoSystem.buildSigMaker(KeyPair(pubKey, privKey))
        val signature = sigMaker.signDigest(digest)

        val expectedSignature = "AFFF580595971B8C1700E77069D73602AEF4C2A760DBD697881423DFFF845DE8579ADB6A1AC03ACDE461B5821A049EBD39A8A8EBF2506B841B15C27342D2E342"
        assertThat(signature.data.toHex()).isEqualTo(expectedSignature)
    }

    /**
     * Please refer to: https://github.com/bitcoin/bips/blob/master/bip-0062.mediawiki#Low_S_values_in_signatures
     * These particular inputs will lead to a high S value that will be canonicalized to S' = CURVE - S
     */
    @Test
    fun `Should canonicalize S values`() {
        val privKey = PrivKey("03".hexStringToByteArray())
        val cryptoSystem = Secp256K1CryptoSystem()
        val pubKey = cryptoSystem.derivePubKey(privKey)
        val sha256 = MessageDigest.getInstance("SHA-256")
        val digest = sha256.digest("All for one and one for all.".toByteArray())
        val sigMaker = cryptoSystem.buildSigMaker(KeyPair(pubKey, privKey))
        val signature = sigMaker.signDigest(digest)

        val expectedSignature = "502C6AC38E1C68CE68F044F5AB680F2880A6C1CD34E70F2B4F945C6FD30ABD0318EF5C6C3392B9D67AD5109C85476A0E159425D7F6ACE2CEBEAA65F02F210BBB"
        assertThat(signature.data.toHex()).isEqualTo(expectedSignature)
    }

    @Test
    fun `Should not allow digests larger than 32 bytes`() {
        val privKey = PrivKey("01".hexStringToByteArray())
        val cryptoSystem = Secp256K1CryptoSystem()
        val pubKey = cryptoSystem.derivePubKey(privKey)
        val sigMaker = cryptoSystem.buildSigMaker(KeyPair(pubKey, privKey))
        val exception = assertThrows<UserMistake> {
            sigMaker.signDigest(ByteArray(33))
        }
        assertThat(exception.message).isEqualTo("Digest to sign must be 32 bytes long")
    }
}