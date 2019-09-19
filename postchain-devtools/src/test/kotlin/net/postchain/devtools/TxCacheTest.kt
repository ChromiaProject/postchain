package net.postchain.devtools

import net.postchain.base.SECP256K1CryptoSystem
import net.postchain.common.hexStringToByteArray
import net.postchain.common.toHex
import net.postchain.configurations.GTXTestModule
import net.postchain.devtools.testinfra.TestOneOpGtxTransaction
import net.postchain.gtx.GTXTransactionFactory
import org.junit.Test
import kotlin.test.assertTrue

class TxCacheTest {
    val cryptoSystem = SECP256K1CryptoSystem()
    protected val blockchainRids = mapOf(
            1L to "78967baa4768cbcef11c508326ffb13a956689fcb6dc3ba17f4b895cbb1577a3",
            2L to "78967baa4768cbcef11c508326ffb13a956689fcb6dc3ba17f4b895cbb1577a4"
    )
    private val gtxTestModule =  GTXTestModule()
    private val factory1 = GTXTransactionFactory(blockchainRids[1L]!!.hexStringToByteArray(), gtxTestModule, cryptoSystem)
    private val factory2 = GTXTransactionFactory(blockchainRids[2L]!!.hexStringToByteArray(), gtxTestModule, cryptoSystem)
    private val factoryMap = mapOf(
            1L to factory1,
            2L to factory2)


    @Test
    fun happyPath() {
        val txPerBlock = 10

        val txCache = TxCache(mutableMapOf())
        for (chain in 1..2) {
            for (block in 0..3) {
                for (blockIndex in 0..(txPerBlock - 1)) {
                    val factory = factoryMap[chain.toLong()]
                    val id = calcId(chain, block, txPerBlock, blockIndex)
                    val tx = TestOneOpGtxTransaction(factory!!, id).getGTXTransaction()
                    txCache.addTx(tx, chain, block, blockIndex)
                }
            }
        }

        var chain = 2
        var block = 0
        var blockIndex = 7
        var id = calcId(chain, block, txPerBlock, blockIndex)
        var txExpected = TestOneOpGtxTransaction(factory2, id).getGTXTransaction()
        var txFound = txCache.getCachedTxRid(chain, block, blockIndex)
        //println("Expected RID:  ${txExpected.getRID().toHex()}")
        //println("Found    RID:  ${txFound.toHex()}")
        assertTrue(txExpected.getRID().contentEquals(txFound))

        chain = 1
        block = 2
        blockIndex = 0
        id = calcId(chain, block, txPerBlock, blockIndex)
        txExpected = TestOneOpGtxTransaction(factory1, id).getGTXTransaction()
        txFound = txCache.getCachedTxRid(chain, block, blockIndex)
        assertTrue(txExpected.getRID().contentEquals(txFound))
    }

    /**
     * Calculates an ID that's unique inside a group of blockchains
     */
    private fun calcId(chain: Int, block: Int, txPerBlock: Int, blockIndex: Int) =
            (chain * (block + 1) * txPerBlock) + blockIndex

}