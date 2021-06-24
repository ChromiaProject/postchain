// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.core

import net.postchain.base.BlockchainRid
import net.postchain.debug.BlockTrace

interface Shutdownable {
    fun shutdown()
}

interface Synchronizable {
    val synchronizer: Any
}

/**
 * Blockchain engine used for building and adding new blocks.
 * (It is specialized on block creation and ignorant about other nodes etc, see [BlockchainProcess] for that)
 *
 * The [BlockchainEngine] delegates tasks to other classes, for example:
 * - [TransactionQueue] is where the engine gets its transactions from, needed to fill the block
 * - [BlockchainConfiguration] knows meta-data about the blockchain
 * - [BlockBuildingStrategy] knows HOW to build a block
 */
interface BlockchainEngine : Shutdownable {
    fun isRunning(): Boolean
    fun initialize()
    fun setAfterCommitHandler(afterCommitHandler: AfterCommitHandler)

    fun loadUnfinishedBlock(block: BlockData): Pair<ManagedBlockBuilder, Exception?>
    fun buildBlock(): Pair<ManagedBlockBuilder, Exception?>
    fun getTransactionQueue(): TransactionQueue
    fun getBlockBuildingStrategy(): BlockBuildingStrategy
    fun getBlockQueries(): BlockQueries
    fun getConfiguration(): BlockchainConfiguration
}

/**
 * Responsible for "running" a blockchain (usually just keep adding new blocks to the chain).
 * A blockchain can be "ran" more than one way, for example it can be a "read only" or play an active p part of
 * block creation.
 *
 * The [BlockchainProcess] delegates many tasks to other classes, typically:
 * - [WorkerContext] that holds most things the process needs, like node specific info and the [BlockchainEngine]
 * - [SyncManager] to sync with other nodes running this blockchain,
 * - [BlockDatabase] to read and store blocks to the DB.
 */
interface BlockchainProcess {
    fun getEngine(): BlockchainEngine
    fun shutdown()
}

/**
 *  Manages a set of [BlockchainProcess]:es (see the implementations for detailed documentation)
 */
interface BlockchainProcessManager : Shutdownable, Synchronizable {
    fun startBlockchain(chainId: Long, bTrace: BlockTrace?): BlockchainRid?
    fun retrieveBlockchain(chainId: Long): BlockchainProcess?
    fun stopBlockchain(chainId: Long, bTrace: BlockTrace?)
}

// A return value of "true" means a restart is needed.
typealias AfterCommitHandler = (BlockTrace?, Long) -> Boolean

