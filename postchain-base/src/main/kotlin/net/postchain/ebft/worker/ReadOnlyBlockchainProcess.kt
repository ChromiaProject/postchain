// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.ebft.worker

import mu.KLogging
import net.postchain.core.framework.AbstractBlockchainProcess
import net.postchain.ebft.BaseBlockDatabase
import net.postchain.ebft.syncmanager.common.FastSyncParameters
import net.postchain.ebft.syncmanager.common.FastSynchronizer
import net.postchain.core.NODE_ID_READ_ONLY
import net.postchain.debug.DiagnosticProperty
import net.postchain.debug.DpNodeType

class ReadOnlyBlockchainProcess(val workerContext: WorkerContext) : AbstractBlockchainProcess("replica-${workerContext.processName}", workerContext.engine) {

    companion object : KLogging()

    private val blockDatabase = BaseBlockDatabase(
            blockchainEngine, blockchainEngine.getBlockQueries(), NODE_ID_READ_ONLY)

    private val fastSynchronizer = FastSynchronizer(
            workerContext,
            blockDatabase,
            FastSyncParameters.fromAppConfig(workerContext.appConfig),
            ::isProcessRunning
    )

    override fun action() {
        fastSynchronizer.syncUntil { !isProcessRunning() }
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
