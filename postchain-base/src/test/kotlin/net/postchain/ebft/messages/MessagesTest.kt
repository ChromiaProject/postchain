// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.ebft.messages

import net.postchain.ebft.message.BlockSignature
import net.postchain.ebft.message.EbftMessage
import net.postchain.ebft.message.GetBlockAtHeight
import net.postchain.ebft.message.Signature
import net.postchain.ebft.message.Status
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class MessagesTest {
    @Test
    fun testGetBlockAtHeight() {
        val mess = GetBlockAtHeight(29)
        val encoded = mess.encoded

        val result = EbftMessage.decodeAs<GetBlockAtHeight>(encoded)
        assertEquals(mess.height, result.height)
    }

    @Test
    fun testBlockSignature() {
        val blockRID = ByteArray(32){it.toByte()}
        val subjectID = ByteArray(33) {it.toByte()}
        val data = ByteArray(40){(it+1).toByte()}
        val sig = Signature(subjectID, data)
        val mess = BlockSignature(blockRID, sig)
        val encoded = mess.encoded

        val result = EbftMessage.decodeAs<BlockSignature>(encoded)
        assertArrayEquals(mess.blockRID, result.blockRID)
        assertArrayEquals(mess.sig.subjectID, result.sig.subjectID)
        assertArrayEquals(mess.sig.data, result.sig.data)
    }

    @Test
    fun testStatus() {
        val blockRID = ByteArray(32){it.toByte()}
        val height = 123321L
        val revolting = true
        val round = 1L
        val serial = 123456L
        val state = 123

        val status = Status(blockRID, height, revolting, round, serial, state)
        val encoded = status.encoded
        val expected = EbftMessage.decodeAs<Status>(encoded)

        assertArrayEquals(status.blockRID, expected.blockRID)
        assertEquals(status.height, expected.height)
        assertEquals(status.revolting, expected.revolting)
        assertEquals(status.round, expected.round)
        assertEquals(status.serial, expected.serial)
        assertEquals(status.state, expected.state)
    }
}