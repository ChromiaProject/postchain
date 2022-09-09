package net.postchain.d1.icmf

import net.postchain.base.BaseBlockBuilderExtension
import net.postchain.base.data.DatabaseAccess
import net.postchain.core.EContext
import net.postchain.crypto.Secp256K1CryptoSystem
import net.postchain.gtx.SimpleGTXModule
import net.postchain.gtx.special.GTXSpecialTxExtension

fun DatabaseAccess.tableMessage(ctx: EContext) = tableName(ctx, "icmf_message")

class IcmfGTXModule : SimpleGTXModule<Unit>(
    Unit,
    mapOf(),
    mapOf()
) {
    val cryptoSystem = Secp256K1CryptoSystem() // TODO inject this

    override fun initializeDB(ctx: EContext) {}

    override fun makeBlockBuilderExtensions(): List<BaseBlockBuilderExtension> =
        listOf(IcmfBlockBuilderExtension(cryptoSystem))

    override fun getSpecialTxExtensions(): List<GTXSpecialTxExtension> = listOf()
}
