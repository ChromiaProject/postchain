// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.modules.ft

import net.postchain.core.BlockchainRid
import net.postchain.base.SECP256K1CryptoSystem
import net.postchain.core.Transaction
import net.postchain.devtools.KeyPairHelper.privKey
import net.postchain.devtools.KeyPairHelper.pubKey
import net.postchain.devtools.modules.ft.FTIntegrationTest
import net.postchain.gtx.GTXTransaction
import net.postchain.gtx.GTXTransactionFactory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.system.measureNanoTime

val testBlockchainRID = BlockchainRid.buildFromHex("78967baa4768cbcef11c508326ffb13a956689fcb6dc3ba17f4b895cbb1577a3")
val myCS = SECP256K1CryptoSystem()

class FTPerfTestNightly : FTIntegrationTest() {

    fun make1000Transactions(): List<ByteArray> {
        val accUtil = AccountUtil(testBlockchainRID, myCS)
        val senderPriv = privKey(0)
        val senderPub = pubKey(0)
        val senderID = accUtil.makeAccountID(
                BasicAccount.makeDescriptor(testBlockchainRID.data, senderPub)
        )
        val receiverID = accUtil.makeAccountID(
                BasicAccount.makeDescriptor(testBlockchainRID.data, pubKey(1))
        )
        return (0..999).map {
            makeTransferTx(
                    senderPub, senderPriv, senderID, "USD",
                    it.toLong(), receiverID)
        }
    }

    init {
        this.setBlockchainRid(BlockchainRid.buildFromHex("1121212121212121212121212121212121212121212121212121212121112212"))
    }


    val accFactory = BaseAccountFactory(
            mapOf(
                    NullAccount.entry,
                    BasicAccount.entry
            )
    )
    val module = FTModule(FTConfig(
            FTIssueRules(arrayOf(), arrayOf()),
            FTTransferRules(arrayOf(), arrayOf(), false),
            FTRegisterRules(arrayOf(), arrayOf()),
            accFactory,
            BaseAccountResolver(accFactory),
            BaseDBOps(),
            myCS,
            testBlockchainRID
    ))
    val txFactory = GTXTransactionFactory(testBlockchainRID, module, myCS)

    @Test
    fun parseTx() {
        val transactions = make1000Transactions()
        var total = 0


        val nanoDelta = measureNanoTime {
            for (tx in transactions) {
                val ttx = txFactory.decodeTransaction(tx)
                total += (ttx as GTXTransaction).ops.size
            }
        }
        assertTrue(total == 1000)
        println("Time elapsed: ${nanoDelta / 1000000} ms")
    }

    @Test
    fun parseTxVerify() {
        val transactions = make1000Transactions()
        var total = 0

        val nanoDelta = measureNanoTime {
            for (tx in transactions) {
                val ttx = txFactory.decodeTransaction(tx)
                total += (ttx as GTXTransaction).ops.size
                assertTrue(ttx.isCorrect())
            }
        }
        assertTrue(total == 1000)
        println("Time elapsed: ${nanoDelta / 1000000} ms")
    }

    @Test
    fun testEverything() {
        configOverrides.setProperty("infrastructure", "base/test")
        val nodes = createNodes(1, "/net/postchain/ft_basic/blockchain_config.xml")
        val node = nodes[0]
        val validTxs = mutableListOf<Transaction>()
        var currentBlockHeight = -1L
        val bcRid = node.getBlockchainRid(1L)!!
        setBlockchainRid(bcRid) // Yes it's ugly but this is old stuff
        fun makeSureBlockIsBuiltCorrectly() {
            currentBlockHeight += 1
            buildBlockAndCommit(node)
            assertEquals(currentBlockHeight, getBestHeight(node))
            val ridsAtHeight = getTxRidsAtHeight(node, currentBlockHeight)
            for (vtx in validTxs) {
                val vtxRID = vtx.getRID()
                assertTrue(ridsAtHeight.any { it.contentEquals(vtxRID) })
            }
            assertEquals(validTxs.size, ridsAtHeight.size)
            validTxs.clear()
        }
        validTxs.add(enqueueTx(
                node,
                makeRegisterTx(arrayOf(aliceAccountDesc, bobAccountDesc), 0)
        )!!)

        makeSureBlockIsBuiltCorrectly()

        validTxs.add(enqueueTx(
                node,
                makeIssueTx(0, issuerID, aliceAccountID, "USD", 10000000)
        )!!)


        validTxs.add(enqueueTx(
                node,
                makeTransferTx(1, aliceAccountID, "USD", 100, bobAccountID)
        )!!)
        makeSureBlockIsBuiltCorrectly()
        System.gc()

        for (j in 0..3) {
            for (i in 0..99) {
                validTxs.add(enqueueTx(
                        node,
                        makeTransferTx(1, aliceAccountID, "USD",
                                1, bobAccountID, i.toString() + " " + j.toString()
                        )
                )!!)
            }
            makeSureBlockIsBuiltCorrectly()
        }
    }
}