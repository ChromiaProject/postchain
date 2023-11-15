// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.base

import assertk.assertThat
import assertk.assertions.isEqualTo
import net.postchain.base.SpecialTransactionPosition.Begin
import net.postchain.base.SpecialTransactionPosition.End
import net.postchain.base.data.BaseBlockBuilder
import net.postchain.base.data.BaseBlockStore
import net.postchain.base.data.BaseBlockWitnessProvider
import net.postchain.base.data.BaseTransactionFactory
import net.postchain.base.data.DatabaseAccess
import net.postchain.common.BlockchainRid
import net.postchain.common.hexStringToByteArray
import net.postchain.core.BadBlockException
import net.postchain.core.Transaction
import net.postchain.core.TxEContext
import net.postchain.core.ValidationResult.Result.INVALID_ROOT_HASH
import net.postchain.core.ValidationResult.Result.INVALID_TIMESTAMP
import net.postchain.core.ValidationResult.Result.OK
import net.postchain.core.block.InitialBlockData
import net.postchain.crypto.KeyPair
import net.postchain.crypto.devtools.KeyPairHelper.privKey
import net.postchain.crypto.devtools.KeyPairHelper.pubKey
import net.postchain.crypto.devtools.MockCryptoSystem
import net.postchain.gtv.Gtv
import net.postchain.gtv.merkle.GtvMerkleHashCalculator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.sql.Connection
import java.time.Clock

class BaseBlockBuilderValidationTest {
    // Mocks
    val cryptoSystem = MockCryptoSystem()
    val merkeHashCalculator = GtvMerkleHashCalculator(cryptoSystem)
    val mockedConn: Connection = mock {}
    val clock: Clock = mock()

    // Real stuff
    var bbs = BaseBlockStore()
    val tf = BaseTransactionFactory()
    val myBlockchainRid = BlockchainRid.ZERO_RID
    val empty32Bytes = ByteArray(32, { 0 })
    val rootHash = "46AF9064F12528CAD6A7C377204ACD0AC38CDC6912903E7DAB3703764C8DD5E5".hexStringToByteArray()
    val badRootHash = "46AF9064F12FFFFFFFFFFFFFF04ACD0AC38CDC6912903E7DAB3703764C8DD5E5".hexStringToByteArray()
    val subjects = arrayOf("test".toByteArray())
    val sigMaker = cryptoSystem.buildSigMaker(KeyPair(pubKey(0), privKey(0)))

    // Objects using mocks
    val db: DatabaseAccess = mock {}
    val ctx = BaseEContext(mockedConn, 2L, db)

    val dummyEventSink = object : TxEventSink {
        override fun processEmittedEvent(ctxt: TxEContext, type: String, data: Gtv) {
            TODO("Not yet implemented")
        }
    }

    val validator = BaseBlockWitnessProvider(cryptoSystem, sigMaker, subjects)
    val bctx = BaseBlockEContext(ctx, 0, 1, 10, mapOf(), dummyEventSink)
    val bbb = BaseBlockBuilder(BlockchainRid.buildRepeat(0), cryptoSystem, ctx, bbs, tf,
            NullSpecialTransactionHandler(),
            subjects, sigMaker, validator, listOf(), listOf(), false,
            maxBlockSize = 26 * 1024 * 1024,
            maxBlockTransactions = 100,
            maxTxExecutionTime = 0,
            maxSpecialEndTransactionSize = 1024,
            suppressSpecialTransactionValidation = false,
            maxBlockFutureTime = -1,
            clock)

    @Test
    fun validateBlockHeader_valid() {
        val timestamp = 100L
        val blockData = InitialBlockData(myBlockchainRid, 2, 2, empty32Bytes, 1, timestamp, null)
        val header = BaseBlockHeader.make(merkeHashCalculator, blockData, rootHash, timestamp, mapOf())
        bbb.bctx = bctx
        bbb.initialBlockData = blockData

        val validation = bbb.validateBlockHeader(header)

        assertEquals(OK, validation.result)
    }

    @Test
    fun validateBlockHeader_invalidMonotoneTimestamp() {
        val timestamp = 1L
        val blockData = InitialBlockData(myBlockchainRid, 2, 2, empty32Bytes, 1, timestamp, null)
        val header = BaseBlockHeader.make(merkeHashCalculator, blockData, rootHash, timestamp, mapOf())
        bbb.bctx = bctx
        bbb.initialBlockData = blockData

        val validation = bbb.validateBlockHeader(header)

        assertEquals(INVALID_TIMESTAMP, validation.result)
    }

    @Test
    fun validateBlockHeader_invalidMonotoneTimestampEquals() {
        val timestamp = 10L
        val blockData = InitialBlockData(myBlockchainRid, 2, 2, empty32Bytes, 1, timestamp, null)
        val header = BaseBlockHeader.make(merkeHashCalculator, blockData, rootHash, timestamp, mapOf())
        bbb.bctx = bctx
        bbb.initialBlockData = blockData

        val validation = bbb.validateBlockHeader(header)

        assertEquals(INVALID_TIMESTAMP, validation.result)
    }

