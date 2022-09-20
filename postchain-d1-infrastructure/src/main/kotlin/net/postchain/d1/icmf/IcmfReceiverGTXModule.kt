package net.postchain.d1.icmf

import net.postchain.core.EContext
import net.postchain.gtx.SimpleGTXModule
import net.postchain.gtx.special.GTXSpecialTxExtension

class IcmfReceiverGTXModule : SimpleGTXModule<Unit>(
        Unit,
        mapOf(),
        mapOf()
) {
    private val specialTxExtension = IcmfRemoteSpecialTxExtension()
    private val _specialTxExtensions = listOf(specialTxExtension)

    override fun initializeDB(ctx: EContext) {
        IcmfDatabaseOperations.initialize(ctx)
    }

    override fun getSpecialTxExtensions(): List<GTXSpecialTxExtension> = _specialTxExtensions
}
