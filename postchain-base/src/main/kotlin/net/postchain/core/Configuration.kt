// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.core

import net.postchain.base.BlockchainRid
import net.postchain.base.Storage
import net.postchain.gtv.Gtv

/**
 * BlockchainConfiguration is a stateless objects which describes
 * an individual blockchain instance within Postchain system
 */
interface BlockchainConfiguration {
    val chainID: Long
    val blockchainRid: BlockchainRid
    val traits: Set<String>

    fun decodeBlockHeader(rawBlockHeader: ByteArray): BlockHeader
    fun decodeWitness(rawWitness: ByteArray): BlockWitness
    fun getTransactionFactory(): TransactionFactory
    fun makeBlockBuilder(ctx: EContext): BlockBuilder
    fun makeSnapshotBuilder(ctx: EContext): SnapshotBuilder
    fun makeBlockQueries(storage: Storage): BlockQueries
    fun initializeDB(ctx: EContext)
    fun getBlockBuildingStrategy(blockQueries: BlockQueries, txQueue: TransactionQueue): BlockBuildingStrategy
    fun getSnapshotBuildingStrategy(blockQueries: BlockQueries): SnapshotBuildingStrategy
}

interface ConfigurationDataStore {
    fun findConfigurationHeightForBlock(context: EContext, height: Long): Long?
    fun getConfigurationData(context: EContext, height: Long): ByteArray?
    fun addConfigurationData(context: EContext, height: Long, binData: ByteArray)
    fun addConfigurationData(context: EContext, height: Long, gtvData: Gtv)
}

interface BlockchainConfigurationFactory {
    fun makeBlockchainConfiguration(configurationData: Any): BlockchainConfiguration
}
