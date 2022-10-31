package net.postchain.client.core

interface PostchainReadClient : PostchainQuery {
    /**
     * Query current block height
     */
    fun blockAtHeightSync(height: Long): BlockDetail?

    /**
     * Query block at height
     */
    fun currentBlockHeightSync(): Long
}
