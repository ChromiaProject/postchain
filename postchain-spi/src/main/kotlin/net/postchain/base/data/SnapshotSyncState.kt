package net.postchain.base.data

data class SnapshotSyncState(
        /** The height this snapshot sync is downlaoding */
        val height: Long,

        /** The final snapshot root hash of the snapshot store */
        val rootHash: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SnapshotSyncState

        if (height != other.height) return false
        if (!rootHash.contentEquals(other.rootHash)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = height.hashCode()
        result = 31 * result + rootHash.contentHashCode()
        return result
    }
}

data class SnapshotSyncContextState(
        val contextId: Long,

        /** Snapshot root hash for this context */
        val contextRootHash: ByteArray,

        /** Last unrequested datum id */
        var datumIdOffset: Long,

        /** Possible max datum id for this context, not verified */
        val maxDatumId: Long?
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SnapshotSyncContextState

        if (contextId != other.contextId) return false
        if (datumIdOffset != other.datumIdOffset) return false
        if (maxDatumId != other.maxDatumId) return false
        if (!contextRootHash.contentEquals(other.contextRootHash)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = contextId.hashCode()
        result = 31 * result + datumIdOffset.hashCode()
        result = 31 * result + (maxDatumId?.hashCode() ?: 0)
        result = 31 * result + contextRootHash.contentHashCode()
        return result
    }
}
