// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.ebft.messages

import net.postchain.common.hexStringToByteArray
import net.postchain.common.toHex
import net.postchain.ebft.message.BlockData
import net.postchain.ebft.message.BlockRange
import net.postchain.ebft.message.BlockSignature
import net.postchain.ebft.message.CompleteBlock
import net.postchain.ebft.message.EbftMessage
import net.postchain.ebft.message.GetBlockAtHeight
import net.postchain.ebft.message.GetBlockRange
import net.postchain.ebft.message.Signature
import net.postchain.ebft.message.Status
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class MessagesTest {

    // Some dummy data
    val headerHex = "12121212"
    val tx1Hex = "23232323"
    val tx2Hex = "45454545"
    val witnessHex = "787878787878"

    @Test
    fun testGetBlockAtHeight() {
        val mess = GetBlockAtHeight(29)
        val encoded = mess.encoded

        val result = EbftMessage.decodeAs<GetBlockAtHeight>(encoded)
        assertEquals(mess.height, result.height)
    }

    @Test
    fun testGetBlockRange() {
        val mess = GetBlockRange(29)
        val encoded = mess.encoded

        val result = EbftMessage.decodeAs<GetBlockRange>(encoded)
        assertEquals(mess.startAtHeight, result.startAtHeight)
    }


    @Test
    fun testBlockSignature() {
        val blockRID = ByteArray(32) { it.toByte() }
        val subjectID = ByteArray(33) { it.toByte() }
        val data = ByteArray(40) { (it + 1).toByte() }
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
        val blockRID = ByteArray(32) { it.toByte() }
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

    @Test
    fun testCompleteBlockGtvEncodeDecode() {
        val mess: CompleteBlock = buildCompleteBlock(11L)
        val encoded = mess.encoded

        val result = EbftMessage.decodeAs<CompleteBlock>(encoded)

        assertEquals(11L, result.height)
        assertEquals(headerHex, result.data.header.toHex())
        assertEquals(tx1Hex, result.data.transactions[0].toHex())
        assertEquals(tx2Hex, result.data.transactions[1].toHex())
        assertEquals(witnessHex, result.witness.toHex())

    }

    @Test
    fun testBlockRangeGtvEncodeDecode() {

        val compBlock1 = buildCompleteBlock(11L)
        //println("block size: ${compBlock1.encoded.size}")
        val compBlock2 = buildCompleteBlock(12L)
        val mess = BlockRange(11L, false, listOf(compBlock1, compBlock2))

        val encoded = mess.encoded

        val result = EbftMessage.decodeAs<BlockRange>(encoded)

        assertEquals(11L, result.startAtHeight)

        assertFalse(result.isFull)

        val compRes1 = result.blocks[0]
        assertEquals(11L, compRes1.height)
        assertEquals(headerHex, compRes1.data.header.toHex())
        assertEquals(tx1Hex, compRes1.data.transactions[0].toHex())
        assertEquals(tx2Hex, compRes1.data.transactions[1].toHex())
        assertEquals(witnessHex, compRes1.witness.toHex())

        val compRes2 = result.blocks[1]
        assertEquals(12L, compRes2.height)
        assertEquals(headerHex, compRes2.data.header.toHex())
        // No point testing the rest
    }

    private fun buildCompleteBlock(height: Long): CompleteBlock {
        val header = headerHex.hexStringToByteArray()

        val transactions = listOf(
                tx1Hex.hexStringToByteArray(),
                tx2Hex.hexStringToByteArray()
        )
        val data = BlockData(header, transactions)
        val witness = witnessHex.hexStringToByteArray()

        return CompleteBlock(data, height, witness)
    }
}