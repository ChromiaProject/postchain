package net.postchain.d1.anchor

import net.postchain.core.EContext
import net.postchain.gtx.SimpleGTXModule
import net.postchain.gtx.special.GTXSpecialTxExtension

/**
 * Anchoring module.
 *
 * Note regarding modules:
 * We write this module as a complement to the "anchor" module that is written in Rell.
 * The Rell module define the "__anchor_block_header" operation for example, it is not known by this module.
 */
class AnchorGTXModule : SimpleGTXModule<Unit>(
    Unit, mapOf(), mapOf()
) {
    private val _specialTxExtensions = listOf(AnchorSpecialTxExtension())

    override fun initializeDB(ctx: EContext) {} // Don't need anything, the "real" anchor module creates tables etc

    /**
     * We need to write our own special type of operation for each header message we get.
     * That's the responsibility of [AnchorSpecialTxExtension]
     */
    override fun getSpecialTxExtensions(): List<GTXSpecialTxExtension> = _specialTxExtensions
}
