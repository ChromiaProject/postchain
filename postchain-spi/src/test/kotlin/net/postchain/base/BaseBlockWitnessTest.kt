package net.postchain.base

import net.postchain.crypto.Signature
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.nio.ByteBuffer

class BaseBlockWitnessTest {

    @Test
    fun `rejects witness with huge signature count without allocating`() {
        val raw = byteArrayOf(0x7F.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte())

        assertThrows<IllegalArgumentException> {
            BaseBlockWitness.fromBytes(raw)
        }
    }

    @Test
    fun `rejects declared subject ID size larger than remaining bytes`() {
        val raw = ByteBuffer.allocate(8)
                .putInt(1) // signature count
                .putInt(100) // subject ID size, but no bytes follow
                .array()

        assertThrows<IllegalArgumentException> {
            BaseBlockWitness.fromBytes(raw)
        }
    }

    @Test
    fun `accepts empty witness`() {
        val witness = BaseBlockWitness.fromSignatures(emptyArray<Signature>())
        val decoded = BaseBlockWitness.fromBytes(witness.getRawData())

        assert(decoded.getSignatures().isEmpty())
    }
}
