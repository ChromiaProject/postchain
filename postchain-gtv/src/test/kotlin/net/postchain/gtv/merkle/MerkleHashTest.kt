package net.postchain.gtv.merkle

import net.postchain.common.hexStringToByteArray
import net.postchain.common.toHex
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.GtvNull
import net.postchain.gtv.merkleHash
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.math.BigInteger

class MerkleHashTest {
    val calculatorV1 = GtvMerkleHashCalculatorV1(cryptoSystem)
    val calculatorV2 = GtvMerkleHashCalculatorV2(cryptoSystem)

    @ParameterizedTest
    @MethodSource("testData")
    fun gtvMerkleHash(name: String, value: Gtv, expectedHashHexV1: String, expectedHashHexV2: String) {
        val merkleHashV1 = value.merkleHash(calculatorV1)
        val actualHexV1 = merkleHashV1.toHex()
        assertEquals(expectedHashHexV1, actualHexV1, "Test case: $name (V1)")

        val merkleHashV2 = value.merkleHash(calculatorV2)
        val actualHexV2 = merkleHashV2.toHex()
        assertEquals(expectedHashHexV2, actualHexV2, "Test case: $name (V2)")
    }

    companion object {
        @JvmStatic
        fun testData(): List<Array<Any>> = listOf(
                arrayOf("simple array",
                        gtv(listOf(
                                gtv("a")
                        )),
                        "5AD2414EDCD34B9A8BDC22921B8A1B8CEF6CAB04115DD0E7EB000B05353B315A",
                        "5AD2414EDCD34B9A8BDC22921B8A1B8CEF6CAB04115DD0E7EB000B05353B315A"
                ),
                arrayOf("nested array",
                        gtv(listOf(
                                gtv(listOf(
                                        gtv("a")
                                ))
                        )),
                        "5AD2414EDCD34B9A8BDC22921B8A1B8CEF6CAB04115DD0E7EB000B05353B315A",
                        "19605D1044CC20248E315F98F2D4C4AA7ADFE6861607A0D000641837C3B962F8"
                ),
                arrayOf("array with various data",
                        gtv(listOf(
                                gtv("foo"),
                                gtv(listOf(
                                        gtv("bar2"),
                                        gtv("bar2")
                                ))
                        )),
                        "6357D3200E0DFB1BCE5F3EB789714842747B39810248F83DBA6382C7E7020E20",
                        "6357D3200E0DFB1BCE5F3EB789714842747B39810248F83DBA6382C7E7020E20"
                ),
                arrayOf("dict with various data",
                        gtv(mapOf(
                                "foo" to gtv(-1),
                                "foo1" to gtv("OK"),
                                "bar" to gtv(BigInteger("170141183460469231731687303715884105727")),
                                "bar1" to gtv(BigInteger("1000000000000"))
                        )),
                        "6981E7EFD8CE0634BDADF3D7C76CC69AD5ABF9792AF951BD0FE5698571589F12",
                        "6981E7EFD8CE0634BDADF3D7C76CC69AD5ABF9792AF951BD0FE5698571589F12"
                ),
                arrayOf("simple dict",
                        gtv(mapOf(
                                "a" to gtv("b"),
                                "c" to gtv("d")
                        )),
                        "B3DF182AD2BF3106683EC82FFAE9293D00A8335706491EDFA4FCA3C7562EE8CC",
                        "B3DF182AD2BF3106683EC82FFAE9293D00A8335706491EDFA4FCA3C7562EE8CC"
                ),
                arrayOf("dict in array",
                        gtv(listOf(
                                gtv(mapOf(
                                        "a" to gtv("b"),
                                        "c" to gtv("d")
                                ))
                        )),
                        "891CDF10FF613A90899FF0FFE1A515D8ED74FE71E36249F0B6DD175EEC70805D",
                        "9D2F6CFA72538E24584363ADA5882C2BE3F83D75AFF598D0009330DB22D961FF"
                ),
                arrayOf("dict flattened to array",
                        gtv(listOf(
                                gtv("a"), gtv("b"), gtv("c"), gtv("d")
                        )),
                        "891CDF10FF613A90899FF0FFE1A515D8ED74FE71E36249F0B6DD175EEC70805D",
                        "891CDF10FF613A90899FF0FFE1A515D8ED74FE71E36249F0B6DD175EEC70805D"
                ),
                arrayOf("all types",
                        gtv(mapOf(
                                "null" to GtvNull,
                                "byte_array" to gtv("1234".hexStringToByteArray()),
                                "string" to gtv("foo"),
                                "integer" to gtv(17),
                                "dict" to gtv(mapOf("a" to gtv(1), "b" to gtv(2))),
                                "array" to gtv(listOf(gtv(1), gtv(2))),
                                "big_integer" to gtv(BigInteger.valueOf(4711)),
                        )),
                        "C8FBBF3B668D56D305A1FC85B721F22CE983AF1EAC9FDC245CF149D5A36BE6BC",
                        "C8FBBF3B668D56D305A1FC85B721F22CE983AF1EAC9FDC245CF149D5A36BE6BC"
                ),
        )
    }
}
