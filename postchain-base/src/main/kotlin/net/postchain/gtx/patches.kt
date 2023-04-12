package net.postchain.gtx

import net.postchain.core.EContext
import net.postchain.core.TxEContext
import net.postchain.gtx.data.ExtOpData

/**
 * nop operation that replaces GtxNop and doesn't impose any size or argument restrictions.
 * This module was created to cope with incompatibilities in old blockchains that had
 * erroneous nops in them.
 */
class GtxPermissiveNop(@Suppress("UNUSED_PARAMETER") u: Unit, opData: ExtOpData) : GTXOperation(opData) {
    override fun apply(ctx: TxEContext): Boolean = true

    override fun checkCorrectness() { }
}

class PatchOpsGTXModule : SimpleGTXModule<Unit>(Unit, mapOf("nop" to ::GtxPermissiveNop), mapOf()) {
    override fun initializeDB(ctx: EContext) {}
}