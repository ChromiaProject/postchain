package net.postchain.snapshot

import net.postchain.core.Tree
import nl.komponents.kovenant.Promise

interface SnapshotManager {
    fun isProcessing(): Boolean
    fun buildSnapshot(): Tree
}

interface SnapshotDatabase {
    fun buildSnapshot(height: Long): Promise<Tree, Exception>
}