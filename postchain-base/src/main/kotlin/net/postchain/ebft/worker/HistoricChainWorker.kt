// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.ebft.worker

import net.postchain.core.*
import net.postchain.ebft.BaseBlockDatabase
import net.postchain.ebft.BlockDatabase
import net.postchain.ebft.syncmanager.common.FastSyncParameters
import net.postchain.ebft.syncmanager.common.FastSynchronizer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * Worker that synchronizes a blockchain using blocks from another blockchain, historicBrid.
 * The idea with this worker is to be able to fork a blockchain reliably.
 *
 * 1 Sync from local-OB (if available) until drained
 * 2 Sync from remote-OB until drained or timeout
 * 3 Sync from FB until drained or timeout
 * 4 Goto 1
 *
 */
class HistoricChainWorker(val workerContext: WorkerContext, val historicWorkerContext: WorkerContext, val historicBlockQueries: BlockQueries) : BlockchainProcess {

    override fun getEngine() = workerContext.engine

    private val fastSynchronizer: FastSynchronizer
    private val historicSynchronizer: FastSynchronizer
    private val done = CountDownLatch(1)
    private val shutdown = AtomicBoolean(false)
    init {
        /*
        1 Sync from local-OB until drained
        2 Sync from remote-OB until drained or timeout
        3 Sync from FB until drained or timeout
        4 Goto 2
        */
        val blockDatabase = BaseBlockDatabase(
                getEngine(), getEngine().getBlockQueries(), NODE_ID_READ_ONLY)
        historicSynchronizer = FastSynchronizer(historicWorkerContext, blockDatabase, FastSyncParameters())
        fastSynchronizer = FastSynchronizer(workerContext, blockDatabase, FastSyncParameters())

        thread(name = "historicSync-${workerContext.processName}") {
            copyBlocksLocally(blockDatabase)
            while (!shutdown.get()) {
                historicSynchronizer.syncUntilResponsiveNodesDrained()
                fastSynchronizer.syncUntilResponsiveNodesDrained()
            }
            done.countDown()
        }
    }

    private fun copyBlocksLocally(newBlockDatabase: BlockDatabase) {
        val newBlockQueries = workerContext.engine.getBlockQueries()
        val newBestHeight = newBlockQueries.getBestHeight().get()
        val newBestBlockHeader = newBlockQueries.getBlockAtHeight(newBestHeight, false).get()
        val historicBlockRid = historicBlockQueries.getBlockRid(newBestHeight).get()
        if (historicBlockRid == null) {
            return // We have no more blocks locally
        }
        if (historicBlockRid != newBestBlockHeader.header.blockRID) {
            throw ProgrammerMistake("Historic blockchain and fork chain disagree on block RID at height " +
                    "$newBestHeight. Historic: $historicBlockRid, fork: ${newBestBlockHeader.header.blockRID}")
        }
        // We have now checked that the fork so far matches the blocks in the historic blockchain.
        // Now let's start the copying process.
        while (!shutdown.get()) {
            try {
                val historicBlock = historicBlockQueries.getBlockAtHeight(newBestHeight + 1).get()
                newBlockDatabase.addBlock(historicBlock)
            } catch (e: UserMistake) {
                // historic block doesn't exist (or something else happened)
                // This means we have finished local sync.
                return
            }
        }
    }

    override fun shutdown() {
        shutdown.set(true)
        historicWorkerContext.shutdown()
        fastSynchronizer.shutdown()
        done.await()
        workerContext.shutdown()
    }
}