package net.postchain.gtx.special

import net.postchain.common.BlockchainRid.Companion.ZERO_RID
import net.postchain.common.exception.ProgrammerMistake
import net.postchain.crypto.Secp256K1CryptoSystem
import net.postchain.gtx.GTXModule
import net.postchain.gtx.GTXTransaction
import net.postchain.gtx.GTXTransactionFactory
import net.postchain.gtx.GtxBuilder
import net.postchain.gtx.data.OpData
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import kotlin.test.assertEquals

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
        val emptyTx = GTXTransactionFactory(ZERO_RID, mock(), cs).decodeTransaction(
                GtxBuilder(ZERO_RID, listOf(), cs).finish().buildGtx().encode()
        ) as GTXTransaction

        val sut = GTXSpecialTxHandler(mock(), 0L, ZERO_RID, cs, mock())

        // validate
        val validated = sut.validateSpecialTransaction(mock(), emptyTx, mock())
        assertEquals(false, validated)
    }

    @Test
    fun `ext gives empty op list then __nop is added`() {
        val ext: GTXSpecialTxExtension = mock {
            on { getRelevantOps() } doReturn setOf("op1", "op11")
            on { needsSpecialTransaction(any()) } doReturn true
            on { createSpecialOperations(any(), any()) } doReturn listOf()
            on { validateSpecialOperations(any(), any(), any()) } doReturn true
        }
        val module: GTXModule = mock {
            on { getSpecialTxExtensions() } doReturn listOf(ext)
        }
        val factory = GTXTransactionFactory(ZERO_RID, module, cs)

        val sut = GTXSpecialTxHandler(module, 0L, ZERO_RID, cs, factory)

        // needs
        assertEquals(true, sut.needsSpecialTransaction(mock()))
        val tx = sut.createSpecialTransaction(mock(), mock()) as GTXTransaction

        // create
        val ops = tx.gtxData.gtxBody.operations.map { it.opName }
        assertEquals(listOf("__nop"), ops)

        // validate
        val validated = sut.validateSpecialTransaction(mock(), tx, mock())
        assertEquals(true, validated)
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
        val factory = GTXTransactionFactory(ZERO_RID, module, cs)

        val sut = GTXSpecialTxHandler(module, 0L, ZERO_RID, cs, factory)

        // needs
        assertEquals(true, sut.needsSpecialTransaction(mock()))
        val tx = sut.createSpecialTransaction(mock(), mock()) as GTXTransaction

        // create
        val ops = tx.gtxData.gtxBody.operations.map { it.opName }
        assertEquals(listOf("unknown_op"), ops)

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
        val factory = GTXTransactionFactory(ZERO_RID, module, cs)

        val sut = GTXSpecialTxHandler(module, 0L, ZERO_RID, cs, factory)

        // needs
        assertEquals(true, sut.needsSpecialTransaction(mock()))
        val tx = sut.createSpecialTransaction(mock(), mock()) as GTXTransaction

        // create
        val ops = tx.gtxData.gtxBody.operations.map { it.opName }
        assertEquals(listOf("op1", "op11"), ops)

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
        val factory = GTXTransactionFactory(ZERO_RID, module, cs)

        val sut = GTXSpecialTxHandler(module, 0L, ZERO_RID, cs, factory)

        // needs
        assertEquals(true, sut.needsSpecialTransaction(mock()))
        val tx = sut.createSpecialTransaction(mock(), mock()) as GTXTransaction

        // create
        val ops = tx.gtxData.gtxBody.operations.map { it.opName }
        assertEquals(listOf("op1", "op11"), ops)

        // validate
        val validated = sut.validateSpecialTransaction(mock(), tx, mock())
        assertEquals(true, validated)
    }
}