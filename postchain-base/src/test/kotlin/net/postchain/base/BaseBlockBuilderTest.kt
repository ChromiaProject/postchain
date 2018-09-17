// Copyright (c) 2017 ChromaWay Inc. See README for license information.

package net.postchain.base

import net.postchain.base.data.BaseBlockBuilder
import net.postchain.base.data.BaseBlockStore
import net.postchain.base.data.BaseTransactionFactory
import net.postchain.core.BlockEContext
import net.postchain.core.EContext
import net.postchain.core.InitialBlockData
import net.postchain.test.KeyPairHelper.privKey
import net.postchain.test.KeyPairHelper.pubKey
import net.postchain.test.MockCryptoSystem
import org.easymock.EasyMock.mock
import org.junit.Test
import java.sql.Connection

class BaseBlockBuilderTest {
    val cryptoSystem = MockCryptoSystem()
    var bbs = BaseBlockStore()
    val tf = BaseTransactionFactory()
    val ctx = EContext(mock(Connection::class.java), 2L, 0)
    val bctx = BlockEContext(mock(Connection::class.java), 2, 0, 1, 10)
    val dummy = ByteArray(32, { 0 })
    val subjects = arrayOf("test".toByteArray())
    val signer = cryptoSystem.makeSigner(pubKey(0), privKey(0))
    val bbb = BaseBlockBuilder(cryptoSystem, ctx, bbs, tf, subjects, signer)

    @Test
    fun invalidMonotoneTimestamp() {
        val timestamp = 1L
        val blockData = InitialBlockData(2, 2, dummy, 1, timestamp)
        val header = BaseBlockHeader.make(cryptoSystem, blockData, dummy, timestamp)
        bbb.bctx = bctx
        bbb.iBlockData = blockData
        assert(!bbb.validateBlockHeader(header))
    }

    @Test
    fun invalidMonotoneTimestampEquals() {
        val timestamp = 10L
        val blockData = InitialBlockData(2, 2, dummy, 1, timestamp)
        val header = BaseBlockHeader.make(cryptoSystem, blockData, dummy, timestamp)
        bbb.bctx = bctx
        bbb.iBlockData = blockData
        assert(!bbb.validateBlockHeader(header))
    }

    @Test
    fun validMonotoneTimestamp() {
        val timestamp = 100L
        val blockData = InitialBlockData(2, 2, dummy, 1, timestamp)
        val header = BaseBlockHeader.make(cryptoSystem, blockData, dummy, timestamp)
        bbb.bctx = bctx
        bbb.iBlockData = blockData
        assert(bbb.validateBlockHeader(header))
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