package net.postchain.core.framework

import mu.NamedKLogging
import net.postchain.core.BlockchainEngine
import net.postchain.core.BlockchainProcess
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

abstract class AbstractBlockchainProcess(private val processName: String, override val blockchainEngine: BlockchainEngine) : BlockchainProcess {

    val logger =  NamedKLogging(this::class.java.simpleName).logger

    private val shutdown = AtomicBoolean(false)
    internal val process: Thread

    init {
        process = thread(name = processName) { main() }
    }

    fun isShuttingDown() = shutdown.get()

    abstract fun action()

    private fun main() {
        try {
            while (!isShuttingDown()) {
                action()
            }
        } catch (e: Exception) {
            logger.error(e) { "Process $processName stopped unexpectedly" }
        }
    }

    override fun shutdown() {
        shutdown.set(true)
        logger.debug { "Shutting down process $processName" }
        process.join()
    }
}