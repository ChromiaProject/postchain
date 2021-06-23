// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.gtv

import net.postchain.base.SECP256K1CryptoSystem
import net.postchain.common.hexStringToByteArray
import net.postchain.common.toHex
import net.postchain.gtv.merkle.GtvMerkleHashCalculator
import org.junit.Ignore
import org.junit.Test
import java.lang.IllegalArgumentException
import java.math.BigInteger
import kotlin.system.measureTimeMillis
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GtvEncoderTest {

    @Test
    fun testGtvNull() {
        val expected = GtvNull
        val b = GtvEncoder.encodeGtv(expected)
        val result = GtvDecoder.decodeGtv(b)
        assertEquals(expected, result)
    }

    @Test
    fun testGtvInteger() {
        val expected = GtvInteger(BigInteger.valueOf(Long.MAX_VALUE).pow(3))
        val b = GtvEncoder.encodeGtv(expected)
        val result = GtvDecoder.decodeGtv(b)
        assertEquals(expected, result)
        assertEquals(expected.asBigInteger().toString(10), result.asBigInteger().toString(10))
    }

    @Test
    fun testGtvString() {
        val expected = GtvString("postchain")
        val b = GtvEncoder.encodeGtv(expected)
        val result = GtvDecoder.decodeGtv(b)
        assertEquals(expected, result)
    }

    @Test
    fun testGtvByteArray() {
        val bytes =  ByteArray(3)
        bytes[0] = 0x10
        bytes[1] = 0x1A
        bytes[2] = 0x68
        val expected = GtvByteArray(bytes)
        val b = GtvEncoder.encodeGtv(expected)
        val result = GtvDecoder.decodeGtv(b)
        assertEquals(expected, result)
    }

    @Test
    fun testGtvArray() {
        val gtvArray = Array<Gtv>(3) { GtvString("postchain")}
        val expected = GtvArray(gtvArray)
        val b = GtvEncoder.encodeGtv(expected)
        val result = GtvDecoder.decodeGtv(b)
        assertEquals(expected, result)
    }

    @Test
    fun testGtvDictionary() {
        val map = mapOf(Pair("name", GtvString("postchain")))
        val expected = GtvDictionary.build(map)
        val b = GtvEncoder.encodeGtv(expected)
        val result = GtvDecoder.decodeGtv(b)
        assertEquals(expected, result)
    }

    @Test
    fun testSimpleEncodeGtvArray() {
        val gtvArray = Array<Gtv>(3) {GtvNull}
        gtvArray[0] = GtvByteArray("c89efdaa54c0f20c7adf612882df0950f5a951637e0307cdcb4c672f298b8bc6".hexStringToByteArray())
        gtvArray[1] = GtvInteger(12345678987654321L)
        gtvArray[2] = GtvByteArray("2a80e1ef1d7842f27f2e6be0972bb708b9a135c38860dbe73c27c3486c34f4de".hexStringToByteArray())
        /*
        TODO:
        val expected = "A5C7F96191E86BBC582179EA537F54556A6413917D38357BD5CAA083F78EE653"

        val actual = SECP256K1Keccak.digest(GtvEncoder.simpleEncodeGtv(GtvArray(gtvArray)))
        println("simple gtv serialization bytearray: ${GtvEncoder.simpleEncodeGtv(GtvArray(gtvArray)).toHex()}")
        assertEquals(expected, actual.toHex())

         */
    }

    @Test
    fun testSimpleEncodeGtvArrayError_Should_Be_Array() {
        assertFailsWith<IllegalArgumentException> {
            GtvEncoder.simpleEncodeGtv(GtvInteger(1L))
        }
    }

    @Test
    fun testSimpleEncodeGtvArrayError_Invalid_Data_Type() {
        val gtvArray = Array<Gtv>(3) {GtvNull}
        gtvArray[0] = GtvByteArray("c89efdaa54c0f20c7adf612882df0950f5a951637e0307cdcb4c672f298b8bc6".toByteArray())
        gtvArray[1] = GtvString("2")
        gtvArray[2] = GtvByteArray("2a80e1ef1d7842f27f2e6be0972bb708b9a135c38860dbe73c27c3486c34f4de".toByteArray())
        assertFailsWith<IllegalArgumentException> {
            GtvEncoder.simpleEncodeGtv(GtvArray(gtvArray))
        }
    }

    @Test
    fun testSimpleEncodeGtvArrayError_Invalid_Data_Length() {
        val gtvArray = Array<Gtv>(3) {GtvNull}
        gtvArray[0] = GtvByteArray("00000000c89efdaa54c0f20c7adf612882df0950f5a951637e0307cdcb4c672f298b8bc6".toByteArray())
        gtvArray[1] = GtvInteger(2L)
        gtvArray[2] = GtvByteArray("2a80e1ef1d7842f27f2e6be0972bb708b9a135c38860dbe73c27c3486c34f4de".toByteArray())
        assertFailsWith<IllegalArgumentException> {
            GtvEncoder.simpleEncodeGtv(GtvArray(gtvArray))
        }
    }

    @Test
    fun stressTestGtv() {
        val size = (1024*1024 * 4) /10  // that could make gtv size is around 2.7 MB
        // TODO: this test is 10 times smaller than it should be because we trigger OOM
        // currently it requires >2 GB to compute hash
        val gtvArray  = (1..size).map { GtvInteger( it.toLong() ) }.toTypedArray()
        var encoded = ByteArray(0)
        val gtv = GtvArray(gtvArray)
        val serializationTime = measureTimeMillis {
            encoded = GtvEncoder.encodeGtv(gtv)
        }
        println("Size of gtv ~: ${encoded.size / (1024*1024)} MB")
        println("Execution time serialization: ${serializationTime} milliseconds")

        val deserializationTime = measureTimeMillis {
            GtvDecoder.decodeGtv(encoded).asArray()
        }
        println("Execution time deserialization: ${deserializationTime} milliseconds")

        val cs = SECP256K1CryptoSystem()
        val hashingTime = measureTimeMillis {
            val hash = gtv.merkleHash(GtvMerkleHashCalculator(cs))
            println(hash.toHex())
        }
        println("Execution hashing time: ${hashingTime} milliseconds")
    }
}