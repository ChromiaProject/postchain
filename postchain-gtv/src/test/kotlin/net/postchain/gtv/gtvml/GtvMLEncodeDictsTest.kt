// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.gtv.gtvml

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isIn
import net.postchain.gtv.*
import org.junit.jupiter.api.Test

class GtvMLEncodeDictsTest {

    @Test
    fun encodeXMLGtv_dict_empty_successfully() {
        val gtv = GtvDictionary.build(mapOf())
        val actual = GtvMLEncoder.encodeXMLGtv(gtv)
        val expected = arrayOf(
                expected("<dict></dict>"),
                expected("<dict/>"))

        assertThat(actual).isIn(*expected)
    }

    @Test
    fun encodeXMLGtv_dict_successfully() {
        val gtv = GtvDictionary.build(mapOf(
                "hello" to GtvString("world"),
                "123" to GtvInteger(123L)
        ))
        val actual = GtvMLEncoder.encodeXMLGtv(gtv)
        val expected = expected("""
            <dict>
                <entry key="123">
                    <int>123</int>
                </entry>
                <entry key="hello">
                    <string>world</string>
                </entry>
            </dict>
        """.trimIndent())

        assertThat(actual).isEqualTo(expected)
    }

    @Test
    fun encodeXMLGtv_compound_dict_successfully() {
        val gtv = GtvDictionary.build(mapOf(
                "k1" to GtvString("hello"),
                "k2" to GtvInteger(42),
                "k3" to GtvArray(arrayOf()),
                "k4" to GtvArray(arrayOf(
                        GtvArray(arrayOf(
                                GtvNull,
                                GtvDictionary.build(mapOf(
                                        "1" to GtvString("1"),
                                        "2" to GtvInteger(2)
                                ))
                        )),
                        GtvDictionary.build(mapOf(
                                "array" to GtvArray(arrayOf(
                                        GtvInteger(1),
                                        GtvString("2")
                                )),
                                "str" to GtvString("foo"),
                                "int" to GtvInteger(42)
                        ))
                )),
                "k5" to GtvDictionary.build(mapOf()),
                "k6" to GtvDictionary.build(mapOf(
                        "0" to GtvNull,
                        "1" to GtvString("1"),
                        "2" to GtvInteger(42)
                ))
        ))

        val actual = GtvMLEncoder.encodeXMLGtv(gtv)

        val expected = expected("""
            <dict>
                <entry key="k1">
                    <string>hello</string>
                </entry>
                <entry key="k2">
                    <int>42</int>
                </entry>
                <entry key="k3">
                    <array/>
                </entry>
                <entry key="k4">
                    <array>
                        <array>
                            <null xsi:nil="true" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"/>
                            <dict>
                                <entry key="1">
                                    <string>1</string>
                                </entry>
                                <entry key="2">
                                    <int>2</int>
                                </entry>
                            </dict>
                        </array>
                        <dict>
                            <entry key="array">
                                <array>
                                    <int>1</int>
                                    <string>2</string>
                                </array>
                            </entry>
                            <entry key="int">
                                <int>42</int>
                            </entry>
                            <entry key="str">
                                <string>foo</string>
                            </entry>
                        </dict>
                    </array>
                </entry>
                <entry key="k5">
                    <dict/>
                </entry>
                <entry key="k6">
                    <dict>
                        <entry key="0">
                            <null xsi:nil="true" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"/>
                        </entry>
                        <entry key="1">
                            <string>1</string>
                        </entry>
                        <entry key="2">
                            <int>42</int>
                        </entry>
                    </dict>
                </entry>
            </dict>""".trimIndent())

        assertThat(actual).isEqualTo(expected)
    }
}