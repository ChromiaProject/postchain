package net.postchain.base.snapshot

import mu.KLogging
import net.postchain.base.BaseBlockBuilderExtension
import net.postchain.base.data.BaseBlockBuilder
import net.postchain.core.BlockEContext
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory.gtv

const val SNAPSHOT_ROOT_EXTRA_HEADER = "snapshot_root"

class RootSnapshotBlockBuilderExtension(
        private val snapshotInterval: Long,
        private val levelsPerPage: Int,
        private val snapshotsToKeep: Int
) : BaseBlockBuilderExtension {

    companion object : KLogging()

    private lateinit var bctx: BlockEContext
    private lateinit var snapshotBuilder: RootSnapshotBlockBuilder

    override fun init(blockEContext: BlockEContext, baseBB: BaseBlockBuilder) {
        bctx = blockEContext
        snapshotBuilder = RootSnapshotBlockBuilder(blockEContext, levelsPerPage, snapshotsToKeep, baseBB.cryptoSystem)
    }

    @Suppress("removal")
    override fun finalize(): Map<String, Gtv> {
        val lastSnapshotHeight = snapshotBuilder.getLastSnapshotHeight() ?: -1
        if (bctx.height - lastSnapshotHeight < snapshotInterval) return emptyMap()
        return mapOf(SNAPSHOT_ROOT_EXTRA_HEADER to gtv(snapshotBuilder.build()))
    }
}
