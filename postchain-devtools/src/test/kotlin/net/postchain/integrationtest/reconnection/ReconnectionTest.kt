// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.integrationtest.reconnection

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isFalse
import net.postchain.common.wrap
import net.postchain.concurrent.util.get
import net.postchain.core.Transaction
import net.postchain.core.block.BlockQueries
import net.postchain.devtools.ConfigFileBasedIntegrationTest
import net.postchain.devtools.PostchainTestNode
import net.postchain.devtools.testinfra.TestTransaction
import java.util.Arrays
import java.util.concurrent.CompletionStage

open class ReconnectionTest : ConfigFileBasedIntegrationTest() {

    protected val tx0 = TestTransaction(0)
    protected val tx1 = TestTransaction(1)
    protected val tx10 = TestTransaction(10)
    protected val tx11 = TestTransaction(11)
    protected val tx100 = TestTransaction(100)
    protected val tx101 = TestTransaction(101)

    protected fun <T> queries(node: PostchainTestNode, action: (BlockQueries) -> CompletionStage<T>): T {
        return node
                .getBlockchainInstance()
                .blockchainEngine
                .getBlockQueries()
                .run {
                    action(this)
                }.get()
    }

    protected fun assertThatNodeInBlockHasTxs(node: PostchainTestNode, height: Long, vararg txs: Transaction) {
        // Asserting number of blocks at height
        val blockRids = queries(node) { it.getBlockRid(height) }
        assertThat(blockRids == null).isFalse()

        // Asserting content of a block
        val txsRids = queries(node) { it.getBlockTransactionRids(blockRids!!) }.map(ByteArray::wrap)
        assertThat(txsRids).containsExactly(
                *txs.map { tx -> tx.getRID().wrap() }.toTypedArray())
    }

    @SuppressWarnings("unused")
    protected fun printPeer(index: Int) {
        try {
            nodes[index].networkTopology()
                    .map { (pubKey, connection) ->
                        "$connection:${nodesNames[pubKey]}"
                    }
                    .let { peers ->
                        println("Node $index: ${Arrays.toString(peers.toTypedArray())}")
                    }
        } catch (e: java.lang.Exception) {
            println("printPeer($index): $e")
        }
    }

}