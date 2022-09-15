package net.postchain.crypto

import assertk.assert
import assertk.assertions.isEqualTo
import net.postchain.common.hexStringToByteArray
import net.postchain.common.toHex
import org.junit.jupiter.api.Test
import java.security.MessageDigest

/**
 * Test values fetched from here (DER encoding is removed in the test cases):
 * https://crypto.stackexchange.com/a/54222
 */
class Secp256k1SigTest {

    @Test
    fun testSign() {
        val privKey = "01".hexStringToByteArray()
        val sha256 = MessageDigest.getInstance("SHA-256")
        val digest = sha256.digest("Absence makes the heart grow fonder.".toByteArray())
        val signature = secp256k1_sign(digest, privKey)

        val expectedSignature = "AFFF580595971B8C1700E77069D73602AEF4C2A760DBD697881423DFFF845DE8579ADB6A1AC03ACDE461B5821A049EBD39A8A8EBF2506B841B15C27342D2E342"
        assert(signature.toHex()).isEqualTo(expectedSignature)
    }

    /**
     * Please refer to: https://github.com/bitcoin/bips/blob/master/bip-0062.mediawiki#Low_S_values_in_signatures
     * These particular inputs will lead to a high S value that will be canonicalized to S' = CURVE - S
     */
    @Test
    fun `Should canonicalize S values`() {
        val privKey = "03".hexStringToByteArray()
        val sha256 = MessageDigest.getInstance("SHA-256")
        val digest = sha256.digest("All for one and one for all.".toByteArray())
        val signature = secp256k1_sign(digest, privKey)

        val expectedSignature = "502C6AC38E1C68CE68F044F5AB680F2880A6C1CD34E70F2B4F945C6FD30ABD0318EF5C6C3392B9D67AD5109C85476A0E159425D7F6ACE2CEBEAA65F02F210BBB"
        assert(signature.toHex()).isEqualTo(expectedSignature)
    }
}