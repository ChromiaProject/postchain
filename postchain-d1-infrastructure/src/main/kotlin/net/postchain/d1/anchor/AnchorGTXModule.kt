package net.postchain.d1.anchor

import net.postchain.base.BaseBlockBuilderExtension
import net.postchain.core.EContext
import net.postchain.gtx.GTXSpecialTxExtension
import net.postchain.gtx.SimpleGTXModule

/**
 * This module doesn't do much.
 *
 * Note regarding modules:
 * We write this module as a complement to the "anchor" module that is written in Rell.
 * The Rell module define the "__anchor_block_header" operation for example, it is not known by this module.
 */
class AnchorGTXModule : SimpleGTXModule<Unit>(
        Unit, mapOf(), mapOf()
) {

    companion object {
        const val PREFIX: String = "sys.x.anchor" // This name should not clash with the Rell "anchor" module
    }

    override fun initializeDB(ctx: EContext) {} // Don't need anything, the "real" anchor module creates tables etc

    override fun makeBlockBuilderExtensions(): List<BaseBlockBuilderExtension> {
        return listOf() // We don't need any BBB extensions to make anchoring work
    }

    /**
     * We need to write our own special type of operation for each header message we get.
     * That's the responsibility of [AnchorSpecialTxExtension]
     */
    override fun getSpecialTxExtensions(): List<GTXSpecialTxExtension> {
        return listOf(AnchorSpecialTxExtension())
    }
}