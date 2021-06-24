// Copyright (c) 2021 ChromaWay AB. See README for license information.

package net.postchain.el2

import net.postchain.base.*
import net.postchain.base.data.DatabaseAccess
import net.postchain.base.snapshot.EventPageStore
import net.postchain.base.snapshot.SimpleDigestSystem
import net.postchain.base.snapshot.SnapshotPageStore
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

class EL2GTXModule : SimpleGTXModule<Unit>(
    Unit, mapOf(), mapOf(
        "get_event_merkle_proof" to ::eventMerkleProofQuery,
        "get_account_state_merkle_proof" to ::accountStateMerkleProofQuery
    )
) {
    var queryRunner = QueryRunner()

    override fun initializeDB(ctx: EContext) {
        val dba = DatabaseAccess.of(ctx)
        dba.createPageTable(ctx,"el2_event")
        dba.createPageTable(ctx,"el2_snapshot")
        dba.createLeafTable(ctx, "el2_event")
        dba.createLeafTable(ctx, "el2_snapshot")
    }

    override fun makeBlockBuilderExtensions(): List<BaseBlockBuilderExtension> {
        return listOf(EthereumL2Implementation(SimpleDigestSystem(KECCAK256), 3))
    }

    override fun getSpecialTxExtensions(): List<GTXSpecialTxExtension> {
        return listOf(EL2SpecialTxExtension())
    }

}

fun eventMerkleProofQuery(config: Unit, ctx: EContext, args: Gtv): Gtv {
    val argsDict = args as GtvDictionary
    val blockHeight = argsDict["blockHeight"]!!.asInteger()
    val eventHash = argsDict["eventHash"]!!.asString().hexStringToByteArray()
    val db = DatabaseAccess.of(ctx)
    val blockHeader = GtvEncoder.simpleEncodeGtv(blockHeaderData(db, ctx, blockHeight))
    val blockWitness = blockWitnessData(db, ctx, blockHeight)
    val eventInfo = db.getEvent(ctx, "el2", blockHeight, eventHash) ?: return GtvNull
    val eventData = eventData(eventInfo)
    val event = EventPageStore(ctx, 2, SimpleDigestSystem(KECCAK256))
    val proofs = event.getMerkleProof(blockHeight, eventInfo.pos)
    val gtvProofs = proofs.map { gtv(it) }.toTypedArray()
    return gtv(
        "eventData" to eventData,
        "blockHeader" to gtv(blockHeader),
        "blockWitness" to blockWitness,
        "merkleProofs" to GtvArray(gtvProofs)
    )
}

fun accountStateMerkleProofQuery(config: Unit, ctx: EContext, args: Gtv): Gtv {
    val argsDict = args as GtvDictionary
    val blockHeight = argsDict["blockHeight"]!!.asInteger()
    val accountNumber = argsDict["accountNumber"]!!.asInteger()
    val db = DatabaseAccess.of(ctx)
    val blockHeader = GtvEncoder.simpleEncodeGtv(blockHeaderData(db, ctx, blockHeight))
    val blockWitness = blockWitnessData(db, ctx, blockHeight)
    val accountState = accountState(db.getAccountState(ctx, "el2", blockHeight, accountNumber))
    val snapshot = SnapshotPageStore(ctx, 2, SimpleDigestSystem(KECCAK256))
    val proofs = snapshot.getMerkleProof(blockHeight, accountNumber)
    val gtvProofs = proofs.map { gtv(it) }.toTypedArray()
    return gtv(
        "accountState" to accountState,
        "blockHeader" to gtv(blockHeader),
        "blockWitness" to blockWitness,
        "merkleProofs" to GtvArray(gtvProofs)
    )
}

private fun eventData(event: DatabaseAccess.EventInfo?): Gtv {
    if (event == null) return GtvNull
    return gtv(gtv(event.blockHeight), gtv(event.pos), gtv(event.hash), gtv(event.data))
}

private fun accountState(state: DatabaseAccess.AccountState?): Gtv {
    if (state == null) return GtvNull
    return gtv(gtv(state.blockHeight), gtv(state.stateN), gtv(state.data))
}

private fun blockHeaderData(
    db: DatabaseAccess,
    ctx: EContext,
    blockHeight: Long
): Gtv {
    val merkleHashCalculator = GtvMerkleHashCalculator(BlockchainRidFactory.cryptoSystem)
    val blockRid = db.getBlockRID(ctx, blockHeight) ?: return GtvNull
    val bh = BaseBlockHeader(db.getBlockHeader(ctx, blockRid), SECP256K1CryptoSystem()).blockHeaderRec
    return gtv(
        bh.gtvBlockchainRid,
        gtv(blockRid),
        bh.gtvPreviousBlockRid,
        gtv(bh.gtvMerkleRootHash.merkleHash(merkleHashCalculator)),
        bh.gtvTimestamp,
        bh.gtvHeight,
        gtv(bh.gtvDependencies.merkleHash(merkleHashCalculator)),
        bh.gtvExtra["l2RootEvent"]!!,
        bh.gtvExtra["l2RootState"]!!
    )
}

private fun blockWitnessData(
    db: DatabaseAccess,
    ctx: EContext,
    blockHeight: Long
): Gtv {
    val blockRid = db.getBlockRID(ctx, blockHeight) ?: return GtvNull
    val witness = BaseBlockWitness.fromBytes(db.getWitnessData(ctx, blockRid)) as MultiSigBlockWitness
    val signatures = witness.getSignatures().map {
        gtv(
            "sig" to GtvByteArray(encodeSignatureWithV(blockRid, it.subjectID, it.data)),
            "pubkey" to GtvByteArray(getEthereumAddress(it.subjectID))
        )
    }
    return GtvArray(signatures.toTypedArray())
}
