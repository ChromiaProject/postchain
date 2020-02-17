package net.postchain.ebft.syncmanager

import net.postchain.snapshot.SnapshotManager

class SnapshotSyncManager(
        private val snapshotManager: SnapshotManager
): SyncManagerBase {

    override fun update() {
        snapshotManager.buildSnapshot()
    }

}