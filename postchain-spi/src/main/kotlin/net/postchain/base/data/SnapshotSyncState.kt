package net.postchain.base.data

data class SnapshotSyncState(
        /** The height this snapshot sync is downlaoding */
        val height: Long,

        /** The final snapshot root hash of the snapshot store */
        val rootHash: ByteArray,
)

data class SnapshotSyncContextState(
        val contextId: Long,

        /** Snapshot root hash for this context */
        val contextRootHash: ByteArray,

        /** Last unrequested datum id */
        var datumIdOffset: Long,

        /** Possible max datum id for this context, not verified */
        val maxDatumId: Long
)
