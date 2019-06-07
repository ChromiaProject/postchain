package net.postchain.util

import mu.KLogging
import net.postchain.common.toHex
import net.postchain.gtx.GTXTransaction

/**
 * Used to fetch a Transaction created in a by the [MultiNodeDoubleChainBlockTestHelper]
 */
class TxCache {

    companion object: KLogging()

    val hashMap = HashMap<String, GTXTransaction>()

    private fun makeKey(nodeId: Int, chainId: Int, height: Int, counter: Int): String {
        return "$nodeId-$chainId-$height-$counter"
    }

    fun add(nodeId: Int, chainId: Int, height: Int, counter: Int, tx: GTXTransaction) {
        val key = makeKey(nodeId, chainId, height, counter)
        logger.debug( "Storing: $key ")
        hashMap[key] = tx
    }

    /**
     *
     * @param nodeId the node we are at
     * @param chainId the chain we are at
     * @param height in blocks
     * @param counter what TX we are at
     * @return the cached transaction
     */
    fun getCachedTxRid(nodeId: Int, chainId: Int, height: Int, counter: Int): ByteArray {
        val key = makeKey(nodeId, chainId, height, counter)
        val expectedTxRid = hashMap[key]!!.getRID()
        logger.debug("Fetching cached TX with key: $key :  TX RID: ${expectedTxRid.toHex()}")
        return expectedTxRid
    }
}