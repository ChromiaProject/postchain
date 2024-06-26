// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.gtv.gtvml

import assertk.assertThat
import assertk.assertions.isEqualTo
import net.postchain.gtv.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class GtvMLParserDictsTest {

    @Test
    fun parseGtv_dict_empty_successfully() {
        val xml = "<dict></dict>"
        val actual = GtvMLParser.parseGtvML(xml)
        val expected = GtvDictionary.build(mapOf())

        assertThat(actual).isEqualTo(expected)
    }

    @Test
    fun parseGtv_dict_of_scalars_successfully() {
        val xml = """
            <dict>
                <entry key="hello"><string>world</string></entry>
                <entry key="123"><int>123</int></entry>
            </dict>
        """.trimIndent()

        val actual = GtvMLParser.parseGtvML(xml)
        val expected = GtvDictionary.build(mapOf(
                "hello" to GtvString("world"),
                "123" to GtvInteger(123L)
        ))

        assertThat(actual).isEqualTo(expected)
    }

    @Test
    fun parseGtv_dict_with_params_successfully() {
        val xml = """
            <dict>
                <entry key="hello"><string>world</string></entry>
                <entry key="num123"><param type='int' key='p_num123'/></entry>
                <entry key="num124"><param type='int' key='p_num124'/></entry>
                <entry key="string1"><param type='string' key='p_str1'/></entry>
                <entry key="bytearray1"><param type='bytea' key='p_butea1'/></entry>
            </dict>
        """.trimIndent()

        val actual = GtvMLParser.parseGtvML(
                xml,
                mapOf(
                        "p_num123" to GtvInteger(123),
                        "p_num124" to GtvInteger(124),
                        "p_str1" to GtvString("my str 1"),
                        "p_butea1" to GtvByteArray(byteArrayOf(0x01, 0x02, 0x03, 0x0A, 0x0B, 0x0C))
                ))

        val expected = GtvDictionary.build(mapOf(
                "hello" to GtvString("world"),
                "num123" to GtvInteger(123L),
                "num124" to GtvInteger(124L),
                "string1" to GtvString("my str 1"),
                "bytearray1" to GtvByteArray(byteArrayOf(0x01, 0x02, 0x03, 0x0A, 0x0B, 0x0C))
        ))

        assertThat(actual).isEqualTo(expected)
    }

    @Test
    fun parseGtv_dict_with_not_found_param_throws_exception() {
        val xml = """
            <dict>
                <entry key="hello"><string>world</string></entry>
                <entry key="num123"><param type='int' key='UNKNOWN_KEY'/></entry>
            </dict>
        """.trimIndent()

        assertThrows<IllegalArgumentException> {
            GtvMLParser.parseGtvML(xml, mapOf())
        }
    }

    @Test
    fun parseGtv_dict_with_CASE_SENSITIVE_not_found_param_throws_exception() {
        val xml = """
            <dict>
                <entry key="hello"><string>world</string></entry>
                <entry key="num123"><param type='int' key='CASE_SENSITIVE_KEY'/></entry>
            </dict>
        """.trimIndent()

        assertThrows<IllegalArgumentException> {
            GtvMLParser.parseGtvML(
                xml,
                mapOf("case_sensitive_key" to GtvInteger(42))
            )
        }
    }

    @Test
    fun parseGtv_dict_of_dicts_successfully() {
        val xml = """
            <dict>
                <entry key="hello">
                    <string>world</string>
                </entry>
                <entry key="my_dict">
                    <dict>
                        <entry key="str"><string>kitty</string></entry>
                        <entry key="number"><int>123</int></entry>
                    </dict>
                </entry>
            </dict>
        """.trimIndent()

        val actual = GtvMLParser.parseGtvML(xml)

        val expected = GtvDictionary.build(mapOf(
                "hello" to GtvString("world"),
                "my_dict" to GtvDictionary.build(mapOf(
                        "str" to GtvString("kitty"),
                        "number" to GtvInteger(123)
                ))
        ))

        assertThat(actual).isEqualTo(expected)
    }

    @Test
    fun parseGtv_dict_of_all_types_successfully() {
        val xml = """
            <dict>
                <entry key="entry_1">
                    <string>foo</string>
                </entry>

                <entry key="entry_2">
                    <int>42</int>
                </entry>

                <entry key="entry_3">
                    <array>
                        <string>foo</string>
                        <string>bar</string>
                        <array>
                            <param type='int' key='param_int_42'/>
                            <int>43</int>
                            <array>
                                <int>44</int>
                            </array>
                            <dict>
                                <entry key="hello"><string>world</string></entry>
                                <entry key="123"><int>123</int></entry>
                            </dict>
                        </array>
                        <dict>
                            <entry key="hello">
                                <array>
                                    <string>world</string>
                                    <string>world</string>
                                </array>
                            </entry>
                            <entry key="123"><int>123</int></entry>
                        </dict>
                    </array>
                </entry>

                <entry key="entry_4">
                    <dict>
                        <entry key="null_entry"><null/></entry>
                        <entry key="hello"><string>world</string></entry>
                        <entry key="dict123">
                            <dict>
                                <entry key="hello">
                                    <param type='string' key='param_string_foo'/>
                                </entry>
                                <entry key="123"><int>123</int></entry>
                            </dict>
                        </entry>
                        <entry key="array123">
                            <array>
                                <int>42</int>
                                <int>43</int>
                            </array>
                        </entry>
                    </dict>
                </entry>

                <entry key="entry_5">
                    <dict>
                        <entry key="hello"><string>world</string></entry>
                        <entry key="123"><int>123</int></entry>
                        <entry key="null_entry">
                            <null/>
                        </entry>
                        <entry key="null_entry2">
                            <null xsi:nil="true" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"/>
                        </entry>
                    </dict>
                </entry>

            </dict>
        """.trimIndent()

        val actual = GtvMLParser.parseGtvML(
                xml,
                mapOf(
                        "param_int_42" to GtvInteger(42),
                        "param_string_foo" to GtvString("foo"))
        )

        val expected = GtvDictionary.build(mapOf(
                "entry_1" to GtvString("foo"),

                "entry_2" to GtvInteger(42),

                "entry_3" to GtvArray(arrayOf(
                        GtvString("foo"),
                        GtvString("bar"),
                        GtvArray(arrayOf(
                                GtvInteger(42),
                                GtvInteger(43),
                                GtvArray(arrayOf(
                                        GtvInteger(44)
                                )),
                                GtvDictionary.build(mapOf(
                                        "hello" to GtvString("world"),
                                        "123" to GtvInteger(123)
                                ))
                        )),
                        GtvDictionary.build(mapOf(
                                "hello" to GtvArray(arrayOf(
                                        GtvString("world"),
                                        GtvString("world")
                                )),
                                "123" to GtvInteger(123)
                        ))
                )),

                "entry_4" to GtvDictionary.build(mapOf(
                        "null_entry" to GtvNull,
                        "hello" to GtvString("world"),
                        "dict123" to GtvDictionary.build(mapOf(
                                "hello" to GtvString("foo"),
                                "123" to GtvInteger(123)
                        )),
                        "array123" to GtvArray(arrayOf(
                                GtvInteger(42),
                                GtvInteger(43)
                        ))
                )),

                "entry_5" to GtvDictionary.build(mapOf(
                        "hello" to GtvString("world"),
                        "123" to GtvInteger(123),
                        "null_entry" to GtvNull,
                        "null_entry2" to GtvNull
                ))
        ))

        assertThat(actual).isEqualTo(expected)
    }
}