    @Test
    fun validateBlokcHeader_invalidRootHash() {
        val timestamp = 100L
        val blockData = InitialBlockData(myBlockchainRid, 2, 2, empty32Bytes, 1, timestamp, null)
        val header = BaseBlockHeader.make(merkeHashCalculator, blockData, badRootHash, timestamp, mapOf())
        bbb.bctx = bctx
        bbb.initialBlockData = blockData

        val validation = bbb.validateBlockHeader(header)

        assertEquals(INVALID_ROOT_HASH, validation.result)
    }

    @Test
    fun `special tx validation is not skipped`() {
        // setup
        val sth: SpecialTransactionHandler = mock {
            on { needsSpecialTransaction(eq(Begin)) } doReturn true
            on { needsSpecialTransaction(eq(End)) } doReturn true
            on { validateSpecialTransaction(eq(End), any(), any()) } doReturn false
        }
        // The second call has to be True to make End state reachable
        doReturn(false).doReturn(true).whenever(sth).validateSpecialTransaction(eq(Begin), any(), any())

        val bbb = buildBaseBlockBuilder(sth, suppressSpecialTransactionValidation = false)
        bbb.bctx = bctx

        // interaction
        assertThrows<BadBlockException>("Special transaction validation failed: $Begin") {
            bbb.appendTransaction(mock<Transaction> {
                on { getRID() } doReturn byteArrayOf(1)
                on { isSpecial() } doReturn true
                on { getRawData() } doReturn byteArrayOf(0)
                on { apply(any()) } doReturn true
            })
        }
        // A valid Begin tx to reach an End state on the next step
        bbb.appendTransaction(mock<Transaction> {
            on { getRID() } doReturn byteArrayOf(2)
            on { isSpecial() } doReturn true
            on { getRawData() } doReturn byteArrayOf(0)
            on { apply(any()) } doReturn true
        })

        assertThrows<BadBlockException>("Special transaction validation failed: $End") {
            bbb.appendTransaction(mock<Transaction> {
                on { getRID() } doReturn byteArrayOf(3)
                on { isSpecial() } doReturn true
                on { getRawData() } doReturn byteArrayOf()
                on { apply(any()) } doReturn true
            })
        }

        // verify
        verify(sth, times(2)).validateSpecialTransaction(eq(Begin), any(), any())
        verify(sth, times(1)).validateSpecialTransaction(eq(End), any(), any())
        assertThat(bbb.transactions.size).isEqualTo(1)
    }

    @Test
    fun `special tx validation is skipped`() {
        // setup
        val sth: SpecialTransactionHandler = mock {
            on { needsSpecialTransaction(eq(Begin)) } doReturn true
            on { needsSpecialTransaction(eq(End)) } doReturn true
            on { validateSpecialTransaction(eq(Begin), any(), any()) } doReturn false
            on { validateSpecialTransaction(eq(End), any(), any()) } doReturn false
        }
        val bbb = buildBaseBlockBuilder(sth, suppressSpecialTransactionValidation = true)
        bbb.bctx = bctx

        // interaction
        bbb.appendTransaction(mock<Transaction> {
            on { getRID() } doReturn byteArrayOf(1)
            on { isSpecial() } doReturn true
            on { getRawData() } doReturn byteArrayOf(0)
            on { apply(any()) } doReturn true
        })
        bbb.appendTransaction(mock<Transaction> {
            on { getRID() } doReturn byteArrayOf(2)
            on { isSpecial() } doReturn true
            on { getRawData() } doReturn byteArrayOf()
            on { apply(any()) } doReturn true
        })

        // verify
        verify(sth, never()).validateSpecialTransaction(eq(Begin), any(), any())
        verify(sth, never()).validateSpecialTransaction(eq(End), any(), any())
        assertThat(bbb.transactions.size).isEqualTo(2)
    }

    @Test
    fun validateBlockHeader_invalid_future_timestamp() {
        doReturn(50L).whenever(clock).millis()
        val timestamp = 100L
        val blockData = InitialBlockData(myBlockchainRid, 2, 2, empty32Bytes, 1, timestamp, null)
        val header = BaseBlockHeader.make(merkeHashCalculator, blockData, rootHash, timestamp, mapOf())
        val bbb = buildBaseBlockBuilder(NullSpecialTransactionHandler(), suppressSpecialTransactionValidation = true, 10)
        bbb.bctx = bctx
        bbb.initialBlockData = blockData

        val validation = bbb.validateBlockHeader(header)

        assertEquals(INVALID_TIMESTAMP, validation.result)
        assertEquals("Block timestamp too far in the future", validation.message)
    }

    private fun buildBaseBlockBuilder(sth: SpecialTransactionHandler, suppressSpecialTransactionValidation: Boolean, maxBlockFutureTime: Long = -1) =
            BaseBlockBuilder(
                    BlockchainRid.ZERO_RID, cryptoSystem, ctx, bbs, tf,
                    sth,
                    subjects, sigMaker, validator, listOf(), listOf(), false,
                    26 * 1024 * 1024, 100, 0, 1024,
                    suppressSpecialTransactionValidation,
                    maxBlockFutureTime,
                    clock)
}
