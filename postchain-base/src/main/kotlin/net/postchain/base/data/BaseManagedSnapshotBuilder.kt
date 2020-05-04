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
            rollback()
            throw e
        }
    }

    override fun rollback() {
        if (closed) throw ProgrammerMistake("Already closed")
        closed = true
        storage.closeWriteConnection(eContext, false)
    }

    override fun begin() {
        return runOp { snapshotBuilder.begin() }
    }

    override fun commit() {
        storage.closeWriteConnection(eContext, true)
    }

    override fun buildSnapshot(): Tree {
        return runOp { snapshotBuilder.buildSnapshot() }
    }

    override fun getSnapshotTree(): TreeElement {
        return snapshotBuilder.getSnapshotTree()
    }

}