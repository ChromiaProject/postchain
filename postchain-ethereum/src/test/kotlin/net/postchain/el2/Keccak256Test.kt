package net.postchain.el2

import net.postchain.common.hexStringToByteArray
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

class Keccak256Test {

    @Test
    fun testGetEthereumAddress() {
        val pubkey = "02e8bc920faad314f9859dd94d8ba0e62888f35ae572e1ebf80912033670b3e793".hexStringToByteArray()
        val address = getEthereumAddress(pubkey)
        val expected = "f4A8c3ef8a8968DA1680e22F289Fe0d5360755b4".hexStringToByteArray()
        assertTrue(expected.contentEquals(address))
    }

}