package net.postchain.gtx.special

import net.postchain.core.block.BlockData

interface GTXBlockBuildingAffectingSpecialTxExtension : GTXSpecialTxExtension {
    /**
     * Will be called from [net.postchain.base.BaseBlockBuilderExtension] when a block has been built.
     *
     * @param blockData  the block
     */
    fun blockCommitted(blockData: BlockData)

    /**
     * Will be called from [net.postchain.base.BaseBlockBuilderExtension].
     *
     * @return true if it is appropriate to build a block now
     */
    fun shouldBuildBlock(): Boolean
}
