package net.postchain.base.snapshot

import mu.KLogging
import net.postchain.base.data.DatabaseAccess
import net.postchain.common.data.Hash
import net.postchain.common.toHex
import net.postchain.core.BlockEContext
import net.postchain.gtx.SNAPSHOT_TABLE_PREFIX
import java.security.MessageDigest
import java.util.TreeMap

class RootSnapshotBlockBuilder(
        private val bctx: BlockEContext,
) {
    private val rootSnapshotStore = SnapshotPageStore(bctx, 2, 0, digestSystem, "${SNAPSHOT_TABLE_PREFIX}_root")

    companion object : KLogging() {
        private val digestSystem = SimpleDigestSystem(MessageDigest.getInstance("SHA-256"))
    }

    fun getLastSnapshotHeight(): Long? = rootSnapshotStore.getLastSnapshotHeight()

    fun build(): Hash {

        logger.info("Creating snapshot at height ${bctx.height}")

        val rootHash = DatabaseAccess.of(bctx).run {
            // TODO: This size might be too big? Do we need to limit it?
            val updatedDataByContext = getUpdatedDatumsByContext(bctx)
            val contextRootHashes = updatedDataByContext.map { (contextId, updatedData) ->
                val snapshotPageStore = SnapshotPageStore(
                        bctx, 2, 0, digestSystem, "${SNAPSHOT_TABLE_PREFIX}_$contextId"
                )

                for (datumInfo in updatedData) {
                    if (datumInfo.rawValue != null) {
                        LeafStore().writeState(bctx, "${SNAPSHOT_TABLE_PREFIX}_$contextId", datumInfo.id, datumInfo.rawValue)
                    }
                }
                contextId to snapshotPageStore.updateSnapshot(bctx.height, TreeMap(updatedData.associate { it.id to it.hash }), 2)
            }

            clearUpdatedDatums(bctx)

            rootSnapshotStore.updateSnapshot(bctx.height, TreeMap(contextRootHashes.associate { it.first to it.second }), 2)
        }

        logger.info("Completed writing snapshot at height: ${bctx.height} with root hash: ${rootHash.toHex()}")

        return rootHash
    }
}
