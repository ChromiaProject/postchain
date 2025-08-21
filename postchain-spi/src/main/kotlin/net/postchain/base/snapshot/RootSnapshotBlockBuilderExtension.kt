package net.postchain.base.snapshot

import mu.KLogging
import net.postchain.base.BaseBlockBuilderExtension
import net.postchain.base.data.BaseBlockBuilder
import net.postchain.base.data.DatabaseAccess
import net.postchain.common.toHex
import net.postchain.core.BlockEContext
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtx.SNAPSHOT_TABLE_PREFIX
import java.security.MessageDigest
import java.util.TreeMap

const val SNAPSHOT_ROOT_EXTRA_HEADER = "snapshot_root"

class RootSnapshotBlockBuilderExtension(private val snapshotInterval: Long) : BaseBlockBuilderExtension {

    companion object : KLogging()

    private val digestSystem = SimpleDigestSystem(MessageDigest.getInstance("SHA-256"))

    private lateinit var bctx: BlockEContext

    override fun init(blockEContext: BlockEContext, baseBB: BaseBlockBuilder) {
        bctx = blockEContext
    }

    override fun finalize(): Map<String, Gtv> {
        val rootSnapshotStore = SnapshotPageStore(bctx, 2, 0, digestSystem, "${SNAPSHOT_TABLE_PREFIX}_root")
        if (bctx.height - (rootSnapshotStore.getLastSnapshotHeight() ?: -1) < snapshotInterval) return emptyMap()

        logger.info("Creating snapshot at height ${bctx.height}")

        val rootHash = DatabaseAccess.of(bctx).run {
            val updatedDatumsByContext = getUpdatedDatumsByContext(bctx)
            val contextRootHashes = updatedDatumsByContext.map { (contextId, updatedDatums) ->
                val snapshotPageStore = SnapshotPageStore(
                        bctx, 2, 0, digestSystem, "${SNAPSHOT_TABLE_PREFIX}_$contextId"
                )

                for (datumInfo in updatedDatums) {
                    if (datumInfo.rawValue != null) {
                        LeafStore().writeState(bctx, "${SNAPSHOT_TABLE_PREFIX}_$contextId", datumInfo.id, datumInfo.rawValue)
                    }
                }
                contextId to snapshotPageStore.updateSnapshot(bctx.height, TreeMap(updatedDatums.associate { it.id to it.hash }), 2)
            }

            clearUpdatedDatums(bctx)

            rootSnapshotStore.updateSnapshot(bctx.height, TreeMap(contextRootHashes.associate { it.first to it.second }), 2)
        }

        logger.info("Completed writing snapshot at height: ${bctx.height} with root hash: ${rootHash.toHex()}")

        return mapOf(SNAPSHOT_ROOT_EXTRA_HEADER to gtv(rootHash))
    }
}
