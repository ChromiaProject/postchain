package net.postchain.core.framework

import mu.KLogging
import net.postchain.core.BlockchainEngine
import net.postchain.core.BlockchainProcess
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

abstract class AbstractBlockchainProcess : BlockchainProcess {

    companion object : KLogging() // TODO: Use logger from child classes

    private val shutdown = AtomicBoolean(false)
    internal lateinit var process: Thread

    fun isShuttingDown() = shutdown.get()

    abstract fun processName(): String

    abstract fun action()

    private fun main() {
        try {
            while (!isShuttingDown()) {
                action()
            }
        } catch (e: Exception) {
            logger.error(e) { "Process ${processName()} stopped unexpectedly" }
        }
    }

    fun startProcess() {
        process = thread(name = processName()) { main() }
    }

    override fun getEngine(): BlockchainEngine {
        TODO("Not yet implemented")
    }

    override fun shutdown() {
        shutdown.set(true)
        if (alreadyShutdown()) return
        logger.debug { "Shutting down process ${processName()}" }
        process.join()
    }

    private fun alreadyShutdown() = !::process.isInitialized || !process.isAlive
}