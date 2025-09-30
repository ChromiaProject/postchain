// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.base.data

import net.postchain.base.BaseBlockEContext
import net.postchain.base.BaseBlockHeader
import net.postchain.base.BaseEContext
import net.postchain.base.SpecialTransactionHandler
import net.postchain.base.SpecialTransactionPosition.Begin
import net.postchain.base.SpecialTransactionPosition.End
import net.postchain.base.TxEventSink
import net.postchain.base.data.BaseBlockBuilder.Companion.PRIMARY_HEADER_KEY
import net.postchain.common.BlockchainRid
import net.postchain.common.hexStringToByteArray
import net.postchain.core.BadBlockException
import net.postchain.core.Transaction
import net.postchain.core.TxEContext
import net.postchain.core.ValidationResult.Result.INVALID_TIMESTAMP
import net.postchain.core.ValidationResult.Result.OK
import net.postchain.core.block.InitialBlockData
import net.postchain.crypto.KeyPair
import net.postchain.crypto.devtools.KeyPairHelper.privKey
import net.postchain.crypto.devtools.KeyPairHelper.pubKey
import net.postchain.crypto.devtools.MockCryptoSystem
import net.postchain.crypto.sha256Digest
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.merkle.GtvMerkleHashCalculatorV2
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class BaseBlockBuilderTest {
    val cryptoSystem = MockCryptoSystem()
    val merkeHashCalculator = GtvMerkleHashCalculatorV2(cryptoSystem)
    var bbs = BaseBlockStore()
    val db: DatabaseAccess = mock {}
    val ctx = BaseEContext(mock {}, 2L, db)

    val dummyEventSink = object : TxEventSink {
        override fun processEmittedEvent(ctxt: TxEContext, type: String, data: Gtv) {
            throw NotImplementedError("not used in mock")
        }
    }

    val bctx = BaseBlockEContext(ctx, 0, 1, 10, mapOf(), dummyEventSink)
    val myMerkleRootHash = "46AF9064F12528CAD6A7C377204ACD0AC38CDC6912903E7DAB3703764C8DD5E5".hexStringToByteArray()
    val myBlockchainRid = BlockchainRid.ZERO_RID
    val dummy = ByteArray(32, { 0 })
    val subjects = arrayOf(pubKey(0))
    val signer = cryptoSystem.buildSigMaker(KeyPair(pubKey(0), privKey(0)))
    val validator = BaseBlockWitnessProvider(cryptoSystem, signer, subjects)
    val specialTransactionHandler: SpecialTransactionHandler = mock()
    val maxBlockSize = 26 * 1024 * 1024L
    val maxSpecialEndTransactionSize = 1024L
    val bbb = BaseBlockBuilder(
            myBlockchainRid, cryptoSystem, ctx, bbs,
            specialTransactionHandler,
            subjects, signer, validator, listOf(), listOf(), false,
            maxBlockSize = maxBlockSize,
            maxBlockTransactions = 100,
            maxSpecialEndTransactionSize = maxSpecialEndTransactionSize,
            suppressSpecialTransactionValidation = false,
            maxBlockFutureTime = -1,
            pubKey(0),
            false,
            GtvMerkleHashCalculatorV2(::sha256Digest),
    )
    val primaryExtraHeader = mapOf(PRIMARY_HEADER_KEY to gtv(pubKey(0)))

    @Test
    fun invalidMonotoneTimestamp() {
        val timestamp = 1L
        val blockData = InitialBlockData(myBlockchainRid, 2, 2, dummy, 1, timestamp, arrayOf())
        val header = BaseBlockHeader.make(merkeHashCalculator, blockData, myMerkleRootHash, timestamp, primaryExtraHeader)
        bbb.bctx = bctx
        bbb.initialBlockData = blockData
        assertEquals(INVALID_TIMESTAMP, bbb.validateBlockHeader(header, primaryExtraHeader).result)
    }

    @Test
    fun invalidMonotoneTimestampEquals() {
        val timestamp = 10L
        val blockData = InitialBlockData(myBlockchainRid, 2, 2, dummy, 1, timestamp, arrayOf())
        val header = BaseBlockHeader.make(merkeHashCalculator, blockData, myMerkleRootHash, timestamp, primaryExtraHeader)
        bbb.bctx = bctx
        bbb.initialBlockData = blockData
        assertEquals(INVALID_TIMESTAMP, bbb.validateBlockHeader(header, primaryExtraHeader).result)
    }

    @Test
    fun validMonotoneTimestamp() {
        val timestamp = 100L
        val blockData = InitialBlockData(myBlockchainRid, 2, 2, dummy, 1, timestamp, arrayOf())
        val header = BaseBlockHeader.make(merkeHashCalculator, blockData, myMerkleRootHash, timestamp, primaryExtraHeader)
        bbb.bctx = bctx
        bbb.initialBlockData = blockData
        assertEquals(OK, bbb.validateBlockHeader(header, primaryExtraHeader).result)
    }

    @Test
    fun `with no limits reached should not stop building blocks`() {
        // setup
        whenever(specialTransactionHandler.needsSpecialTransaction(End)).doReturn(false)
        // execute & verify
        assertFalse(bbb.shouldStopBuildingBlock(2))
    }

    @Test
    fun `if max transaction count is reached should stop building blocks`() {
        // setup
        whenever(specialTransactionHandler.needsSpecialTransaction(End)).doReturn(false)
        bbb.transactions.add(mock())
        bbb.transactions.add(mock())
        // execute & verify
        assertTrue(bbb.shouldStopBuildingBlock(2))
    }

    @Test
    fun `if max transaction count is reached through special end transaction should stop building blocks`() {
        // setup
        whenever(specialTransactionHandler.needsSpecialTransaction(End)).doReturn(true)
        bbb.transactions.add(mock())
        assertEquals(1, bbb.transactions.size)
        // execute & verify
        assertTrue(bbb.shouldStopBuildingBlock(2))
    }

    @Test
    fun `max size of transactions reached should stop building blocks`() {
        // setup
        whenever(specialTransactionHandler.needsSpecialTransaction(End)).doReturn(false)
        bbb.blockSize = maxBlockSize
        assertEquals(0, bbb.transactions.size)
        // execute & verify
        assertTrue(bbb.shouldStopBuildingBlock(2))
    }

    @Test
    fun `max size of transactions reached through special end transaction should stop building blocks`() {
        // setup
        whenever(specialTransactionHandler.needsSpecialTransaction(End)).doReturn(true)
        bbb.blockSize = maxBlockSize - maxSpecialEndTransactionSize
        assertEquals(0, bbb.transactions.size)
        // execute & verify
        assertTrue(bbb.shouldStopBuildingBlock(2))
    }

    @Test
    fun `skipping begin special tx is allowed if all extensions allow it`() {
        // setup
        whenever(specialTransactionHandler.needsSpecialTransaction(Begin)).doReturn(true)
        whenever(specialTransactionHandler.isAllowedToSkipSpecialTransaction(Begin, bctx)).doReturn(true)
        val normalTx: Transaction = mock {
            on { isSpecial() } doReturn false
            on { getRawData() } doReturn ByteArray(0)
            on { getRID() } doReturn ByteArray(0)
            on { apply(any()) } doReturn true
        }
        bbb.bctx = bctx

        // execute and verify
        bbb.appendTransaction(normalTx)
    }

    @Test
    fun `skipping begin special tx is not allowed if not all extensions allow it`() {
        // setup
        whenever(specialTransactionHandler.needsSpecialTransaction(Begin)).doReturn(true)
        whenever(specialTransactionHandler.isAllowedToSkipSpecialTransaction(Begin, bctx)).doReturn(false)
        val normalTx: Transaction = mock {
            on { isSpecial() } doReturn false
            on { getRawData() } doReturn ByteArray(0)
            on { getRID() } doReturn ByteArray(0)
            on { apply(any()) } doReturn true
        }
        bbb.bctx = bctx

        // execute and verify
        assertThrows<BadBlockException> {
            bbb.appendTransaction(normalTx)
        }
    }

    @Test
    fun `skipping end special tx is allowed if all extensions allow it`() {
        // setup
        whenever(specialTransactionHandler.needsSpecialTransaction(Begin)).doReturn(false)
        whenever(specialTransactionHandler.needsSpecialTransaction(End)).doReturn(true)
        whenever(specialTransactionHandler.isAllowedToSkipSpecialTransaction(End, bctx)).doReturn(true)
        bbb.bctx = bctx
        val blockData = InitialBlockData(myBlockchainRid, 2, 2, dummy, 1, 100L, arrayOf())
        bbb.initialBlockData = blockData
        val header = BaseBlockHeader.make(merkeHashCalculator, blockData, myMerkleRootHash, 100L, primaryExtraHeader)

        // execute and verify
        bbb.finalizeAndValidate(header)
    }

    @Test
    fun `skipping end special tx is not allowed if not all extensions allow it`() {
        // setup
        whenever(specialTransactionHandler.needsSpecialTransaction(Begin)).doReturn(false)
        whenever(specialTransactionHandler.needsSpecialTransaction(End)).doReturn(true)
        whenever(specialTransactionHandler.isAllowedToSkipSpecialTransaction(End, bctx)).doReturn(false)
        bbb.bctx = bctx
        val blockData = InitialBlockData(myBlockchainRid, 2, 2, dummy, 1, 100L, arrayOf())
        bbb.initialBlockData = blockData
        val header = BaseBlockHeader.make(merkeHashCalculator, blockData, myMerkleRootHash, 100L, primaryExtraHeader)

        // execute and verify
        assertThrows<BadBlockException> {
            bbb.finalizeAndValidate(header)
        }
    }
}