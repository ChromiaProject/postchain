package net.postchain.core


interface SnapshotBuilder {
    fun begin()
    fun buildSnapshot(): Tree
    fun getSnapshotTree(): TreeElement
    fun commit()
}

interface ManagedSnapshotBuilder: SnapshotBuilder {
    fun rollback()
}

/**
 * Strategy configurations for how to create new snapshot
 */
interface SnapshotBuildingStrategy {
    fun shouldBuildSnapshot(): Boolean
}