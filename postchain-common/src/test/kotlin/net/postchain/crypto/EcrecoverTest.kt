package net.postchain.crypto

import assertk.assert
import assertk.assertions.isEqualTo
import net.postchain.common.hexStringToByteArray
import net.postchain.common.toHex
import org.junit.jupiter.api.Test

class EcrecoverTest {

    /**
     * Test data taken from: https://gist.github.com/webmaster128/130b628d83621a33579751846699ed15
     */
    @Test
    fun testEcrecover() {
        val expectedPubKey = "044a071e8a6e10aada2b8cf39fa3b5fb3400b04e99ea8ae64ceea1a977dbeaf5d5f8c8fbd10b71ab14cd561f7df8eb6da50f8a8d81ba564342244d26d1d4211595"

        val recId = 1
        val signature = "45c0b7f8c09a9e1f1cea0c25785594427b6bf8f9f878a8af0b1abbb48e16d0920d8becd0c220f67c51217eecfd7184ef0732481c843857e6bc7fc095c4f6b78801".hexStringToByteArray()
        val components = secp256k1_decodeSignature(signature)
        val messageHash = "5ae8317d34d1e595e3fa7247db80c0af4320cce1116de187f8f7e2e099c0d8d0".hexStringToByteArray()

        val recoveredPubKey = ecrecover(recId, messageHash, components[0], components[1])

        // Skipping the first byte of pubkey
        assert(expectedPubKey.substring(2)).isEqualTo(recoveredPubKey!!.toHex(), true)
    }
}