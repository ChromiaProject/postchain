package net.postchain.base.snapshot

import mu.KLogging
import net.postchain.base.BaseBlockBuilderExtension
import net.postchain.base.data.BaseBlockBuilder
import net.postchain.common.toHex
import net.postchain.core.BlockEContext
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory.gtv
import java.util.concurrent.atomic.AtomicLong

const val SNAPSHOT_ROOT_EXTRA_HEADER = "snapshot_root"

class RootSnapshotBlockBuilderExtension(
        private val snapshotInterval: Long,
        private val levelsPerPage: Int,
        private val snapshotsToKeep: Int
) : BaseBlockBuilderExtension {

    companion object : KLogging() {
        val includeRootSnapshotFromHeight = AtomicLong(Long.MAX_VALUE)
    }

    private lateinit var bctx: BlockEContext
    private lateinit var snapshotBuilder: RootSnapshotBlockBuilder
    private var includeRootSnapshot = false

    override fun init(blockEContext: BlockEContext, baseBB: BaseBlockBuilder) {
        bctx = blockEContext
        snapshotBuilder = RootSnapshotBlockBuilder(blockEContext, levelsPerPage, snapshotsToKeep, baseBB.cryptoSystem)
        includeRootSnapshot = bctx.height >= includeRootSnapshotFromHeight.get()
    }

    @Suppress("removal")
    override fun finalize(): Map<String, Gtv> {
        val lastSnapshotHeight = snapshotBuilder.getLastSnapshotHeight() ?: -1
        if (bctx.height - lastSnapshotHeight < snapshotInterval) return emptyMap()
        val rootHash = snapshotBuilder.build()
        if (!includeRootSnapshot) {
            return emptyMap()
        }
        logger.info("Creating snapshot at height ${bctx.height} with root hash: ${rootHash.toHex()}")
        return mapOf(SNAPSHOT_ROOT_EXTRA_HEADER to gtv(rootHash))
    }
}
