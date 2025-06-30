package net.postchain.base.snapshot

import net.postchain.base.BaseBlockBuilderExtension
import net.postchain.base.data.BaseBlockBuilder
import net.postchain.base.data.DatabaseAccess
import net.postchain.core.BlockEContext
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtx.SNAPSHOT_TABLE_PREFIX
import java.security.MessageDigest
import java.util.TreeMap

const val SNAPSHOT_ROOT_EXTRA_HEADER = "snapshot_root"

class RootSnapshotBlockBuilderExtension : BaseBlockBuilderExtension {
    private lateinit var bctx: BlockEContext

    override fun init(blockEContext: BlockEContext, baseBB: BaseBlockBuilder) {
        bctx = blockEContext
    }

    override fun finalize(): Map<String, Gtv> {
        val snapshotContexts = DatabaseAccess.of(bctx).getSnapshotModuleContextIds(bctx)
        val digestSystem = SimpleDigestSystem(MessageDigest.getInstance("SHA-256"))
        val rootSnapshotStore = SnapshotPageStore(bctx, 2, 0, digestSystem, "${SNAPSHOT_TABLE_PREFIX}_root")

        val leafUpdates = TreeMap<Long, ByteArray>()
        for (contextId in snapshotContexts) {
            val moduleSnapshotStore = SnapshotPageStore(bctx, 2, 0, digestSystem, "${SNAPSHOT_TABLE_PREFIX}_$contextId")
            leafUpdates[contextId] = moduleSnapshotStore.getRootHashAtHeight(bctx.height)
        }

        val rootHash = rootSnapshotStore.updateSnapshot(bctx.height, leafUpdates, 2)
        return mapOf(SNAPSHOT_ROOT_EXTRA_HEADER to gtv(rootHash))
    }
}
