package net.postchain.gtx.special

import net.postchain.base.SpecialTransactionPosition
import net.postchain.core.BlockEContext

interface GTXNonSkippingSpecialTxExtension : GTXSpecialTxExtension {
    /**
     * @return true if extension allows that no relevant operations are included in special tx
     */
    fun isAllowedToSkipSpecialOperations(
            position: SpecialTransactionPosition,
            bctx: BlockEContext
    ): Boolean
}
