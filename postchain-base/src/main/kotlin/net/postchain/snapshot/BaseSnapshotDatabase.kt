package net.postchain.snapshot

import mu.KLogging
import net.postchain.core.BlockchainEngine
import net.postchain.core.ManagedSnapshotBuilder
import net.postchain.core.Tree
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.deferred
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class BaseSnapshotDatabase(
        private val engine: BlockchainEngine
): SnapshotDatabase {

    private val executor = Executors.newSingleThreadExecutor {
        Thread(it, "BaseSnapshotDatabaseWorker")
                .apply {
                    isDaemon = true // So it can't block the JVM from exiting if still running
                }
    }

    companion object : KLogging()

    private var snapshotBuilder: ManagedSnapshotBuilder? = null

    fun stop() {
        BaseSnapshotDatabase.logger.debug("BaseSnapshotDatabase stopping")
        executor.shutdownNow()
        executor.awaitTermination(1000, TimeUnit.MILLISECONDS) // TODO: [et]: 1000 ms
    }

    private fun <RT> runOp(name: String, op: () -> RT): Promise<RT, Exception> {
        BaseSnapshotDatabase.logger.trace("BaseSnapshotDatabase putting a job")

        val deferred = deferred<RT, Exception>()
        executor.execute {
            try {
                BaseSnapshotDatabase.logger.debug("Starting job $name")
                val res = op()
                BaseSnapshotDatabase.logger.debug("Finish job $name")
                deferred.resolve(res)
            } catch (e: Exception) {
                BaseSnapshotDatabase.logger.debug("Failed job $name", e)
                deferred.reject(e)
            }
        }

        return deferred.promise
    }

    override fun buildSnapshot(height: Long): Promise<Tree, Exception> {
        return runOp("build snapshot") {
            snapshotBuilder = engine.buildSnapshot(height)
            snapshotBuilder?.getSnapshotTree()
        }
    }
}