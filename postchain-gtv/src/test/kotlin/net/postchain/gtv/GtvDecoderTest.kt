package net.postchain.gtv

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import net.postchain.gtv.GtvFactory.gtv
import org.junit.jupiter.api.Test

class GtvDecoderTest {
    private val validGtv: ByteArray = GtvEncoder.encodeGtv(gtv("valid"))
    private val invalidGtv = ByteArray(8) { it.toByte() }
    private val emptyGtv = ByteArray(0)

    @Test
    fun valid() {
        val result = GtvDecoder.decodeGtv(validGtv)
        assertThat(result.asString()).isEqualTo("valid")
    }

    @Test
    fun invalid() {
        assertFailure {
            GtvDecoder.decodeGtv(invalidGtv)
        }.isInstanceOf(GtvException::class)
    }

    @Test
    fun empty() {
        assertFailure {
            GtvDecoder.decodeGtv(emptyGtv)
        }.isInstanceOf(GtvException::class)
    }
}
