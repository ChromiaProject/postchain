// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.core

import net.postchain.base.BlockchainRid

interface Shutdownable {
    fun shutdown()
}

interface Synchronizable {
    val synchronizer: Any
}

/**
 * Blockchain engine used for building and adding new blocks
 */
interface BlockchainEngine : Shutdownable {
    fun initialize()
    fun setRestartHandler(restartHandler: RestartHandler)

    // TODO: POS-111: Remove `addBlock()` and rename `loadUnfinishedBlock()` to `loadBlock()`
    fun addBlock(block: BlockDataWithWitness)
    fun loadUnfinishedBlock(block: BlockData): Pair<ManagedBlockBuilder, Exception?>
	fun buildBlock(): Pair<ManagedBlockBuilder, Exception?>
	fun buildSnapshot(): ManagedSnapshotBuilder
    fun getTransactionQueue(): TransactionQueue
    fun getBlockBuildingStrategy(): BlockBuildingStrategy
    fun getSnapshotBuildingStrategy(): SnapshotBuildingStrategy
    fun getBlockQueries(): BlockQueries
    fun getConfiguration(): BlockchainConfiguration
}

interface BlockchainProcess : Shutdownable {
    fun getEngine(): BlockchainEngine
}

interface BlockchainProcessManager : Shutdownable, Synchronizable {
    fun startBlockchainAsync(chainId: Long)
    fun startBlockchain(chainId: Long): BlockchainRid?
    fun retrieveBlockchain(chainId: Long): BlockchainProcess?
    fun stopBlockchain(chainId: Long)
    fun restartHandler(chainId: Long): RestartHandler
}

// A return value of "true" means a restart is needed.
typealias RestartHandler = () -> Boolean

