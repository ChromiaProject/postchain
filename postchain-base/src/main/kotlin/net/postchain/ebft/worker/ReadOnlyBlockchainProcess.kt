// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.ebft.worker

import mu.KLogging
import net.postchain.core.framework.AbstractBlockchainProcess
import net.postchain.ebft.BaseBlockDatabase
import net.postchain.ebft.syncmanager.common.FastSyncParameters
import net.postchain.ebft.syncmanager.common.FastSynchronizer
import net.postchain.core.NODE_ID_READ_ONLY
import net.postchain.core.block.BlockQueries
import net.postchain.debug.DiagnosticProperty
import net.postchain.debug.DpNodeType
import net.postchain.ebft.syncmanager.common.FastSyncPeerStatuses
import net.postchain.ebft.syncmanager.common.SlowSynchronizer

class ReadOnlyBlockchainProcess(
    val workerContext: WorkerContext,
    val blockQueries: BlockQueries
) : AbstractBlockchainProcess("replica-${workerContext.processName}", workerContext.engine) {

    companion object : KLogging()

    private val configuredPeers = workerContext.peerCommConfiguration.networkNodes.getPeerIds()

    private val blockDatabase = BaseBlockDatabase(
        blockchainEngine, blockchainEngine.getBlockQueries(), NODE_ID_READ_ONLY
    )

    private val params = FastSyncParameters.fromAppConfig(workerContext.appConfig)

    private val fastSynchronizer = FastSynchronizer(
        workerContext,
        blockDatabase,
        params,
        ::isProcessRunning
    )

    private val slowSynchronizer = SlowSynchronizer(
        workerContext,
        blockDatabase,
        params,
        ::isProcessRunning
    )

    private val peerStatuses = FastSyncPeerStatuses(params)

    var blockHeight: Long = blockQueries.getBestHeight().get()
        private set

    /**
     * For read only nodes we don't want to fast sync forever.
     * When the nodes are drained we move to slow sync instead.
     */
    override fun action() {
        val exitDelay = 1000
        val timeout = System.currentTimeMillis() + exitDelay

        if (logger.isDebugEnabled) {
            logger.debug("Read only fastsync: begin with exitDelay: $exitDelay")
        }

        fastSynchronizer.syncUntil {
            val syncableCount = peerStatuses.getSyncable(blockHeight + 1).intersect(configuredPeers).size

            // Keep syncing until this becomes true, i.e. to exit we must have:
            val done = timeout < System.currentTimeMillis()      // 1. must have timeout
                    && syncableCount == 0                        // 2. must have no syncable nodes
                    && blockHeight >= params.mustSyncUntilHeight // 3. must BC height above the minimum specified height

            if (logger.isDebugEnabled && done) {
                logger.debug("We are done fastsyncing. Syncable count: $syncableCount, height: $blockHeight, must sync until: ${params.mustSyncUntilHeight}.")
            }
            !isProcessRunning() || done
        }

        // Move to slow sync and proceed until shutdown
        slowSynchronizer.syncUntil()
    }

    override fun cleanup() {
        blockDatabase.stop()
        workerContext.shutdown()
    }

    override fun registerDiagnosticData(diagnosticData: MutableMap<DiagnosticProperty, () -> Any>) {
        diagnosticData.putAll(mapOf(
                DiagnosticProperty.BLOCKCHAIN_RID to { workerContext.blockchainConfiguration.blockchainRid.toHex() },
                DiagnosticProperty.BLOCKCHAIN_NODE_TYPE to { DpNodeType.NODE_TYPE_REPLICA.prettyName },
                DiagnosticProperty.BLOCKCHAIN_CURRENT_HEIGHT to { fastSynchronizer.blockHeight }
        ))
    }

}
