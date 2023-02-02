package net.postchain.client.core

interface PostchainBlockClient : PostchainQuery {
    /**
     * Query block at height.
     */
    fun blockAtHeight(height: Long): BlockDetail?

    /**
     * Query current block height.
     */
    fun currentBlockHeight(): Long
}
