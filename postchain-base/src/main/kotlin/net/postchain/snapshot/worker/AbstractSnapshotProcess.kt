package net.postchain.snapshot.worker

import mu.KLogging
import net.postchain.core.BlockchainEngine
import net.postchain.core.BlockchainProcess
import net.postchain.debug.SnapshotProcessName
import net.postchain.ebft.syncmanager.SnapshotSyncManager
import net.postchain.snapshot.BaseSnapshotDatabase
import kotlin.concurrent.thread

abstract class AbstractSnapshotProcess: BlockchainProcess {
    abstract val processName: SnapshotProcessName
    abstract val syncManager: SnapshotSyncManager
    abstract val blockchainEngine: BlockchainEngine
    abstract val snapshotDatabase: BaseSnapshotDatabase

    private lateinit var updateLoop: Thread

    companion object : KLogging()

    override fun getEngine() = blockchainEngine

    /**
     * Create and run the [updateLoop] thread
     */
    protected fun startUpdateLoop() {

        updateLoop = thread(name = "updateLoop-$processName") {
            while (!Thread.interrupted()) {
                try {
                    syncManager.update()
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                try {
                    Thread.sleep(20)
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
            }
        }
    }

    /**
     * Stop the postchain node
     */
    override fun shutdown() {
        updateLoop.interrupt()
        updateLoop.join()
        blockchainEngine.shutdown()
        snapshotDatabase.stop()
    }
}