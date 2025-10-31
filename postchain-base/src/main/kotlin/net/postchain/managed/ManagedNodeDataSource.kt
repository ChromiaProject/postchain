// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.managed

import net.postchain.base.configuration.BlockchainConfigurationOptions
import net.postchain.common.BlockchainRid
import net.postchain.config.node.PeerInfoDataSource
import net.postchain.core.BlockchainState
import net.postchain.crypto.PubKey
import net.postchain.managed.query.QueryRunner

interface ManagedNodeDataSource : PeerInfoDataSource, QueryRunner {

    val nmApiVersion: Int

    fun computeBlockchainInfoList(): List<BlockchainInfo>
    fun getConfiguration(blockchainRidRaw: ByteArray, height: Long): ByteArray?

    /**
     * Looks for the nearest configuration height strictly after parameter height. Returns
     * null if no future configurations found or if blockchain doesn't exist.
     */
    fun findNextConfigurationHeight(blockchainRidRaw: ByteArray, height: Long): Long?

    fun getPendingBlockchainConfiguration(blockchainRid: BlockchainRid, height: Long): List<PendingBlockchainConfiguration>

    fun getFaultyBlockchainConfiguration(blockchainRid: BlockchainRid, height: Long): ByteArray?

    fun getBlockchainState(blockchainRid: BlockchainRid): BlockchainState

    fun getBlockchainConfigurationOptions(blockchainRid: BlockchainRid, height: Long): BlockchainConfigurationOptions?

    fun findNextInactiveBlockchains(height: Long): List<InactiveBlockchainInfo>

    fun getMigratingBlockchainNodeInfo(blockchainRid: BlockchainRid): MigratingBlockchainNodeInfo?

    fun isBlockchainProvider(providerPubKey: PubKey, blockchainRid: BlockchainRid): Boolean

    /**
     * Returns the REST API URL:s of signer nodes for a blockchain.
     */
    fun getBlockchainApiUrls(brid: BlockchainRid): List<String>

    fun getHistoricConfigurationHeight(blockchainRid: BlockchainRid, historicBlockHeight: Long): Long?

    /** Returns the RID of the management/directory chain, or `null` if not possible to fetch. */
    fun getManagementChain(): BlockchainRid?
}
