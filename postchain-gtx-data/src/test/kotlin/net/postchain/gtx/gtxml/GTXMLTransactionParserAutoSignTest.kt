// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.gtx.gtxml

import net.postchain.common.BlockchainRid
import net.postchain.common.data.byteArrayKeyOf
import net.postchain.crypto.devtools.KeyPairHelper.privKey
import net.postchain.crypto.devtools.KeyPairHelper.pubKey
import net.postchain.crypto.devtools.MockCryptoSystem
import net.postchain.gtv.GtvInteger
import net.postchain.gtv.GtvString
import net.postchain.gtv.merkle.GtvMerkleHashCalculator
import net.postchain.gtx.Gtx
import net.postchain.gtx.GtxBody
import net.postchain.gtx.GtxOp
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class GTXMLTransactionParserAutoSignTest {
    val blockchainRID = BlockchainRid.buildFromHex("1234567812345678123456781234567812345678123456781234567812345678")

    @Test
    fun autoSign_autosigning_for_empty_signatures_successfully() {
        val xml = readResourceFile("tx_two_empty_signatures.xml")

        val mockCalculator = GtvMerkleHashCalculator(MockCryptoSystem())  // TODO: POS-04_sig ??

        val pubKey0 = pubKey(0)
        val privKey0 = privKey(0)
        val sigMaker0 = MockCryptoSystem().buildSigMaker(pubKey0, privKey0)

        val pubKey1 = pubKey(1)
        val privKey1 = privKey(1)
        val sigMaker1 = MockCryptoSystem().buildSigMaker(pubKey1, privKey1)

        val expectedBody = GtxBody(
                blockchainRID,
                arrayOf(
                        GtxOp("ft_transfer",
                                        GtvString("hello"),
                                        GtvString("hello2"),
                                        GtvString("hello3"),
                                        GtvInteger(42),
                                        GtvInteger(43))
                ),
                arrayOf(
                        pubKey0,
                        byteArrayOf(0x12, 0x38, 0x71, 0x23),
                        byteArrayOf(0x12, 0x38, 0x71, 0x24),
                        pubKey1
                )
        )

        // Auto-signing
        val merkleRoot = expectedBody.calculateTxRid(mockCalculator)
        val expectedTx = Gtx( expectedBody,
                arrayOf(
                        sigMaker0.signDigest(merkleRoot).data, // Auto signed
                        byteArrayOf(0x34, 0x56, 0x78, 0x54),
                        byteArrayOf(0x34, 0x56, 0x78, 0x55),
                        sigMaker1.signDigest(merkleRoot).data // Auto signed
                )
        )

        val actual = GTXMLTransactionParser.parseGTXMLTransaction(
            xml,
            TransactionContext(
                null,
                mapOf(),
                true,
                mapOf(
                    pubKey0.byteArrayKeyOf() to sigMaker0,
                    pubKey1.byteArrayKeyOf() to sigMaker1
                )
            ),
            MockCryptoSystem()
        )

        assertEquals(expectedTx, actual)
    }

    @Test
    fun autoSign_autosigning_no_signer_throws_exception() {
        val xml = readResourceFile("tx_no_signer.xml")

        assertThrows<java.lang.IllegalArgumentException> {
            GTXMLTransactionParser.parseGTXMLTransaction(
                xml,
                TransactionContext(null, mapOf(), true, mapOf()),
                MockCryptoSystem()
            )
        }
    }

    @Test
    fun autoSign_no_autosigning_and_no_signer_successfully() {
        val xml = readResourceFile("tx_two_empty_signatures.xml")

        val pubKey0 = pubKey(0)
        val pubKey1 = pubKey(1)

        val expectedBody = GtxBody(
                blockchainRID,
                arrayOf(
                        GtxOp("ft_transfer",
                                        GtvString("hello"),
                                        GtvString("hello2"),
                                        GtvString("hello3"),
                                        GtvInteger(42),
                                        GtvInteger(43))
                ),
                arrayOf(
                        pubKey0,
                        byteArrayOf(0x12, 0x38, 0x71, 0x23),
                        byteArrayOf(0x12, 0x38, 0x71, 0x24),
                        pubKey1
                )
        )

        val expectedTx = Gtx(
                expectedBody,
                arrayOf(
                        byteArrayOf(), // empty signature
                        byteArrayOf(0x34, 0x56, 0x78, 0x54),
                        byteArrayOf(0x34, 0x56, 0x78, 0x55),
                        byteArrayOf() // empty signature
                )
        )

        val actual = GTXMLTransactionParser.parseGTXMLTransaction(
            xml,
            TransactionContext(null, mapOf(), false, mapOf()),
            MockCryptoSystem()
        )

        assertEquals(expectedTx, actual)
    }

    @Test
    fun autoSign_autosigning_no_signatures_element_successfully() {
        val xml = readResourceFile("tx_no_signatures_element.xml")

        val mockCalculator = GtvMerkleHashCalculator(MockCryptoSystem())  // TODO: POS-04_sig ??

        val pubKey0 = pubKey(0)
        val privKey0 = privKey(0)
        val sigMaker0 = MockCryptoSystem().buildSigMaker(pubKey0, privKey0)

        val pubKey1 = pubKey(1)
        val privKey1 = privKey(1)
        val sigMaker1 = MockCryptoSystem().buildSigMaker(pubKey1, privKey1)

        val expectedBody = GtxBody(
                blockchainRID,
                arrayOf(
                        GtxOp("ft_transfer",
                                        GtvString("hello"),
                                        GtvString("hello2"),
                                        GtvString("hello3"),
                                        GtvInteger(42),
                                        GtvInteger(43))
                ),
                arrayOf(pubKey0, pubKey1)
        )

        val merkleRoot = expectedBody.calculateTxRid(mockCalculator)
        val expectedTx = Gtx(expectedBody,
                    arrayOf(
                        sigMaker0.signDigest(merkleRoot).data,
                        sigMaker1.signDigest(merkleRoot).data
                    )
                )

        val actual = GTXMLTransactionParser.parseGTXMLTransaction(
            xml,
            TransactionContext(
                null,
                mapOf(),
                true,
                mapOf(
                    pubKey0.byteArrayKeyOf() to sigMaker0,
                    pubKey1.byteArrayKeyOf() to sigMaker1
                )
            ),
            MockCryptoSystem()
        )

        assertEquals(expectedTx, actual)
    }

    private fun readResourceFile(filename: String): String {
        return javaClass.getResource("/net/postchain/gtx/gtxml/auto-sign/$filename").readText()
    }
}