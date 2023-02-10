package net.postchain.core.framework

import mu.NamedKLogging
import net.postchain.concurrent.util.get
import net.postchain.core.BlockchainEngine
import net.postchain.core.BlockchainProcess
import net.postchain.debug.DiagnosticProperty
import net.postchain.debug.DiagnosticValueMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

abstract class AbstractBlockchainProcess(private val processName: String, override val blockchainEngine: BlockchainEngine) : BlockchainProcess {

    val logger = NamedKLogging(this::class.java.simpleName).logger

    private val running = AtomicBoolean(false)
    internal lateinit var process: Thread

    fun isProcessRunning() = running.get()

    final override fun start() {
        running.set(true)
        process = thread(name = processName) { main() }
    }

    private fun main() {
        try {
            while (isProcessRunning()) {
                action()
            }
        } catch (e: Exception) {
            logger.error(e) { "Process $processName stopped unexpectedly" }
            running.set(false)
        } finally {
            try {
                logger.debug { "Cleanup up resources" }
                cleanup()
            } catch (e: Exception) {
                logger.error { "Failed to free resources on shutdown!" }
            }
        }
    }

    /**
     * Action to be performed repeatedly on a separate thread
     */
    protected abstract fun action()

    /**
     * Clean up any resources and connections used by this process
     */
    protected abstract fun cleanup()

    final override fun shutdown() {
        running.set(false)
        if (alreadyShutdown()) return
        logger.debug { "Shutting down process $processName" }
        process.join()
    }

    private fun alreadyShutdown() = !::process.isInitialized || !process.isAlive

    override fun registerDiagnosticData(diagnosticData: DiagnosticValueMap) {
        diagnosticData.add(DiagnosticProperty.BLOCKCHAIN_RID withLazyValue { blockchainEngine.getConfiguration().blockchainRid.toHex() })
        diagnosticData.add(DiagnosticProperty.BLOCKCHAIN_CURRENT_HEIGHT withLazyValue { blockchainEngine.getBlockQueries().getBestHeight().get() })
    }
}
