// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.gtv

import net.postchain.common.toHex
import net.postchain.crypto.Secp256K1CryptoSystem
import net.postchain.gtv.merkle.GtvMerkleHashCalculatorV2
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.math.BigInteger
import kotlin.system.measureTimeMillis

class GtvEncoderTest {

    @Test
    fun `GTV null`() {
        val expected = GtvNull
        val b = GtvEncoder.encodeGtv(expected)
        val result = GtvDecoder.decodeGtv(b)
        assertEquals(expected, result)
    }

    @Test
    fun `positive integer`() {
        val expected = GtvInteger(Long.MAX_VALUE)
        val b = GtvEncoder.encodeGtv(expected)
        val result = GtvDecoder.decodeGtv(b)
        assertEquals(expected, result)
        assertEquals(expected.asInteger().toString(10), result.asInteger().toString(10))
    }

    @Test
    fun `zero integer`() {
        val expected = GtvInteger(0)
        val b = GtvEncoder.encodeGtv(expected)
        val result = GtvDecoder.decodeGtv(b)
        assertEquals(expected, result)
        assertEquals(expected.asInteger().toString(10), result.asInteger().toString(10))
    }

    @Test
    fun `negative integer`() {
        val expected = GtvInteger(Long.MIN_VALUE)
        val b = GtvEncoder.encodeGtv(expected)
        val result = GtvDecoder.decodeGtv(b)
        assertEquals(expected, result)
        assertEquals(expected.asInteger().toString(10), result.asInteger().toString(10))
    }

    @Test
    fun `small negative integer`() {
        val expected = GtvInteger(-65535)
        val b = GtvEncoder.encodeGtv(expected)
        println(b.toHex())
        val result = GtvDecoder.decodeGtv(b)
        assertEquals(expected, result)
        assertEquals(expected.asInteger().toString(10), result.asInteger().toString(10))
    }

    @Test
    fun `positive big integer`() {
        val expected = GtvBigInteger(BigInteger.valueOf(Long.MAX_VALUE).pow(3))
        val b = GtvEncoder.encodeGtv(expected)
        val result = GtvDecoder.decodeGtv(b)
        assertEquals(expected, result)
        assertEquals(expected.asBigInteger().toString(10), result.asBigInteger().toString(10))
    }

    @Test
    fun `zero big integer`() {
        val expected = GtvBigInteger(BigInteger.valueOf(0))
        val b = GtvEncoder.encodeGtv(expected)
        val result = GtvDecoder.decodeGtv(b)
        assertEquals(expected, result)
        assertEquals(expected.asBigInteger().toString(10), result.asBigInteger().toString(10))
    }

    @Test
    fun `negative big integer`() {
        val expected = GtvBigInteger(BigInteger("-92233720368547758078"))
        val b = GtvEncoder.encodeGtv(expected)
        val result = GtvDecoder.decodeGtv(b)
        assertEquals(expected, result)
        assertEquals(expected.asBigInteger().toString(10), result.asBigInteger().toString(10))
    }

    @Test
    fun `ASCII string`() {
        val expected = GtvString("postchain")
        val b = GtvEncoder.encodeGtv(expected)
        val result = GtvDecoder.decodeGtv(b)
        assertEquals(expected, result)
    }

    @Test
    fun `UTF-8 string`() {
        val expected = GtvString("Räksmörgås!")
        val b = GtvEncoder.encodeGtv(expected)
        val result = GtvDecoder.decodeGtv(b)
        assertEquals(expected, result)
    }

    @Test
    fun `empty string`() {
        val expected = GtvString("")
        val b = GtvEncoder.encodeGtv(expected)
        val result = GtvDecoder.decodeGtv(b)
        assertEquals(expected, result)
    }

    @Test
    fun `byte array`() {
        val bytes = ByteArray(3)
        bytes[0] = 0x10
        bytes[1] = 0x1A
        bytes[2] = 0x68
        val expected = GtvByteArray(bytes)
        val b = GtvEncoder.encodeGtv(expected)
        val result = GtvDecoder.decodeGtv(b)
        assertEquals(expected, result)
    }

    @Test
    fun `empty byte array`() {
        val bytes = ByteArray(0)
        val expected = GtvByteArray(bytes)
        val b = GtvEncoder.encodeGtv(expected)
        val result = GtvDecoder.decodeGtv(b)
        assertEquals(expected, result)
    }

    @Test
    fun array() {
        val gtvArray = Array<Gtv>(3) { GtvString("postchain") }
        val expected = GtvArray(gtvArray)
        val b = GtvEncoder.encodeGtv(expected)
        val result = GtvDecoder.decodeGtv(b)
        assertEquals(expected, result)
    }

    @Test
    fun `empty array`() {
        val expected = GtvArray(arrayOf())
        val b = GtvEncoder.encodeGtv(expected)
        val result = GtvDecoder.decodeGtv(b)
        assertEquals(expected, result)
    }

    @Test
    fun dictionary() {
        val map = mapOf("name" to GtvString("postchain"), "age" to GtvInteger(42))
        val expected = GtvDictionary.build(map)
        val b = GtvEncoder.encodeGtv(expected)
        val result = GtvDecoder.decodeGtv(b)
        assertEquals(expected, result)
    }

    @Test
    fun `empty dictionary`() {
        val expected = GtvDictionary.build(mapOf())
        val b = GtvEncoder.encodeGtv(expected)
        val result = GtvDecoder.decodeGtv(b)
        assertEquals(expected, result)
    }

    @Test
    fun `stress test`() {
        val size = (1024 * 1024 * 4) / 10  // that could make gtv size around 2.7 MiB
        // TODO: this test is 10 times smaller than it should be because we trigger OOM
        // currently it requires >2 GiB to compute hash
        val gtvArray = (1..size).map { GtvInteger(it.toLong()) }.toTypedArray()
        var encoded: ByteArray
        val gtv = GtvArray(gtvArray)
        val serializationTime = measureTimeMillis {
            encoded = GtvEncoder.encodeGtv(gtv)
        }
        println("Size of gtv ~: ${encoded.size / (1024 * 1024)} MiB")
        println("Execution time serialization: $serializationTime milliseconds")

        val deserializationTime = measureTimeMillis {
            GtvDecoder.decodeGtv(encoded).asArray()
        }
        println("Execution time deserialization: $deserializationTime milliseconds")

        val cs = Secp256K1CryptoSystem()
        val hashingTime = measureTimeMillis {
            val hash = gtv.merkleHash(GtvMerkleHashCalculatorV2(cs))
            println(hash.toHex())
        }
        println("Execution hashing time: $hashingTime milliseconds")
    }
}
