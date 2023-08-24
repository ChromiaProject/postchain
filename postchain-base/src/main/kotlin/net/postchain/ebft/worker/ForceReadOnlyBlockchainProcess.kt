package net.postchain.ebft.worker

import mu.withLoggingContext
import net.postchain.core.BlockchainState
import net.postchain.core.framework.AbstractBlockchainProcess
import net.postchain.debug.DiagnosticData
import net.postchain.debug.DiagnosticProperty
import net.postchain.debug.DpNodeType
import net.postchain.debug.EagerDiagnosticValue
import net.postchain.ebft.syncmanager.readonly.ForceReadOnlyMessageProcessor
import net.postchain.logging.BLOCKCHAIN_RID_TAG
import net.postchain.logging.CHAIN_IID_TAG
import kotlin.collections.mapOf
import kotlin.collections.set

class ForceReadOnlyBlockchainProcess(
        private val workerContext: WorkerContext,
        private val blockchainState: BlockchainState
) : AbstractBlockchainProcess("force-readonly-c${workerContext.blockchainConfiguration.chainID}", workerContext.engine) {

    private val loggingContext = mapOf(
            CHAIN_IID_TAG to workerContext.blockchainConfiguration.chainID.toString(),
            BLOCKCHAIN_RID_TAG to workerContext.blockchainConfiguration.blockchainRid.toHex()
    )

    private val forceReadOnlyMessageProcessor = ForceReadOnlyMessageProcessor(workerContext.engine.getBlockQueries(), workerContext.communicationManager)

    override fun action() {
        withLoggingContext(loggingContext) {
            forceReadOnlyMessageProcessor.processMessages()
            Thread.sleep(100)
        }
    }

    override fun cleanup() {
        withLoggingContext(loggingContext) {
            workerContext.shutdown()
        }
    }

    override fun isSigner(): Boolean = false

    override fun getBlockchainState(): BlockchainState = blockchainState

    override fun registerDiagnosticData(diagnosticData: DiagnosticData) {
        super.registerDiagnosticData(diagnosticData)
        diagnosticData[DiagnosticProperty.BLOCKCHAIN_NODE_TYPE] = EagerDiagnosticValue(DpNodeType.NODE_TYPE_FORCE_READ_ONLY.prettyName)
    }
}