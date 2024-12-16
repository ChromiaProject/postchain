package net.postchain.gtv

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import net.postchain.gtv.GtvFactory.gtv
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream

class GtvDecoderTest {
    private val validGtv: ByteArray = GtvEncoder.encodeGtv(gtv("valid"))
    private val validGtv2: ByteArray = GtvEncoder.encodeGtv(gtv("also valid"))
    private val invalidGtv = ByteArray(8) { it.toByte() }
    private val emptyGtv = ByteArray(0)
    private val array1: ByteArray = GtvEncoder.encodeGtv(gtv(listOf(gtv("1"), gtv("2"), gtv("3"))))
    private val array2: ByteArray = GtvEncoder.encodeGtv(gtv(listOf(gtv("4"), gtv("5"), gtv("6"))))
    private val dict1: ByteArray = GtvEncoder.encodeGtv(gtv(mapOf("one" to gtv("1"), "two" to gtv("2"), "three" to gtv("3"))))
    private val dict2: ByteArray = GtvEncoder.encodeGtv(gtv(mapOf("four" to gtv("4"), "five" to gtv("5"), "six" to gtv("6"))))

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

    @Test
    fun `extra data at end is not accepted for ByteArray`() {
        assertFailure {
            GtvDecoder.decodeGtv(validGtv + validGtv2)
        }.isInstanceOf(GtvException::class)
    }

    @Test
    fun `cannot concatenate arrays for ByteArray`() {
        assertFailure {
            GtvDecoder.decodeGtv(array1 + array2)
        }.isInstanceOf(GtvException::class)
    }

    @Test
    fun `cannot concatenate dict for ByteArray`() {
        assertFailure {
            GtvDecoder.decodeGtv(dict1 + dict2)
        }.isInstanceOf(GtvException::class)
    }

    @Test
    fun validStream() {
        val result = GtvDecoder.decodeGtv(ByteArrayInputStream(validGtv))
        assertThat(result.asString()).isEqualTo("valid")
    }

    @Test
    fun invalidStream() {
        assertFailure {
            GtvDecoder.decodeGtv(ByteArrayInputStream(invalidGtv))
        }.isInstanceOf(GtvException::class)
    }

    @Test
    fun emptyStream() {
        assertFailure {
            GtvDecoder.decodeGtv(ByteArrayInputStream(emptyGtv))
        }.isInstanceOf(GtvException::class)
    }

    @Test
    fun `extra data at end is accepted and ignored for stream`() {
        val result = GtvDecoder.decodeGtv(ByteArrayInputStream(validGtv + validGtv2))
        assertThat(result.asString()).isEqualTo("valid")
    }

    @Test
    fun `cannot concatenate arrays for stream`() {
        val result = GtvDecoder.decodeGtv(ByteArrayInputStream(array1 + array2))
        assertThat(result).equals(array1)
    }

    @Test
    fun `cannot concatenate dict for stream`() {
        val result = GtvDecoder.decodeGtv(ByteArrayInputStream(dict1 + dict2))
        assertThat(result).equals(dict1)
    }
}
