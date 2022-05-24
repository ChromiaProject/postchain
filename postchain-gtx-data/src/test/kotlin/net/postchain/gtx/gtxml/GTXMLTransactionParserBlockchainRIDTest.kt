// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.gtx.gtxml

import net.postchain.common.BlockchainRid
import net.postchain.crypto.devtools.MockCryptoSystem
import net.postchain.gtx.data.GTXTransactionBodyData
import net.postchain.gtx.data.GTXTransactionData
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class GTXMLTransactionParserBlockchainRIDTest {
    val blockchainRID = BlockchainRid.buildRepeat(0x0A)

    @Test
    fun parseGTXMLTransaction_in_context_with_empty_blockchainRID_successfully() {
        val xml = """
            <transaction blockchainRID="">
                <signers/><operations/><signatures/>
            </transaction>
        """.trimIndent()

        val expectedBody = GTXTransactionBodyData(
                blockchainRID,
                arrayOf(),
                arrayOf())

        val expectedTx = GTXTransactionData(expectedBody, arrayOf())

        val actual = GTXMLTransactionParser.parseGTXMLTransaction(
            xml,
            TransactionContext(blockchainRID),
            MockCryptoSystem()
        )

        assertEquals(actual, expectedTx)
    }

    @Test
    fun parseGTXMLTransaction_in_context_with_no_blockchainRID_successfully() {
        val xml = """
            <transaction>
                <signers/><operations/><signatures/>
            </transaction>
        """.trimIndent()

        val expectedBody = GTXTransactionBodyData(
                blockchainRID,
                arrayOf(), arrayOf())

        val expectedTx = GTXTransactionData(expectedBody, arrayOf())

        val actual = GTXMLTransactionParser.parseGTXMLTransaction(
            xml,
            TransactionContext(blockchainRID),
            MockCryptoSystem()
        )

        assertEquals(actual, expectedTx)
    }

    @Test
    fun parseGTXMLTransaction_in_context_with_no_blockchainRID_and_null_context_one_successfully() {
        val xml = """
            <transaction>
                <signers/><operations/><signatures/>
            </transaction>
        """.trimIndent()

        val expectedBody = GTXTransactionBodyData(
                BlockchainRid.ZERO_RID,
                arrayOf(), arrayOf())

        val expectedTx = GTXTransactionData(expectedBody, arrayOf())

        val actual = GTXMLTransactionParser.parseGTXMLTransaction(
            xml,
            TransactionContext(null),
            MockCryptoSystem()
        )

        assertEquals(actual, expectedTx)
    }

    @Test
    fun parseGTXMLTransaction_in_context_with_blockchainRID_equal_to_context_one_successfully() {
        val xml = """
            <transaction blockchainRID="0A0A0A0A0A0A0A0A0A0A0A0A0A0A0A0A0A0A0A0A0A0A0A0A0A0A0A0A0A0A0A0A">
                <signers/><operations/><signatures/>
            </transaction>
        """.trimIndent()

        val expectedBody = GTXTransactionBodyData(
                blockchainRID,
                arrayOf(), arrayOf())

        val expectedTx = GTXTransactionData(expectedBody, arrayOf())

        val actual = GTXMLTransactionParser.parseGTXMLTransaction(
            xml,
            TransactionContext(null),
            MockCryptoSystem()
        )

        assertEquals(actual, expectedTx)
    }

    @Test
    fun parseGTXMLTransaction_in_context_with_blockchainRID_not_equal_to_context_one() {
        val xml = """
            <transaction blockchainRID="0A0A0A0A0A0A0A0A0A0A0A0A0A0A0A0A0A0A0A0A0A0A0A0A0A0A0A0A0A0A0A0A">
                <signers/><operations/><signatures/>
            </transaction>
        """.trimIndent()

        val blockchainRID1 = BlockchainRid.buildRepeat(0x01)

        assertThrows<java.lang.IllegalArgumentException> {
            GTXMLTransactionParser.parseGTXMLTransaction(
                xml,
                TransactionContext(blockchainRID1),
                MockCryptoSystem()
            )
        }
    }
}