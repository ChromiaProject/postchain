// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.core

import net.postchain.PostchainContext
import net.postchain.base.BaseBlockBuilderExtension
import net.postchain.base.BlockWitnessProvider
import net.postchain.base.BlockchainRelatedInfo
import net.postchain.base.configuration.BlockchainConfigurationOptions
import net.postchain.common.BlockchainRid
import net.postchain.core.block.BlockBuilder
import net.postchain.core.block.BlockBuildingStrategy
import net.postchain.core.block.BlockHeader
import net.postchain.core.block.BlockQueries
import net.postchain.core.block.BlockWitness
import net.postchain.crypto.CryptoSystem
import net.postchain.crypto.SigMaker
import net.postchain.gtv.Gtv
import kotlin.time.Duration

/**
 * [BlockchainConfiguration] describes an individual blockchain instance (within Postchain system).
 * This type is also able to use the configuration settings to construct some core domain objects on its own
 * (for example [BlockBuildingStrategy]).
 */
interface BlockchainConfiguration {
    val rawConfig: Gtv
    val blockchainContext: BlockchainContext
    val chainID: Long
    val blockchainRid: BlockchainRid
    val blockchainDependencies: List<BlockchainRelatedInfo>
    val signers: List<ByteArray>
    val effectiveBlockchainRID: BlockchainRid
    val traits: Set<String>
    val syncInfrastructureName: DynamicClassName?
    val syncInfrastructureExtensionNames: List<DynamicClassName>
    val transactionQueueSize: Int
    val transactionQueueRecheckInterval: Duration
    val configHash: ByteArray

    fun decodeBlockHeader(rawBlockHeader: ByteArray): BlockHeader
    fun decodeWitness(rawWitness: ByteArray): BlockWitness
    fun getBlockHeaderValidator(): BlockWitnessProvider
    fun getTransactionFactory(): TransactionFactory
    fun makeBlockBuilder(ctx: EContext, isSyncing: Boolean, extraExtensions: List<BaseBlockBuilderExtension> = listOf()): BlockBuilder
    fun makeBlockQueries(storage: Storage): BlockQueries
    fun hasQuery(name: String): Boolean
    fun getBlockBuildingStrategy(blockQueries: BlockQueries, txQueue: TransactionQueue): BlockBuildingStrategy
    fun initializeModules(postchainContext: PostchainContext)
    fun shutdownModules()
}

fun interface BlockchainConfigurationFactorySupplier {
    fun supply(factoryName: String): BlockchainConfigurationFactory
}

interface BlockchainConfigurationFactory {
    fun makeBlockchainConfiguration(
            configurationData: Any,
            partialContext: BlockchainContext,
            blockSigMaker: SigMaker,
            eContext: EContext,
            cryptoSystem: CryptoSystem,
            blockchainConfigurationOptions: BlockchainConfigurationOptions = BlockchainConfigurationOptions.DEFAULT
    ): BlockchainConfiguration
}
