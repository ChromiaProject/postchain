// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.base

import net.postchain.common.toHex
import net.postchain.crypto.secp256k1_derivePubKey
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.*

class SECP256K1CryptoSystemTest {
    @Test
    fun testSignVerify() {
        val SUT = SECP256K1CryptoSystem()
        val random = Random()
        var privKey = ByteArray(32)
        for (i in 0..39) {
            random.nextBytes(privKey)
            val pubKey = secp256k1_derivePubKey(privKey)
            val sigMaker = SUT.buildSigMaker(pubKey, privKey)
            val data = "Hello".toByteArray()
            val signature = sigMaker.signMessage(data) // TODO: POS-04_sig ???
            val verifier = SUT.makeVerifier()
            assertTrue(verifier(data, signature), "Positive test failed for privkey ${privKey.toHex()}")
            assertFalse(verifier("Hell0".toByteArray(), signature), "Negative test failed for privkey ${privKey.toHex()}")
        }
    }
}