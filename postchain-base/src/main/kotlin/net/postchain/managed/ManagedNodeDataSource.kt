// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.managed

import net.postchain.common.BlockchainRid
import net.postchain.config.node.PeerInfoDataSource
import net.postchain.managed.query.QueryRunner

interface ManagedNodeDataSource : PeerInfoDataSource, QueryRunner {

    val nmApiVersion: Int

    fun getPeerListVersion(): Long
    fun computeBlockchainList(): List<ByteArray>
    fun computeBlockchainInfoList(): List<BlockchainInfo>
    fun getLastBuiltHeight(blockchainRidRaw: ByteArray): Long
    fun getConfiguration(blockchainRidRaw: ByteArray, height: Long): ByteArray?

    /**
     * Looks for the nearest configuration height strictly after parameter height. Returns
     * null if no future configurations found or if blockchain doesn't exist.
     */
    fun findNextConfigurationHeight(blockchainRidRaw: ByteArray, height: Long): Long?

    fun getPendingBlockchainConfiguration(blockchainRid: BlockchainRid, height: Long): PendingBlockchainConfiguration?

    fun isPendingBlockchainConfigurationApplied(blockchainRid: BlockchainRid, height: Long, baseConfigHash: ByteArray): Boolean
}
