package net.postchain.d1.icmf

import net.postchain.base.BaseBlockBuilderExtension
import net.postchain.base.data.DatabaseAccess
import net.postchain.core.EContext
import net.postchain.gtx.SimpleGTXModule
import net.postchain.gtx.special.GTXSpecialTxExtension

fun DatabaseAccess.tableMessage(ctx: EContext) = tableName(ctx, "sent_icmf_message")

class IcmfGTXModule : SimpleGTXModule<Unit>(
    Unit,
    mapOf(),
    mapOf()
) {
    override fun initializeDB(ctx: EContext) {}

    override fun makeBlockBuilderExtensions(): List<BaseBlockBuilderExtension> =
        listOf(IcmfBlockBuilderExtension())

    override fun getSpecialTxExtensions(): List<GTXSpecialTxExtension> = listOf()
}