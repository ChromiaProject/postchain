// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.gtx.gtxml

import net.postchain.common.BlockchainRid
import net.postchain.gtv.*
import net.postchain.gtx.Gtx
import net.postchain.gtx.GtxBody
import net.postchain.gtx.GtxOp
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class GTXMLTransactionEncodeTest {

//    val blockchainRID = BlockchainRid.buildRepeat(23)
    val blockchainRID = BlockchainRid.buildFromHex("1234567812345678123456781234567812345678123456781234567812345678")
    @Test
    fun encodeXMLGTXTransaction_successfully() {
        val gtxTxBodyData = GtxBody(
                blockchainRID,
                arrayOf(
                        GtxOp("ft_transfer",
                                        GtvString("hello"),
                                        GtvString("hello2"),
                                        GtvString("hello3"),
                                        GtvInteger(42),
                                        GtvInteger(43)),
                        GtxOp("ft_transfer",
                                        GtvString("HELLO"),
                                        GtvString("HELLO2"),
                                        GtvInteger(142),
                                        GtvInteger(143))
                ),
                arrayOf(
                        byteArrayOf(0x12, 0x38, 0x71, 0x23),
                        byteArrayOf(0x12, 0x38, 0x71, 0x24)
                )
        )

        val gtxTx = Gtx(gtxTxBodyData,
                arrayOf(
                        byteArrayOf(0x34, 0x56, 0x78, 0x54),
                        byteArrayOf(0x34, 0x56, 0x78, 0x55)
                )
                )

        val expected = javaClass.getResource("/net/postchain/gtx/gtxml/encode/tx_full.xml").readText()
                .replace("\r\n", "\n").trim()
        val actual = GTXMLTransactionEncoder.encodeXMLGTXTransaction(gtxTx)

        assertEquals(expected, actual.trim())
    }

    @Test
    fun encodeXMLGTXTransaction_empty_successfully() {
        val gtxTxBodyData = GtxBody(
                blockchainRID,
                arrayOf(),
                arrayOf()
        )

        val gtxTx = Gtx(gtxTxBodyData, arrayOf())

        val expected = javaClass.getResource("/net/postchain/gtx/gtxml/encode/tx_empty.xml").readText()
                .replace("\r\n", "\n").trim()
        val actual = GTXMLTransactionEncoder.encodeXMLGTXTransaction(gtxTx)

        assertEquals(expected , actual.trim())
    }

    @Test
    fun encodeXMLGTXTransaction_with_empty_signers_and_signatures_successfully() {
        val gtxTxBodyData = GtxBody(
                blockchainRID,
                arrayOf(
                        GtxOp("ft_transfer",
                                        GtvString("hello"),
                                        GtvString("hello2"),
                                        GtvString("hello3"),
                                        GtvInteger(42),
                                        GtvInteger(43)),
                        GtxOp("ft_transfer",
                                        GtvString("HELLO"),
                                        GtvString("HELLO2"),
                                        GtvInteger(142),
                                        GtvInteger(143))
                ),
                arrayOf()
        )

        val gtxTx = Gtx(gtxTxBodyData, arrayOf())

        val expected = javaClass.getResource("/net/postchain/gtx/gtxml/encode/tx_empty_signers_and_signatures.xml").readText()
                .replace("\r\n", "\n").trim()
        val actual = GTXMLTransactionEncoder.encodeXMLGTXTransaction(gtxTx)

        assertEquals(expected, actual.trim())
    }

    @Test
    fun encodeXMLGTXTransaction_with_empty_operations_successfully() {
        val gtxTxBodyData = GtxBody(
                blockchainRID,
                arrayOf(),
                arrayOf(
                        byteArrayOf(0x12, 0x38, 0x71, 0x23),
                        byteArrayOf(0x12, 0x38, 0x71, 0x24)
                )
        )

        val gtxTx = Gtx(gtxTxBodyData,
                arrayOf(
                        byteArrayOf(0x34, 0x56, 0x78, 0x54),
                        byteArrayOf(0x34, 0x56, 0x78, 0x55)
                )
        )

        val expected = javaClass.getResource("/net/postchain/gtx/gtxml/encode/tx_empty_operations.xml").readText()
                .replace("\r\n", "\n").trim()
        val actual = GTXMLTransactionEncoder.encodeXMLGTXTransaction(gtxTx)

        assertEquals(expected, actual.trim())
    }

    @Test
    fun encodeXMLGTXTransaction_with_empty_operation_parameters_successfully() {
        val gtxTxBodyData = GtxBody(
                blockchainRID,
                arrayOf(
                        GtxOp("ft_transfer"),
                        GtxOp("ft_transfer")
                ),
                arrayOf(
                        byteArrayOf(0x12, 0x38, 0x71, 0x23),
                        byteArrayOf(0x12, 0x38, 0x71, 0x24)
                )
        )

        val gtxTx = Gtx(gtxTxBodyData,
                arrayOf(
                        byteArrayOf(0x34, 0x56, 0x78, 0x54),
                        byteArrayOf(0x34, 0x56, 0x78, 0x55)
                )
        )

        val expected = javaClass.getResource("/net/postchain/gtx/gtxml/encode/tx_empty_operation_parameters.xml").readText()
                .replace("\r\n", "\n").trim()
        val actual = GTXMLTransactionEncoder.encodeXMLGTXTransaction(gtxTx)

        assertEquals(expected, actual.trim())
    }

    @Test
    fun encodeXMLGTXTransaction_compound_parameter_of_operation_successfully() {
        val gtxBodyData = GtxBody(
                blockchainRID,
                arrayOf(
                        GtxOp("ft_transfer",
                                        GtvString("foo"),
                                        GtvArray(arrayOf(
                                                GtvString("foo"),
                                                GtvArray(arrayOf(
                                                        GtvString("foo"),
                                                        GtvString("bar")
                                                )),
                                                GtvDictionary.build(mapOf(
                                                        "key1" to GtvInteger(42),
                                                        "key2" to GtvString("42"),
                                                        "key3" to GtvArray(arrayOf(
                                                                GtvString("hello"),
                                                                GtvInteger(42)
                                                        ))
                                                ))
                                        )),
                                        GtvDictionary.build(mapOf(
                                                "key1" to GtvNull,
                                                "key2" to GtvString("42")
                                        ))
                                )
                ),
                arrayOf()
        )

        val gtxTxData = Gtx(
                gtxBodyData,
                arrayOf()
                )

        val expected = javaClass.getResource("/net/postchain/gtx/gtxml/encode/tx_compound_parameter_of_operation.xml").readText()
                .replace("\r\n", "\n").trim()
        val actual = GTXMLTransactionEncoder.encodeXMLGTXTransaction(gtxTxData)

        assertEquals(expected, actual.trim())
    }

}