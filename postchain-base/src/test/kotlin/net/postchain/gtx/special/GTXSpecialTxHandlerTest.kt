package net.postchain.gtx.special

import net.postchain.base.SpecialTransactionPosition
import net.postchain.common.BlockchainRid.Companion.ZERO_RID
import net.postchain.common.exception.ProgrammerMistake
import net.postchain.crypto.Secp256K1CryptoSystem
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.merkle.GtvMerkleHashCalculatorV2
import net.postchain.gtx.GTXModule
import net.postchain.gtx.GTXTransaction
import net.postchain.gtx.GTXTransactionFactory
import net.postchain.gtx.Gtx
import net.postchain.gtx.GtxBody
import net.postchain.gtx.GtxBuilder
import net.postchain.gtx.GtxOp
import net.postchain.gtx.GtxSpecNop
import net.postchain.gtx.data.OpData
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

class GTXSpecialTxHandlerTest {

    private val cs = Secp256K1CryptoSystem()

    @Test
    fun `overlapped ops sets throw ProgrammerMistake in constructor`() {
        val ext1: GTXSpecialTxExtension = mock {
            on { getRelevantOps() } doReturn setOf("op0", "op1")
        }
        val ext2: GTXSpecialTxExtension = mock {
            on { getRelevantOps() } doReturn setOf("op0", "op2")
        }
        val module: GTXModule = mock {
            on { getSpecialTxExtensions() } doReturn listOf(ext1, ext2)
        }

        assertThrows<ProgrammerMistake> {
            GTXSpecialTxHandler(module, 0L, mock(), mock(), mock())
        }
    }

    @Test
    fun `gtx-tx has no ops`() {
        val emptyTx = GTXTransactionFactory(ZERO_RID, mock(), cs, GtvMerkleHashCalculatorV2(cs)).decodeTransaction(
                GtxBuilder(ZERO_RID, listOf(), cs, GtvMerkleHashCalculatorV2(cs)).finish().buildGtx().encode()
        ) as GTXTransaction

        val sut = GTXSpecialTxHandler(mock(), 0L, ZERO_RID, cs, mock())

        // validate
        val validated = sut.validateSpecialTransaction(mock(), emptyTx, mock())
        assertEquals(false, validated)
    }

    @Test
    fun `ext gives empty op list then null is returned by createSpecialOperations`() {
        val ext: GTXSpecialTxExtension = mock {
            on { getRelevantOps() } doReturn setOf("op1", "op11")
            on { needsSpecialTransaction(any()) } doReturn true
            on { createSpecialOperations(any(), any()) } doReturn listOf()
            on { validateSpecialOperations(any(), any(), any()) } doReturn true
        }
        val module: GTXModule = mock {
            on { getSpecialTxExtensions() } doReturn listOf(ext)
        }
        val factory = GTXTransactionFactory(ZERO_RID, module, cs, GtvMerkleHashCalculatorV2(cs))

        val sut = GTXSpecialTxHandler(module, 0L, ZERO_RID, cs, factory)

        assertEquals(true, sut.needsSpecialTransaction(mock()))
        assertNull(sut.createSpecialTransaction(mock(), mock()))
    }

    @Test
    fun `unknown op in gtx-tx makes tx invalid`() {
        val ext: GTXSpecialTxExtension = mock {
            on { getRelevantOps() } doReturn setOf("op1", "op11")
            on { needsSpecialTransaction(any()) } doReturn true
            on { createSpecialOperations(any(), any()) } doReturn listOf(
                    OpData("unknown_op", emptyArray())
            )
            on { validateSpecialOperations(any(), any(), any()) } doReturn true
        }
        val module: GTXModule = mock {
            on { getSpecialTxExtensions() } doReturn listOf(ext)
        }
        val factory = GTXTransactionFactory(ZERO_RID, module, cs, GtvMerkleHashCalculatorV2(cs))

        val sut = GTXSpecialTxHandler(module, 0L, ZERO_RID, cs, factory)

        // needs
        assertEquals(true, sut.needsSpecialTransaction(mock()))
        val tx = sut.createSpecialTransaction(mock(), mock()) as GTXTransaction

        // create
        val ops = tx.gtxData.gtxBody.operations.map { it.opName }
        assertEquals(listOf("unknown_op", GtxSpecNop.OP_NAME), ops)

        // validate
        val validated = sut.validateSpecialTransaction(mock(), tx, mock())
        assertEquals(false, validated)
    }

    @Test
    fun `one of two extensions needs special-tx but provides empty list of ops`() {
        val ext1: GTXSpecialTxExtension = mock {
            on { getRelevantOps() } doReturn setOf("op1", "op11")
            on { needsSpecialTransaction(any()) } doReturn true
            on { createSpecialOperations(any(), any()) } doReturn listOf(
                    OpData("op1", emptyArray()),
                    OpData("op11", emptyArray()),
            )
            on { validateSpecialOperations(any(), any(), any()) } doReturn true
        }
        val ext2: GTXSpecialTxExtension = mock {
            on { getRelevantOps() } doReturn setOf("op2")
            on { needsSpecialTransaction(any()) } doReturn true
            on { createSpecialOperations(any(), any()) } doReturn listOf()
            on { validateSpecialOperations(any(), any(), any()) } doReturn true
        }
        val module: GTXModule = mock {
            on { getSpecialTxExtensions() } doReturn listOf(ext1, ext2)
        }
        val factory = GTXTransactionFactory(ZERO_RID, module, cs, GtvMerkleHashCalculatorV2(cs))

        val sut = GTXSpecialTxHandler(module, 0L, ZERO_RID, cs, factory)

        // needs
        assertEquals(true, sut.needsSpecialTransaction(mock()))
        val tx = sut.createSpecialTransaction(mock(), mock()) as GTXTransaction

        // create
        val ops = tx.gtxData.gtxBody.operations.map { it.opName }
        assertEquals(listOf("op1", "op11", GtxSpecNop.OP_NAME), ops)

        // validate
        val validated = sut.validateSpecialTransaction(mock(), tx, mock())
        assertEquals(true, validated)
    }

