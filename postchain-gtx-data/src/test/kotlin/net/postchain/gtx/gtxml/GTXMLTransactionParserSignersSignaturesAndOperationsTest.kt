// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.gtx.gtxml

import net.postchain.common.BlockchainRid
import net.postchain.common.exception.UserMistake
import net.postchain.crypto.devtools.MockCryptoSystem
import net.postchain.gtv.GtvArray
import net.postchain.gtv.GtvByteArray
import net.postchain.gtv.GtvDictionary
import net.postchain.gtv.GtvInteger
import net.postchain.gtv.GtvString
import net.postchain.gtx.Gtx
import net.postchain.gtx.GtxBody
import net.postchain.gtx.GtxOp
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class GTXMLTransactionParserSignersSignaturesAndOperationsTest {
    val blockchainRID = BlockchainRid.buildFromHex("1234567812345678123456781234567812345678123456781234567812345678")

    @Test
    fun parseGTXMLTransaction_successfully() {
        val xml = javaClass.getResource("/net/postchain/gtx/gtxml/parse/tx_full.xml").readText()

        val expectedBody = GtxBody(
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

        val expectedTx = Gtx(expectedBody,
                arrayOf(
                        byteArrayOf(0x34, 0x56, 0x78, 0x54),
                        byteArrayOf(0x34, 0x56, 0x78, 0x55)
                )
        )

        val actual = GTXMLTransactionParser.parseGTXMLTransaction(xml, TransactionContext.empty(), MockCryptoSystem())

        assertEquals(expectedTx, actual)
    }

    @Test
    fun parseGTXMLTransaction_with_empty_all_sections_successfully() {
        val xml = javaClass.getResource("/net/postchain/gtx/gtxml/parse/tx_empty.xml").readText()

        val expectedBody = GtxBody(
                blockchainRID,
                arrayOf(),
                arrayOf()
        )

        val expectedTx = Gtx(expectedBody, listOf())

        val actual = GTXMLTransactionParser.parseGTXMLTransaction(xml, TransactionContext.empty(), MockCryptoSystem())


        assertEquals(expectedTx, actual)
    }

    @Test
    fun parseGTXMLTransaction_with_empty_signers_and_signatures_successfully() {
        val xml = javaClass.getResource("/net/postchain/gtx/gtxml/parse/tx_empty_signers_and_signatures.xml").readText()

        val expectedBody = GtxBody(
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

        val expectedTx = Gtx(expectedBody, listOf())

        val actual = GTXMLTransactionParser.parseGTXMLTransaction(xml, TransactionContext.empty(), MockCryptoSystem())

        assertEquals(expectedTx, actual)
    }

    @Test
    fun parseGTXMLTransaction_with_empty_operations_successfully() {
        val xml = javaClass.getResource("/net/postchain/gtx/gtxml/parse/tx_empty_operations.xml").readText()

        val expectedBody = GtxBody(
                blockchainRID,
                arrayOf(),
                arrayOf(
                        byteArrayOf(0x12, 0x38, 0x71, 0x23),
                        byteArrayOf(0x12, 0x38, 0x71, 0x24)
                )
        )
        val expectedTx = Gtx(expectedBody,
                arrayOf(
                        byteArrayOf(0x34, 0x56, 0x78, 0x54),
                        byteArrayOf(0x34, 0x56, 0x78, 0x55)
                )
        )

        val actual = GTXMLTransactionParser.parseGTXMLTransaction(xml, TransactionContext.empty(), MockCryptoSystem())

        assertEquals(expectedTx, actual)
    }

    @Test
    fun parseGTXMLTransaction_with_empty_operation_parameters_successfully() {
        val xml = javaClass.getResource("/net/postchain/gtx/gtxml/parse/tx_empty_operation_parameters.xml").readText()

        val expectedBody = GtxBody(
                blockchainRID,
                arrayOf(GtxOp("ft_transfer")),
                arrayOf(
                        byteArrayOf(0x12, 0x38, 0x71, 0x23),
                        byteArrayOf(0x12, 0x38, 0x71, 0x24)
                )
        )

        val expectedTx = Gtx(expectedBody,
                arrayOf(
                        byteArrayOf(0x34, 0x56, 0x78, 0x54),
                        byteArrayOf(0x34, 0x56, 0x78, 0x55)
                )
        )

        val actual = GTXMLTransactionParser.parseGTXMLTransaction(xml, TransactionContext.empty(), MockCryptoSystem())

        assertEquals(expectedTx, actual)
    }

    @Test
    fun parseGTXMLTransaction_in_context_with_params_in_all_sections_successfully() {
        val xml = javaClass.getResource("/net/postchain/gtx/gtxml/parse/tx_full_params.xml").readText()

        val expectedBody = GtxBody(
                blockchainRID,
                arrayOf(
                    GtxOp("ft_transfer",
                        GtvString("hello"),
                                GtvString("my string param"),
                                GtvInteger(123),
                                GtvByteArray(byteArrayOf(0x0A, 0x0B, 0x0C)))
                ),
                arrayOf(
                        byteArrayOf(0x12, 0x38, 0x71, 0x23),
                        byteArrayOf(0x12, 0x38, 0x71, 0x24),
                        byteArrayOf(0x01, 0x02, 0x03)
                )
        )

        val expectedTx = Gtx(expectedBody,
                arrayOf(
                        byteArrayOf(0x34, 0x56, 0x78, 0x54),
                        byteArrayOf(0x0A, 0x0B, 0x0C, 0x0D),
                        byteArrayOf(0x0E, 0x0F)
                )
        )

        val context = TransactionContext(
                null,
                mapOf(
                        "param_signer" to GtvByteArray(byteArrayOf(0x01, 0x02, 0x03)),

                        "param_string" to GtvString("my string param"),
                        "param_int" to GtvInteger(123),
                        "param_bytearray" to GtvByteArray(byteArrayOf(0x0A, 0x0B, 0x0C)),

                        "param_signature_1" to GtvByteArray(byteArrayOf(0x0A, 0x0B, 0x0C, 0x0D)),
                        "param_signature_2" to GtvByteArray(byteArrayOf(0x0E, 0x0F))
                )
        )

        val actual = GTXMLTransactionParser.parseGTXMLTransaction(xml, context, MockCryptoSystem())

        assertEquals(expectedTx, actual)
    }

    @Test
    fun parseGTXMLTransaction_in_context_with_not_found_params_throws_exception() {
        val xml = javaClass.getResource("/net/postchain/gtx/gtxml/parse/tx_full_params_not_found.xml").readText()

        assertThrows<java.lang.IllegalArgumentException> {
            GTXMLTransactionParser.parseGTXMLTransaction(
                xml, TransactionContext.empty(), MockCryptoSystem()
            )
        }
    }

    @Test
    fun parseGTXMLTransaction_in_context_with_not_bytea_param_in_signers_throws_exception() {
        val xml = javaClass.getResource("/net/postchain/gtx/gtxml/parse/tx_params_not_bytea_signer.xml").readText()

        val context = TransactionContext(
                null,
                mapOf(
                        "param_foo" to GtvString("my string param")
                )
        )

        assertThrows<UserMistake> {
            GTXMLTransactionParser.parseGTXMLTransaction(xml, context, MockCryptoSystem())
        }
    }

    @Test
    fun parseGTXMLTransaction_in_context_with_not_bytea_param_in_signature_throws_exception() {
        val xml = javaClass.getResource("/net/postchain/gtx/gtxml/parse/tx_params_not_bytea_signature.xml").readText()

        val context = TransactionContext(
                null,
                mapOf(
                        "param_foo" to GtvString("my string param")
                )
        )

        assertThrows<UserMistake> {
            GTXMLTransactionParser.parseGTXMLTransaction(xml, context, MockCryptoSystem())
        }
    }

    @Test
    fun parseGTXMLTransaction_in_context_with_compound_parameters_of_operation_successfully() {
        val xml = javaClass.getResource(
                "/net/postchain/gtx/gtxml/parse/tx_params_is_compound_of_parameter_of_operation.xml").readText()

        val expectedBody = GtxBody(
                blockchainRID,
                arrayOf(
                        GtxOp("ft_transfer",
                                GtvArray(arrayOf(
                                        GtvString("foo"),
                                        GtvArray(arrayOf(
                                                GtvString("foo"),
                                                GtvString("bar")
                                        )),
                                        GtvDictionary.build(mapOf(
                                                "key2" to GtvString("42"),
                                                "key1" to GtvInteger(42),
                                                "key3" to GtvArray(arrayOf(
                                                        GtvString("hello"),
                                                        GtvInteger(42)))
                                        ))
                                ))
                        )
                ),
                arrayOf()
        )

        val expectedTx = Gtx( expectedBody, listOf())

        val context = TransactionContext(
                null,
                mapOf("param_compound" to
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
                        ))
                )
        )

        val actual = GTXMLTransactionParser.parseGTXMLTransaction(xml, context, MockCryptoSystem())

        assertEquals(expectedTx, actual)
    }

    @Test
    fun parseGTXMLTransaction_signers_more_than_signatures_throws_exception() {
        val xml = javaClass.getResource(
                "/net/postchain/gtx/gtxml/parse/tx_signers_and_signatures_incompatibility__signers_more_than_signatures.xml")
                .readText()

        assertThrows<IllegalArgumentException> {
            GTXMLTransactionParser.parseGTXMLTransaction(xml, TransactionContext.empty(), MockCryptoSystem())
        }
    }

    @Test
    fun parseGTXMLTransaction_signers_less_than_signatures_throws_exception() {
        val xml = javaClass.getResource(
                "/net/postchain/gtx/gtxml/parse/tx_signers_and_signatures_incompatibility__signers_less_than_signatures.xml")
                .readText()

        assertThrows<IllegalArgumentException> {
            GTXMLTransactionParser.parseGTXMLTransaction(xml, TransactionContext.empty(), MockCryptoSystem())
        }
    }

    @Test
    fun parseGTXMLTransaction_no_signatures_element_successfully() {
        val xml = javaClass.getResource(
                "/net/postchain/gtx/gtxml/parse/tx_signers_and_signatures_incompatibility__no_signatures_element.xml")
                .readText()

        val expectedBody = GtxBody(
                blockchainRID,
                arrayOf(),
                arrayOf(
                        byteArrayOf(0x12, 0x38, 0x71, 0x23),
                        byteArrayOf(0x12, 0x38, 0x71, 0x24),
                        byteArrayOf(0x12, 0x38, 0x71, 0x25)
                )
        )

        val expectedTx = Gtx(expectedBody,
                arrayOf(
                        byteArrayOf(),
                        byteArrayOf(),
                        byteArrayOf()
                )
        )

        val actual = GTXMLTransactionParser.parseGTXMLTransaction(xml, TransactionContext.empty(), MockCryptoSystem())

        assertEquals(expectedTx, actual)
    }
}