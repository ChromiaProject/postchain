package net.postchain.base.snapshot

import mu.KLogging
import net.postchain.base.BaseBlockEContext
import net.postchain.base.data.DatabaseAccess
import net.postchain.common.data.Hash
import net.postchain.common.exception.UserMistake
import net.postchain.common.toHex
import net.postchain.core.BlockEContext
import net.postchain.core.EContext
import net.postchain.crypto.CryptoSystem
import net.postchain.gtx.SNAPSHOT_TABLE_PREFIX
import java.util.TreeMap

class RootSnapshotBlockBuilder(
        private val bctx: BlockEContext,
        val levelsPerPage: Int,
        val snapshotsToKeep: Int,
        cryptoSystem: CryptoSystem,
) {
    private val digestSystem = SimpleDigestSystem(cryptoSystem)
    private val rootSnapshotStore = SnapshotPageStore(bctx, levelsPerPage, snapshotsToKeep, digestSystem, "${SNAPSHOT_TABLE_PREFIX}_root")
    private val leafStore = LeafStore()

    companion object : KLogging()

    constructor(ctx: EContext, height: Long, levelsPerPage: Int, snapshotsToKeep: Int, cryptoSystem: CryptoSystem) : this(
            BaseBlockEContext(ctx, height, -1, -1, mapOf()) { _, _, _ -> }, levelsPerPage, snapshotsToKeep, cryptoSystem
    )

    fun getLastSnapshotHeight(): Long? = rootSnapshotStore.getLastSnapshotHeight()

    fun build(): Hash {

        logger.info("Creating snapshot at height ${bctx.height}")

        val rootHash = DatabaseAccess.of(bctx).run {
            // TODO: This size might be too big? Do we need to limit it?
            val updatedDataByContext = getUpdatedDatumsByContext(bctx)
            val contextRootHashes = updatedDataByContext.map { (contextId, updatedData) ->
                val snapshotPageStore = SnapshotPageStore(bctx, levelsPerPage, snapshotsToKeep, digestSystem, "${SNAPSHOT_TABLE_PREFIX}_$contextId")

                if (snapshotPageStore.getLastSnapshotHeight() == null && updatedData.none { it.id == 0L }) {
                    throw UserMistake("Snapshot datum IDs must start at 0")
                }

                for (datumInfo in updatedData) {
                    if (datumInfo.rawValue != null) {
                        leafStore.writeState(bctx, "${SNAPSHOT_TABLE_PREFIX}_$contextId", datumInfo.id, datumInfo.rawValue)
                    }
                }
                snapshotPageStore.pruneSnapshot(bctx.height)
                contextId to snapshotPageStore.updateSnapshot(bctx.height, TreeMap(updatedData.associate { it.id to it.hash }), 2)
            }

            clearUpdatedDatums(bctx)
            if (contextRootHashes.isNotEmpty()) {
                rootSnapshotStore.pruneSnapshot(bctx.height)
            }

            rootSnapshotStore.updateSnapshot(bctx.height, TreeMap(contextRootHashes.associate { it.first to it.second }), 2)
        }

        logger.info("Completed writing snapshot at height: ${bctx.height} with root hash: ${rootHash.toHex()}")

        return rootHash
    }
}
