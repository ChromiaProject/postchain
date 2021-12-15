// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.core

import assertk.assert
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEqualTo
import assertk.isContentEqualTo
import net.postchain.common.hexStringToByteArray
import org.junit.jupiter.api.Test

class BlockchainRidTest {

    @Test
    fun testConstructorZeroRid() {
        val actual = BlockchainRid.ZERO_RID

        assert(actual.data).isContentEqualTo(ByteArray(32) { 0 })
    }

    @Test
    fun testBuildFromHex() {
        val ridString = "78967baa4768cbcef11c508326ffb13a956689fcb6dc3ba17f4b895cbb1577a3"
        val actual = BlockchainRid.buildFromHex(ridString)

        assert(actual.data).isContentEqualTo(ridString.hexStringToByteArray())
    }

    @Test
    fun testBuildRepeat() {
        val expected = ByteArray(32) { 7 }
        val actual = BlockchainRid.buildRepeat(7)

        assert(actual.data).isContentEqualTo(expected)
    }

    @Test
    fun testToHex() {
        val expected = "78967baa4768cbcef11c508326ffb13a956689fcb6dc3ba17f4b895cbb1577a3"
        val ridByteArray = expected.hexStringToByteArray()
        val actual = BlockchainRid(ridByteArray)

        assert(actual.toHex().toUpperCase())
                .isEqualTo(expected.toUpperCase())
    }

    @Test
    fun testToShortHex() {
        val expected = "78967baa4768cbcef11c508326ffb13a956689fcb6dc3ba17f4b895cbb1577a3"
        val ridByteArray = expected.hexStringToByteArray()
        val actual = BlockchainRid(ridByteArray)

        assert(actual.toShortHex().toUpperCase())
                .isEqualTo("78:a3".toUpperCase())
    }

    @Test
    fun testToString() {
        val ridString = "78967baa4768cbcef11c508326ffb13a956689fcb6dc3ba17f4b895cbb1577a3"
        val ridByteArray = ridString.hexStringToByteArray()
        val blockchainRid = BlockchainRid(ridByteArray)

        assert(blockchainRid.toString().toUpperCase())
                .isEqualTo(ridString.toUpperCase())
    }

    @Test
    fun testEquals() {
        val lower = "78967baa4768cbcef11c508326ffb13a956689fcb6dc3ba17f4b895cbb1577a3"
        val upper = "78967BAA4768CBCEF11C508326FFB13A956689FCB6DC3BA17F4B895CBB1577A3"

        val ridA = BlockchainRid(lower.hexStringToByteArray())
        val ridB = BlockchainRid(upper.hexStringToByteArray())

        assert(ridA).isEqualTo(ridB)
    }

    @Test
    fun testNotEquals() {
        val rid = "78967baa4768cbcef11c508326ffb13a956689fcb6dc3ba17f4b895cbb1577a3"
        val other = "0000000000000000000000000000000000000000000000000000000000000000"

        val ridA = BlockchainRid(rid.hexStringToByteArray())
        val ridB = BlockchainRid(other.hexStringToByteArray())

        assert(ridA).isNotEqualTo(ridB)
    }
}