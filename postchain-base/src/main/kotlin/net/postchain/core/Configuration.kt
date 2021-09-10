// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.core

import net.postchain.core.BlockchainRid
import net.postchain.base.Storage
import net.postchain.gtv.Gtv

/**
 * [BlockchainConfiguration] describes an individual blockchain instance (within Postchain system).
 * This type is also able to use the configuration settings to construct some core domain objects on its own
 * (for example [BlockBuildingStrategy]).
 */
interface BlockchainConfiguration {
    val chainID: Long
    val blockchainRid: BlockchainRid
    val effectiveBlockchainRID: BlockchainRid
    val traits: Set<String>
    val syncInfrastructureName: DynamicClassName?
    val syncInfrastructureExtensionNames: List<DynamicClassName>
    val icmfTarget: String?

    fun decodeBlockHeader(rawBlockHeader: ByteArray): BlockHeader
    fun decodeWitness(rawWitness: ByteArray): BlockWitness
    fun getTransactionFactory(): TransactionFactory
    fun makeBlockBuilder(ctx: EContext): BlockBuilder
    fun makeBlockQueries(storage: Storage): BlockQueries
    fun initializeDB(ctx: EContext)
    fun getBlockBuildingStrategy(blockQueries: BlockQueries, txQueue: TransactionQueue): BlockBuildingStrategy
    fun <T> getComponent(name: String): T?

}

interface ConfigurationDataStore {
    fun findConfigurationHeightForBlock(context: EContext, height: Long): Long?
    fun getConfigurationData(context: EContext, height: Long): ByteArray?
    fun addConfigurationData(context: EContext, height: Long, binData: ByteArray)
    fun addConfigurationData(context: EContext, height: Long, gtvData: Gtv)
    fun setMustSyncUntil(context: EContext, brid: BlockchainRid, height: Long) : Boolean
}

interface BlockchainConfigurationFactory {
    fun makeBlockchainConfiguration(configurationData: Any): BlockchainConfiguration
}