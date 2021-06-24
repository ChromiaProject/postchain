package net.postchain.el2

import junit.framework.TestCase
import net.postchain.common.hexStringToByteArray
import net.postchain.common.toHex
import org.junit.Test

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
}