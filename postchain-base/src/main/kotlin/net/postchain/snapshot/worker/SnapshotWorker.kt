package net.postchain.snapshot.worker

import net.postchain.core.BlockchainEngine
import net.postchain.debug.SnapshotProcessName
import net.postchain.ebft.syncmanager.SnapshotSyncManager
import net.postchain.snapshot.BaseSnapshotDatabase
import net.postchain.snapshot.BaseSnapshotManager
import net.postchain.snapshot.SnapshotManager

class SnapshotWorker(
        override val processName: SnapshotProcessName,
        override val blockchainEngine: BlockchainEngine
): AbstractSnapshotProcess() {
    override val snapshotDatabase: BaseSnapshotDatabase
    override val syncManager: SnapshotSyncManager
    private val snapshotManager: SnapshotManager

    init {
        snapshotDatabase = BaseSnapshotDatabase(blockchainEngine)

        snapshotManager = BaseSnapshotManager(snapshotDatabase, blockchainEngine.getSnapshotBuildingStrategy())

        syncManager = SnapshotSyncManager(snapshotManager)

        startUpdateLoop()
    }

}