package net.postchain.base.data

import mu.KLogging
import net.postchain.base.Storage
import net.postchain.core.*

class BaseManagedSnapshotBuilder(
        private val eContext: EContext,
        val storage: Storage,
        private val snapshotBuilder: SnapshotBuilder
): ManagedSnapshotBuilder {

    companion object : KLogging()

    var closed: Boolean = false

    fun <RT> runOp(fn: () -> RT): RT {
        if (closed)
            throw ProgrammerMistake("Already closed")
        try {
            return fn()
        } catch (e: Exception) {
            throw e
        }
    }

    override fun begin() {
        return runOp { snapshotBuilder.begin() }
    }

    override fun commit() {
        // Only need to close the read connection
        storage.closeReadConnection(eContext)
    }

    override fun buildSnapshot(height: Long): Tree {
        return runOp { snapshotBuilder.buildSnapshot(height) }
    }

    override fun getSnapshotTree(): TreeElement {
        return snapshotBuilder.getSnapshotTree()
    }

}