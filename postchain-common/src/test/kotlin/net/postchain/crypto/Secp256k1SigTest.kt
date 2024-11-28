package net.postchain.crypto

import assertk.assertThat
import assertk.assertions.isEqualTo
import net.postchain.common.exception.UserMistake
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
        val privKey = PrivKey(ByteArray(32) { 1 })
        val cryptoSystem = Secp256K1CryptoSystem()
        val pubKey = cryptoSystem.derivePubKey(privKey)
        val sha256 = MessageDigest.getInstance("SHA-256")
        val digest = sha256.digest("Absence makes the heart grow fonder.".toByteArray())
        val sigMaker = cryptoSystem.buildSigMaker(KeyPair(pubKey, privKey))
        val signature = sigMaker.signDigest(digest)

        val expectedSignature = "225C195DDD198AD2840EFB7017B7484FF353A77686F5D211B22D12129A4C78214CE22891D74A635A5F18A56EEB17716AA1C84E4F5ED389A8F56E7F5ECC9F8BCA"
        assertThat(signature.data.toHex()).isEqualTo(expectedSignature)
    }

    /**
     * Please refer to: https://github.com/bitcoin/bips/blob/master/bip-0062.mediawiki#Low_S_values_in_signatures
     * These particular inputs will lead to a high S value that will be canonicalized to S' = CURVE - S
     */
    @Test
    fun `Should canonicalize S values`() {
        val privKey = PrivKey(ByteArray(32) { 3 })
        val cryptoSystem = Secp256K1CryptoSystem()
        val pubKey = cryptoSystem.derivePubKey(privKey)
        val sha256 = MessageDigest.getInstance("SHA-256")
        val digest = sha256.digest("All for one and one for all.".toByteArray())
        val sigMaker = cryptoSystem.buildSigMaker(KeyPair(pubKey, privKey))
        val signature = sigMaker.signDigest(digest)

        val expectedSignature = "45DE827CD49A36D658A1F8E69BDBDBFC612F2307C6ED299078900E86C3C47E3A469AAB836E48ED66F6E62BE4EA17DAA7A33318CCD27A9825CC30EB18A51810DA"
        assertThat(signature.data.toHex()).isEqualTo(expectedSignature)
    }

    @Test
    fun `Should not allow digests larger than 32 bytes`() {
        val cryptoSystem = Secp256K1CryptoSystem()
        val sigMaker = cryptoSystem.buildSigMaker(cryptoSystem.generateKeyPair())
        val exception = assertThrows<UserMistake> {
            sigMaker.signDigest(ByteArray(33))
        }
        assertThat(exception.message).isEqualTo("Digest to sign must be 32 bytes long")
    }
}