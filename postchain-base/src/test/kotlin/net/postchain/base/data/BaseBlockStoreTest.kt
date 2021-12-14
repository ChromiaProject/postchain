// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.base.data

import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import net.postchain.base.BaseEContext
import net.postchain.core.BlockchainRid
import net.postchain.base.SECP256K1CryptoSystem
import net.postchain.core.EContext
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class BaseBlockStoreTest {

    val cryptoSystem = SECP256K1CryptoSystem()
    val blockchainRID = BlockchainRid(cryptoSystem.digest("Test BlockchainRID".toByteArray()))
    lateinit var sut: BaseBlockStore
    lateinit var db: DatabaseAccess
    lateinit var ctx: EContext

    @BeforeEach
    fun setup() {
        sut = BaseBlockStore()
        db = mock {}
        //sut.db = db
        ctx = BaseEContext(mock {}, 2L, 0, db)
    }

    @Test
    fun beginBlockReturnsBlockchainRIDOnFirstBlock() {
        whenever(db.getLastBlockHeight(ctx)).thenReturn(-1)
        whenever(db.getBlockchainRid(ctx)).thenReturn(blockchainRID)
        whenever(db.insertBlock(ctx, 0)).thenReturn(17)
        whenever(db.getLastBlockTimestamp(ctx)).thenReturn(1509606236)
        val initialBlockData = sut.beginBlock(ctx, blockchainRID, null)
        assertArrayEquals(blockchainRID.data, initialBlockData.prevBlockRID)
    }

    @Test
    fun beginBlockReturnsPrevBlockRIdOnSecondBlock() {
        val anotherRID = cryptoSystem.digest("A RID".toByteArray())
        whenever(db.getLastBlockHeight(ctx)).thenReturn(0)
        whenever(db.getBlockRID(ctx, 0)).thenReturn(anotherRID)
        whenever(db.getBlockchainRid(ctx)).thenReturn(blockchainRID)
        whenever(db.insertBlock(ctx, 1)).thenReturn(17)
        whenever(db.getLastBlockTimestamp(ctx)).thenReturn(1509606236)
        val initialBlockData = sut.beginBlock(ctx, blockchainRID, null)
        assertArrayEquals(anotherRID, initialBlockData.prevBlockRID)
    }
}