    @Test
    fun `one of two extensions needs special-tx`() {
        val ext1: GTXSpecialTxExtension = mock {
            on { getRelevantOps() } doReturn setOf("op1", "op11")
            on { needsSpecialTransaction(any()) } doReturn true
            on { createSpecialOperations(any(), any()) } doReturn listOf(
                    OpData("op1", emptyArray()),
                    OpData("op11", emptyArray()),
            )
            on { validateSpecialOperations(any(), any(), any()) } doReturn true
        }
        val ext2: GTXSpecialTxExtension = mock {
            on { getRelevantOps() } doReturn setOf("op2")
            on { needsSpecialTransaction(any()) } doReturn false
            on { validateSpecialOperations(any(), any(), any()) } doReturn true
        }
        val module: GTXModule = mock {
            on { getSpecialTxExtensions() } doReturn listOf(ext1, ext2)
        }
        val factory = GTXTransactionFactory(ZERO_RID, module, cs, GtvMerkleHashCalculatorV2(cs))

        val sut = GTXSpecialTxHandler(module, 0L, ZERO_RID, cs, factory)

        // needs
        assertEquals(true, sut.needsSpecialTransaction(mock()))
        val tx = sut.createSpecialTransaction(mock(), mock()) as GTXTransaction

        // create
        val ops = tx.gtxData.gtxBody.operations.map { it.opName }
        assertEquals(listOf("op1", "op11", GtxSpecNop.OP_NAME), ops)

        // validate
        val validated = sut.validateSpecialTransaction(mock(), tx, mock())
        assertEquals(true, validated)
    }

    @Test
    fun `special tx has op at position but extension does not need it`() {
        val ext1: GTXSpecialTxExtension = mock {
            on { getRelevantOps() } doReturn setOf("op1")
            on { needsSpecialTransaction(SpecialTransactionPosition.Begin) } doReturn true
            on { needsSpecialTransaction(SpecialTransactionPosition.End) } doReturn false
            on { createSpecialOperations(any(), any()) } doReturn listOf(OpData("op1", emptyArray()))
            on { validateSpecialOperations(any(), any(), any()) } doReturn true
        }
        val module: GTXModule = mock {
            on { getSpecialTxExtensions() } doReturn listOf(ext1)
        }
        val factory = GTXTransactionFactory(ZERO_RID, module, cs, GtvMerkleHashCalculatorV2(cs))

        val sut = GTXSpecialTxHandler(module, 0L, ZERO_RID, cs, factory)

        // needs
        assertEquals(true, sut.needsSpecialTransaction(SpecialTransactionPosition.Begin))
        assertEquals(false, sut.needsSpecialTransaction(SpecialTransactionPosition.End))
        val tx = sut.createSpecialTransaction(SpecialTransactionPosition.Begin, mock()) as GTXTransaction

        // create
        val ops = tx.gtxData.gtxBody.operations.map { it.opName }
        assertEquals(listOf("op1", GtxSpecNop.OP_NAME), ops)

        // validate
        val validatedBegin = sut.validateSpecialTransaction(SpecialTransactionPosition.Begin, tx, mock())
        assertEquals(true, validatedBegin)
        val validatedEnd = sut.validateSpecialTransaction(SpecialTransactionPosition.End, tx, mock())
        assertEquals(false, validatedEnd)
    }

    @Test
    fun `extensions has no operations and does not allow skipping`() {
        val ext1: GTXNonSkippingSpecialTxExtension = mock {
            on { getRelevantOps() } doReturn setOf("op1")
            on { needsSpecialTransaction(any()) } doReturn true
            on { createSpecialOperations(any(), any()) } doReturn listOf()
            on { validateSpecialOperations(any(), any(), any()) } doReturn true
            on { isAllowedToSkipSpecialOperations(any(), any()) } doReturn false
        }
        val module: GTXModule = mock {
            on { getSpecialTxExtensions() } doReturn listOf(ext1)
        }
        val factory = GTXTransactionFactory(ZERO_RID, module, cs, GtvMerkleHashCalculatorV2(cs))

        val sut = GTXSpecialTxHandler(module, 0L, ZERO_RID, cs, factory)
        assertFalse(sut.isAllowedToSkipSpecialTransaction(SpecialTransactionPosition.Begin, mock()))

        // needs
        assertEquals(true, sut.needsSpecialTransaction(mock()))
        val nopGtx = Gtx(GtxBody(ZERO_RID, listOf(GtxOp(GtxSpecNop.OP_NAME, gtv(cs.getRandomBytes(32)))), listOf()), listOf())
        val tx = factory.decodeTransaction(nopGtx.encode()) as GTXTransaction

        // validate
        val validated = sut.validateSpecialTransaction(mock(), tx, mock())
        assertEquals(false, validated)
    }
}