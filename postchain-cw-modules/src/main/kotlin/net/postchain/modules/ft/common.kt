// Copyright (c) 2017 ChromaWay Inc. See README for license information.

package net.postchain.modules.ft

import net.postchain.base.CryptoSystem
import net.postchain.core.EContext
import net.postchain.core.TxEContext
import net.postchain.gtx.ExtOpData
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvArray

class OpEContext (val txCtx: TxEContext, val opIndex: Int)

typealias ExtraData = Map<String, Gtv>

val NoExtraData = mapOf<String, Gtv>()

fun getExtraData(data: ExtOpData, idx: Int): ExtraData {
    if (data.args.size > idx) {
        return data.args[idx].asDict()
    } else {
        return NoExtraData
    }
}

open class FTRules<DataT>(val staticRules: Array<(DataT)->Boolean>, val dbRules: Array<(OpEContext, FTDBOps, DataT)->Boolean>) {
    open fun applyStaticRules(data: DataT): Boolean {
        return staticRules.all { it(data) }
    }

    open fun applyDbRules(ctx: OpEContext, dbops: FTDBOps, data: DataT): Boolean {
        return dbRules.all { it(ctx, dbops, data) }
    }
}

interface AccountFactory {
    fun makeInputAccount(accountID: ByteArray, descriptor: GtvArray): FTInputAccount
    fun makeOutputAccount(accountID: ByteArray, descriptor: GtvArray): FTOutputAccount
}

interface AccountResolver {
    fun resolveInputAccount(ctx: OpEContext, dbops: FTDBOps, accountID: ByteArray): FTInputAccount
    fun resolveOutputAccount(ctx: OpEContext, dbops: FTDBOps, accountID: ByteArray): FTOutputAccount
}

/**
 * Configuration for the FT module.
 *
 * @property issueRules rules for the issuing operation
 * @property transferRules rules for the transfer operation
 * @property registerRules rules for the register operation
 * @property accountFactory factory for creating new input and output accounts
 * @property accountResolver resolves accounts given their descriptors
 * @property dbOps database operations
 * @property cryptoSystem cryptographic operations
 * @property blockchainRID reference to the blockchain
 */
open class FTConfig (
        val issueRules : FTIssueRules,
        val transferRules : FTTransferRules,
        val registerRules: FTRegisterRules,
        val accountFactory: AccountFactory,
        val accountResolver: AccountResolver,
        val dbOps : FTDBOps,
        val cryptoSystem: CryptoSystem,
        val blockchainRID: ByteArray
)

class HistoryEntry(val delta: Long,
                   val txRID: ByteArray,
                   val opIndex: Int,
                   val memo: String?)

interface FTDBOps {
    fun update(ctx: OpEContext, accountID: ByteArray, assetID: String, amount: Long, memo: String?, allowNeg: Boolean = false)
    fun registerAccount(ctx: OpEContext, accountID: ByteArray, accountType: Int, accountDesc: ByteArray)
    fun registerAsset(ctx: OpEContext, assetID: String)

    fun getDescriptor(ctx: EContext, accountID: ByteArray): Gtv?
    fun getBalance(ctx: EContext, accountID: ByteArray, assetID: String): Long
    fun getHistory(ctx: EContext, accountID: ByteArray, assetID: String): List<HistoryEntry>
}

