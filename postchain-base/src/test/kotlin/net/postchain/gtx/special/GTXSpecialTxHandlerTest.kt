package net.postchain.gtx.special

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.messageContains
import net.postchain.base.SpecialTransactionPosition
import net.postchain.common.BlockchainRid
import net.postchain.common.BlockchainRid.Companion.ZERO_RID
import net.postchain.common.exception.ProgrammerMistake
import net.postchain.common.exception.UserMistake
import net.postchain.core.BlockEContext
import net.postchain.core.ExtensionBroadcaster
import net.postchain.crypto.CryptoSystem
import net.postchain.crypto.Secp256K1CryptoSystem
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.merkle.GtvMerkleHashCalculatorV2
import net.postchain.gtx.BroadcastAware
import net.postchain.gtx.BroadcastContext
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
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

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

        assertFailure {
            GTXSpecialTxHandler(module, 0L, mock(), mock(), mock(), mock())
        }.isInstanceOf<ProgrammerMistake>().messageContains("Overlapping op")
    }

    @Test
    fun `gtx-tx has no ops`() {
        val emptyTx = GTXTransactionFactory(ZERO_RID, mock(), cs, GtvMerkleHashCalculatorV2(cs)).decodeTransaction(
                GtxBuilder(ZERO_RID, listOf(), cs, GtvMerkleHashCalculatorV2(cs)).finish().buildGtx().encode()
        ) as GTXTransaction

        val sut = GTXSpecialTxHandler(mock(), 0L, ZERO_RID, cs, mock(), mock())

        // validate
        assertFailure {
            sut.validateSpecialTransaction(mock(), emptyTx, mock())
        }.isInstanceOf<UserMistake>().messageContains("Empty operation list is not allowed")
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

        val sut = GTXSpecialTxHandler(module, 0L, ZERO_RID, cs, factory, mock())

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

        val sut = GTXSpecialTxHandler(module, 0L, ZERO_RID, cs, factory, mock())

        // needs
        assertEquals(true, sut.needsSpecialTransaction(mock()))
        val tx = sut.createSpecialTransaction(mock(), mock()) as GTXTransaction

        // create
        val ops = tx.gtxData.gtxBody.operations.map { it.opName }
        assertEquals(listOf("unknown_op", GtxSpecNop.OP_NAME), ops)

        // validate
        assertFailure {
            sut.validateSpecialTransaction(mock(), tx, mock())
        }.isInstanceOf<UserMistake>().messageContains("Unknown operation detected")
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

        val sut = GTXSpecialTxHandler(module, 0L, ZERO_RID, cs, factory, mock())

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

        val sut = GTXSpecialTxHandler(module, 0L, ZERO_RID, cs, factory, mock())

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

        val sut = GTXSpecialTxHandler(module, 0L, ZERO_RID, cs, factory, mock())

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
        assertFailure {
            sut.validateSpecialTransaction(SpecialTransactionPosition.End, tx, mock())
        }.isInstanceOf<UserMistake>().messageContains("does not need special transaction at position: End")
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

        val sut = GTXSpecialTxHandler(module, 0L, ZERO_RID, cs, factory, mock())
        assertFalse(sut.isAllowedToSkipSpecialTransaction(SpecialTransactionPosition.Begin, mock()))

        // needs
        assertEquals(true, sut.needsSpecialTransaction(mock()))
        val nopGtx = Gtx(GtxBody(ZERO_RID, listOf(GtxOp(GtxSpecNop.OP_NAME, gtv(cs.getRandomBytes(32)))), listOf()), listOf())
        val tx = factory.decodeTransaction(nopGtx.encode()) as GTXTransaction

        // validate
        assertFailure {
            sut.validateSpecialTransaction(mock(), tx, mock())
        }.isInstanceOf<UserMistake>().messageContains("Skipping special operations is not allowed by handler")
    }

    @Test
    fun `extension can broadcast message`() {
        val ext1 = BroadcastAwareGTXSpecialTxExtension()
        val ext2: GTXSpecialTxExtension = mock()
        val module: GTXModule = mock {
            on { getSpecialTxExtensions() } doReturn listOf(ext1, ext2)
        }
        val factory = GTXTransactionFactory(ZERO_RID, module, cs, GtvMerkleHashCalculatorV2(cs))

        val extensionBroadcaster = mock<ExtensionBroadcaster>()
        val sut = GTXSpecialTxHandler(module, 0L, ZERO_RID, cs, factory, extensionBroadcaster)

        sut.createSpecialTransaction(mock(), mock())
        verify(extensionBroadcaster).broadcast(ext1.javaClass.name, gtv("send"))

        sut.receiveBroadcast(ext1.javaClass.name, gtv("receive"))
        assertThat(ext1.receivedBroadcast).isEqualTo("receive")
    }

    class BroadcastAwareGTXSpecialTxExtension : GTXSpecialTxExtension, BroadcastAware {
        lateinit var broadcastContext: BroadcastContext

        var receivedBroadcast: String? = null

        override fun init(module: GTXModule, chainID: Long, blockchainRID: BlockchainRid, cs: CryptoSystem) {}

        override fun getRelevantOps(): Set<String> = setOf("op1")

        override fun needsSpecialTransaction(position: SpecialTransactionPosition): Boolean = true

        override fun createSpecialOperations(position: SpecialTransactionPosition, bctx: BlockEContext): List<OpData> {
            broadcastContext.broadcast(gtv("send"))
            return listOf()
        }

        override fun validateSpecialOperations(position: SpecialTransactionPosition, bctx: BlockEContext, ops: List<OpData>): Boolean = true

        override fun initializeBroadcastContext(context: BroadcastContext) {
            broadcastContext = context
        }

        override fun receiveBroadcast(data: Gtv) {
            receivedBroadcast = data.asString()
        }
    }
}
