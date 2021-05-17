// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.ebft.worker

import mu.KLogging
import net.postchain.base.BaseBlockchainEngine
import net.postchain.base.BlockchainRid
import net.postchain.base.HistoricBlockchainContext
import net.postchain.base.data.BaseBlockStore
import net.postchain.base.data.DatabaseAccess
import net.postchain.base.withReadConnection
import net.postchain.core.*
import net.postchain.debug.BlockTrace
import net.postchain.debug.BlockchainProcessName
import net.postchain.ebft.BaseBlockDatabase
import net.postchain.ebft.BlockDatabase
import net.postchain.ebft.syncmanager.common.FastSyncParameters
import net.postchain.ebft.syncmanager.common.FastSynchronizer
import nl.komponents.kovenant.Promise
import net.postchain.config.node.NodeConfig
import java.lang.IllegalStateException
import java.lang.Thread.sleep
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

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
 *
 */
class HistoricChainWorker(val workerContext: WorkerContext,
                          val historicBlockchainContext: HistoricBlockchainContext) : BlockchainProcess {

    override fun getEngine() = workerContext.engine

    private val fastSynchronizer: FastSynchronizer
    private var historicSynchronizer: FastSynchronizer? = null
    private val done = CountDownLatch(1)
    private val shutdown = AtomicBoolean(false)
    private val storage = (workerContext.engine as BaseBlockchainEngine).storage

    // Logging only
    var blockTrace: BlockTrace? = null // For logging only
    val myNodeConf: NodeConfig // For logging only
    val myBRID: BlockchainRid // For logging only
    val procName: BlockchainProcessName // For logging only

    companion object : KLogging()

    init {
        val engine = getEngine()
        myBRID = engine.getConfiguration().blockchainRid
        val blockDatabase = BaseBlockDatabase(
                engine, engine.getBlockQueries(), NODE_ID_READ_ONLY)
        val params = FastSyncParameters()
        myNodeConf = workerContext.nodeConfig
        procName = BlockchainProcessName(myNodeConf.pubKey, myBRID)

        params.exitDelay = myNodeConf.fastSyncExitDelay
        params.jobTimeout = myNodeConf.fastSyncJobTimeout
        fastSynchronizer = FastSynchronizer(workerContext, blockDatabase, params)

        thread(name = "historicSync-${workerContext.processName}") {
            try {
                val chainsToSyncFrom = mutableListOf(
                        myBRID,
                        historicBlockchainContext.historicBrid
                )
                chainsToSyncFrom.addAll(historicBlockchainContext.aliases.keys)

                while (!shutdown.get()) {
                    val bestHeightSoFar = engine.getBlockQueries().getBestHeight().get()
                    initDebug("Historic sync bc ${myBRID}, height: ${bestHeightSoFar}")

                    // try local sync first
                    for (brid in chainsToSyncFrom) {
                        if (shutdown.get()) break
                        if (brid == myBRID) continue
                        copyBlocksLocally(brid, blockDatabase)
                    }

                    // try syncing via network
                    for (brid in chainsToSyncFrom) {
                        if (shutdown.get()) break
                        copyBlocksNetwork(brid, myBRID, blockDatabase, params)
                        if (shutdown.get()) break // Check before sleep
                        initTrace("Network synch go sleep")
                        sleep(1000)
                        initTrace("Network synch waking up")
                    }
                    if (shutdown.get()) break // Check before sleep
                    initTrace("Network synch full go sleep")
                    sleep(1000)
                    initTrace("Network synch full waking up")
                }
            } catch (e: Exception) {
                logger.error(e) { "$procName Error syncing forkchain" }
            } finally {
                try {
                    blockDatabase.stop()
                } catch (e: Exception) {
                    logger.error(e) { "$procName Error syncing forkchain - stopping db" }
                } finally {
                    initTrace("Sync done, countdown latches")
                    done.countDown()
                }
            }
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
            params: FastSyncParameters) {

        if (brid == myBRID) {
            netDebug("try network sync using own BRID")
            // using our own BRID
            workerContext.communicationManager.init()
            fastSynchronizer.syncUntilResponsiveNodesDrained()
            workerContext.communicationManager.shutdown()
        } else {
            val localChainID = getLocalChainId(brid)
            if (localChainID == null) {
                // we ONLY try syncing over network iff chain is not locally present
                // Reason for this is a bit complicated:
                //
                // (Excerpt from Alex discussion)
                // TODO: When chain2 is started it might fail because 0x02 is already associated in conman to chain3.
                //
                // So two ways to deal with this:
                // 1. eliminate race using mutexes and such
                // 2. create a new flag to make connection pre-emptible, so e.g.
                // chain3 can connect but if it's pre-emptible conman can disconnect it once chain2 can connect. we also need to make sure that fastsynchronizer understands disconnects.
                //
                //TL;DR: we can make it nicer at expense of higher complexity. "BRID is in DB thus we avoid this" is very simple rule.
                // Option 3: teach procman to distinguish restart from shutdown, e.g. keep a set of chainID which are "potentially will be launched soon".
                //Then we can ask procman if it's something to be concerned about.

                logger.debug("$procName Historic sync: try network sync using historic BRID since chainId $localChainID is new" )
                try {
                    val historicWorkerContext = historicBlockchainContext.contextCreator(brid)
                    historicSynchronizer = FastSynchronizer(historicWorkerContext, blockDatabase, params)
                    historicSynchronizer!!.syncUntilResponsiveNodesDrained()
                    historicWorkerContext.communicationManager.shutdown()
                } catch (e: Exception) {
                    logger.error(e) { "Exception while attempting remote sync" }
                }
            }
        }
    }

    private fun getBlockFromStore(blockStore: BaseBlockStore, ctx: EContext, height: Long): BlockDataWithWitness? {
        val blockchainConfiguration = getEngine().getConfiguration()
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
        return withReadConnection(storage, -1) {
            DatabaseAccess.of(it).getChainId(it, brid)
        }
    }

    /**
     * The fastest way is always to get the blocks we need from the local database, if we have them.
     *
     * NOTE: the tricky part is to handle shutdown of the blockchains without deadlock.
     */
    private fun copyBlocksLocally(brid: BlockchainRid, newBlockDatabase: BlockDatabase) {
        val localChainID = getLocalChainId(brid)
        if (localChainID == null) return // can't sync locally
        copyInfo("Begin cross syncing locally", -1)

        val fromCtx = storage.openReadConnection(localChainID)
        val fromBstore = BaseBlockStore()
        var heightToCopy: Long = -2L
        var lastHeight: Long = -2L
        try {
            lastHeight = fromBstore.getLastBlockHeight(fromCtx)
            if (lastHeight == -1L) return // no block = nothing to do
            val ourHeight = getEngine().getBlockQueries().getBestHeight().get()
            if (lastHeight > ourHeight) {
                if (ourHeight > -1L) {
                    // Just a verification of Block RID being the same
                    verifyBlockAtHeightIsTheSame(fromBstore, fromCtx, ourHeight)
                }
                heightToCopy = ourHeight + 1
                var pendingPromise: Promise<Unit, java.lang.Exception>? = null
                var readMoreBlocks = true
                while (!shutdown.get() && readMoreBlocks) {
                    readMoreBlocks = false
                    val historicBlock = getBlockFromStore(fromBstore, fromCtx, heightToCopy)
                    if (historicBlock == null) {
                        copyInfo("Done cross syncing height", heightToCopy)
                    } else {
                        // DEBUG
                        var bbDebug: BlockTrace? = if (logger.isTraceEnabled) {
                            this.blockTrace = BlockTrace.buildBeforeBlock(procName, heightToCopy) // At this point we don't have the Block RID.
                            copyLog("Got block height", heightToCopy)
                            this.blockTrace
                        } else {
                            null // Use null for speed
                        }

                        // ADD BLOCK
                        if (!shutdown.get()) {
                            pendingPromise = newBlockDatabase.addBlock(historicBlock, bbDebug)
                            if (pendingPromise != null) {
                                copyLog("Got promise to add", heightToCopy)
                                try {
                                    if (awaitPromise(pendingPromise, heightToCopy)) {
                                        if (pendingPromise.isSuccess()) {
                                            if (logger.isTraceEnabled) { // To avoid NPE when looking at the bbDebug object
                                                copyTrace("Successfully added RID: ${bbDebug!!.blockRid} ", heightToCopy) // Now we should have the block RID in the debug
                                            }
                                            heightToCopy += 1
                                            readMoreBlocks = true // this went well, let's continue
                                        } else if (pendingPromise.isFailure()) {
                                            copyErr("Failed to add", heightToCopy, pendingPromise.getError())
                                            logger.error("copyBlocksLocally() -- : $heightToCopy to DB (blockchain ${historicBlockchainContext.historicBrid}). err:  ")
                                        } else {
                                            throw IllegalStateException("copyBlocksLocally() -- The promise is \"done\" why don't we have a result? $heightToCopy to DB (blockchain ${historicBlockchainContext.historicBrid}).")
                                        }
                                    }
                                } catch (e: java.lang.Exception) {
                                    logger.error("copyBlocksLocally() - Exception caught: Failed to add: $heightToCopy to DB (blockchain ${historicBlockchainContext.historicBrid}). err: ${pendingPromise.getError()}.", e)
                                } finally {
                                    pendingPromise = null
                                }
                            } else {
                                logger.warn("copyBlocksLocally() -- Didn't get a promise to add: $heightToCopy to DB (blockchain ${historicBlockchainContext.historicBrid}).")
                            }
                        }
                    }
                }

                if (pendingPromise != null) awaitPromise(pendingPromise, heightToCopy) // wait pending block
                copyLog("End at height", heightToCopy)
            }

        } finally {
            copyInfo("Shutdown cross syncing, lastHeight: $lastHeight", heightToCopy)
            storage.closeReadConnection(fromCtx)
        }
    }

    /**
     * Cannot use blocking wait (i.e. Promise.get()) b/c the blockchain might shut down in the meantime
     * and this causes a deadlock.
     *
     * @return "true" if we got something from the promise, "false" if we have a shutdown
     */
    private fun awaitPromise(pendingPromise: Promise<Unit, java.lang.Exception>, height: Long): Boolean {
        while (!shutdown.get()) {
            if (pendingPromise.isDone()) {
                awaitDebug("done", height)
                return true
            } else {
                awaitDebug("sleep", height)
                sleep(100)
                awaitDebug("wake up", height)
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
        val ourLastBlockRID = BlockchainRid(getEngine().getBlockQueries().getBlockRid(ourHeight).get()!!)
        if (historictBlockRID != ourLastBlockRID) {
            throw BadDataMistake(BadDataType.OTHER,
                    "Historic blockchain and fork chain disagree on block RID at height" +
                            "${ourHeight}. Historic: $historictBlockRID, fork: ${ourLastBlockRID}")
        }
    }

    override fun shutdown() {
        if (shutdown.get()) {
            shutdownDebug("Historic worker already shutting ")
        }
        shutdownDebug("Historic worker shutting down")
        shutdown.set(true)
        historicSynchronizer?.shutdown()
        fastSynchronizer.shutdown()
        shutdownDebug("Wait for complete shutdown")
        done.await()
        shutdownDebug("Shutdown finished")
        workerContext.shutdown()
    }

    // ----------------------------------------------
    // To cut down on boilerplate logging in code
    // ----------------------------------------------

    // init
    private fun initDebug(str: String)  {
        if (logger.isDebugEnabled) {
            logger.debug("$procName init() -- $str")
        }
    }
    private fun initTrace(str: String)  {
        if (logger.isTraceEnabled) {
            logger.trace("$procName init() --- $str")
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
    private fun netDebug(str: String, heightToCopy: Long) {
        if (logger.isDebugEnabled) {
            logger.debug("copyBlocksNetwork() -- $str: $heightToCopy from blockchain ${historicBlockchainContext.historicBrid}")
        }
    }
    private fun netInfo(str: String, heightToCopy: Long) {
        if (logger.isInfoEnabled) {
            logger.info("copyBlocksNetwork() - $str: $heightToCopy from blockchain ${historicBlockchainContext.historicBrid}")
        }
    }
    private fun netErr(str: String, heightToCopy: Long, e: Exception) {
        logger.error("copyBlocksNetwork() - $str: $heightToCopy from blockchain ${historicBlockchainContext.historicBrid}", e)
    }
    private fun netErr(str: String, heightToCopy: Long, err: String) {
        logger.error("copyBlocksNetwork() - $str: $heightToCopy locally from blockchain ${historicBlockchainContext.historicBrid}, err: $err")
    }


    // copyBlocksLocally()
    private fun copyTrace(str: String, heightToCopy: Long) {
        if (logger.isTraceEnabled) {
            logger.trace("copyBlocksLocally() -- $str: $heightToCopy locally from blockchain ${historicBlockchainContext.historicBrid}")
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
    private fun copyErr(str: String, heightToCopy: Long, e: Exception) {
        logger.error("copyBlocksLocally() - $str: $heightToCopy locally from blockchain ${historicBlockchainContext.historicBrid}", e)
    }
    private fun copyErr(str: String, heightToCopy: Long, err: String) {
        logger.error("copyBlocksLocally() - $str: $heightToCopy locally from blockchain ${historicBlockchainContext.historicBrid}, err: $err")
    }

    // AwaitPromise
    private fun awaitDebug(str: String, height: Long) {
        if (logger.isDebugEnabled) {
            logger.debug("awaitPromise() - height: $height, $str")
        }
    }

    private fun shutdownDebug(str: String) {
        if (logger.isDebugEnabled) {
            logger.debug("shutdown() - $str, at height: ${this.fastSynchronizer.blockHeight}, the block that's causing the shutdown: $blockTrace")
        }
    }

}