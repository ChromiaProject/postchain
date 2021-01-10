package net.postchain.crypto

import junit.framework.TestCase
import net.postchain.common.hexStringToByteArray
import net.postchain.common.toHex
import org.junit.Test
import java.security.InvalidParameterException
import kotlin.test.assertFailsWith

class SECP256K1KeccakTest : TestCase() {

    @Test
    fun testGetEthereumAddress1() {
        val pubkey = "02e8bc920faad314f9859dd94d8ba0e62888f35ae572e1ebf80912033670b3e793".hexStringToByteArray()
        val address = SECP256K1Keccak.getEthereumAddress(pubkey)
        val expected = "f4A8c3ef8a8968DA1680e22F289Fe0d5360755b4".hexStringToByteArray()
        assertTrue(expected.contentEquals(address))
    }

    @Test
    fun testToChecksumAddress() {
        val pubkey = "039562c20fffe6f1b2f62565978da44fd25eae3703492c869deb105f83259df6b0".hexStringToByteArray()
        val address = SECP256K1Keccak.getEthereumAddress(pubkey)
        val checksumAddress = SECP256K1Keccak.toChecksumAddress(address.toHex())
        val expected = "0x17d2C9EAb8d3BeDf39497c1A176eaEedfc3075CB"
        assertEquals(expected, checksumAddress)
    }

    @Test
    fun testTreeHasher() {
        val expected = "08629AE32294B0F0B9B75732D124F37E3F1E88C67028C8FB63F5280D11945961"
        val left = "c89efdaa54c0f20c7adf612882df0950f5a951637e0307cdcb4c672f298b8bc6".hexStringToByteArray()
        val right = "ad7c5bef027816a800da1736444fb58a807ef4c9603b7848673f7e3a68eb14a5".hexStringToByteArray()
        val actual = SECP256K1Keccak.treeHasher(left, right)
        assertEquals(expected, actual.toHex())
        assertTrue(actual.contentEquals(expected.hexStringToByteArray()))
    }

    @Test
    fun testTreeHasherLeftNull() {
        val expected = "075E5B763130D2422F348BE7B0B5F6325D77894507B96AB2B266A3BF89E27129"
        val left = ByteArray(32) {0}
        val right = "ad7c5bef027816a800da1736444fb58a807ef4c9603b7848673f7e3a68eb14a5".hexStringToByteArray()
        val actual = SECP256K1Keccak.treeHasher(left, right)
        assertEquals(expected, actual.toHex())
        assertTrue(actual.contentEquals(expected.hexStringToByteArray()))
    }

    @Test
    fun testTreeHasherRightNull() {
        val expected = "6225C4B700F552912ACDFAD9481140B6B2F8B19D27459370EC47600BB18D73A7"
        val left = "ad7c5bef027816a800da1736444fb58a807ef4c9603b7848673f7e3a68eb14a5".hexStringToByteArray()
        val right = ByteArray(32) {0}
        val actual = SECP256K1Keccak.treeHasher(left, right)
        assertEquals(expected, actual.toHex())
        assertTrue(actual.contentEquals(expected.hexStringToByteArray()))
    }

    @Test
    fun testTreeHasherEmpty() {
        val expected = ByteArray(32) {0}
        val actual = SECP256K1Keccak.treeHasher(ByteArray(32) {0}, ByteArray(32) {0})
        assertTrue(actual.contentEquals(expected))
    }

    @Test
    fun testTreeHasherInvalidHash() {
        val exception = assertFailsWith<InvalidParameterException> {
            SECP256K1Keccak.treeHasher(byteArrayOf(0), ByteArray(32) {0})
        }
        assertEquals("invalid hash length", exception.message)
    }
}