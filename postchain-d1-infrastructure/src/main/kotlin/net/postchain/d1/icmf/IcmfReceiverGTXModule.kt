package net.postchain.d1.icmf

import net.postchain.core.EContext
import net.postchain.core.Transactor
import net.postchain.core.TxEContext
import net.postchain.d1.icmf.IcmfRemoteSpecialTxExtension.Companion.OP_ICMF_HEADER
import net.postchain.gtx.SimpleGTXModule
import net.postchain.gtx.special.GTXSpecialTxExtension

class IcmfReceiverGTXModule : SimpleGTXModule<Unit>(
        Unit,
        mapOf(OP_ICMF_HEADER to { u, opdata ->
            object : Transactor {
                override fun isSpecial() = true
                override fun isCorrect() = true
                override fun apply(ctx: TxEContext) = true
            }
        }),
        mapOf()
) {
    private val specialTxExtension = IcmfRemoteSpecialTxExtension()
    private val _specialTxExtensions = listOf(specialTxExtension)

    override fun initializeDB(ctx: EContext) {
        IcmfDatabaseOperations.initialize(ctx)
    }

    override fun getSpecialTxExtensions(): List<GTXSpecialTxExtension> = _specialTxExtensions
}