// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.integrationtest.reconfiguration

import net.postchain.devtools.IntegrationTest
import net.postchain.devtools.PostchainTestNode
import net.postchain.devtools.PostchainTestNode.Companion.DEFAULT_CHAIN_IID
import net.postchain.devtools.testinfra.TestTransaction
import net.postchain.devtools.testinfra.TestTransactionFactory
import net.postchain.integrationtest.awaitedHeight
import net.postchain.integrationtest.buildBlocksUpTo
import net.postchain.integrationtest.query
import kotlin.test.assertNotNull

open class ReconfigurationTest : IntegrationTest() {

    protected fun tx(id: Int): TestTransaction = TestTransaction(id)

    protected fun blockTxsIds(node: PostchainTestNode, height: Long): Set<Int> {
        val blockRids = node.query(DEFAULT_CHAIN_IID) { it.getBlockRid(height) }
        assertNotNull(blockRids)

        val txsRids = node.query(DEFAULT_CHAIN_IID) { it.getBlockTransactionRids(blockRids!!) }
        assertNotNull(txsRids)

        val txFactory = TestTransactionFactory()
        return txsRids!!.asSequence().map { txRid ->
            val tx = node.query(DEFAULT_CHAIN_IID) { it.getTransaction(txRid) }
            assertNotNull(tx)

            (txFactory.decodeTransaction(tx!!.getRawData()) as TestTransaction).id
        }.toSet()
    }

    protected fun awaitReconfiguration(height: Long) {
        nodes.first().buildBlocksUpTo(DEFAULT_CHAIN_IID, height - 1)
        while (nodes.any { it.awaitedHeight(DEFAULT_CHAIN_IID) < height });
    }
}