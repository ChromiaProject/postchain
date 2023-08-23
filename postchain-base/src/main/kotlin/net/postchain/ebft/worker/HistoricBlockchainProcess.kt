// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.ebft.worker

import mu.KLogging
import mu.withLoggingContext
import net.postchain.base.HistoricBlockchainContext
import net.postchain.base.data.BaseBlockStore
import net.postchain.base.data.DatabaseAccess
import net.postchain.base.withReadConnection
import net.postchain.common.BlockchainRid
import net.postchain.concurrent.util.get
import net.postchain.concurrent.util.whenCompleteUnwrapped
import net.postchain.core.BadBlockRIDAtHeightException
import net.postchain.core.BlockchainState
import net.postchain.core.EContext
import net.postchain.core.NODE_ID_READ_ONLY
import net.postchain.core.block.BlockDataWithWitness
import net.postchain.core.block.BlockTrace
import net.postchain.core.framework.AbstractBlockchainProcess
import net.postchain.debug.DiagnosticData
import net.postchain.debug.DiagnosticProperty
import net.postchain.debug.DpNodeType
import net.postchain.debug.EagerDiagnosticValue
import net.postchain.debug.LazyDiagnosticValue
import net.postchain.ebft.BaseBlockDatabase
import net.postchain.ebft.BlockDatabase
import net.postchain.ebft.rest.contract.StateNodeStatus
import net.postchain.ebft.syncmanager.common.FastSynchronizer
import net.postchain.ebft.syncmanager.common.KnownState
import net.postchain.ebft.syncmanager.common.SyncMethod
import net.postchain.ebft.syncmanager.common.SyncParameters
import net.postchain.logging.BLOCKCHAIN_RID_TAG
import net.postchain.logging.CHAIN_IID_TAG
import java.lang.Thread.sleep
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Worker that synchronizes a blockchain using blocks from another blockchain, historicBrid.
 * The idea with this worker is to be able to fork a blockchain reliably.
 * OB = original blockchain (historic Brid)
 * FB = forked blockchain
 *
 * 1 Sync from local-OB (if available) until drained
 * 2 Sync from remote-FB until drained or timeout
 * 3 Sync from remote-OB until drained or timeout
 * 4 Goto 1
 */
