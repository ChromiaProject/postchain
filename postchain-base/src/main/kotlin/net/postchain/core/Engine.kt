// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.core

import net.postchain.common.BlockchainRid
import net.postchain.core.*
import net.postchain.core.block.*
import net.postchain.debug.DiagnosticData

interface Shutdownable {
    fun shutdown()
    //fun isShutdown() // TODO: Olle: shouldn't we have this too, so many are using a flag for this
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
    val blockBuilderStorage: Storage
    val sharedStorage: Storage

    fun isRunning(): Boolean

    fun loadUnfinishedBlock(block: BlockData): Pair<ManagedBlockBuilder, Exception?>
    fun buildBlock(): Pair<ManagedBlockBuilder, Exception?>
    fun getTransactionQueue(): TransactionQueue
    fun getBlockBuildingStrategy(): BlockBuildingStrategy
    fun getBlockQueries(): BlockQueries
    fun getConfiguration(): BlockchainConfiguration
    fun hasBuiltFirstBlockAfterConfigUpdate(): Boolean
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
    val blockchainEngine: BlockchainEngine
    fun start()
    fun shutdown()
    fun registerDiagnosticData(diagnosticData: DiagnosticData) = Unit
    fun isSigner(): Boolean
    fun getBlockchainState(): BlockchainState
}

// TODO: [POS-358]: Should we add chainId and brid to BlockchainProcess?
interface RemoteBlockchainProcess {
    val chainId: Long
    val blockchainRid: BlockchainRid
    val restApiUrl: String
}

/**
 *  Manages a set of [BlockchainProcess]:es (see the implementations for detailed documentation)
 */
interface BlockchainProcessManager : Shutdownable, Synchronizable {
    fun startBlockchain(chainId: Long, bTrace: BlockTrace?): BlockchainRid
    fun retrieveBlockchain(chainId: Long): BlockchainProcess?
    fun retrieveBlockchain(blockchainRid: BlockchainRid): BlockchainProcess?
    fun stopBlockchain(chainId: Long, bTrace: BlockTrace?, restart: Boolean = false)
}

// A return value of "true" means a restart is needed.
typealias AfterCommitHandler = (bTrace: BlockTrace?, height: Long, blockTimestamp: Long) -> Boolean

typealias BeforeCommitHandler = (bTrace: BlockTrace?, bctx: BlockEContext) -> Unit
