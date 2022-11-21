package net.postchain.client.core

interface PostchainReadClient : PostchainQuery {
    /**
     * Query current block height
     */
    fun blockAtHeight(height: Long): BlockDetail?

    /**
     * Query block at height
     */
    fun currentBlockHeight(): Long
}
