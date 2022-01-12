package net.postchain.core.framework

import mu.NamedKLogging
import net.postchain.core.BlockchainEngine
import net.postchain.core.BlockchainProcess
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

abstract class AbstractBlockchainProcess(private val processName: String, override val blockchainEngine: BlockchainEngine) : BlockchainProcess {

    val logger =  NamedKLogging(this::class.java.simpleName).logger

    private val shutdown = AtomicBoolean(false)
    internal lateinit var process: Thread

    fun isShuttingDown() = shutdown.get()

    final override fun start() {
        process = thread(name = processName) { main() }
    }

    private fun main() {
        try {
            while (!isShuttingDown()) {
                action()
            }
        } catch (e: Exception) {
            logger.error(e) { "Process $processName stopped unexpectedly" }
        }
    }

    protected abstract fun action()

    override fun shutdown() {
        shutdown.set(true)
        if (alreadyShutdown()) return
        logger.debug { "Shutting down process $processName" }
        process.join()
    }

    private fun alreadyShutdown() = !::process.isInitialized || !process.isAlive
}