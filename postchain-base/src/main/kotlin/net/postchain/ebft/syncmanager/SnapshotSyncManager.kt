package net.postchain.ebft.syncmanager

import net.postchain.snapshot.SnapshotManager

class SnapshotSyncManager(
        private val snapshotManager: SnapshotManager
): SyncManager {

    override fun update() {
        snapshotManager.buildSnapshot()
    }

}