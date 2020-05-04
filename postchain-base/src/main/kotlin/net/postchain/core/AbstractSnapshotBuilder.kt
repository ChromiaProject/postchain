package net.postchain.core

import net.postchain.base.BaseSnaphotEContext

abstract class AbstractSnapshotBuilder(
        val ectx: EContext
): SnapshotBuilder {

    var finalized: Boolean = false
    private lateinit var bctx: EContext

    var snapshotData: Tree = null

    override fun begin() {
        if (finalized) {
            ProgrammerMistake("This builder has already been used once (you must create a new builder instance)")
        }
        bctx = BaseSnaphotEContext(ectx)
    }

    override fun commit() {
        TODO("Not yet implemented")
    }

    override fun getSnapshotTree(): TreeElement {
        return snapshotData ?: throw ProgrammerMistake("Snapshot is not finalized yet")
    }
}

