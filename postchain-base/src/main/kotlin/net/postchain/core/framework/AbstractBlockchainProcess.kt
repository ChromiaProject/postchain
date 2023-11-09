package net.postchain.core.framework

import mu.NamedKLogging
import net.postchain.common.exception.ProgrammerMistake
import net.postchain.core.BlockchainEngine
import net.postchain.core.BlockchainProcess
import net.postchain.metrics.AbstractBlockchainProcessMetrics
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

abstract class AbstractBlockchainProcess(private val processName: String, override val blockchainEngine: BlockchainEngine) : BlockchainProcess {

    val logger = NamedKLogging(this::class.java.simpleName).logger

    private val running = AtomicBoolean(false)
    private val started = AtomicBoolean(false)
    internal val process: Thread = thread(name = processName, start = false) { main() }

    val metrics = AbstractBlockchainProcessMetrics(blockchainEngine.getConfiguration().chainID, blockchainEngine.getConfiguration().blockchainRid, this)

    override fun isProcessRunning() = running.get()

    final override fun start() {
        if (!started.getAndSet(true)) {
            running.set(true)
            process.start()
        } else throw ProgrammerMistake("Process is already started")
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
        // Clean up the process here if it was never started
        if (!started.get()) cleanup()
        running.set(false)
        if (!process.isAlive) return
        logger.debug { "Shutting down process $processName" }
        process.join()
        metrics.close()
    }

    abstract fun currentBlockHeight(): Long
}
