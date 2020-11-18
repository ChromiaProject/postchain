package net.postchain.snapshot

import mu.KLogging
import net.postchain.core.SnapshotBuildingStrategy
import net.postchain.core.Tree
import nl.komponents.kovenant.Promise

open class BaseSnapshotManager(
        private val snapshotDB: SnapshotDatabase,
        private val strategy: SnapshotBuildingStrategy
): SnapshotManager {

    @Volatile
    var processing = false

    @Volatile
    var snapshot: Tree = null

    companion object : KLogging()

    override fun isProcessing(): Boolean {
        return processing
    }

    override fun buildSnapshot(): Tree {
        synchronized(this) {
            update()
        }
        return snapshot
    }

    private fun <RT> runDBOp(op: () -> Promise<RT, Exception>, onSuccess: (RT) -> Unit) {
        if (!processing) {
            synchronized (this) {
                processing = true
                val promise = op()
                promise.success { res ->
                    synchronized (snapshotDB) {
                        onSuccess(res)
                        processing = false
                    }
                }
                promise.fail { err ->
                    processing = false
                    BaseSnapshotManager.logger.debug("Error in runDBOp()", err)
                }
            }
        }
    }

    protected fun update() {
        if (processing) return
        val s = strategy.shouldBuildSnapshot()
        if (!s.first) {
            return
        }
        BaseSnapshotManager.logger.debug("It's time to build a snapshot.")
        runDBOp({
            snapshotDB.buildSnapshot(s.second)
        }, {
            snapshot = it
        })
    }
}