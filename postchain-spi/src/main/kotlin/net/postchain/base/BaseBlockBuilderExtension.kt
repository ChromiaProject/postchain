package net.postchain.base

import net.postchain.base.data.BaseBlockBuilder
import net.postchain.core.BlockEContext
import net.postchain.gtv.Gtv

/**
 * NOTE: Remember that the BBB Extension is just a part of many extension interfaces working together.
 * (examples: Spec TX Ext and Sync Ext).
 * To see how it all goes together, see: doc/extension_classes.graphml
 */
interface BaseBlockBuilderExtension {
    fun init(blockEContext: BlockEContext, baseBB: BaseBlockBuilder)
    fun finalize(): Map<String, Gtv>
}