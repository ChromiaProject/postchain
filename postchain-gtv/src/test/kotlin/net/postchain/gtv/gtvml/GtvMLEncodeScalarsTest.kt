// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.gtv.gtvml

import assertk.assertThat
import assertk.assertions.isEqualTo
import net.postchain.gtv.*
import org.junit.jupiter.api.Test

class GtvMLEncodeScalarsTest {

    @Test
    fun encodeXMLGtv_null_successfully() {
        val gtv = GtvNull
        val actual = GtvMLEncoder.encodeXMLGtv(gtv)
        val expected = expected("<null xsi:nil=\"true\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"/>")

        assertThat(actual).isEqualTo(expected)
    }

    @Test
    fun encodeXMLGtv_string_successfully() {
        val gtv = GtvString("hello")
        val actual = GtvMLEncoder.encodeXMLGtv(gtv)
        val expected = expected("<string>hello</string>")

        assertThat(actual).isEqualTo(expected)
    }

    @Test
    fun encodeXMLGtv_int_successfully() {
        val gtv = GtvInteger(42)
        val actual = GtvMLEncoder.encodeXMLGtv(gtv)
        val expected = expected("<int>42</int>")

        assertThat(actual).isEqualTo(expected)
    }

    /**
     * According [1-3] XmlAdapter will not be applied to the root element of xml
     * and should be called manually.
     *
     * 1. https://stackoverflow.com/questions/21640566/
     * 2. https://jcp.org/en/jsr/detail?id=222
     * 3. https://coderanch.com/t/505457/
     * 4. [ObjectFactory.createBytearrayElement]
     */
    @Test
    fun encodeXMLGtv_bytea_as_root_element_and_xmladapter_not_called_successfully() {
        val gtv = GtvByteArray(
                byteArrayOf(0x01, 0x02, 0x03, 0x0A, 0x0B, 0x0C))
        val actual = GtvMLEncoder.encodeXMLGtv(gtv)
        val expected = expected("<bytea>AQIDCgsM</bytea>") // 0102030A0B0C

        assertThat(actual).isEqualTo(expected)
    }

    @Test
    fun encodeXMLGtv_bytea_as_nested_element_successfully() {
        val gtv = GtvByteArray(
                byteArrayOf(0x01, 0x02, 0x03, 0x0A, 0x0B, 0x0C))
        val array = GtvArray(arrayOf(gtv))

        val actual = GtvMLEncoder.encodeXMLGtv(array)
        val expected = expected("""
            <array>
                <bytea>0102030A0B0C</bytea>
            </array>
        """.trimIndent())

        assertThat(actual).isEqualTo(expected)
    }

    @Test
    fun encodeXMLGtv_bytea_empty_successfully() {
        val gtv = GtvByteArray(byteArrayOf())
        val actual = GtvMLEncoder.encodeXMLGtv(gtv)
        val expected = expected("<bytea></bytea>")

        assertThat(actual).isEqualTo(expected)
    }
}