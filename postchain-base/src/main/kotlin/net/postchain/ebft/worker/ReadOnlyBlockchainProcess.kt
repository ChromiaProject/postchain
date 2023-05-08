// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.ebft.worker

import mu.KLogging
import net.postchain.concurrent.util.get
import net.postchain.core.NODE_ID_READ_ONLY
import net.postchain.core.block.BlockQueries
import net.postchain.core.framework.AbstractBlockchainProcess
import net.postchain.debug.DiagnosticData
import net.postchain.debug.DiagnosticProperty
import net.postchain.debug.DpNodeType
import net.postchain.debug.EagerDiagnosticValue
import net.postchain.debug.LazyDiagnosticValue
import net.postchain.ebft.BaseBlockDatabase
import net.postchain.ebft.rest.contract.StateNodeStatus
import net.postchain.ebft.syncmanager.common.FastSynchronizer
import net.postchain.ebft.syncmanager.common.KnownState
import net.postchain.ebft.syncmanager.common.SlowSynchronizer
import net.postchain.ebft.syncmanager.common.SyncMethod
import net.postchain.ebft.syncmanager.common.SyncParameters

class ReadOnlyBlockchainProcess(
        val workerContext: WorkerContext,
        val blockQueries: BlockQueries
) : AbstractBlockchainProcess("replica-${workerContext.processName}", workerContext.engine) {

    companion object : KLogging()

    private val myPubKey = workerContext.appConfig.pubKey

    private val blockDatabase = BaseBlockDatabase(
            blockchainEngine, blockchainEngine.getBlockQueries(), NODE_ID_READ_ONLY
    )

    private val params = SyncParameters.fromAppConfig(workerContext.appConfig)

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

    var blockHeight: Long = blockQueries.getLastBlockHeight().get()
        private set

    private var syncMethod = SyncMethod.NOT_SYNCING

    /**
     * For read only nodes we don't want to fast sync forever.
     * When the nodes are drained we move to slow sync instead.
     */
    override fun action() {
        if (params.slowSyncEnabled) {
            logger.debug { "Using slow sync for read only bc process" }
            syncMethod = SyncMethod.FAST_SYNC
            fastSynchronizer.syncUntilResponsiveNodesDrained()
            // Move to slow sync and proceed until shutdown
            syncMethod = SyncMethod.SLOW_SYNC
            slowSynchronizer.syncUntil()
            syncMethod = SyncMethod.NOT_SYNCING
        } else {
            logger.debug { "Using fast sync for read only bc process" }
            syncMethod = SyncMethod.FAST_SYNC
            fastSynchronizer.syncUntil { !isProcessRunning() }
            syncMethod = SyncMethod.NOT_SYNCING
        }
    }

    override fun cleanup() {
        blockDatabase.stop()
        workerContext.shutdown()
    }

    override fun registerDiagnosticData(diagnosticData: DiagnosticData) {
        super.registerDiagnosticData(diagnosticData)
        diagnosticData[DiagnosticProperty.BLOCKCHAIN_NODE_TYPE] = EagerDiagnosticValue(DpNodeType.NODE_TYPE_REPLICA.prettyName)
        diagnosticData[DiagnosticProperty.BLOCKCHAIN_NODE_STATUS] = LazyDiagnosticValue {
            StateNodeStatus(myPubKey, DpNodeType.NODE_TYPE_REPLICA.name, syncMethod.name, blockQueries.getLastBlockHeight().get())
        }
        diagnosticData[DiagnosticProperty.BLOCKCHAIN_NODE_PEERS_STATUSES] = LazyDiagnosticValue {
            val peerStates: List<Pair<String, KnownState>> = when (syncMethod) {
                SyncMethod.FAST_SYNC -> fastSynchronizer.peerStatuses.peersStates
                SyncMethod.SLOW_SYNC -> slowSynchronizer.peerStatuses.peersStates
                else -> emptyList()
            }
            peerStates.map { (pubKey, knownState) -> StateNodeStatus(pubKey, "PEER", knownState.state.name) }
        }
    }

    override fun isSigner() = false
}
