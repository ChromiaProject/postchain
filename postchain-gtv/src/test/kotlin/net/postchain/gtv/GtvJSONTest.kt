// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.gtv

import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import net.postchain.common.exception.ProgrammerMistake
import net.postchain.common.hexStringToByteArray
import net.postchain.gtv.GtvFactory.gtv
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class GtvJSONTest {

    @Test
    fun testJsonArray_2Gtv() {
        val jsonArr = "[\"foo\", \"1234\"]"
        val gson = make_gtv_gson()
        val gtvArr = gson.fromJson(jsonArr, Gtv::class.java)!!
       assertEquals("foo", gtvArr[0].asString())
       assertEquals("1234", gtvArr[1].asString())
    }


    @Test
    fun testGtvArray_2Json_2Gtv() {
        val gson = make_gtv_gson()
        val gtvArrOrg = gtv(gtv("foo"), gtv("1234"))
        val jsonValue = gson.toJson(gtvArrOrg, Gtv::class.java)
        //println(jsonValue)
        val gtvArrAfterRoundtrip = gson.fromJson(jsonValue, Gtv::class.java)!!
       assertEquals("foo",  gtvArrAfterRoundtrip[0].asString())
       assertEquals("1234", gtvArrAfterRoundtrip[1].asString())
    }

    @Test
    fun testJsonDict_2Gtv() {
        val gson = make_gtv_gson()
        val jsonValue = JsonObject()
        jsonValue.add("foo", JsonPrimitive("bar"))
        jsonValue.add("bar", JsonPrimitive("1234"))
        val gtvDict = gson.fromJson(jsonValue, Gtv::class.java)!!
       assertEquals("bar", gtvDict["foo"]!!.asString())
       assertEquals("1234", gtvDict["bar"]!!.asString())
       assertTrue(gtvDict["bar"]!!.asByteArray(true).size == 2)
    }

    @Test
    fun testGtvDict_2Json_2Gtv() {
        val gson = make_gtv_gson()
        val gtvDictOrg = gtv("foo" to gtv("bar"), "bar" to gtv("1234".hexStringToByteArray()))
        val jsonValue = gson.toJson(gtvDictOrg, Gtv::class.java)
        //println(jsonValue)
        val gtvDictAfterRoundtrip = gson.fromJson(jsonValue, Gtv::class.java)!!
        assertEquals("bar", gtvDictAfterRoundtrip["foo"]!!.asString())
        assertEquals("1234", gtvDictAfterRoundtrip["bar"]!!.asString())
        assertTrue(gtvDictAfterRoundtrip["bar"]!!.asByteArray(true).size == 2)
    }

    @Test
    fun can_parse_numbers() {
        val gson = make_gtv_gson()
        val gtv = gson.fromJson(Long.MAX_VALUE.toString(), Gtv::class.java)
        assertEquals(Long.MAX_VALUE, gtv.asInteger())
    }

    @Test
    fun decimal_value_should_throw_exception() {
        val gson = make_gtv_gson()
        val number = BigDecimal("1.2")
        assertThrows(
                ProgrammerMistake::class.java,
                { gson.fromJson(number.toString(), Gtv::class.java) },
                errorMsg(number)
        )
    }

    @Test
    fun too_big_integer_should_throw_exception() {
        val gson = make_gtv_gson()
        val number = BigDecimal.valueOf(Long.MAX_VALUE).add(BigDecimal.ONE)
        assertThrows(
                ProgrammerMistake::class.java,
                { gson.fromJson(number.toString(), Gtv::class.java) },
                errorMsg(number)
        )
    }
}