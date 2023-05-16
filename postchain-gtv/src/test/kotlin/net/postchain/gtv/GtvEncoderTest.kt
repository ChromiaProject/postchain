// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.gtv

import net.postchain.common.toHex
import net.postchain.crypto.Secp256K1CryptoSystem
import net.postchain.gtv.merkle.GtvMerkleHashCalculator
import org.junit.jupiter.api.Test
import java.math.BigInteger
import kotlin.system.measureTimeMillis
import kotlin.test.assertEquals

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
        val expected = GtvInteger(Long.MAX_VALUE)
        val b = GtvEncoder.encodeGtv(expected)
        val result = GtvDecoder.decodeGtv(b)
        assertEquals(expected, result)
        assertEquals(expected.asInteger().toString(10), result.asInteger().toString(10))
    }

    @Test
    fun testGtvBigInteger() {
        val expected = GtvBigInteger(BigInteger.valueOf(Long.MAX_VALUE).pow(3))
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
    fun stressTestGtv() {
        val size = (1024 * 1024 * 4) / 10  // that could make gtv size is around 2.7 MiB
        // TODO: this test is 10 times smaller than it should be because we trigger OOM
        // currently it requires >2 GiB to compute hash
        val gtvArray  = (1..size).map { GtvInteger( it.toLong() ) }.toTypedArray()
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
            val hash = gtv.merkleHash(GtvMerkleHashCalculator(cs))
            println(hash.toHex())
        }
        println("Execution hashing time: $hashingTime milliseconds")
    }
}