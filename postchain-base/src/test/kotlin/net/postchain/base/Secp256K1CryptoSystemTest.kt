// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.base

import net.postchain.common.toHex
import net.postchain.crypto.Secp256K1CryptoSystem
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class Secp256K1CryptoSystemTest {
    @Test
    fun testSignVerify() {
        val SUT = Secp256K1CryptoSystem()
        for (i in 0..39) {
            val keyPair = SUT.generateKeyPair()
            val sigMaker = SUT.buildSigMaker(keyPair)
            val data = "Hello".toByteArray()
            val signature = sigMaker.signMessage(data) // TODO: POS-04_sig ???
            val verifier = SUT.makeVerifier()
            assertTrue(verifier(data, signature), "Positive test failed for privkey ${keyPair.privKey.data.toHex()}")
            assertFalse(verifier("Hell0".toByteArray(), signature), "Negative test failed for privkey ${keyPair.privKey.data.toHex()}")
        }
    }
}