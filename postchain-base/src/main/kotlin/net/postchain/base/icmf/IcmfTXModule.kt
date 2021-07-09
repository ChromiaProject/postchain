package net.postchain.base.icmf

import net.postchain.base.data.DatabaseAccess
import net.postchain.base.BaseBlockBuilderExtension
import net.postchain.common.data.KECCAK256
import net.postchain.common.hexStringToByteArray
import net.postchain.core.EContext
import net.postchain.core.MultiSigBlockWitness
import net.postchain.gtv.*
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.merkle.GtvMerkleHashCalculator
import net.postchain.gtx.GTXSpecialTxExtension
import net.postchain.gtx.SimpleGTXModule
import org.apache.commons.dbutils.QueryRunner

class IcmfTXModule : SimpleGTXModule<Unit>(
    Unit, mapOf(), mapOf(
        "get_event_merkle_proof" to ::eventMerkleProofQuery,
        "get_account_state_merkle_proof" to ::accountStateMerkleProofQuery
    )
) {

    companion object {
        val PREFIX_ICMF = "icmf"
    }

    /**
     * ICMF only needs the event-table (we store the [IcmfMessage] type as [DatabaseAccess.EventInfo] )
     */
    override fun initializeDB(ctx: EContext) {
        val dba = DatabaseAccess.of(ctx)
        dba.createOptionalEventLeafTable(ctx, PREFIX_ICMF) // ICMF specific event (for anchoring etc)

    }

    override fun makeBlockBuilderExtensions(): List<BaseBlockBuilderExtension> {
        return listOf(EthereumL2Implementation(SimpleDigestSystem(KECCAK256), 3))
    }

    override fun getSpecialTxExtensions(): List<GTXSpecialTxExtension> {
        return listOf(IcmfSpecialTxExtension())
    }

}