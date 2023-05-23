// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.base

import net.postchain.base.data.BaseBlockBuilder
import net.postchain.base.data.BaseBlockStore
import net.postchain.base.data.BaseBlockWitnessProvider
import net.postchain.base.data.BaseTransactionFactory
import net.postchain.base.data.DatabaseAccess
import net.postchain.common.BlockchainRid
import net.postchain.common.hexStringToByteArray
import net.postchain.core.TxEContext
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
import org.mockito.kotlin.mock

class BaseBlockBuilderTest {
    val cryptoSystem = MockCryptoSystem()
    val merkeHashCalculator = GtvMerkleHashCalculator(cryptoSystem)
    var bbs = BaseBlockStore()
    val tf = BaseTransactionFactory()
    val db: DatabaseAccess = mock {}
    val ctx = BaseEContext(mock {}, 2L, db)

    val dummyEventSink = object : TxEventSink {
        override fun processEmittedEvent(ctxt: TxEContext, type: String, data: Gtv) {
            TODO("Not yet implemented")
        }
    }

    val bctx = BaseBlockEContext(ctx, 0, 1, 10, mapOf(), dummyEventSink)
    val myMerkleRootHash = "46AF9064F12528CAD6A7C377204ACD0AC38CDC6912903E7DAB3703764C8DD5E5".hexStringToByteArray()
    val myBlockchainRid = BlockchainRid.ZERO_RID
    val dummy = ByteArray(32, { 0 })
    val subjects = arrayOf("test".toByteArray())
    val signer = cryptoSystem.buildSigMaker(KeyPair(pubKey(0), privKey(0)))
    val validator = BaseBlockWitnessProvider(cryptoSystem, signer, subjects)
    val bbb = BaseBlockBuilder(myBlockchainRid, cryptoSystem, ctx, bbs, tf,
            NullSpecialTransactionHandler(),
            subjects, signer, validator, listOf(), listOf(), false,
            maxBlockSize = 26 * 1024 * 1024,
            maxBlockTransactions = 100,
            maxTxExecutionTime = 0)

    @Test
    fun invalidMonotoneTimestamp() {
        val timestamp = 1L
        val blockData = InitialBlockData(myBlockchainRid, 2, 2, dummy, 1, timestamp, arrayOf())
        val header = BaseBlockHeader.make(merkeHashCalculator, blockData, myMerkleRootHash, timestamp, mapOf())
        bbb.bctx = bctx
        bbb.initialBlockData = blockData
        assertEquals(INVALID_TIMESTAMP, bbb.validateBlockHeader(header).result)
    }

    @Test
    fun invalidMonotoneTimestampEquals() {
        val timestamp = 10L
        val blockData = InitialBlockData(myBlockchainRid, 2, 2, dummy, 1, timestamp, arrayOf())
        val header = BaseBlockHeader.make(merkeHashCalculator, blockData, myMerkleRootHash, timestamp, mapOf())
        bbb.bctx = bctx
        bbb.initialBlockData = blockData
        assertEquals(INVALID_TIMESTAMP, bbb.validateBlockHeader(header).result)
    }

    @Test
    fun validMonotoneTimestamp() {
        val timestamp = 100L
        val blockData = InitialBlockData(myBlockchainRid, 2, 2, dummy, 1, timestamp, arrayOf())
        val header = BaseBlockHeader.make(merkeHashCalculator, blockData, myMerkleRootHash, timestamp, mapOf())
        bbb.bctx = bctx
        bbb.initialBlockData = blockData
        assertEquals(OK, bbb.validateBlockHeader(header).result)
    }
}
/*
interface BlockBuilder {
fun begin()
fun appendTransaction(tx: Transaction)
fun appendTransaction(txData: ByteArray)
fun finalize()
fun finalizeAndValidate(bh: BlockHeader)
fun getBlockData(): BlockData
fun getBlockWitnessBuilder(): BlockWitnessBuilder?;
fun commit(w: BlockWitness?)
}

 */


//fun testBegin() {
//        val conn = mock<Connection> {}
//        val chainID = 18
//        val ctx = EContext(conn, chainID)
//        val initialBlockData = InitialBlockData(1L, ByteArray(32), 0L)
//        var txFactory = mock<TransactionFactory>()
//        val blockStore = mock<BlockStore> {
//            on { beginBlock(ctx) } doReturn(initialBlockData)
//            on { finalizeBlock() }
//        }
//
//        val SUT = BaseBlockBuilder(MockCryptoSystem(), ctx, blockStore, txFactory) as BlockBuilder
//        SUT.begin();
//
//        verify(blockStore).beginBlock(ctx)
//
//        SUT.finalize()
//
//        SUT.commit()

//}
//}