class HistoricBlockchainProcess(val workerContext: WorkerContext,
                                private val historicBlockchainContext: HistoricBlockchainContext
) : AbstractBlockchainProcess("historic-c${workerContext.blockchainConfiguration.chainID}", workerContext.engine) {

    companion object : KLogging()

    private val AWAIT_PROMISE_MS = 5L // The amount of millis we wait before we check if the add block promise has been completed

    private var historicSynchronizer: FastSynchronizer? = null
    private val blockBuilderStorage = workerContext.engine.blockBuilderStorage
    private val loggingContext = mapOf(
            CHAIN_IID_TAG to workerContext.blockchainConfiguration.chainID.toString(),
            BLOCKCHAIN_RID_TAG to workerContext.blockchainConfiguration.blockchainRid.toHex()
    )
    private val blockDatabase = BaseBlockDatabase(
            loggingContext, blockchainEngine, blockchainEngine.getBlockQueries(), NODE_ID_READ_ONLY
    )
    private val fastSynchronizer = FastSynchronizer(
            workerContext, blockDatabase,
            SyncParameters.fromAppConfig(workerContext.appConfig),
            ::isProcessRunning
    )

    private var syncMethod = SyncMethod.NOT_SYNCING
    private var isSyncingHistoric = false
    private val myPubKey = workerContext.appConfig.pubKey

    // Logging only
    var blockTrace: BlockTrace? = null // For logging only
    val myBRID = blockchainEngine.getConfiguration().blockchainRid

    override fun action() {
        withLoggingContext(loggingContext) {
            val chainsToSyncFrom = historicBlockchainContext.getChainsToSyncFrom(myBRID)

            val lastHeightSoFar = blockchainEngine.getBlockQueries().getLastBlockHeight().get()
            initDebug("Historic sync bc ${myBRID}, height: $lastHeightSoFar")

            // try local sync first
            for (brid in chainsToSyncFrom) {
                if (!isProcessRunning()) break
                if (brid == myBRID) continue
                syncMethod = SyncMethod.LOCAL_DB
                copyBlocksLocally(brid, blockDatabase)
            }

            // try syncing via network
            for (brid in chainsToSyncFrom) {
                if (!isProcessRunning()) break
                syncMethod = SyncMethod.FAST_SYNC
                copyBlocksNetwork(brid, myBRID, blockDatabase, fastSynchronizer.params)
                if (!isProcessRunning()) break // Check before sleep
                initTrace("Network synch go sleep")
                sleep(1000)
                initTrace("Network synch waking up")
            }
            syncMethod = SyncMethod.NOT_SYNCING
            if (!isProcessRunning()) return // Check before sleep
            initTrace("Network synch full go sleep")
            sleep(1000)
            initTrace("Network synch full waking up")
        }
    }

    /**
     * When network sync begins our starting point is the height where we left off
     * after copying blocks from our local DB.
     */
    private fun copyBlocksNetwork(
            brid: BlockchainRid, // the BC we are trying to pull blocks from
            myBRID: BlockchainRid, // our BC
            blockDatabase: BlockDatabase,
            params: SyncParameters) {

        if (brid == myBRID) {
            netDebug("Try network sync using own BRID")
            // using our own BRID
            workerContext.communicationManager.init()
            fastSynchronizer.syncUntilResponsiveNodesDrained()
            workerContext.communicationManager.shutdown()
        } else {
            val localChainID = getLocalChainId(brid)
            if (localChainID == null) {
                // we ONLY try syncing over network iff chain is not locally present
                // Reason for this is a bit complicated:
                // (Alex:) "BRID is in DB thus we avoid this" is very simple rule.
                netDebug("Try network sync using historic BRID since chainId $localChainID is new")
                try {
                    val historicWorkerContext = historicBlockchainContext.contextCreator(brid)
                    historicSynchronizer = FastSynchronizer(historicWorkerContext, blockDatabase, params, ::isProcessRunning)
                    isSyncingHistoric = true
                    historicSynchronizer!!.syncUntilResponsiveNodesDrained()
                    isSyncingHistoric = false
                    netDebug("Done network sync")
                    historicWorkerContext.communicationManager.shutdown()
                } catch (e: Exception) {
                    netErr("Exception while attempting remote sync", e)
                }
            }
        }
    }

    private fun getBlockFromStore(blockStore: BaseBlockStore, ctx: EContext, height: Long): BlockDataWithWitness? {
        val blockchainConfiguration = blockchainEngine.getConfiguration()
        val blockRID = blockStore.getBlockRID(ctx, height)
        if (blockRID == null) {
            return null
        } else {
            val headerBytes = blockStore.getBlockHeader(ctx, blockRID)
            val witnessBytes = blockStore.getWitnessData(ctx, blockRID)
            val txBytes = blockStore.getBlockTransactions(ctx, blockRID)
            // note: We are decoding header of other blockchain using our configuration.
            // We believe we have a right to do that because it should be sufficiently compatible
            val header = blockchainConfiguration.decodeBlockHeader(headerBytes)
            val witness = blockchainConfiguration.decodeWitness(witnessBytes)

            return BlockDataWithWitness(header, txBytes, witness)
        }
    }

    private fun getLocalChainId(brid: BlockchainRid): Long? {
        return withReadConnection(blockBuilderStorage, -1) {
            DatabaseAccess.of(it).getChainId(it, brid)
        }
    }

    /**
     * The fastest way is always to get the blocks we need from the local database, if we have them.
     *
     * NOTE: the tricky part is to handle shutdown of the blockchains without deadlock.
     *
     * @param brid is the BC RID we are trying to copy from. Very likely we don't have it, and then we just exit
     * @param newBlockDatabase
     */
    private fun copyBlocksLocally(brid: BlockchainRid, newBlockDatabase: BlockDatabase) {
        val localChainID = getLocalChainId(brid)
        if (localChainID == null) return // Don't have the chain, can't sync locally
        copyInfo("Begin cross syncing locally", -1)

        val fromCtx = blockBuilderStorage.openReadConnection(localChainID)
        val fromBstore = BaseBlockStore()
        var heightToCopy: Long = -2L
        var lastHeight: Long = -2L
        try {
            lastHeight = fromBstore.getLastBlockHeight(fromCtx)
            if (lastHeight == -1L) return // no block = nothing to do
            val ourHeight = blockchainEngine.getBlockQueries().getLastBlockHeight().get()
            if (lastHeight > ourHeight) {
                if (ourHeight > -1L) {
                    // Just a verification of Block RID being the same
                    verifyBlockAtHeightIsTheSame(fromBstore, fromCtx, ourHeight)
                }
                heightToCopy = ourHeight + 1
                var pendingFuture: CompletableFuture<Unit>? = null
                val readMoreBlocks = AtomicBoolean(true)
                while (isProcessRunning() && readMoreBlocks.get()) {
                    if (newBlockDatabase.getQueuedBlockCount() > 3) {
                        sleep(1)
                        continue
                    }
                    val historicBlock = getBlockFromStore(fromBstore, fromCtx, heightToCopy)
                    if (historicBlock == null) {
                        copyInfo("Done cross syncing height", heightToCopy)
                        break
                    } else {
                        val bTrace: BlockTrace? = getCopyBTrace(heightToCopy)
                        if (isProcessRunning() && readMoreBlocks.get()) {
                            pendingFuture = newBlockDatabase.addBlock(historicBlock, pendingFuture, bTrace)
                            val myHeightToCopy = heightToCopy
                            pendingFuture.whenCompleteUnwrapped(loggingContext) { _: Any?, exception ->
                                if (exception == null) {
                                    copyTrace("Successfully added", bTrace, myHeightToCopy) // Now we should have the block RID in the debug
                                } else {
                                    copyErr("Failed to add", myHeightToCopy, exception)
                                    readMoreBlocks.set(false)
                                }
                            }
                            copyLog("Got promise to add", heightToCopy)
                            heightToCopy += 1
                        }
                    }
                }

                if (pendingFuture != null) awaitFuture(pendingFuture, heightToCopy) // wait pending block
                copyLog("End at height", heightToCopy)
            }
        } finally {
            copyInfo("Shutdown cross syncing, lastHeight: $lastHeight", heightToCopy)
            blockBuilderStorage.closeReadConnection(fromCtx)
        }
    }

    /**
     * Cannot use blocking wait (i.e. Promise.get()) b/c the blockchain might shut down in the meantime
     * and this causes a deadlock.
     *
     * @return "true" if we got something from the promise, "false" if we have a shutdown
     */
    private fun awaitFuture(pendingFuture: CompletableFuture<Unit>, height: Long): Boolean {
        while (isProcessRunning()) {
            if (pendingFuture.isDone) {
                awaitTrace("done", height)
                return true
            } else {
                awaitTrace("sleep", height)
                sleep(AWAIT_PROMISE_MS)
                awaitTrace("wake up", height)
            }
        }
        awaitDebug("BC is shutting down", height)
        return false
    }

    /**
     * We don't want to proceed if our last block isn't the same as the one in the historic chain.
     *
     * NOTE: Here we are actually comparing Block RIDs but using the [BlockchainRid] type for convenience.
     * TODO: Should we have a BlockRID type?
     */
    private fun verifyBlockAtHeightIsTheSame(fromBstore: BaseBlockStore, fromCtx: EContext, ourHeight: Long) {
        val historictBlockRID = BlockchainRid(fromBstore.getBlockRID(fromCtx, ourHeight)!!)
        val ourLastBlockRID = BlockchainRid(blockchainEngine.getBlockQueries().getBlockRid(ourHeight).get()!!)
        if (historictBlockRID != ourLastBlockRID) {
            throw BadBlockRIDAtHeightException("Historic blockchain and fork chain disagree on block RID at height" +
                    "${ourHeight}. Historic: $historictBlockRID, fork: $ourLastBlockRID")
        }
    }

    override fun cleanup() {
        withLoggingContext(loggingContext) {
            shutdownDebug("Historic worker shutting down")
            blockDatabase.stop()
            workerContext.shutdown()
            shutdownDebug("Shutdown finished")
        }
    }

    // ----------------------------------------------
    // To cut down on boilerplate logging in code
    // ----------------------------------------------

    // init
    private fun initDebug(str: String) {
        if (logger.isDebugEnabled) {
            logger.debug("init() -- $str")
        }
    }

    private fun initTrace(str: String) {
        if (logger.isTraceEnabled) {
            logger.trace("init() --- $str")
        }
    }

    //copyBlocksNetwork()
    private fun netTrace(str: String, heightToCopy: Long) {
        if (logger.isTraceEnabled) {
            logger.trace("copyBlocksNetwork() -- $str: $heightToCopy from blockchain ${historicBlockchainContext.historicBrid}")
        }
    }

    private fun netDebug(str: String) {
        if (logger.isDebugEnabled) {
            logger.debug("copyBlocksNetwork() -- $str from blockchain ${historicBlockchainContext.historicBrid}")
        }
    }

    private fun netInfo(str: String, heightToCopy: Long) {
        if (logger.isInfoEnabled) {
            logger.info("copyBlocksNetwork() - $str: $heightToCopy from blockchain ${historicBlockchainContext.historicBrid}")
        }
    }

    private fun netErr(str: String, e: Exception) {
        logger.error("copyBlocksNetwork() - $str, from blockchain ${historicBlockchainContext.historicBrid}", e)
    }


    // copyBlocksLocally()
    private fun copyTrace(str: String, bTrace: BlockTrace?, heightToCopy: Long) {
        if (logger.isTraceEnabled) {
            logger.trace("copyBlocksLocally() -- $str: $heightToCopy ,RID: ${bTrace!!.blockRid}, " +
                    "locally from blockchain ${historicBlockchainContext.historicBrid}")
        }
    }

    private fun copyLog(str: String, heightToCopy: Long) {
        if (logger.isDebugEnabled) {
            logger.debug("copyBlocksLocally() -- $str: $heightToCopy locally from blockchain ${historicBlockchainContext.historicBrid}")
        }
    }

    private fun copyInfo(str: String, heightToCopy: Long) {
        if (logger.isInfoEnabled) {
            logger.info("copyBlocksLocally() - $str: $heightToCopy locally from blockchain ${historicBlockchainContext.historicBrid}")
        }
    }

    private fun copyErr(str: String, heightToCopy: Long, e: Throwable) {
        logger.error("copyBlocksLocally() - $str: $heightToCopy locally from blockchain ${historicBlockchainContext.historicBrid}", e)
    }

    private fun copyErr(str: String, heightToCopy: Long, err: String) {
        logger.error("copyBlocksLocally() - $str: $heightToCopy locally from blockchain ${historicBlockchainContext.historicBrid}, err: $err")
    }

    private fun getCopyBTrace(heightToCopy: Long): BlockTrace? {
        return if (logger.isTraceEnabled) {
            logger.trace { "getCopyBTrace() - Creating block trace with height: $heightToCopy " }

            this.blockTrace = BlockTrace.buildBeforeBlock(heightToCopy) // At this point we don't have the Block RID.
            this.blockTrace
        } else {
            null // Use null for speed
        }
    }

    // AwaitPromise
    private fun awaitTrace(str: String, height: Long) {
        if (logger.isTraceEnabled) {
            logger.trace("awaitPromise() -- height: $height, $str")
        }
    }

    private fun awaitDebug(str: String, height: Long) {
        if (logger.isDebugEnabled) {
            logger.debug("awaitPromise() -- height: $height, $str")
        }
    }

    private fun shutdownDebug(str: String) {
        if (logger.isDebugEnabled) {
            logger.debug("shutdown() - $str, at height: ${this.fastSynchronizer.blockHeight}, the block that's causing the shutdown: $blockTrace")
        }
    }

    override fun registerDiagnosticData(diagnosticData: DiagnosticData) {
        super.registerDiagnosticData(diagnosticData)
        diagnosticData[DiagnosticProperty.BLOCKCHAIN_NODE_TYPE] = EagerDiagnosticValue(DpNodeType.NODE_TYPE_HISTORIC_REPLICA.prettyName)
        diagnosticData[DiagnosticProperty.BLOCKCHAIN_NODE_STATUS] = LazyDiagnosticValue {
            StateNodeStatus(
                    myPubKey,
                    DpNodeType.NODE_TYPE_HISTORIC_REPLICA.name,
                    syncMethod.name,
                    blockchainEngine.getBlockQueries().getLastBlockHeight().get())
        }
        diagnosticData[DiagnosticProperty.BLOCKCHAIN_NODE_PEERS_STATUSES] = LazyDiagnosticValue {
            val peerStates: List<Pair<String, KnownState>> = when (syncMethod) {
                SyncMethod.FAST_SYNC ->
                    if (isSyncingHistoric)
                        historicSynchronizer?.peerStatuses?.peersStates ?: emptyList()
                    else
                        fastSynchronizer.peerStatuses.peersStates

                else -> emptyList()
            }
            peerStates.map { (pubKey, knownState) -> StateNodeStatus(pubKey, "PEER", knownState.state.name) }
        }
    }

    override fun isSigner() = false
    override fun getBlockchainState(): BlockchainState = BlockchainState.RUNNING
}
